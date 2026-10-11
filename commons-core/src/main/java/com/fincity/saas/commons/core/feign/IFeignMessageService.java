package com.fincity.saas.commons.core.feign;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

/**
 * The message service's internal routes, for {@code CoreServices.Message.*}.
 *
 * <p>The same routes entity-processor calls, and with the same contract: they check that a session
 * belongs to the app and client named, and nothing else. Whether this caller may act for that
 * client is decided before any of these is called, in the functions.
 *
 * <p>Untyped bodies on purpose, as on entity-processor's side: the responses are the message
 * service's own DTOs and are passed straight back to KIRun.
 */
@ReactiveFeignClient(name = "message")
public interface IFeignMessageService {

    String WHATSAPP_SESSION_PATH = "/api/message/whatsapp/sessions/internal";
    String EXOTEL_CALL_PATH = "/api/message/call/exotel";

    /**
     * Starts linking a number: places a session on a bridge and starts pairing. The body carries
     * {@code phoneNumber}, optional {@code productId} and {@code ownerService}.
     *
     * <p>409 with a message when the number is already linked and paired.
     */
    @PostMapping(WHATSAPP_SESSION_PATH)
    Mono<Map<String, Object>> createWhatsappSession(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody Map<String, Object> request);

    /** Every session the tenant has, from the message service's own rows (up to a heartbeat stale). */
    @GetMapping(WHATSAPP_SESSION_PATH)
    Mono<List<Map<String, Object>>> listWhatsappSessions(
            @RequestParam("appCode") String appCode, @RequestParam("clientCode") String clientCode);

    /** The pairing code (QR) of a session that is still pairing. */
    @GetMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/qr")
    Mono<Map<String, Object>> getWhatsappSessionQr(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("sessionId") String sessionId);

    /** Unlinks a session: logs the device out and frees its bridge slot. */
    @DeleteMapping(WHATSAPP_SESSION_PATH + "/{sessionId}")
    Mono<Void> unlinkWhatsappSession(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("sessionId") String sessionId);

    /**
     * Makes one number the tenant's default, the fallback for products that name no number.
     *
     * <p>Answers false when the code names nothing placeable (an unknown or unlinked session).
     */
    @PatchMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/default")
    Mono<Boolean> markWhatsappSessionDefault(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("sessionId") String sessionId);

    /**
     * The session a code names, or the tenant's default when it names nothing placeable.
     *
     * <p>Empty, not an error, when the tenant has no linked number at all.
     */
    @GetMapping(WHATSAPP_SESSION_PATH + "/resolve")
    Mono<Map<String, Object>> resolveWhatsappSession(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam(value = "sessionCode", required = false) String sessionCode);

    /**
     * Sends a text, or an attachment with an optional caption, through a linked session.
     *
     * <p>Slow by design: the bridge holds the call for its paced gap and typing indicator.
     */
    @PostMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/messages")
    Mono<Map<String, Object>> sendWhatsappSessionMessage(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, Object> request);

    /**
     * Places a call, recording {@code ownerService} so the call's status events come back to it.
     *
     * <p>Places real, billable calls with no check of its own.
     */
    @PostMapping(EXOTEL_CALL_PATH + "/internal/make")
    Mono<Map<String, Object>> makeCallInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("ownerService") String ownerService,
            @RequestBody Map<String, Object> request);
}
