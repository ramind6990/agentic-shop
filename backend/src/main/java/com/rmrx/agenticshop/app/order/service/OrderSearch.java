package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * Order filters; null or empty values match everything. Customer and seller filters are narrowed to the caller's own
 * scope by the service.
 *
 * @param q            part of the order number
 * @param productQuery words that must all appear in the title of one of the order's lines
 * @param from         placed at or after (inclusive)
 * @param to           placed before (exclusive)
 */
public record OrderSearch(String q, List<OrderStatus> statuses, Long customerId, Long sellerId, Instant from,
                          Instant to, String productQuery) {
}
