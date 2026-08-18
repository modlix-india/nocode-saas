package com.fincity.saas.entity.processor.feign;

import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import java.math.BigInteger;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "message")
public interface IFeignMessageService {

    String MESSAGE_PATH = "/api/message";
    String EXOTEL_CALL_PATH = MESSAGE_PATH + "/call/exotel";
    String WHATSAPP_PATH = MESSAGE_PATH + "/whatsapp";
    String WHATSAPP_TICKET_PATH = WHATSAPP_PATH + "/ticket";

    @PostMapping(EXOTEL_CALL_PATH + "/connect")
    Mono<ExotelConnectAppletResponse> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest callRequest);

    /**
     * Places an outbound call through the provider.
     *
     * <p>Only call after confirming the caller may act on the deal, and pass the number taken from
     * that deal rather than one the caller supplied. The message service performs neither check: it
     * cannot evaluate deal access, and it has no way to tell a legitimate destination from an
     * arbitrary one.
     *
     * <p>Untyped both ways. The response is the message service's own call representation, including
     * the provider's call id and raw payloads, and mirroring that DTO here would create a second copy
     * to keep in step for no gain: this service maps it straight onto its own row.
     */
    @PostMapping(EXOTEL_CALL_PATH + "/internal/make")
    Mono<Map<String, Object>> makeCallInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody Map<String, Object> request);

    /**
     * A deal's WhatsApp thread. Only call this after confirming the caller may see the ticket; the
     * message service performs no deal-level check of its own.
     *
     * <p>Untyped on purpose. The response is a Spring {@code Page}, which does not deserialize
     * cleanly into {@code PageImpl} over Feign, and mirroring the full WhatsApp message DTO here
     * would create a second copy to keep in step. The body is passed straight through to the
     * caller, so the UI sees exactly the shape it saw before.
     */
    @GetMapping(WHATSAPP_PATH + "/internal/ticket/{ticketId}/messages")
    Mono<Map<String, Object>> getTicketWhatsappMessages(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @PathVariable("ticketId") BigInteger ticketId,
            @RequestParam int page,
            @RequestParam int size);

    /**
     * Sends a free-form WhatsApp message on a deal.
     *
     * <p>Only call after checking that the caller may act on the deal and that Meta's 24-hour
     * window is open. The message service performs neither check: it cannot evaluate deal access,
     * and since the conversation history moved it no longer holds the timestamps the window is
     * computed from.
     *
     * <p>The caller's token is forwarded explicitly, matching how {@code IFeignSecurityService}
     * does it. Feign here does not propagate the security context on its own, and the message
     * service still attributes the outbound record to the sending user.
     */
    @PostMapping(WHATSAPP_TICKET_PATH + "/internal/send")
    Mono<Map<String, Object>> sendWhatsappMessageByTicket(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody Map<String, Object> request);

    /**
     * Pulls a media file down from Meta and stores it, returning where it landed.
     *
     * <p>Takes Meta's media id rather than a message id: the two services no longer share message
     * rows, so a row id means nothing across the boundary. The caller has already checked that the
     * requester may see the deal.
     */
    @PostMapping(WHATSAPP_PATH + "/internal/media/download")
    Mono<Map<String, Object>> downloadWhatsappMedia(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody Map<String, Object> request);

    // ---------------------------------------------------------------------------------------------
    // Linked-device sessions.
    //
    // Replaces the template and business-number half of this interface, which went with the Cloud
    // API: there is no approval to request and no WABA to sync. This service never learns that a
    // fleet of bridges exists, which is the point of the message service being the control plane.
    // Placement, routing and instance health all resolve on the far side of these calls.
    // ---------------------------------------------------------------------------------------------

    String WHATSAPP_SESSION_PATH = WHATSAPP_PATH + "/sessions/internal";

    /**
     * Starts linking a number, returning as soon as the instance is pairing.
     *
     * <p>Not when the customer has scanned: that can take minutes and may never happen. The QR code
     * is polled from {@link #getWhatsappSessionQr} instead, because a code rotates roughly every 20
     * seconds and holding a socket open for it buys nothing.
     *
     * <p>A country with no instance registered surfaces as a plain "not available in that country
     * yet", raised at placement, rather than as a connection error.
     */
    @PostMapping(WHATSAPP_SESSION_PATH)
    Mono<Map<String, Object>> createWhatsappSession(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody Map<String, Object> request);

    /** Every session the tenant has, for the integration page. Cached view, up to a heartbeat stale. */
    @GetMapping(WHATSAPP_SESSION_PATH)
    Mono<java.util.List<Map<String, Object>>> listWhatsappSessions(
            @RequestParam String appCode, @RequestParam String clientCode);

    /**
     * The session a product sends from, falling back to the tenant's default.
     *
     * <p>Answers 200 with an empty body when there is no linked number, rather than 404. Not being
     * configured is a state every send path has to handle anyway, and making it an error would mean
     * each one distinguishing that from the message service being down.
     */
    @GetMapping(WHATSAPP_SESSION_PATH + "/by-product/{productId}")
    Mono<Map<String, Object>> getWhatsappSessionByProduct(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @PathVariable("productId") BigInteger productId);

    /**
     * The session a code names, falling back to the tenant's default when it names nothing placeable.
     *
     * <p>Supersedes {@link #getWhatsappSessionByProduct}: the product now carries the code, so this
     * service arrives knowing which number it wants instead of asking the message service to look it
     * up. Placement and the fallback stay on the far side, which is why this is one call rather than
     * a read followed by a decision here.
     *
     * <p>{@code sessionCode} may be null, meaning the product named no number.
     */
    @GetMapping(WHATSAPP_SESSION_PATH + "/resolve")
    Mono<Map<String, Object>> resolveWhatsappSession(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(value = "sessionCode", required = false) String sessionCode);

    /** Makes one number the tenant's fallback for products that name none. */
    @PatchMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/default")
    Mono<Boolean> markWhatsappSessionDefault(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("sessionId") String sessionId);

    /** Live state, read from the holding instance rather than from a cached row. */
    @GetMapping(WHATSAPP_SESSION_PATH + "/{sessionId}")
    Mono<Map<String, Object>> getWhatsappSession(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("sessionId") String sessionId);

    /** The current pairing code, polled by the link panel while the session is PAIRING. */
    @GetMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/qr")
    Mono<Map<String, Object>> getWhatsappSessionQr(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("sessionId") String sessionId);

    /** Unlinks a number and frees its slot on the instance. */
    @DeleteMapping(WHATSAPP_SESSION_PATH + "/{sessionId}")
    Mono<Void> unlinkWhatsappSession(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("sessionId") String sessionId);

    /**
     * Sends a message through a linked session.
     *
     * <p><b>Slow by design.</b> The bridge holds the call open for its randomised 5-15 second gap and
     * the typing indicator, so this can take most of a minute. A client that gives up early does not
     * cancel the send: the message still goes, and nobody is left holding the id it returned.
     */
    @PostMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/messages")
    Mono<Map<String, Object>> sendWhatsappSessionMessage(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, Object> request);

    /** Marks a thread read on the customer's handset, called when a person opens it rather than on arrival. */
    @PostMapping(WHATSAPP_SESSION_PATH + "/{sessionId}/read")
    Mono<Void> markWhatsappSessionRead(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, Object> request);
}
