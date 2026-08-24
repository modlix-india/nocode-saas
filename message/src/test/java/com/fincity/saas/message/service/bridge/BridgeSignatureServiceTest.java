package com.fincity.saas.message.service.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-language contract test for the bridge HMAC.
 *
 * <p>The signatures below were produced by the Go implementation in {@code internal/authn} and
 * pasted here verbatim. That is the entire point: this is the one place where two codebases in two
 * languages must agree on a byte layout, and there is no compiler, no shared type and no schema
 * keeping them honest. If the two drift, every call from every bridge 401s while both services log
 * nothing worse than "signature did not verify" and both look perfectly healthy.
 *
 * <p>Regenerate with, in the whatsapp-bridge repo:
 *
 * <pre>
 *   authn.Sign([]byte("local-hmac-secret"), time.Unix(1754467200, 0), []byte(body))
 * </pre>
 *
 * <p>If a change here needs the vectors regenerated, that is a wire-format change and both sides
 * have to ship together.
 */
class BridgeSignatureServiceTest {

    private static final String SECRET = "local-hmac-secret";
    private static final String TIMESTAMP = "1754467200";

    @Test
    @DisplayName("matches the Go implementation on a typical events body")
    void matchesGoForJsonBody() {
        assertTrue(BridgeSignatureService.matches(
                "sha256=143b0721b8c7e1c239493d260b6805ae302ece7920038ec8ae2e7f336fdbd1aa",
                TIMESTAMP,
                "{\"instanceId\":\"inst-in-01\",\"events\":[]}",
                SECRET,
                "test"));
    }

    @Test
    @DisplayName("matches the Go implementation on an empty body")
    void matchesGoForEmptyBody() {
        assertTrue(BridgeSignatureService.matches(
                "sha256=7b826076720ca9a1cc18c729fbe81ee30b3b4c9453fb4dcea37cd9ca4a2e5c14",
                TIMESTAMP,
                "",
                SECRET,
                "test"));
    }

    /**
     * The case the separator exists for.
     *
     * <p>A body of pure digits is what would let a timestamp and a body run together if they were
     * concatenated without one, so this vector is worth keeping specifically: it is the shape that
     * would still pass if somebody "simplified" the digest by dropping the dot.
     */
    @Test
    @DisplayName("matches the Go implementation on a digit-leading body")
    void matchesGoForNumericBody() {
        assertTrue(BridgeSignatureService.matches(
                "sha256=f6276ba2fe191b08b4080768f4b3ba5dc5722b9bd1a012eec4fd3931d6760451",
                TIMESTAMP,
                "123456789",
                SECRET,
                "test"));
    }

    @Test
    @DisplayName("rejects a body that was altered after signing")
    void rejectsTamperedBody() {
        assertFalse(BridgeSignatureService.matches(
                "sha256=143b0721b8c7e1c239493d260b6805ae302ece7920038ec8ae2e7f336fdbd1aa",
                TIMESTAMP,
                "{\"instanceId\":\"inst-in-02\",\"events\":[]}",
                SECRET,
                "test"));
    }

    /**
     * The timestamp is inside the MAC, not merely checked alongside it.
     *
     * <p>Without this a captured request could be replayed with a fresh timestamp to get past the
     * skew window, and the signature would still verify.
     */
    @Test
    @DisplayName("rejects a signature replayed under a different timestamp")
    void rejectsReplayedTimestamp() {
        assertFalse(BridgeSignatureService.matches(
                "sha256=143b0721b8c7e1c239493d260b6805ae302ece7920038ec8ae2e7f336fdbd1aa",
                "1754467260",
                "{\"instanceId\":\"inst-in-01\",\"events\":[]}",
                SECRET,
                "test"));
    }

    @Test
    @DisplayName("rejects the right body under the wrong secret")
    void rejectsWrongSecret() {
        assertFalse(BridgeSignatureService.matches(
                "sha256=143b0721b8c7e1c239493d260b6805ae302ece7920038ec8ae2e7f336fdbd1aa",
                TIMESTAMP,
                "{\"instanceId\":\"inst-in-01\",\"events\":[]}",
                "a-different-secret",
                "test"));
    }

    /** Malformed input must be a rejection, never an exception escaping into the request path. */
    @Test
    @DisplayName("rejects a signature that is not hex")
    void rejectsNonHexSignature() {
        assertFalse(BridgeSignatureService.matches(
                "sha256=not-hex-at-all",
                TIMESTAMP,
                "{\"instanceId\":\"inst-in-01\",\"events\":[]}",
                SECRET,
                "test"));
    }
}
