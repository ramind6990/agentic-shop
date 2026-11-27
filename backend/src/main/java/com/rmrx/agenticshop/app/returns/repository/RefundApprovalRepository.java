package com.rmrx.agenticshop.app.returns.repository;

import com.rmrx.agenticshop.app.returns.entity.RefundApproval;
import com.rmrx.agenticshop.app.returns.entity.RefundApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface RefundApprovalRepository extends JpaRepository<RefundApproval, Long> {

    Optional<RefundApproval> findByApprovalNumber(String approvalNumber);

    Optional<RefundApproval> findByIdempotencyKey(String idempotencyKey);

    Optional<RefundApproval> findFirstByOrderIdAndStatus(Long orderId, RefundApprovalStatus status);

    /** Requests of others the caller may decide: within their limit. */
    Page<RefundApproval> findByStatusAndRequestedByIdNotAndAmountLessThanEqual(RefundApprovalStatus status,
                                                                               Long requesterId, BigDecimal maxAmount,
                                                                               Pageable pageable);

    /** Requests of others the caller may decide: no limit. */
    Page<RefundApproval> findByStatusAndRequestedByIdNot(RefundApprovalStatus status, Long requesterId,
                                                         Pageable pageable);

    Page<RefundApproval> findByRequestedById(Long requesterId, Pageable pageable);

    Page<RefundApproval> findByRequestedByIdAndStatus(Long requesterId, RefundApprovalStatus status,
                                                      Pageable pageable);

    Page<RefundApproval> findByStatus(RefundApprovalStatus status, Pageable pageable);
}
