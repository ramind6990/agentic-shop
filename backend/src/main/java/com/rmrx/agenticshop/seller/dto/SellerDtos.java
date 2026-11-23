package com.rmrx.agenticshop.seller.dto;

import com.rmrx.agenticshop.seller.entity.FeePlan;
import com.rmrx.agenticshop.seller.entity.PayoutStatus;
import com.rmrx.agenticshop.seller.entity.SellerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SellerDtos {

    private SellerDtos() {
    }

    public record SellerSummary(Long id, String name, String slug, String country, BigDecimal ratingAvg,
                                SellerStatus status, Instant joinedAt) {
    }

    public record SellerStats(long productCount, long orderCount) {
    }

    public record SellerDetail(Long id, String name, String slug, String legalName, String description,
                               String email, String country, BigDecimal ratingAvg, SellerStatus status,
                               Instant joinedAt, FeePlan currentFeePlan, SellerStats stats) {
    }

    public record ContractDto(Long id, FeePlan feePlan, BigDecimal commissionRate, BigDecimal perOrderFee,
                              BigDecimal monthlyFee, int returnWindowDays, LocalDate effectiveFrom,
                              LocalDate effectiveTo, String documentCode) {
    }

    public record PayoutDto(Long id, LocalDate periodStart, LocalDate periodEnd, int orderCount,
                            BigDecimal grossSales, BigDecimal commissionAmount, BigDecimal feesAmount,
                            BigDecimal refundsAmount, BigDecimal netAmount, PayoutStatus status, Instant paidAt) {
    }
}
