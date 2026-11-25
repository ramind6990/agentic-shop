package com.rmrx.agenticshop.ai.rag;

import java.util.List;

/**
 * Reorders the fused, relevance-filtered chunks of one search before the top k are taken (e.g. a cross-encoder).
 * It may drop chunks but must never add any: every chunk it gets already passed the ACL filter in SQL. The default
 * bean ({@link #NONE}) keeps the fused order.
 */
public interface Reranker {

    Reranker NONE = (query, hits) -> hits;

    List<PolicyRetriever.Hit> rerank(String query, List<PolicyRetriever.Hit> hits);
}
