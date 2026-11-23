package com.rmrx.agenticshop.returns.service;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.returns.entity.*;
import com.rmrx.agenticshop.returns.repository.RefundRepository;
import com.rmrx.agenticshop.returns.repository.ReturnRequestRepository;
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

    public ReturnService(ReturnRequestRepository returnRepository, RefundRepository refundRepository,
                         ReturnMapper mapper) {
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.mapper = mapper;
    }

    public PageResponse<ReturnDto> searchReturns(ReturnStatus status, ReturnReason reason, Long sellerId,
                                                 Long customerId, Instant from, Instant to, Pageable pageable) {
        Specification<ReturnRequest> spec = Specification.allOf(
                Specs.eq("status", status),
                Specs.eq("reason", reason),
                Specs.eq("order.seller.id", sellerId),
                Specs.eq("customer.id", customerId),
                Specs.gte("openedAt", from),
                Specs.lt("openedAt", to));
        return PageResponse.of(returnRepository.findAll(spec, pageable), mapper::toReturn);
    }

    public ReturnDto getReturn(Long id) {
        return returnRepository.findById(id).map(mapper::toReturn)
                .orElseThrow(() -> new NotFoundException("Return", id));
    }

    public PageResponse<RefundDto> searchRefunds(RefundStatus status, Long orderId, Long sellerId,
                                                 Instant from, Instant to, Pageable pageable) {
        Specification<Refund> spec = Specification.allOf(
                Specs.eq("status", status),
                Specs.eq("order.id", orderId),
                Specs.eq("order.seller.id", sellerId),
                Specs.gte("createdAt", from),
                Specs.lt("createdAt", to));
        return PageResponse.of(refundRepository.findAll(spec, pageable), mapper::toRefund);
    }
}
