package com.rmrx.agenticshop.ai.rag;

import com.rmrx.agenticshop.ai.rag.PolicyChunker.SectionSource;
import com.rmrx.agenticshop.ai.rag.PolicyChunker.VersionSource;
import com.rmrx.agenticshop.ai.rag.RagDtos.CountRow;
import com.rmrx.agenticshop.ai.rag.RagDtos.VersionCountRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.*;

/**
 * JDBC access to {@code ai_policy_chunk} (not a JPA entity: the vector and tsvector columns stay out of Hibernate)
 * and to the document rows it is built from.
 *
 * <h2>How retrieval must filter chunks (ACL)</h2>
 * The columns copy the inputs of {@code security.AccessScope.canSeeDocument} and {@code canSeeSection}, so the rule
 * can run inside the search query. For a caller with role {@code R}, seller {@code S} (null unless SELLER) and
 * {@code :roles} = the min_role values R satisfies ({@code AccessScope.visibleMinRoles()}):
 * <pre>
 *   (seller_id is not null and seller_id = :S)                 -- own tenant document: every section
 *   or ((seller_id is null or :isStaff)                         -- other tenants' documents: staff only
 *       and doc_min_role = any(:roles)                          -- canSeeDocument
 *       and section_min_role = any(:roles))                     -- canSeeSection
 * </pre>
 * Time filter for "the policy in force on day D": {@code effective_from <= D and (effective_to is null or
 * effective_to > D)} (effective_to exclusive, as in {@code DocumentVersionRepository.findEffective}). {@code doc_type}
 * narrows the corpus (e.g. PUBLIC_POLICY for customers). {@code embedding} (HNSW, cosine) serves vector search and
 * {@code tsv} (GIN, english, heading path + text) keyword search. The ACL columns are snapshots: a change to a
 * document's or section's min_role/seller is picked up by the next reindex (metadata-only update, no re-embedding).
 */
@Repository
public class PolicyChunkStore {

    private final JdbcTemplate jdbc;

    public PolicyChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** An existing chunk row without its embedding. */
    public record ExistingChunk(long id, long versionId, Long sectionId, int chunkIndex, String contentHash,
                                String metaKey) {
    }

    /** A chunk row to insert. */
    public record NewChunk(VersionSource version, PolicyChunker.Chunk chunk, int tokenCount, String contentHash,
                           float[] embedding, String model) {
    }

    public List<Long> versionIds() {
        return jdbc.queryForList("select id from document_version order by id", Long.class);
    }

    /** The versions (all when versionIds is null) with document metadata and sections, by version id. */
    public List<VersionSource> loadVersions(Collection<Long> versionIds) {
        String where = versionIds == null ? "" : " where v.id = any(?)";
        Object[] args = versionIds == null ? new Object[0] : new Object[]{longArray(versionIds)};
        Map<Long, List<SectionSource>> sections = new HashMap<>();
        jdbc.query("""
                select s.id, s.version_id, s.ordinal, s.heading, s.body, s.min_role
                from document_section s join document_version v on v.id = s.version_id""" + where
                        + " order by s.version_id, s.ordinal, s.id",
                rs -> {
                    sections.computeIfAbsent(rs.getLong("version_id"), k -> new ArrayList<>())
                            .add(new SectionSource(rs.getLong("id"), rs.getInt("ordinal"), rs.getString("heading"),
                                    rs.getString("body"), rs.getString("min_role")));
                }, args);
        return jdbc.query("""
                select d.id as doc_id, d.code, d.type, d.title, d.min_role, d.seller_id,
                       v.id as version_id, v.version_no, v.effective_from, v.effective_to, v.summary
                from document_version v join document d on d.id = v.document_id""" + where + " order by v.id",
                (rs, n) -> new VersionSource(rs.getLong("doc_id"), rs.getString("code"), rs.getString("type"),
                        rs.getString("title"), rs.getString("min_role"), (Long) rs.getObject("seller_id", Long.class),
                        rs.getLong("version_id"), rs.getInt("version_no"), localDate(rs, "effective_from"),
                        localDate(rs, "effective_to"), rs.getString("summary"),
                        List.copyOf(sections.getOrDefault(rs.getLong("version_id"), List.of()))),
                args);
    }

    /** Existing chunks (all when versionIds is null), grouped by version id. */
    public Map<Long, List<ExistingChunk>> loadExisting(Collection<Long> versionIds) {
        String where = versionIds == null ? "" : " where document_version_id = any(?)";
        Object[] args = versionIds == null ? new Object[0] : new Object[]{longArray(versionIds)};
        Map<Long, List<ExistingChunk>> out = new HashMap<>();
        jdbc.query("""
                select id, document_version_id, section_id, chunk_index, content_hash,
                       doc_code, doc_type, seller_id, doc_min_role, section_min_role, section_ordinal, heading_path
                from ai_policy_chunk""" + where,
                rs -> {
                    long versionId = rs.getLong("document_version_id");
                    out.computeIfAbsent(versionId, k -> new ArrayList<>()).add(new ExistingChunk(
                            rs.getLong("id"), versionId, rs.getObject("section_id", Long.class),
                            rs.getInt("chunk_index"), rs.getString("content_hash"),
                            metaKey(rs.getString("doc_code"), rs.getString("doc_type"),
                                    rs.getObject("seller_id", Long.class), rs.getString("doc_min_role"),
                                    rs.getString("section_min_role"), rs.getObject("section_ordinal", Integer.class),
                                    rs.getString("heading_path"))));
                }, args);
        return out;
    }

    /** The ACL/display metadata that may change without changing the embedded text. */
    public static String metaKey(String docCode, String docType, Long sellerId, String docMinRole,
                                 String sectionMinRole, Integer sectionOrdinal, String headingPath) {
        return String.join("|", docCode, docType, String.valueOf(sellerId), docMinRole, sectionMinRole,
                String.valueOf(sectionOrdinal), headingPath);
    }

    public Map<Long, float[]> loadEmbeddings(Collection<Long> chunkIds) {
        Map<Long, float[]> out = new HashMap<>();
        if (chunkIds.isEmpty()) return out;
        jdbc.query("select id, embedding::text as embedding from ai_policy_chunk where id = any(?)", rs -> {
            out.put(rs.getLong("id"), parseVector(rs.getString("embedding")));
        }, (Object) longArray(chunkIds));
        return out;
    }

    /** Applies one version's changes; call inside a transaction so the version is written all-or-nothing. */
    public void apply(Collection<Long> deleteIds, List<NewChunk> inserts, Map<Long, NewChunk> metadataUpdates) {
        if (!deleteIds.isEmpty()) {
            jdbc.update("delete from ai_policy_chunk where id = any(?)", (Object) longArray(deleteIds));
        }
        if (!inserts.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into ai_policy_chunk (document_id, document_version_id, section_id, doc_code, doc_type,
                        seller_id, doc_min_role, section_min_role, version_no, version_label, effective_from,
                        effective_to, doc_title, section_heading, heading_path, section_ordinal, chunk_index, text,
                        char_count, token_count, content_hash, embedding, embedding_model)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
                    """, inserts, 200, (ps, row) -> bindInsert(ps, row));
        }
        if (!metadataUpdates.isEmpty()) {
            List<Map.Entry<Long, NewChunk>> rows = new ArrayList<>(metadataUpdates.entrySet());
            jdbc.batchUpdate("""
                    update ai_policy_chunk set doc_code = ?, doc_type = ?, seller_id = ?, doc_min_role = ?,
                        section_min_role = ?, section_ordinal = ?, heading_path = ?, updated_at = now()
                    where id = ?
                    """, rows, 200, (ps, e) -> {
                NewChunk c = e.getValue();
                ps.setString(1, c.version().docCode());
                ps.setString(2, c.version().docType());
                setLong(ps, 3, c.version().sellerId());
                ps.setString(4, c.version().docMinRole());
                ps.setString(5, c.chunk().sectionMinRole());
                setInt(ps, 6, c.chunk().sectionOrdinal());
                ps.setString(7, c.chunk().headingPath());
                ps.setLong(8, e.getKey());
            });
        }
    }

    private static void bindInsert(PreparedStatement ps, NewChunk row) throws SQLException {
        VersionSource v = row.version();
        PolicyChunker.Chunk c = row.chunk();
        ps.setLong(1, v.documentId());
        ps.setLong(2, v.versionId());
        setLong(ps, 3, c.sectionId());
        ps.setString(4, v.docCode());
        ps.setString(5, v.docType());
        setLong(ps, 6, v.sellerId());
        ps.setString(7, v.docMinRole());
        ps.setString(8, c.sectionMinRole());
        ps.setInt(9, v.versionNo());
        ps.setString(10, v.versionLabel());
        ps.setDate(11, Date.valueOf(v.effectiveFrom()));
        if (v.effectiveTo() == null) ps.setNull(12, Types.DATE);
        else ps.setDate(12, Date.valueOf(v.effectiveTo()));
        ps.setString(13, v.docTitle());
        ps.setString(14, c.sectionHeading());
        ps.setString(15, c.headingPath());
        setInt(ps, 16, c.sectionOrdinal());
        ps.setInt(17, c.chunkIndex());
        ps.setString(18, c.text());
        ps.setInt(19, c.text().length());
        ps.setInt(20, row.tokenCount());
        ps.setString(21, row.contentHash());
        ps.setString(22, formatVector(row.embedding()));
        ps.setString(23, row.model());
    }

    public long countChunks() {
        Long n = jdbc.queryForObject("select count(*) from ai_policy_chunk", Long.class);
        return n == null ? 0 : n;
    }

    public long countIndexedVersions() {
        Long n = jdbc.queryForObject("select count(distinct document_version_id) from ai_policy_chunk", Long.class);
        return n == null ? 0 : n;
    }

    public long countVersions() {
        Long n = jdbc.queryForObject("select count(*) from document_version", Long.class);
        return n == null ? 0 : n;
    }

    public List<CountRow> countBy(String column) {
        if (!Set.of("doc_type", "section_min_role", "doc_min_role", "embedding_model").contains(column)) {
            throw new IllegalArgumentException(column);
        }
        return jdbc.query("select " + column + " as k, count(*) as n from ai_policy_chunk group by 1 order by 1",
                (rs, n) -> new CountRow(rs.getString("k"), rs.getLong("n")));
    }

    public List<VersionCountRow> countByVersion(String docType) {
        String where = docType == null ? "" : " where doc_type = ?";
        Object[] args = docType == null ? new Object[0] : new Object[]{docType};
        return jdbc.query("""
                select document_version_id, doc_code, doc_type, version_label, effective_from, effective_to,
                       count(*) as chunks, count(distinct section_id) as sections, sum(token_count) as tokens,
                       max(updated_at) as updated_at
                from ai_policy_chunk""" + where + " " + """
                 group by document_version_id, doc_code, doc_type, version_label, effective_from, effective_to
                 order by doc_type, doc_code, document_version_id""",
                (rs, n) -> new VersionCountRow(rs.getLong("document_version_id"), rs.getString("doc_code"),
                        rs.getString("doc_type"), rs.getString("version_label"), localDate(rs, "effective_from"),
                        localDate(rs, "effective_to"), rs.getLong("chunks"), rs.getLong("sections"),
                        rs.getLong("tokens"), rs.getTimestamp("updated_at").toInstant()),
                args);
    }

    /** pgvector text form: [0.1,0.2,...] (passed as text and cast, so no driver type registration is needed). */
    static String formatVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 12).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    static float[] parseVector(String text) {
        String body = text.strip();
        body = body.substring(1, body.length() - 1);
        if (body.isBlank()) return new float[0];
        String[] parts = body.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Float.parseFloat(parts[i].strip());
        return v;
    }

    private static Long[] longArray(Collection<Long> ids) {
        return ids.toArray(Long[]::new);
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static void setLong(PreparedStatement ps, int i, Long value) throws SQLException {
        if (value == null) ps.setNull(i, Types.BIGINT);
        else ps.setLong(i, value);
    }

    private static void setInt(PreparedStatement ps, int i, Integer value) throws SQLException {
        if (value == null) ps.setNull(i, Types.INTEGER);
        else ps.setInt(i, value);
    }
}
