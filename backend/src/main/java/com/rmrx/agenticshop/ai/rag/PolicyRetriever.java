package com.rmrx.agenticshop.ai.rag;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.conversation.ConversationExceptions;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.app.docs.entity.DocumentType;
import com.rmrx.agenticshop.app.identity.entity.Role;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Permission-aware hybrid search over {@code ai_policy_chunk}.
 *
 * <h2>Query</h2>
 * One SQL statement with two candidate lists, both filtered by the caller's ACL, the reference date and the doc
 * types <em>inside</em> their scans (see {@link PolicyChunkStore} for the ACL rule), so a chunk the caller may not
 * see is never ranked, fused or returned; nothing is filtered afterwards in Java:
 * <ul>
 * <li>vector: top {@code vectorCandidates} by cosine distance ({@code embedding <=> :q}, HNSW index);</li>
 * <li>keyword: top {@code keywordCandidates} by {@code ts_rank_cd} for {@code websearch_to_tsquery('english', :q)}
 * with its AND turned into OR (a natural question rarely has every word in one section; ranking still prefers
 * chunks matching more terms), GIN index.</li>
 * </ul>
 * They are fused with Reciprocal Rank Fusion: {@code score = 1/(k + vectorRank) + 1/(k + keywordRank)}, a missing
 * rank contributing 0. The statement also returns each candidate's keyword coverage: the share of the query's
 * distinct terms the chunk contains, each term weighted by its IDF {@code ln(1 + N/(1 + df))} over the chunks the
 * caller may see on that day (N, df), so a word in nearly every visible chunk ("policy" is in every heading path)
 * counts little and a rare one counts a lot. The corpus statistics use the same ACL/time/type filter, so they never
 * reflect chunks the caller cannot see.
 *
 * <h2>HNSW and filters</h2>
 * HNSW applies WHERE conditions to the candidates its graph walk yields; with a selective filter (a customer sees
 * ~30 of ~5500 chunks, a seller only their own contract) the first {@code ef_search} candidates may contain few
 * or no visible chunks, so the index would return fewer rows than asked. Two settings are applied per query
 * ({@code set_config(..., true)}, i.e. transaction-local): {@code hnsw.ef_search} (bigger list = better recall,
 * slower) and, on pgvector 0.8+, {@code hnsw.iterative_scan = relaxed_order}, which keeps walking the graph until
 * enough rows pass the filter (bounded by {@code hnsw.max_scan_tuples}). relaxed_order may return rows slightly out
 * of distance order; the vector rank is recomputed from the exact distance in SQL, so fusion is unaffected. When the
 * filter is very selective the planner may skip HNSW altogether (filter by the ACL/effective indexes, then sort by
 * exact distance), which is exact and cheap at that size. Correctness never depends on the plan: the ACL is a WHERE
 * predicate of both scans.
 *
 * <h2>Relevance</h2>
 * A fused chunk is supporting when its keyword coverage is at least {@code minKeywordCoverage} or its cosine
 * similarity is at least {@code minVectorSimilarity}. Only supporting chunks go to the {@link Reranker}; the top
 * {@code topK} are returned. None means "no supporting policy": the caller must not answer from general knowledge.
 */
@Slf4j
@Component
public class PolicyRetriever {

    /** One ranked chunk. Ranks are 1-based and null when the chunk was not in that list. */
    public record Hit(long chunkId, long documentId, String docCode, String docType, String docTitle, long versionId,
                      int versionNo, String versionLabel, LocalDate effectiveFrom, LocalDate effectiveTo,
                      Long sectionId, String sectionHeading, String headingPath, String text, Long sellerId,
                      String docMinRole, String sectionMinRole, Integer vectorRank, Double vectorSimilarity,
                      Integer keywordRank, Double keywordScore, double keywordCoverage, double fusedScore,
                      boolean supporting) {
    }

    /**
     * The outcome of one search: hits are the supporting chunks after reranking (at most k), candidates every fused
     * chunk (for diagnostics), visibleMinRoles the min_role values the caller meets.
     */
    public record Result(String query, LocalDate referenceDate, Set<DocumentType> docTypes,
                         List<String> visibleMinRoles, List<Hit> hits, List<Hit> candidates) {

        public boolean noSupportingPolicy() {
            return hits.isEmpty();
        }
    }

    /** The ACL rule of {@link PolicyChunkStore}, for alias {@code c}. */
    static final String ACL = """
            ((c.seller_id is not null and c.seller_id = cast(:sellerId as bigint))
                  or ((c.seller_id is null or :isStaff)
                      and c.doc_min_role in (:roles)
                      and c.section_min_role in (:roles)))""";

    static final String TIME = "c.effective_from <= :refDate and (c.effective_to is null or c.effective_to > :refDate)";

    static final String TYPES = "c.doc_type in (:docTypes)";

    static final String KEYWORD_QUERY =
            "cast(replace(websearch_to_tsquery('english', :query)::text, ' & ', ' | ') as tsquery)";

    static final String SEARCH_SQL = """
            with visible as materialized (
                select c.tsv from ai_policy_chunk c
                where %1$s and %2$s and %3$s
            ),
            term_w as (
                select term, ln(1 + n.total::float8
                           / (1 + (select count(*) from visible v where v.tsv @@ quote_literal(term)::tsquery))) as w
                from unnest(tsvector_to_array(to_tsvector('english', :query))) as term,
                     (select count(*) as total from visible) n
            ),
            vec as (
                select id, dist, row_number() over (order by dist, id) as rnk
                from (
                    select c.id, c.embedding <=> cast(:embedding as vector) as dist
                    from ai_policy_chunk c
                    where %1$s and %2$s and %3$s
                    order by c.embedding <=> cast(:embedding as vector)
                    limit :vecN
                ) v
            ),
            kw as (
                select id, kscore, row_number() over (order by kscore desc, id) as rnk
                from (
                    select c.id, ts_rank_cd(c.tsv, %4$s) as kscore
                    from ai_policy_chunk c
                    where c.tsv @@ %4$s and %1$s and %2$s and %3$s
                    order by kscore desc, c.id
                    limit :kwN
                ) k
            ),
            fused as (
                select coalesce(v.id, k.id) as id, v.rnk as vrank, (1 - v.dist)::float8 as vsim, k.rnk as krank,
                       k.kscore::float8 as kscore,
                       (coalesce(1.0 / (:rrfK + v.rnk), 0) + coalesce(1.0 / (:rrfK + k.rnk), 0))::float8 as fused
                from vec v full outer join kw k on k.id = v.id
            )
            select f.vrank, f.vsim, f.krank, f.kscore, f.fused,
                   coalesce((select sum(w) from term_w where c.tsv @@ quote_literal(term)::tsquery)
                            / nullif((select sum(w) from term_w), 0), 0) as coverage,
                   c.id, c.document_id, c.doc_code, c.doc_type, c.doc_title, c.document_version_id, c.version_no,
                   c.version_label, c.effective_from, c.effective_to, c.section_id, c.section_heading,
                   c.heading_path, c.text, c.seller_id, c.doc_min_role, c.section_min_role
            from fused f join ai_policy_chunk c on c.id = f.id
            order by f.fused desc, c.id
            """.formatted(ACL, TIME, TYPES, KEYWORD_QUERY);

    private static final Set<String> ITERATIVE_SCAN_MODES = Set.of("off", "relaxed_order", "strict_order");

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final Reranker reranker;
    private final AiProperties.Rag props;
    private volatile Boolean iterativeScanSupported;

    public PolicyRetriever(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                           ObjectProvider<EmbeddingModel> embeddingModel, Reranker reranker, AiProperties props) {
        this.plainJdbc = jdbc;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.tx = new TransactionTemplate(txManager);
        this.tx.setReadOnly(true);
        this.embeddingModel = embeddingModel;
        this.reranker = reranker;
        this.props = props.rag();
    }

    /**
     * Default corpus per role; the ACL filter still decides what is visible inside it. Customers: public policies.
     * Sellers: public policies (incl. seller-only ones) and seller contracts (the ACL leaves only their own).
     * Support: public policies and SOPs. Finance: public policies and seller contracts. Fraud: public policies and
     * the fraud playbook. Admin: everything.
     */
    public static Set<DocumentType> defaultDocTypes(Role role) {
        return switch (role) {
            case CUSTOMER -> EnumSet.of(DocumentType.PUBLIC_POLICY);
            case SELLER, FINANCE -> EnumSet.of(DocumentType.PUBLIC_POLICY, DocumentType.SELLER_CONTRACT);
            case SUPPORT_T1, SUPPORT_T2 -> EnumSet.of(DocumentType.PUBLIC_POLICY, DocumentType.SUPPORT_SOP);
            case FRAUD -> EnumSet.of(DocumentType.PUBLIC_POLICY, DocumentType.FRAUD_PLAYBOOK);
            case ADMIN -> EnumSet.allOf(DocumentType.class);
        };
    }

    /** The min_role values this role meets (same rule as {@code AccessScope.visibleMinRoles}). */
    public static List<String> visibleMinRoles(Role role) {
        return Arrays.stream(Role.values()).filter(role::satisfies).map(Enum::name).toList();
    }

    /**
     * Searches the chunks ctx may see that were in force on referenceDate. docTypes null/empty = the role's default
     * corpus; k &lt;= 0 = the configured topK. The query embedding is billed as EMBEDDING_QUERY to the bound
     * {@link RunScope} (the turn's user, ticket and run), or to ctx's user and ticket when none is bound.
     */
    public Result search(AgentContext ctx, String query, LocalDate referenceDate, Set<DocumentType> docTypes, int k) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        Set<DocumentType> types = docTypes == null || docTypes.isEmpty() ? defaultDocTypes(ctx.role()) : docTypes;
        LocalDate day = referenceDate == null ? LocalDate.now(java.time.ZoneOffset.UTC) : referenceDate;
        int topK = k > 0 ? k : props.topK();
        List<String> roles = visibleMinRoles(ctx.role());

        float[] vector = embed(ctx, query);
        MapSqlParameterSource params = params(ctx, query, day, types, roles, vector);
        List<Hit> candidates = tx.execute(status -> {
            applyHnswSettings();
            return jdbc.query(SEARCH_SQL, params, (rs, n) -> hit(rs));
        });
        List<Hit> supporting = candidates.stream().filter(Hit::supporting).toList();
        List<Hit> reranked = reranker.rerank(query, supporting);
        List<Hit> hits = reranked.stream().limit(topK).toList();
        log.debug("Policy search for user {} ({}): {} candidates, {} supporting, {} returned", ctx.userId(),
                ctx.role(), candidates.size(), supporting.size(), hits.size());
        return new Result(query, day, types, roles, hits, candidates);
    }

    /** EXPLAIN (ANALYZE, BUFFERS) of the search statement with the same parameters and settings (dev only). */
    public List<String> explain(AgentContext ctx, String query, LocalDate referenceDate, Set<DocumentType> docTypes) {
        Set<DocumentType> types = docTypes == null || docTypes.isEmpty() ? defaultDocTypes(ctx.role()) : docTypes;
        LocalDate day = referenceDate == null ? LocalDate.now(java.time.ZoneOffset.UTC) : referenceDate;
        MapSqlParameterSource params = params(ctx, query, day, types, visibleMinRoles(ctx.role()), embed(ctx, query));
        return tx.execute(status -> {
            applyHnswSettings();
            return jdbc.query("explain (analyze, buffers, costs off) " + SEARCH_SQL, params,
                    (rs, n) -> rs.getString(1));
        });
    }

    private MapSqlParameterSource params(AgentContext ctx, String query, LocalDate day, Set<DocumentType> types,
                                         List<String> roles, float[] vector) {
        return new MapSqlParameterSource()
                .addValue("sellerId", ctx.isSeller() ? ctx.sellerId() : null, java.sql.Types.BIGINT)
                .addValue("isStaff", ctx.isStaff())
                .addValue("roles", roles)
                .addValue("refDate", Date.valueOf(day))
                .addValue("docTypes", types.stream().map(Enum::name).toList())
                .addValue("query", query)
                .addValue("embedding", PolicyChunkStore.formatVector(vector))
                .addValue("vecN", props.vectorCandidates())
                .addValue("kwN", props.keywordCandidates())
                .addValue("rrfK", props.rrfK());
    }

    private float[] embed(AgentContext ctx, String query) {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) throw new ConversationExceptions.AiUnavailable("AI is disabled: no embedding model");
        RunScope scope = RunScopes.current()
                .map(s -> s.withPurpose(AiUsagePurpose.EMBEDDING_QUERY))
                .orElseGet(() -> RunScope.of(ctx, null, AiUsagePurpose.EMBEDDING_QUERY));
        return RunScopes.get(scope, () -> model.embed(query).content().vector());
    }

    /** Transaction-local HNSW settings; must run inside the search transaction. */
    private void applyHnswSettings() {
        plainJdbc.queryForObject("select set_config('hnsw.ef_search', ?, true)", String.class,
                String.valueOf(Math.max(props.hnswEfSearch(), props.vectorCandidates())));
        String mode = props.hnswIterativeScan() == null ? "off" : props.hnswIterativeScan().strip().toLowerCase();
        if (!ITERATIVE_SCAN_MODES.contains(mode)) {
            log.warn("Unknown hnsw-iterative-scan '{}'; using off", mode);
            mode = "off";
        }
        if (!"off".equals(mode) && iterativeScanSupported()) {
            plainJdbc.queryForObject("select set_config('hnsw.iterative_scan', ?, true)", String.class, mode);
        }
    }

    /** hnsw.iterative_scan exists from pgvector 0.8.0; checked once against the installed extension. */
    private boolean iterativeScanSupported() {
        Boolean supported = iterativeScanSupported;
        if (supported == null) {
            String version = plainJdbc.queryForObject(
                    "select coalesce((select extversion from pg_extension where extname = 'vector'), '0')",
                    String.class);
            supported = atLeast(version, 0, 8);
            log.info("pgvector {}: hnsw.iterative_scan {}", version, supported ? "available" : "not available");
            iterativeScanSupported = supported;
        }
        return supported;
    }

    static boolean atLeast(String version, int major, int minor) {
        try {
            String[] parts = version.split("\\.");
            int ma = Integer.parseInt(parts[0]);
            int mi = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return ma > major || (ma == major && mi >= minor);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Hit hit(ResultSet rs) throws SQLException {
        Long vrankRaw = rs.getObject("vrank", Long.class);
        Long krankRaw = rs.getObject("krank", Long.class);
        Integer vrank = vrankRaw == null ? null : vrankRaw.intValue();
        Integer krank = krankRaw == null ? null : krankRaw.intValue();
        Double vsim = rs.getObject("vsim", Double.class);
        Double kscore = rs.getObject("kscore", Double.class);
        double coverage = rs.getDouble("coverage");
        boolean supporting = coverage >= props.minKeywordCoverage()
                || (vsim != null && vsim >= props.minVectorSimilarity());
        Date to = rs.getDate("effective_to");
        return new Hit(rs.getLong("id"), rs.getLong("document_id"), rs.getString("doc_code"),
                rs.getString("doc_type"), rs.getString("doc_title"), rs.getLong("document_version_id"),
                rs.getInt("version_no"), rs.getString("version_label"), rs.getDate("effective_from").toLocalDate(),
                to == null ? null : to.toLocalDate(), rs.getObject("section_id", Long.class),
                rs.getString("section_heading"), rs.getString("heading_path"), rs.getString("text"),
                rs.getObject("seller_id", Long.class), rs.getString("doc_min_role"),
                rs.getString("section_min_role"), vrank, vsim, krank, kscore, coverage, rs.getDouble("fused"),
                supporting);
    }

    /** Exposed for the dev endpoint's report. */
    public AiProperties.Rag settings() {
        return props;
    }
}
