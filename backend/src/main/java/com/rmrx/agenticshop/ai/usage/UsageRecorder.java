package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.usage.entity.AiModelPrice;
import com.rmrx.agenticshop.ai.usage.entity.AiTokenType;
import com.rmrx.agenticshop.ai.usage.entity.AiUsage;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.ai.usage.repository.AiModelPriceRepository;
import com.rmrx.agenticshop.ai.usage.repository.AiUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Writes one ledger row per billable call, priced with the price rows in effect now. Each row is written in its own
 * transaction (REQUIRES_NEW, so it survives a rolled-back turn), and a failure is logged, never thrown into the
 * model call.
 */
@Slf4j
@Service
public class UsageRecorder {

    public static final String OPENAI = "openai";

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 8;
    /** Providers answer with dated snapshot names (gpt-5-mini-2025-08-07); prices are kept per base model. */
    private static final Pattern DATE_SUFFIX = Pattern.compile("-\\d{4}-\\d{2}-\\d{2}$");

    private final AiModelPriceRepository prices;
    private final AiUsageRepository usages;
    private final TransactionTemplate tx;

    public UsageRecorder(AiModelPriceRepository prices, AiUsageRepository usages,
                         PlatformTransactionManager transactionManager) {
        this.prices = prices;
        this.usages = usages;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records one call. cachedInputTokens is the part of inputTokens served from the prompt cache; it is billed at
     * the cached price and the rest of the input at the input price. A null scope is recorded as CHAT with no user,
     * with a warning. Returns the saved row, or empty if recording failed.
     */
    public Optional<AiUsage> record(String provider, String model, int inputTokens, int cachedInputTokens,
                                    int outputTokens, RunScope scope, boolean partial) {
        RunScope effective = scope;
        try {
            if (effective == null) {
                log.warn("AI usage without a RunScope ({} {}, {} in / {} out tokens): recorded as CHAT with no user",
                        provider, model, inputTokens, outputTokens);
                effective = RunScope.system(AiUsagePurpose.CHAT);
            }
            RunScope s = effective;
            return Optional.ofNullable(tx.execute(status ->
                    save(provider, model, inputTokens, cachedInputTokens, outputTokens, s, partial)));
        } catch (Exception e) {
            log.error("Failed to record AI usage ({} {}, {} in / {} cached / {} out tokens, scope {})",
                    provider, model, inputTokens, cachedInputTokens, outputTokens, effective, e);
            return Optional.empty();
        }
    }

    private AiUsage save(String provider, String model, int input, int cached, int output, RunScope scope,
                         boolean partial) {
        Instant now = Instant.now();
        int safeInput = Math.max(0, input);
        int safeCached = Math.max(0, Math.min(cached, safeInput));
        int safeOutput = Math.max(0, output);

        Priced in = price(provider, model, AiTokenType.INPUT, safeInput - safeCached, now);
        Priced cachedIn = price(provider, model, AiTokenType.CACHED_INPUT, safeCached, now);
        Priced out = price(provider, model, AiTokenType.OUTPUT, safeOutput, now);
        BigDecimal cost = in.cost().add(cachedIn.cost()).add(out.cost()).setScale(COST_SCALE, RoundingMode.HALF_UP);

        return usages.save(AiUsage.record(scope.userId(), scope.ticketId(), scope.runId(), scope.purpose(),
                provider, model, safeInput, safeCached, safeOutput, cost,
                in.priceId(), cachedIn.priceId(), out.priceId(), partial, now));
    }

    /** cost = tokens x usd_per_million / 1,000,000; a missing price row costs 0 (warned when tokens were used). */
    private Priced price(String provider, String model, AiTokenType type, int tokens, Instant now) {
        Optional<AiModelPrice> price = prices.findEffective(provider, model, type, now);
        if (price.isEmpty()) {
            String base = DATE_SUFFIX.matcher(model).replaceFirst("");
            if (!base.equals(model)) price = prices.findEffective(provider, base, type, now);
        }
        if (price.isEmpty()) {
            if (tokens > 0) {
                log.warn("No {} price for {} {}: {} tokens recorded at cost 0", type, provider, model, tokens);
            }
            return new Priced(null, BigDecimal.ZERO);
        }
        BigDecimal cost = BigDecimal.valueOf(tokens).multiply(price.get().getUsdPerMillion())
                .divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
        return new Priced(price.get().getId(), cost);
    }

    private record Priced(Long priceId, BigDecimal cost) {
    }
}
