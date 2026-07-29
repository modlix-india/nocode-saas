package com.modlix.saas.adzump.platform;

import java.util.Map;

/**
 * The connection token the SPI is handed on every call. Resolved by J2 from the Core connection
 * records (RETRIEVAL §2.3 keeps credential retrieval a separate, gateway-gated concern); the SPI
 * never fetches its own credentials.
 *
 * <p>Per J2b §9 this is a small struct, not a bare access-token string, because Google needs an
 * MCC / login-customer context alongside the token. {@code attributes} carries any extra
 * platform-specific context (Meta pageId/pixelId, Google developer-token slots, etc.) without
 * leaking an SDK type above the SPI.
 *
 * @param accessToken     OAuth access token for the platform API.
 * @param accountId       the ad account this token acts against (Meta {@code act_...}, Google customer id).
 * @param loginCustomerId Google MCC / login-customer id used for the {@code login-customer-id} header; null for Meta.
 * @param attributes      extra platform context (never null; empty when unset).
 */
public record Token(
        String accessToken,
        String accountId,
        String loginCustomerId,
        Map<String, String> attributes) {

    public Token {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
