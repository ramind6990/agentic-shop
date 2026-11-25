package com.rmrx.agenticshop.app.returns.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.web.Paging;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.app.returns.entity.RefundStatus;
import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import com.rmrx.agenticshop.app.returns.entity.ReturnStatus;
import com.rmrx.agenticshop.app.returns.service.ReturnService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PreAuthorize("hasAuthority('RETURNS_READ')")
    @GetMapping("/returns")
    public PageResponse<ReturnDto> returns(@RequestParam(required = false) ReturnStatus status,
                                           @RequestParam(required = false) ReturnReason reason,
                                           @RequestParam(required = false) Long sellerId,
                                           @RequestParam(required = false) Long customerId,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return returnService.searchReturns(status, reason, sellerId, customerId, from, to,
                Paging.of(page, size, Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("id"))));
    }

    @PreAuthorize("hasAuthority('RETURNS_READ')")
    @GetMapping("/returns/{id}")
    public ReturnDto getReturn(@PathVariable Long id) {
        return returnService.getReturn(id);
    }

    @PreAuthorize("hasAuthority('REFUNDS_READ')")
    @GetMapping("/refunds")
    public PageResponse<RefundDto> refunds(@RequestParam(required = false) RefundStatus status,
                                           @RequestParam(required = false) Long orderId,
                                           @RequestParam(required = false) Long sellerId,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return returnService.searchRefunds(status, orderId, sellerId, from, to,
                Paging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }
}
