package com.fincity.saas.message.controller.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.dto.bridge.BridgeInstance;
import com.fincity.saas.message.model.request.bridge.BridgeEventsRequest;
import com.fincity.saas.message.model.request.bridge.BridgeHeartbeatRequest;
import com.fincity.saas.message.model.request.bridge.BridgeRegisterRequest;
import com.fincity.saas.message.model.response.bridge.BridgeControlResponse;
import com.fincity.saas.message.service.bridge.BridgeEventIngestService;
import com.fincity.saas.message.service.bridge.BridgeNotRegisteredException;
import com.fincity.saas.message.service.bridge.BridgeRegistryService;
import com.fincity.saas.message.service.bridge.BridgeSignatureService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The control plane's HTTP surface, and the only way a bridge reaches the platform.
 *
 * <p>Every route here takes its body as a raw String rather than a bound type, for the same reason
 * the Meta webhook does: the signature is computed over the exact bytes received, so binding first
 * and re-serialising would change them and every check would fail.
 *
 * <p>Paths and verbs are fixed by the Go client and must not be tidied. It POSTs to {@code
 * /register}, {@code /{id}/heartbeat} and {@code /{id}/events}; changing a verb here to something
 * more RESTful silently strands the entire fleet, which will keep retrying against a 404 while
 * looking healthy in its own logs.
 */
@RestController
@RequestMapping("/api/message/bridges")
public class BridgeController {

    private static final Logger logger = LoggerFactory.getLogger(BridgeController.class);

    private final BridgeRegistryService registryService;
    private final BridgeEventIngestService ingestService;
    private final BridgeSignatureService signatureService;
    private final ObjectMapper objectMapper;

    public BridgeController(
            BridgeRegistryService registryService,
            BridgeEventIngestService ingestService,
            BridgeSignatureService signatureService,
            ObjectMapper objectMapper) {
        this.registryService = registryService;
        this.ingestService = ingestService;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    /**
     * Admits an instance to the fleet.
     *
     * <p>Two credentials, not one. The HMAC proves the body was not tampered with; the bootstrap
     * secret proves the caller is allowed to become a bridge at all. They are separate because this
     * is the call that gets an unknown host handed live customer sessions, and that deserves a
     * credential which can be rotated without re-keying every message in flight.
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<BridgeControlResponse>> register(
            @RequestHeader HttpHeaders headers, @RequestBody String rawBody) {

        if (!this.signatureService.hasValidBootstrapSecret(headers) || !this.signatureService.isTrusted(headers, rawBody))
            return unauthorized();

        BridgeRegisterRequest request;
        try {
            request = this.objectMapper.readValue(rawBody, BridgeRegisterRequest.class);
        } catch (Exception e) {
            logger.error("Could not parse a bridge registration body.", e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        if (request.getInstanceId() == null || request.getInstanceId().isBlank())
            return Mono.just(ResponseEntity.badRequest().build());

        return this.registryService.register(request).map(ResponseEntity::ok);
    }

    /**
     * The fifteen-second liveness and state report.
     *
     * <p>The response is the fleet's only inbound channel: it carries the desired image and the
     * draining flag, which together are the entire deployment mechanism. Nothing reaches into the
     * bridge host, so there is no inbound SSH to Mumbai and no key for it to hold.
     */
    @PostMapping("/{instanceId}/heartbeat")
    public Mono<ResponseEntity<BridgeControlResponse>> heartbeat(
            @PathVariable String instanceId, @RequestHeader HttpHeaders headers, @RequestBody String rawBody) {

        if (!this.signatureService.isTrusted(headers, rawBody)) return unauthorized();

        BridgeHeartbeatRequest request;
        try {
            request = this.objectMapper.readValue(rawBody, BridgeHeartbeatRequest.class);
        } catch (Exception e) {
            logger.error("Could not parse a heartbeat body from bridge {}.", instanceId, e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return this.registryService
                .heartbeat(instanceId, request)
                .map(ResponseEntity::ok)
                // NOT_FOUND is a instruction to the bridge, not a failure to report. Its heartbeat
                // loop re-registers after consecutive failures, and this is the signal that starts
                // that. Mapped here rather than left to the generic handler so the status is part of
                // this contract and visible next to the route it belongs to.
                .onErrorResume(BridgeNotRegisteredException.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    /**
     * Inbound messages and receipts, drained from a bridge's local outbox.
     *
     * <p><b>The 2xx here is a durability promise.</b> The bridge deletes its only copy of every
     * event in this batch the moment it sees one, and WhatsApp will not redeliver, so this must not
     * answer until the dispatch outbox rows are committed. That is why the service is awaited rather
     * than fired off: answering early and committing later would lose messages during exactly the
     * outage the outbox exists to survive.
     *
     * <p>A failure returns 5xx and the bridge retries the whole batch. Re-sending events already
     * committed is harmless, because the unique key on (channel, event key, event type) makes the
     * repeat a no-op.
     */
    @PostMapping("/{instanceId}/events")
    public Mono<ResponseEntity<Map<String, Object>>> events(
            @PathVariable String instanceId, @RequestHeader HttpHeaders headers, @RequestBody String rawBody) {

        if (!this.signatureService.isTrusted(headers, rawBody))
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        BridgeEventsRequest request;
        try {
            request = this.objectMapper.readValue(rawBody, BridgeEventsRequest.class);
        } catch (Exception e) {
            logger.error("Could not parse an events body from bridge {}.", instanceId, e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        if (request.getInstanceId() == null) request.setInstanceId(instanceId);

        return this.ingestService
                .ingest(request)
                .map(accepted -> ResponseEntity.ok(Map.<String, Object>of("accepted", accepted)));
    }

    /**
     * Declares the image a country's instances should run. Called by CI, not by a person.
     *
     * <p>Authenticated with the bootstrap secret rather than a user token, because the caller is a
     * GitHub Actions job with no user context and no way to obtain one. That also keeps the deploy
     * path off the platform's authentication entirely, which is the point: the job that can release
     * an image should not need, or have, a login that can do anything else.
     */
    @PostMapping("/release")
    public Mono<ResponseEntity<Map<String, Object>>> release(
            @RequestHeader HttpHeaders headers, @RequestBody String rawBody) {

        if (!this.signatureService.hasValidBootstrapSecret(headers))
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        Map<String, String> body;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = this.objectMapper.readValue(rawBody, Map.class);
            body = parsed;
        } catch (Exception e) {
            logger.error("Could not parse a release body.", e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        String image = body.get("image");
        if (image == null || image.isBlank())
            return Mono.just(ResponseEntity.badRequest().build());

        return this.registryService
                .release(body.get("country"), body.get("instanceId"), image)
                .map(updated -> ResponseEntity.ok(Map.<String, Object>of("instances", updated, "image", image)));
    }

    /**
     * The fleet view.
     *
     * <p>Deliberately <b>not</b> in the permit-all list with the routes above. Those are
     * machine-to-machine and carry their own credentials; this one exposes instance ids and internal
     * base URLs, so it goes through the platform's normal authentication like any other read.
     */
    @GetMapping
    public Mono<ResponseEntity<List<BridgeInstance>>> fleet() {
        return this.registryService.listFleet().map(ResponseEntity::ok);
    }

    private static <T> Mono<ResponseEntity<T>> unauthorized() {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
