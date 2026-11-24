package com.rmrx.agenticshop.ai.tools.port;

import java.math.BigDecimal;

/**
 * What a write would do if it ran now, from a port's side-effect-free check: a short summary of the affected record,
 * the money involved (null when none moves) and why the write is allowed (e.g. "Within the 30-day return window").
 * An ineligible write is not a preview; the check throws the same {@link PortException} the write would.
 */
public record WritePreview(String target, BigDecimal amount, String currency, String policyReason) {
}
