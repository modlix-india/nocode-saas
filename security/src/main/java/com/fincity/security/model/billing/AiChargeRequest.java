package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * Immediate AI charge posted by nocode-ai right after an LLM call. The token
 * count is already model-weighted in ai; security applies the monthly free
 * grant and {@code aiTokensPerMillion}. Idempotent on {@code requestId}.
 */
public record AiChargeRequest(
        String clientCode,
        String appCode,
        String model,
        BigDecimal weightedTokens,
        String requestId,
        String sessionId) {
}
