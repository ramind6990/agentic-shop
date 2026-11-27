package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import com.rmrx.agenticshop.app.returns.entity.ReturnStatus;

import java.time.Instant;
import java.util.List;

/**
 * Return request filters; null or empty values match everything. Customer and seller filters are narrowed to the
 * caller's own scope by the service.
 *
 * @param from opened at or after (inclusive)
 * @param to   opened before (exclusive)
 */
public record ReturnSearch(String orderNumber, List<ReturnStatus> statuses, ReturnReason reason, Long customerId,
                           Long sellerId, Instant from, Instant to) {
}
