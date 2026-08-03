package com.fincity.saas.message.service.message.provider.whatsapp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The HMAC is the entire security boundary on the WhatsApp webhook, and it fails silently: get it
 * subtly wrong and every real webhook is rejected, or worse, every forged one is accepted. So the
 * expected signature here is not computed by the same code under test. It comes from openssl:
 *
 * <pre>
 *   printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET"
 * </pre>
 *
 * If this test ever needs its constant "updated" to pass, the implementation has drifted from what
 * Meta actually sends, and the constant is the correct one.
 */
class WhatsappWebhookSignatureServiceTest {

    private static final String PAYLOAD = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1339022241085528\"}]}";
    private static final String SECRET = "test_app_secret_value";
    private static final String EXPECTED_HEX = "dc6978a73b9ae3468723d0c206b8fa57cfd3e637f3e4aa83b6f3569dfa8c0bf8";

    @Test
    @DisplayName("accepts the signature Meta would actually send")
    void acceptsGenuineSignature() {
        assertTrue(WhatsappWebhookSignatureService.matches("sha256=" + EXPECTED_HEX, PAYLOAD, SECRET));
    }

    @Test
    @DisplayName("accepts a bare hex signature without the sha256= prefix")
    void acceptsWithoutPrefix() {
        assertTrue(WhatsappWebhookSignatureService.matches(EXPECTED_HEX, PAYLOAD, SECRET));
    }

    @Test
    @DisplayName("rejects a signature computed with a different app secret")
    void rejectsWrongSecret() {
        assertFalse(WhatsappWebhookSignatureService.matches("sha256=" + EXPECTED_HEX, PAYLOAD, "not_the_secret"));
    }

    @Test
    @DisplayName("rejects a body tampered with after signing")
    void rejectsTamperedPayload() {
        String tampered = PAYLOAD.replace("1339022241085528", "9999999999999999");
        assertFalse(WhatsappWebhookSignatureService.matches("sha256=" + EXPECTED_HEX, tampered, SECRET));
    }

    @Test
    @DisplayName("rejects a single flipped character rather than matching on a prefix")
    void rejectsNearMiss() {
        String nearMiss = "d" + EXPECTED_HEX.substring(1, EXPECTED_HEX.length() - 1) + "9";
        assertFalse(WhatsappWebhookSignatureService.matches("sha256=" + nearMiss, PAYLOAD, SECRET));
    }

    @Test
    @DisplayName("rejects rather than throws when the signature is not hex")
    void rejectsMalformedSignature() {
        assertFalse(WhatsappWebhookSignatureService.matches("sha256=not-hex-at-all", PAYLOAD, SECRET));
    }

    @Test
    @DisplayName("rejects a truncated signature")
    void rejectsTruncatedSignature() {
        assertFalse(WhatsappWebhookSignatureService.matches("sha256=" + EXPECTED_HEX.substring(0, 32), PAYLOAD, SECRET));
    }

    @Test
    @DisplayName("whitespace around the signature does not break a genuine match")
    void toleratesSurroundingWhitespace() {
        assertTrue(WhatsappWebhookSignatureService.matches("sha256=" + EXPECTED_HEX + "  ", PAYLOAD, SECRET));
    }
}
