package com.fincity.saas.message.service.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Proves a call actually came from one of our bridges.
 *
 * <p>Required, and not made optional by the traffic running over a private VCN peering. The old
 * justification for leaving {@code /internal} endpoints unauthenticated was that this service
 * verified Meta's signature over the raw webhook body before calling them. There is no Meta
 * signature any more and the caller is in another region, so private transport is all that is left,
 * and private transport is not authentication.
 *
 * <p>The digest must match {@code internal/authn} in the bridge exactly:
 *
 * <pre>
 *   HMAC-SHA256(secret, unix_seconds_as_ascii || '.' || raw_body)
 *   X-Bridge-Signature: sha256=&lt;hex&gt;
 * </pre>
 *
 * <p>The separator is load-bearing rather than cosmetic. Without it a body beginning with digits
 * could be shifted into the timestamp field to produce a different request with the same MAC.
 */
@Service
public class BridgeSignatureService {

    public static final String SIGNATURE_HEADER = "X-Bridge-Signature";
    public static final String TIMESTAMP_HEADER = "X-Bridge-Timestamp";
    public static final String INSTANCE_HEADER = "X-Bridge-Instance";
    public static final String BOOTSTRAP_HEADER = "X-Bridge-Bootstrap";

    private static final Logger logger = LoggerFactory.getLogger(BridgeSignatureService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Shared secret for every call in both directions, per environment. Held in configuration rather
     * than in a connection document because the bridge is not a Spring Cloud Config client and both
     * sides must read the same value from their own environment.
     */
    @Value("${message.bridge.hmac-secret:}")
    private String hmacSecret;

    /**
     * Authenticates registration specifically.
     *
     * <p>Separate from the HMAC secret on purpose: registration is the call that gets an unknown host
     * admitted to the fleet and handed live customer sessions, so it is worth a credential that can
     * be rotated without re-keying every message in flight.
     */
    @Value("${message.bridge.bootstrap-secret:}")
    private String bootstrapSecret;

    /** Bounds clock skew between regions. Wide enough for a badly synced host, narrow enough that a captured request expires. */
    @Value("${message.bridge.signature-tolerance-seconds:300}")
    private long toleranceSeconds;

    /**
     * Verifies the signature over the exact bytes received.
     *
     * <p>Fails closed on every path. A missing header is a rejection, never an unsigned legacy
     * caller: "absent means allowed" is how an authentication check becomes decorative.
     */
    public boolean isTrusted(HttpHeaders headers, String rawBody) {

        if (this.hmacSecret == null || this.hmacSecret.isBlank()) {
            // Refusing rather than allowing. An unconfigured secret must not silently open the
            // endpoint that hands out session ownership.
            logger.error("message.bridge.hmac-secret is not configured. Rejecting the bridge call.");
            return false;
        }

        String provided = trimmed(headers.getFirst(SIGNATURE_HEADER));
        if (provided == null) {
            logger.error("Rejected a bridge call from {}: no signature header.", instanceOf(headers));
            return false;
        }

        String rawTimestamp = trimmed(headers.getFirst(TIMESTAMP_HEADER));
        if (rawTimestamp == null) {
            logger.error("Rejected a bridge call from {}: no timestamp header.", instanceOf(headers));
            return false;
        }

        long seconds;
        try {
            seconds = Long.parseLong(rawTimestamp);
        } catch (NumberFormatException e) {
            logger.error("Rejected a bridge call from {}: malformed timestamp {}.", instanceOf(headers), rawTimestamp);
            return false;
        }

        Duration skew = Duration.between(Instant.ofEpochSecond(seconds), Instant.now()).abs();
        if (skew.getSeconds() > this.toleranceSeconds) {
            logger.error(
                    "Rejected a bridge call from {}: timestamp is {}s outside the {}s window."
                            + " Check clock sync on the bridge host before assuming an attack.",
                    instanceOf(headers),
                    skew.getSeconds(),
                    this.toleranceSeconds);
            return false;
        }

        return matches(provided, rawTimestamp, rawBody == null ? "" : rawBody, this.hmacSecret, instanceOf(headers));
    }

    /**
     * Checks the registration credential.
     *
     * <p>Constant time, because it is a plain shared secret rather than a MAC and a byte-by-byte
     * compare would leak its prefix to anyone able to time the endpoint.
     */
    public boolean hasValidBootstrapSecret(HttpHeaders headers) {

        if (this.bootstrapSecret == null || this.bootstrapSecret.isBlank()) {
            logger.error("message.bridge.bootstrap-secret is not configured. Rejecting the registration.");
            return false;
        }

        String provided = trimmed(headers.getFirst(BOOTSTRAP_HEADER));
        if (provided == null) {
            logger.error("Rejected a registration from {}: no bootstrap secret.", instanceOf(headers));
            return false;
        }

        boolean ok = MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), this.bootstrapSecret.getBytes(StandardCharsets.UTF_8));

        if (!ok) logger.error("Rejected a registration from {}: bootstrap secret did not match.", instanceOf(headers));

        return ok;
    }

    /** Signs an outgoing call to a bridge, so the same check can run in the other direction. */
    public String sign(long unixSeconds, String body) {
        return SIGNATURE_PREFIX + HexFormat.of().formatHex(digest(unixSeconds, body, this.hmacSecret));
    }

    /** Package-private and static so the digest is testable without standing up the service. */
    static boolean matches(String provided, String rawTimestamp, String rawBody, String secret, String instance) {
        try {
            byte[] expected = digest(Long.parseLong(rawTimestamp), rawBody, secret);

            String hex =
                    provided.startsWith(SIGNATURE_PREFIX) ? provided.substring(SIGNATURE_PREFIX.length()) : provided;

            // Constant time. A byte-by-byte compare leaks how much of a guessed signature was
            // correct, which is enough to forge one given enough attempts.
            boolean ok = MessageDigest.isEqual(expected, HexFormat.of().parseHex(hex.trim()));

            if (!ok) logger.error("Rejected a bridge call from {}: signature did not verify.", instance);

            return ok;
        } catch (Exception e) {
            logger.error("Could not compute the bridge signature for a call from {}.", instance, e);
            return false;
        }
    }

    private static byte[] digest(long unixSeconds, String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            // Field order and the separator both matter, and both must match internal/authn on the
            // Go side byte for byte.
            mac.update(Long.toString(unixSeconds).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(rawBody.getBytes(StandardCharsets.UTF_8));

            return mac.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute the bridge HMAC.", e);
        }
    }

    private static String trimmed(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String instanceOf(HttpHeaders headers) {
        String instance = headers.getFirst(INSTANCE_HEADER);
        return instance == null || instance.isBlank() ? "an unidentified instance" : instance;
    }
}
