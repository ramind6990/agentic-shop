package com.rmrx.agenticshop.ai.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Retrieval beans with defaults that another configuration may replace. */
@Configuration(proxyBeanMethods = false)
public class RagConfig {

    /** No reranking: the RRF order is final. */
    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    public Reranker noopReranker() {
        return Reranker.NONE;
    }
}
