package com.rmrx.agenticshop.ai.rag;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.conversation.ConversationExceptions;
import com.rmrx.agenticshop.ai.rag.PolicyChunkStore.ExistingChunk;
import com.rmrx.agenticshop.ai.rag.PolicyChunkStore.NewChunk;
import com.rmrx.agenticshop.ai.rag.PolicyChunker.Chunk;
import com.rmrx.agenticshop.ai.rag.PolicyChunker.VersionSource;
import com.rmrx.agenticshop.ai.rag.RagDtos.RagStats;
import com.rmrx.agenticshop.ai.rag.RagDtos.ReindexResult;
import com.rmrx.agenticshop.ai.rag.RagDtos.VersionError;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Builds {@code ai_policy_chunk} from the document versions: chunk ({@link PolicyChunker}), embed in batches, write.
 *
 * <p><b>Idempotent per version.</b> Each chunk carries a content hash of its embedded text + model + dimensions.
 * A chunk whose (section, index) row already has that hash is kept (its ACL/display columns are refreshed if the
 * source changed); a new hash reuses the embedding of a row of the same version with that hash, otherwise it is
 * embedded; rows no longer produced are deleted. Re-ingesting an unchanged version writes nothing and makes no
 * embedding call.
 *
 * <p><b>All-or-nothing per version.</b> All embeddings a version needs are computed before anything is written; the
 * version's deletes, inserts and updates then run in one transaction. A failed embedding batch (after the client's
 * own retries) is retried per version, so only the version whose text fails is reported; it keeps its previous
 * chunks, and the other versions continue.
 *
 * <p>Embedding calls are billed as {@code INGESTION} system usage (no user, ticket or run). One reindex runs at a
 * time. Ingestion is manual unless {@code app.ai.rag.reindex-on-startup=true}.
 */
@Slf4j
@Service
public class PolicyIngestionService {

    private final PolicyChunker chunker;
    private final PolicyChunkStore store;
    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final AiProperties props;
    private final TransactionTemplate tx;
    private final ExecutorService aiExecutor;
    private final ReentrantLock running = new ReentrantLock();

    public PolicyIngestionService(PolicyChunker chunker, PolicyChunkStore store,
                                  ObjectProvider<EmbeddingModel> embeddingModel, AiProperties props,
                                  PlatformTransactionManager transactionManager,
                                  @Qualifier("aiExecutor") ExecutorService aiExecutor) {
        this.chunker = chunker;
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
        this.aiExecutor = aiExecutor;
    }

    /** Thrown when a reindex is requested while another one runs. */
    public static class ReindexInProgress extends RuntimeException {
        public ReindexInProgress() {
            super("A policy reindex is already running");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void reindexOnStartup() {
        if (!props.rag().reindexOnStartup() || embeddingModel.getIfAvailable() == null) return;
        aiExecutor.submit(() -> {
            try {
                reindexAll();
            } catch (RuntimeException e) {
                log.error("Startup policy reindex failed", e);
            }
        });
    }

    public ReindexResult reindexAll() {
        return locked(() -> run(store.loadVersions(null), store.loadExisting(null)));
    }

    public ReindexResult reindexVersion(long versionId) {
        return locked(() -> {
            List<VersionSource> versions = store.loadVersions(List.of(versionId));
            if (versions.isEmpty()) throw new NotFoundException("Document version", versionId);
            return run(versions, store.loadExisting(List.of(versionId)));
        });
    }

    public RagStats stats(String docType) {
        requireModel();
        return new RagStats(store.countChunks(), store.countVersions(), store.countIndexedVersions(),
                store.countBy("doc_type"), store.countBy("section_min_role"), store.countBy("doc_min_role"),
                store.countBy("embedding_model"), store.countByVersion(docType));
    }

    private void requireModel() {
        if (embeddingModel.getIfAvailable() == null) {
            throw new ConversationExceptions.AiUnavailable(
                    "AI is not enabled on this server (app.ai.enabled=false); policy ingestion needs the embedding model");
        }
    }

    private ReindexResult locked(java.util.function.Supplier<ReindexResult> work) {
        requireModel();
        if (!running.tryLock()) throw new ReindexInProgress();
        try {
            return work.get();
        } finally {
            running.unlock();
        }
    }

    // ------------------------------------------------------------------------------------------------ planning

    /** One version's work: rows to keep, insert (embedding needed or reused), refresh and delete. */
    private static final class Plan {
        final VersionSource version;
        final List<NewChunk> inserts = new ArrayList<>();
        /** Index into inserts -> existing chunk id whose embedding is reused. */
        final Map<Integer, Long> reuse = new HashMap<>();
        /** Indexes into inserts that need an embedding call. */
        final List<Integer> toEmbed = new ArrayList<>();
        final Map<Long, NewChunk> metadataUpdates = new HashMap<>();
        final Set<Long> deletes = new HashSet<>();
        int unchanged;
        String error;

        Plan(VersionSource version) {
            this.version = version;
        }

        boolean noop() {
            return inserts.isEmpty() && metadataUpdates.isEmpty() && deletes.isEmpty();
        }
    }

    private record Key(Long sectionId, int chunkIndex) {
    }

    private Plan plan(VersionSource version, List<ExistingChunk> existing, String model, int dims) {
        Plan plan = new Plan(version);
        Map<Key, ExistingChunk> byKey = new HashMap<>();
        Map<String, Long> idByHash = new HashMap<>();
        for (ExistingChunk e : existing) {
            byKey.put(new Key(e.sectionId(), e.chunkIndex()), e);
            idByHash.putIfAbsent(e.contentHash(), e.id());
        }
        Set<Long> kept = new HashSet<>();
        for (Chunk chunk : chunker.chunk(version)) {
            String hash = hash(model, dims, chunk.embeddedText());
            NewChunk row = new NewChunk(version, chunk, approxTokens(chunk.embeddedText()), hash, null, model);
            ExistingChunk current = byKey.get(new Key(chunk.sectionId(), chunk.chunkIndex()));
            if (current != null && current.contentHash().equals(hash)) {
                kept.add(current.id());
                plan.unchanged++;
                String meta = PolicyChunkStore.metaKey(version.docCode(), version.docType(), version.sellerId(),
                        version.docMinRole(), chunk.sectionMinRole(), chunk.sectionOrdinal(), chunk.headingPath());
                if (!meta.equals(current.metaKey())) plan.metadataUpdates.put(current.id(), row);
                continue;
            }
            int index = plan.inserts.size();
            plan.inserts.add(row);
            Long reusable = idByHash.get(hash);
            if (reusable != null) plan.reuse.put(index, reusable);
            else plan.toEmbed.add(index);
        }
        for (ExistingChunk e : existing) {
            if (!kept.contains(e.id())) plan.deletes.add(e.id());
        }
        return plan;
    }

    // ------------------------------------------------------------------------------------------------ running

    private ReindexResult run(List<VersionSource> versions, Map<Long, List<ExistingChunk>> existing) {
        long started = System.currentTimeMillis();
        EmbeddingModel model = embeddingModel.getObject();
        String modelName = props.embedding().model();
        int dims = props.embedding().dimensions();
        int batchSize = Math.max(1, props.rag().embeddingBatchSize());
        Counters counters = new Counters();

        List<Plan> plans = new ArrayList<>(versions.size());
        for (VersionSource v : versions) plans.add(plan(v, existing.getOrDefault(v.versionId(), List.of()), modelName, dims));

        // groups of consecutive versions filling at most one embedding batch (a larger version alone), embedded then
        // written, so small versions share requests and memory stays bounded
        List<Plan> group = new ArrayList<>();
        int pending = 0;
        for (Plan plan : plans) {
            if (!group.isEmpty() && pending + plan.toEmbed.size() > batchSize) {
                processGroup(group, model, batchSize, counters);
                group.clear();
                pending = 0;
            }
            group.add(plan);
            pending += plan.toEmbed.size();
        }
        if (!group.isEmpty()) processGroup(group, model, batchSize, counters);

        List<VersionError> errors = new ArrayList<>();
        int changed = 0, unchangedVersions = 0, created = 0, unchangedChunks = 0, deleted = 0, meta = 0, reused = 0;
        for (Plan p : plans) {
            if (p.error != null) {
                errors.add(new VersionError(p.version.versionId(), p.version.docCode(), p.version.versionLabel(), p.error));
                continue;
            }
            unchangedChunks += p.unchanged;
            if (p.noop()) {
                unchangedVersions++;
                continue;
            }
            changed++;
            created += p.toEmbed.size();
            reused += p.reuse.size();
            deleted += p.deletes.size();
            meta += p.metadataUpdates.size();
        }
        ReindexResult result = new ReindexResult(plans.size(), changed, unchangedVersions, errors.size(), created,
                unchangedChunks, deleted, meta, reused, counters.calls, counters.tokens,
                System.currentTimeMillis() - started, errors);
        log.info("Policy reindex: {} versions ({} changed, {} unchanged, {} failed); chunks {} created, {} unchanged, "
                        + "{} deleted, {} metadata-updated, {} reused; {} embedding calls, {} tokens, {} ms",
                result.versionsTotal(), changed, unchangedVersions, errors.size(), created, unchangedChunks, deleted,
                meta, reused, counters.calls, counters.tokens, result.durationMs());
        return result;
    }

    private static final class Counters {
        int calls;
        long tokens;
    }

    /** Embeds what the group's versions need, then writes each version that got all its embeddings. */
    private void processGroup(List<Plan> group, EmbeddingModel model, int batchSize, Counters counters) {
        record Slot(Plan plan, int insertIndex) {
        }
        List<Slot> slots = new ArrayList<>();
        for (Plan p : group) for (int i : p.toEmbed) slots.add(new Slot(p, i));

        for (int from = 0; from < slots.size(); from += batchSize) {
            List<Slot> batch = slots.subList(from, Math.min(slots.size(), from + batchSize));
            try {
                List<float[]> vectors = embed(model, batch.stream().map(s -> s.plan().inserts.get(s.insertIndex())).toList(), counters);
                for (int i = 0; i < batch.size(); i++) setEmbedding(batch.get(i).plan(), batch.get(i).insertIndex(), vectors.get(i));
            } catch (RuntimeException batchError) {
                // isolate: embed each affected version on its own so only the failing one is reported
                LinkedHashSet<Plan> affected = new LinkedHashSet<>();
                for (Slot s : batch) affected.add(s.plan());
                log.warn("Embedding batch of {} chunks ({} versions) failed: {}; retrying per version",
                        batch.size(), affected.size(), batchError.toString());
                for (Plan p : affected) {
                    if (p.error != null) continue;
                    List<Integer> missing = p.toEmbed.stream().filter(i -> p.inserts.get(i).embedding() == null).toList();
                    try {
                        for (int f = 0; f < missing.size(); f += batchSize) {
                            List<Integer> part = missing.subList(f, Math.min(missing.size(), f + batchSize));
                            List<float[]> vectors = embed(model, part.stream().map(p.inserts::get).toList(), counters);
                            for (int i = 0; i < part.size(); i++) setEmbedding(p, part.get(i), vectors.get(i));
                        }
                    } catch (RuntimeException e) {
                        p.error = "Embedding failed: " + e.getMessage();
                        log.warn("Policy version {} ({} {}) not reindexed, previous chunks kept: {}",
                                p.version.versionId(), p.version.docCode(), p.version.versionLabel(), e.toString());
                    }
                }
            }
        }
        for (Plan p : group) {
            if (p.error == null && !p.noop()) write(p);
        }
    }

    private static void setEmbedding(Plan plan, int insertIndex, float[] vector) {
        NewChunk row = plan.inserts.get(insertIndex);
        plan.inserts.set(insertIndex, new NewChunk(row.version(), row.chunk(), row.tokenCount(), row.contentHash(),
                vector, row.model()));
    }

    private List<float[]> embed(EmbeddingModel model, List<NewChunk> rows, Counters counters) {
        List<TextSegment> segments = rows.stream().map(r -> TextSegment.from(r.chunk().embeddedText())).toList();
        Response<List<Embedding>> response = RunScopes.get(RunScope.system(AiUsagePurpose.INGESTION),
                () -> model.embedAll(segments));
        counters.calls++;
        if (response.tokenUsage() != null && response.tokenUsage().inputTokenCount() != null) {
            counters.tokens += response.tokenUsage().inputTokenCount();
        }
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != segments.size()) {
            throw new IllegalStateException("Embedding response has " + (embeddings == null ? 0 : embeddings.size())
                    + " vectors for " + segments.size() + " inputs");
        }
        int dims = props.embedding().dimensions();
        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (Embedding e : embeddings) {
            if (e.dimension() != dims) {
                throw new IllegalStateException("Embedding has " + e.dimension() + " dimensions, expected " + dims);
            }
            vectors.add(e.vector());
        }
        return vectors;
    }

    /** One transaction per version: deletes, inserts (with reused embeddings) and metadata refreshes. */
    private void write(Plan p) {
        try {
            tx.executeWithoutResult(status -> {
                if (!p.reuse.isEmpty()) {
                    Map<Long, float[]> reused = store.loadEmbeddings(new HashSet<>(p.reuse.values()));
                    p.reuse.forEach((index, id) -> {
                        float[] vector = reused.get(id);
                        if (vector == null) throw new IllegalStateException("Reusable chunk " + id + " disappeared");
                        setEmbedding(p, index, vector);
                    });
                }
                store.apply(p.deletes, p.inserts, p.metadataUpdates);
            });
        } catch (RuntimeException e) {
            p.error = "Write failed: " + e.getMessage();
            log.warn("Policy version {} ({} {}) write failed, previous chunks kept", p.version.versionId(),
                    p.version.docCode(), p.version.versionLabel(), e);
        }
    }

    private static int approxTokens(String text) {
        return (text.length() + 3) / 4;
    }

    static String hash(String model, int dims, String embeddedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((model + "|" + dims + "|" + embeddedText).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
