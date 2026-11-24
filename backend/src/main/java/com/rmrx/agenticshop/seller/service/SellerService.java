package com.rmrx.agenticshop.seller.service;

import com.rmrx.agenticshop.catalog.repository.ProductRepository;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.seller.dto.SellerDtos.*;
import com.rmrx.agenticshop.seller.entity.Seller;
import com.rmrx.agenticshop.seller.entity.SellerContract;
import com.rmrx.agenticshop.seller.entity.SellerStatus;
import com.rmrx.agenticshop.seller.repository.PayoutRepository;
import com.rmrx.agenticshop.seller.repository.SellerContractRepository;
import com.rmrx.agenticshop.seller.repository.SellerRepository;
import com.rmrx.agenticshop.security.AccessScope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SellerService {

    private final SellerRepository sellerRepository;
    private final SellerContractRepository contractRepository;
    private final PayoutRepository payoutRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;
    private final SellerMapper mapper;
    private final AccessScope scope;

    public SellerService(SellerRepository sellerRepository, SellerContractRepository contractRepository,
                         PayoutRepository payoutRepository, ProductRepository productRepository,
                         CustomerOrderRepository orderRepository, SellerMapper mapper,
                         AccessScope scope) {
        this.sellerRepository = sellerRepository;
        this.contractRepository = contractRepository;
        this.payoutRepository = payoutRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.scope = scope;
    }

    public PageResponse<SellerSummary> search(String q, SellerStatus status, String country, Pageable pageable) {
        Specification<Seller> spec = Specification.allOf(
                Specs.containsIgnoreCase(q, "name", "legalName"),
                Specs.eq("status", status),
                Specs.eq("country", country),
                Specs.eq("id", scope.sellerFilter(null)));
        return PageResponse.of(sellerRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public SellerDetail get(String idOrSlug) {
        Seller seller = find(idOrSlug);
        List<SellerContract> contracts = contractRepository.findBySellerIdOrderByEffectiveFromDesc(seller.getId());
        var currentPlan = contracts.stream().filter(c -> c.getEffectiveTo() == null).findFirst()
                .map(SellerContract::getFeePlan).orElse(null);
        var stats = new SellerStats(productRepository.countBySellerId(seller.getId()),
                orderRepository.countBySellerId(seller.getId()));
        return mapper.toDetail(seller, currentPlan, stats);
    }

    public List<ContractDto> contracts(Long sellerId) {
        requireExists(sellerId);
        return contractRepository.findBySellerIdOrderByEffectiveFromDesc(sellerId).stream()
                .map(mapper::toContract).toList();
    }

    public PageResponse<PayoutDto> payouts(Long sellerId, Pageable pageable) {
        requireExists(sellerId);
        return PageResponse.of(payoutRepository.findBySellerId(sellerId, pageable), mapper::toPayout);
    }

    private Seller find(String idOrSlug) {
        var found = idOrSlug.chars().allMatch(Character::isDigit)
                ? sellerRepository.findById(Long.parseLong(idOrSlug))
                : sellerRepository.findBySlug(idOrSlug);
        Seller seller = found.orElseThrow(() -> new NotFoundException("Seller", idOrSlug));
        scope.requireOwned(null, seller.getId(), "Seller", idOrSlug);
        return seller;
    }

    private void requireExists(Long sellerId) {
        if (!sellerRepository.existsById(sellerId)) throw new NotFoundException("Seller", sellerId);
        scope.requireOwned(null, sellerId, "Seller", sellerId);
    }
}
