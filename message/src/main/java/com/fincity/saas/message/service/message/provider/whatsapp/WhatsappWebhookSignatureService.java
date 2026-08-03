package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.RestConnectionService;
import com.fincity.saas.message.service.message.MessageConnectionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Proves an inbound WhatsApp webhook actually came from Meta.
 *
 * <p>Until this existed the endpoint was an unauthenticated write: {@code appCode} and {@code
 * clientCode} arrive as caller-supplied headers and nothing checked the body, so anyone who could
 * reach the URL could post a forged payload into any tenant. That was already wrong, and it became
 * materially worse once an inbound message from an unknown number started <em>creating a deal</em>,
 * which turned a junk-row problem into a way to manufacture CRM records.
 *
 * <p>Meta signs the raw request body with the Meta app secret and sends {@code X-Hub-Signature-256:
 * sha256=<hex>}. Reaching that secret is a three-hop walk, because none of it is stored locally:
 *
 * <pre>
 *   WABA (payload entry id)
 *     -> CONNECTION_NAME on the business account, else the configured default
 *     -> TEXT/WHATSAPP connection
 *     -> connectionDetails.tokenConnection            e.g. "meta_Connection"
 *     -> REST connection
 *     -> connectionDetails.tokenDetails.queryParams.client_secret
 * </pre>
 *
 * <p>Fails closed. A missing header, an unresolvable secret or a mismatch all return false, and the
 * caller rejects. The only way through without a valid signature is the explicit config switch
 * below, which announces itself on every request.
 */
@Service
public class WhatsappWebhookSignatureService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappWebhookSignatureService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String KEY_TOKEN_CONNECTION = "tokenConnection";
    private static final String KEY_TOKEN_DETAILS = "tokenDetails";
    private static final String KEY_QUERY_PARAMS = "queryParams";
    private static final String KEY_CLIENT_SECRET = "client_secret";

    private final MessageConnectionService messageConnectionService;
    private final RestConnectionService restConnectionService;
    private final WhatsappBusinessAccountService businessAccountService;

    /**
     * Only for local work against replayed payloads, where there is no real Meta signature. Never
     * set this anywhere a real webhook can reach, and note that it logs a warning per request so a
     * environment left in this state is loud rather than quietly open.
     */
    @Value("${message.whatsapp.webhook.verify-signature:true}")
    private boolean verifySignature;

    /** Used when a business account predates {@code CONNECTION_NAME} and has none recorded. */
    @Value("${message.whatsapp.webhook.default-connection-name:whatsapp_connection}")
    private String defaultConnectionName;

    public WhatsappWebhookSignatureService(
            MessageConnectionService messageConnectionService,
            RestConnectionService restConnectionService,
            WhatsappBusinessAccountService businessAccountService) {
        this.messageConnectionService = messageConnectionService;
        this.restConnectionService = restConnectionService;
        this.businessAccountService = businessAccountService;
    }

    /**
     * @param wabaId Meta's business account id, taken from the payload. Used only to find which
     *     connection holds the secret; the signature is what establishes trust, so reading it from
     *     an unverified body is safe.
     * @param signatureHeader the {@code X-Hub-Signature-256} value, prefix included
     * @param rawPayload the body exactly as received. Re-serialising it would change the bytes and
     *     every signature would fail.
     */
    public Mono<Boolean> isTrusted(
            String appCode, String clientCode, String wabaId, String signatureHeader, String rawPayload) {

        if (!this.verifySignature) {
            logger.warn(
                    "WhatsApp webhook signature verification is DISABLED. Accepting an unverified payload for"
                            + " app {} client {}. This must not be the case outside local development.",
                    appCode,
                    clientCode);
            return Mono.just(Boolean.TRUE);
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            logger.error("Rejected WhatsApp webhook for app {} client {}: no signature header.", appCode, clientCode);
            return Mono.just(Boolean.FALSE);
        }

        if (rawPayload == null || rawPayload.isEmpty()) return Mono.just(Boolean.FALSE);

        return this.resolveAppSecret(appCode, clientCode, wabaId)
                .map(secret -> matches(signatureHeader, rawPayload, secret))
                .defaultIfEmpty(Boolean.FALSE)
                .doOnNext(trusted -> {
                    if (Boolean.FALSE.equals(trusted))
                        logger.error(
                                "Rejected WhatsApp webhook for app {} client {} waba {}: signature did not verify.",
                                appCode,
                                clientCode,
                                wabaId);
                })
                .onErrorResume(e -> {
                    logger.error(
                            "Rejected WhatsApp webhook for app {} client {} waba {}: could not verify signature.",
                            appCode,
                            clientCode,
                            wabaId,
                            e);
                    return Mono.just(Boolean.FALSE);
                });
    }

    /** Package-private and static so the HMAC itself is testable without standing up the service. */
    static boolean matches(String signatureHeader, String rawPayload, String appSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));

            String provided = signatureHeader.startsWith(SIGNATURE_PREFIX)
                    ? signatureHeader.substring(SIGNATURE_PREFIX.length())
                    : signatureHeader;

            // Constant time. A byte-by-byte compare leaks how much of a guessed signature was
            // correct, which is enough to forge one given enough attempts.
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(provided.trim()));
        } catch (Exception e) {
            logger.error("Could not compute the WhatsApp webhook signature.", e);
            return false;
        }
    }

    /** Walks WABA to WhatsApp connection to token connection to reach the Meta app secret. */
    private Mono<String> resolveAppSecret(String appCode, String clientCode, String wabaId) {

        return this.resolveConnectionName(appCode, clientCode, wabaId)
                .flatMap(connectionName ->
                        this.messageConnectionService.getCoreDocument(appCode, clientCode, connectionName))
                .flatMap(whatsappConnection -> {
                    String tokenConnection = stringDetail(whatsappConnection, KEY_TOKEN_CONNECTION);
                    if (tokenConnection == null) {
                        logger.error(
                                "WhatsApp connection for app {} client {} has no {}, so the Meta app secret"
                                        + " cannot be reached.",
                                appCode,
                                clientCode,
                                KEY_TOKEN_CONNECTION);
                        return Mono.empty();
                    }
                    return this.restConnectionService.getCoreDocument(appCode, clientCode, tokenConnection);
                })
                .mapNotNull(this::clientSecretOf);
    }

    private Mono<String> resolveConnectionName(String appCode, String clientCode, String wabaId) {

        if (wabaId == null || wabaId.isBlank()) return Mono.just(this.defaultConnectionName);

        return this.businessAccountService
                .getBusinessAccount(MessageAccess.of(appCode, clientCode, Boolean.TRUE), wabaId)
                .map(waba -> waba.getConnectionName() != null
                                && !waba.getConnectionName().isBlank()
                        ? waba.getConnectionName()
                        : this.defaultConnectionName)
                // No business account for this (app, client, waba) means the payload claims an
                // account this tenant does not own. Fall through to the default connection so the
                // signature check still runs and rejects it, rather than erroring out here.
                .defaultIfEmpty(this.defaultConnectionName);
    }

    @SuppressWarnings("unchecked")
    private String clientSecretOf(Connection tokenConnection) {

        Map<String, Object> details = tokenConnection.getConnectionDetails();
        if (details == null) return null;

        Object tokenDetails = details.get(KEY_TOKEN_DETAILS);
        if (!(tokenDetails instanceof Map)) return null;

        Object queryParams = ((Map<String, Object>) tokenDetails).get(KEY_QUERY_PARAMS);
        if (!(queryParams instanceof Map)) return null;

        Object secret = ((Map<String, Object>) queryParams).get(KEY_CLIENT_SECRET);
        return secret instanceof String s && !s.isBlank() ? s : null;
    }

    private String stringDetail(Connection connection, String key) {
        Map<String, Object> details = connection.getConnectionDetails();
        if (details == null) return null;
        Object value = details.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
