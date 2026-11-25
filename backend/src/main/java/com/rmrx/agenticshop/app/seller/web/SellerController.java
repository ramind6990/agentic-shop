package com.rmrx.agenticshop.app.seller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.web.Paging;
import com.rmrx.agenticshop.app.seller.dto.SellerDtos.*;
import com.rmrx.agenticshop.app.seller.entity.SellerStatus;
import com.rmrx.agenticshop.app.seller.service.SellerService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("hasAuthority('SELLERS_READ')")
@RequestMapping("/api/sellers")
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @GetMapping
    public PageResponse<SellerSummary> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) SellerStatus status,
                                              @RequestParam(required = false) String country,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return sellerService.search(q, status, country, Paging.of(page, size, Sort.by("name")));
    }

    @GetMapping("/{idOrSlug}")
    public SellerDetail get(@PathVariable String idOrSlug) {
        return sellerService.get(idOrSlug);
    }

    /** Fee agreements over time (finance data: the seller itself, FINANCE and ADMIN). */
    @PreAuthorize("hasAuthority('SELLER_FINANCE_READ')")
    @GetMapping("/{id}/contracts")
    public List<ContractDto> contracts(@PathVariable Long id) {
        return sellerService.contracts(id);
    }

    @PreAuthorize("hasAuthority('SELLER_FINANCE_READ')")
    @GetMapping("/{id}/payouts")
    public PageResponse<PayoutDto> payouts(@PathVariable Long id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return sellerService.payouts(id, Paging.of(page, size, Sort.by(Sort.Order.desc("periodStart"))));
    }
}
