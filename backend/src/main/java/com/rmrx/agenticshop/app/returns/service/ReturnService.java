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
        Specification<ReturnRequest> spec = Specification.allOf(
                Specs.eq("status", status),
                Specs.eq("reason", reason),
                Specs.eq("order.seller.id", scope.sellerFilter(sellerId)),
                Specs.eq("customer.id", scope.customerFilter(customerId)),
                Specs.gte("openedAt", from),
                Specs.lt("openedAt", to));
        return PageResponse.of(returnRepository.findAll(spec, pageable), mapper::toReturn);
    }

    public ReturnDto getReturn(Long id) {
        ReturnRequest r = returnRepository.findById(id).orElseThrow(() -> new NotFoundException("Return", id));
        scope.requireOwned(r.getCustomer().getId(), r.getOrder().getSeller().getId(), "Return", id);
        return mapper.toReturn(r);
    }

    public PageResponse<RefundDto> searchRefunds(RefundStatus status, Long orderId, Long sellerId,
                                                 Instant from, Instant to, Pageable pageable) {
        Specification<Refund> spec = Specification.allOf(
                Specs.eq("status", status),
                Specs.eq("order.id", orderId),
                Specs.eq("order.seller.id", scope.sellerFilter(sellerId)),
                Specs.eq("order.customer.id", scope.customerFilter(null)),
                Specs.gte("createdAt", from),
                Specs.lt("createdAt", to));
        return PageResponse.of(refundRepository.findAll(spec, pageable), mapper::toRefund);
    }
}
