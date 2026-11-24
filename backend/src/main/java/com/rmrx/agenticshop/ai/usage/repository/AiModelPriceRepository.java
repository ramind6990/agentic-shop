package com.rmrx.agenticshop.ai.usage.repository;

import com.rmrx.agenticshop.ai.usage.entity.AiModelPrice;
import com.rmrx.agenticshop.ai.usage.entity.AiTokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AiModelPriceRepository extends JpaRepository<AiModelPrice, Long> {

    /** The price in effect at the given instant: the latest row with effectiveFrom <= at. */
    Optional<AiModelPrice> findFirstByProviderAndModelAndTokenTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String provider, String model, AiTokenType tokenType, Instant at);

    default Optional<AiModelPrice> findEffective(String provider, String model, AiTokenType tokenType, Instant at) {
        return findFirstByProviderAndModelAndTokenTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                provider, model, tokenType, at);
    }
}
