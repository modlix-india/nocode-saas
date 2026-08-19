package com.fincity.saas.message.service.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.model.request.bridge.BridgeSessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

/**
 * Calls a bridge instance.
 *
 * <p>The base URL is passed in per call rather than configured, because it comes from the instance's
 * own registration and a session is pinned to one instance. There is no pooling by tenant and no
 * service discovery: the session row names the instance and the instance row names the URL.
 *
 * <p>Every request is signed over the exact bytes sent, which is why bodies are serialised to a
 * String here and handed to the client as-is. Letting the codec serialise the object would produce
 * different bytes from the ones that were signed, and every call would 401.
 */
@Service
public class BridgeClient {

    private static final Logger logger = LoggerFactory.getLogger(BridgeClient.class);

    private final BridgeSignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    /**
     * Generous on purpose.
     *
     * <p>A send blocks on the bridge for the randomised 5-15 second Layer-1 gap plus a typing
     * indicator, and may queue behind another send on the same session. A conventional 10-second
     * timeout would cut off precisely the pacing that keeps the number from being banned, and would
     * do it invisibly: the message would often have gone out already by the time we gave up on it.
     */
    @Value("${message.bridge.call-timeout-seconds:120}")
    private long callTimeoutSeconds;

    /** Reads that do not pace: status, QR, capability checks. Kept short so the UI stays responsive. */
    @Value("${message.bridge.read-timeout-seconds:10}")
    private long readTimeoutSeconds;

    public BridgeClient(BridgeSignatureService signatureService, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;

        // Response timeout is set per call below; the connect timeout is the only thing worth
        // pinning here, since a bridge whose host is gone should fail fast rather than hang a
        // request thread for the full call budget.
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)))
                .build();
    }

    public Mono<BridgeSessionSnapshot> createSession(
            String baseUrl, String sessionId, String appCode, String clientCode, String phone) {

        Map<String, Object> body = Map.of(
                "sessionId", sessionId,
                "appCode", appCode,
                "clientCode", clientCode,
                "phone", phone);

        return this.post(baseUrl, "/sessions", body, this.readTimeoutSeconds)
                .map(raw -> this.read(raw, BridgeSessionSnapshot.class));
    }

    public Mono<BridgeSessionSnapshot> getSession(String baseUrl, String sessionId) {
        return this.get(baseUrl, "/sessions/" + sessionId, this.readTimeoutSeconds)
                .map(raw -> this.read(raw, BridgeSessionSnapshot.class));
    }

    /**
     * The current pairing code.
     *
     * <p>Polled by the page every couple of seconds rather than streamed, because a code rotates
     * roughly every 20 seconds and the underlying channel times out, so a socket buys nothing.
     */
    public Mono<Map<String, Object>> getQr(String baseUrl, String sessionId) {
        return this.get(baseUrl, "/sessions/" + sessionId + "/qr", this.readTimeoutSeconds)
                .map(this::readMap);
    }

    public Mono<Void> unlink(String baseUrl, String sessionId) {
        return this.webClient
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri(baseUrl + "/sessions/" + sessionId)
                .headers(h -> this.sign(h, ""))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(this.readTimeoutSeconds))
                .then();
    }

    /**
     * Sends a text and returns the WhatsApp message id.
     *
     * <p>The id matters more than it looks: it is the idempotency key that every receipt for this
     * message will carry, so losing it here means the delivered and read ticks arrive with nothing
     * to attach to and create a stub row instead.
     */
    public Mono<Map<String, Object>> sendText(String baseUrl, String sessionId, String to, String text) {
        return this.post(
                        baseUrl,
                        "/sessions/" + sessionId + "/messages",
                        Map.of("to", to, "text", text),
                        this.callTimeoutSeconds)
                .map(this::readMap);
    }

    /**
     * Sends an attachment.
     *
     * <p>Names the file by path rather than carrying its bytes. The bridge fetches them back over
     * its own channel, which keeps this request small: every signed route on the bridge caps the
     * body at 8 MiB and a document can be several times that.
     */
    public Mono<Map<String, Object>> sendMedia(
            String baseUrl,
            String sessionId,
            String to,
            String filePath,
            String kind,
            String mimeType,
            String fileName,
            String caption,
            boolean voiceNote) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        // The bridge fetches by (sessionId, filePath); the session is already in the URL.
        body.put("mediaToken", filePath);
        body.put("kind", kind == null ? "" : kind);
        body.put("mimeType", mimeType == null ? "" : mimeType);
        body.put("fileName", fileName == null ? "" : fileName);
        body.put("caption", caption == null ? "" : caption);
        body.put("voiceNote", voiceNote);

        return this.post(baseUrl, "/sessions/" + sessionId + "/media", body, this.callTimeoutSeconds)
                .map(this::readMap);
    }

    /**
     * Marks a conversation read on the customer's side.
     *
     * <p>Called when a person actually opens the thread, never on arrival. Instant read-then-reply on
     * every inbound message at any hour is one of the clearer tells that nobody is really there, and
     * it also happens to be untrue.
     */
    public Mono<Void> markRead(
            String baseUrl, String sessionId, String chat, String sender, List<String> messageIds) {

        return this.post(
                        baseUrl,
                        "/sessions/" + sessionId + "/read",
                        Map.of("chat", chat, "sender", sender == null ? "" : sender, "messageIds", messageIds),
                        this.readTimeoutSeconds)
                .then();
    }

    /** Whether a number is reachable on WhatsApp, checked before a lead is messaged at all. */
    public Mono<Map<String, Object>> onWhatsApp(String baseUrl, String sessionId, String number) {
        return this.get(
                        baseUrl,
                        "/sessions/" + sessionId + "/on-whatsapp?number=" + number,
                        this.readTimeoutSeconds)
                .map(this::readMap);
    }

    private Mono<String> post(String baseUrl, String path, Object body, long timeoutSeconds) {

        String raw = this.write(body);

        return this.webClient
                .post()
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> this.sign(h, raw))
                .bodyValue(raw)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorMap(WebClientResponseException.class, e -> this.translate(path, e));
    }

    private Mono<String> get(String baseUrl, String path, long timeoutSeconds) {
        return this.webClient
                .get()
                .uri(baseUrl + path)
                // Signed over an empty body, matching what the bridge verifies for a GET.
                .headers(h -> this.sign(h, ""))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorMap(WebClientResponseException.class, e -> this.translate(path, e));
    }

    private void sign(HttpHeaders headers, String rawBody) {
        long now = Instant.now().getEpochSecond();
        headers.set(BridgeSignatureService.TIMESTAMP_HEADER, Long.toString(now));
        headers.set(BridgeSignatureService.SIGNATURE_HEADER, this.signatureService.sign(now, rawBody));
    }

    /**
     * Turns a bridge error into something a person can act on.
     *
     * <p>The bridge answers with a machine code and a written reason, and both are worth keeping. A
     * country mismatch in particular is fixable by the customer in seconds if they are told which
     * country the instance serves and which number they scanned, and unfixable if they are shown
     * "something went wrong".
     */
    private Throwable translate(String path, WebClientResponseException e) {

        String body = e.getResponseBodyAsString();
        logger.error("Bridge call to {} failed with {}: {}", path, e.getStatusCode(), body);

        try {
            Map<String, Object> parsed = this.readMap(body);
            Object message = parsed.get("message");
            if (message != null) return new BridgeCallException(e.getStatusCode().value(),
                    String.valueOf(parsed.get("error")), String.valueOf(message), parsed);
        } catch (Exception ignored) {
            // Fall through to the raw body: an unparseable error is still better than none.
        }

        return new BridgeCallException(e.getStatusCode().value(), "bridge_error", body, Map.of());
    }

    private String write(Object body) {
        try {
            return this.objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encode a bridge request body.", e);
        }
    }

    private <T> T read(String raw, Class<T> type) {
        try {
            return this.objectMapper.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decode a bridge response.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String raw) {
        return this.read(raw, Map.class);
    }

    /** Carries the bridge's own error code and detail through to the caller rather than flattening it. */
    public static class BridgeCallException extends RuntimeException {

        private static final long serialVersionUID = 7719284403148169341L;

        private final transient int status;
        private final transient String code;
        private final transient Map<String, Object> detail;

        public BridgeCallException(int status, String code, String message, Map<String, Object> detail) {
            super(message);
            this.status = status;
            this.code = code;
            this.detail = detail;
        }

        public int getStatus() {
            return this.status;
        }

        public String getCode() {
            return this.code;
        }

        public Map<String, Object> getDetail() {
            return this.detail;
        }
    }
}
