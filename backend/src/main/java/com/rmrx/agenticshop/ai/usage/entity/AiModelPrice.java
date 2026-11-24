package com.rmrx.agenticshop.ai.usage.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** USD price per 1M tokens for one provider, model and token type, valid from effectiveFrom. Immutable. */
@Getter
@Entity
@Immutable
@Table(name = "ai_model_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiModelPrice extends BaseEntity {

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private AiTokenType tokenType;

    @Column(name = "usd_per_million", nullable = false, precision = 12, scale = 6)
    private BigDecimal usdPerMillion;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    public static AiModelPrice of(String provider, String model, AiTokenType tokenType, BigDecimal usdPerMillion,
                                  Instant effectiveFrom) {
        AiModelPrice price = new AiModelPrice();
        price.provider = provider;
        price.model = model;
        price.tokenType = tokenType;
        price.usdPerMillion = usdPerMillion;
        price.effectiveFrom = effectiveFrom;
        return price;
    }
}
