package com.rmrx.agenticshop.ai.contract.domain;

/** Order of product search results; RELEVANCE favours popular products. */
public enum ProductSort {
    RELEVANCE,
    BESTSELLING,
    PRICE_ASC,
    PRICE_DESC,
    RATING,
    NEWEST
}
