package com.rmrx.agenticshop.risk.web;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.common.dto.RefMapper;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.common.web.Paging;
import com.rmrx.agenticshop.risk.entity.AccountFlag;
import com.rmrx.agenticshop.risk.entity.FlagSubjectType;
import com.rmrx.agenticshop.risk.entity.FlagType;
import com.rmrx.agenticshop.risk.repository.AccountFlagRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Fraud/risk flags (fraud-team data; access will be restricted once authentication exists). */
@RestController
@RequestMapping("/api/flags")
public class AccountFlagController {

    public record FlagDto(Long id, FlagSubjectType subjectType, Long subjectId, FlagType flagType,
                          BigDecimal riskScore, String note, Ref createdBy, boolean active, Instant createdAt) {
    }

    private final AccountFlagRepository repository;
    private final RefMapper refs;

    public AccountFlagController(AccountFlagRepository repository, RefMapper refs) {
        this.repository = repository;
        this.refs = refs;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public PageResponse<FlagDto> search(@RequestParam(required = false) FlagSubjectType subjectType,
                                        @RequestParam(required = false) Long subjectId,
                                        @RequestParam(required = false) FlagType flagType,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Specification<AccountFlag> spec = Specification.allOf(
                Specs.eq("subjectType", subjectType),
                Specs.eq("subjectId", subjectId),
                Specs.eq("flagType", flagType),
                Specs.eq("active", active));
        var pageable = Paging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(repository.findAll(spec, pageable), f -> new FlagDto(f.getId(), f.getSubjectType(),
                f.getSubjectId(), f.getFlagType(), f.getRiskScore(), f.getNote(), refs.user(f.getCreatedBy()),
                f.isActive(), f.getCreatedAt()));
    }
}
