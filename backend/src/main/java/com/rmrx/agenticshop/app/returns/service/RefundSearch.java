package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.returns.entity.RefundStatus;

import java.time.Instant;
import java.util.List;

/**
 * Refund filters; null or empty values match everything. Customer and seller filters are narrowed to the caller's
 * own scope by the service.
 *
 * @param from created at or after (inclusive)
 * @param to   created before (exclusive)
 */
public record RefundSearch(String orderNumber, Long orderId, List<RefundStatus> statuses, Long customerId,
                           Long sellerId, Instant from, Instant to) {
}
