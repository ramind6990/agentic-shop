package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.repository.Specs;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.app.returns.entity.*;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final RefundRepository refundRepository;
    private final ReturnMapper mapper;
    private final AccessScope scope;

    public ReturnService(ReturnRequestRepository returnRepository, RefundRepository refundRepository,
                         ReturnMapper mapper, AccessScope scope) {
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.mapper = mapper;
        this.scope = scope;
    }

    public PageResponse<ReturnDto> searchReturns(ReturnStatus status, ReturnReason reason, Long sellerId,
                                                 Long customerId, Instant from, Instant to, Pageable pageable) {
        return searchReturns(new ReturnSearch(null, status == null ? List.of() : List.of(status), reason,
                customerId, sellerId, from, to), pageable);
    }

    public PageResponse<ReturnDto> searchReturns(ReturnSearch s, Pageable pageable) {
        Specification<ReturnRequest> spec = Specification.allOf(
                Specs.eq("order.orderNumber", upper(s.orderNumber())),
                Specs.in("status", s.statuses()),
                Specs.eq("reason", s.reason()),
                Specs.eq("order.seller.id", scope.sellerFilter(s.sellerId())),
                Specs.eq("customer.id", scope.customerFilter(s.customerId())),
                Specs.gte("openedAt", s.from()),
                Specs.lt("openedAt", s.to()));
        return PageResponse.of(returnRepository.findAll(spec, pageable), mapper::toReturn);
    }

    public ReturnDto getReturn(Long id) {
        ReturnRequest r = returnRepository.findById(id).orElseThrow(() -> new NotFoundException("Return", id));
        scope.requireOwned(r.getCustomer().getId(), r.getOrder().getSeller().getId(), "Return", id);
        return mapper.toReturn(r);
    }

    public PageResponse<RefundDto> searchRefunds(RefundStatus status, Long orderId, Long sellerId,
                                                 Instant from, Instant to, Pageable pageable) {
        return searchRefunds(new RefundSearch(null, orderId, status == null ? List.of() : List.of(status), null,
                sellerId, from, to), pageable);
    }

    public PageResponse<RefundDto> searchRefunds(RefundSearch s, Pageable pageable) {
        Specification<Refund> spec = Specification.allOf(
                Specs.eq("order.orderNumber", upper(s.orderNumber())),
                Specs.eq("order.id", s.orderId()),
                Specs.in("status", s.statuses()),
                Specs.eq("order.seller.id", scope.sellerFilter(s.sellerId())),
                Specs.eq("order.customer.id", scope.customerFilter(s.customerId())),
                Specs.gte("createdAt", s.from()),
                Specs.lt("createdAt", s.to()));
        return PageResponse.of(refundRepository.findAll(spec, pageable), mapper::toRefund);
    }

    private static String upper(String orderNumber) {
        return orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim().toUpperCase(Locale.ROOT);
    }
}
