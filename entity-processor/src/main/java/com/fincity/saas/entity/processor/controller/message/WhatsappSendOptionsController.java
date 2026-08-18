package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.WhatsappSendOptionsService;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public entry point for the numbers a WhatsApp message can be sent from, and how healthy they are.
 * Thin by design: the access check and the call out to the message service both live in the service,
 * per this codebase's convention that authorization belongs on the service layer.
 *
 * <p>These stand in front of the message service's own routes so the UI has a single host to talk
 * to and {@code /api/message/**} can be closed at the edge. The UI never reaches a bridge, and never
 * learns that a fleet of them exists: placement, routing and instance health all resolve two hops
 * away from here.
 *
 * <p>Split by authority rather than by verb. The reads are open to anyone who can work a deal,
 * because a salesperson has to see whether the number is connected before they can send at all, and
 * gating that on the administrative role would blank the composer for exactly the people who use it.
 * Linking and unlinking commit the tenant's real business number and stay on {@code ROLE_Owner}.
 *
 * <p>Bodies are passed through unchanged rather than remapped, so a page repointed from {@code
 * api/message/whatsapp/...} to here binds to exactly the fields it already did.
 */
@RestController
@RequestMapping("api/entity/processor/whatsapp")
public class WhatsappSendOptionsController {

    private final WhatsappSendOptionsService service;

    public WhatsappSendOptionsController(WhatsappSendOptionsService service) {
        this.service = service;
    }

    /** Every number the tenant has linked, with its live state. The settings page list. */
    @GetMapping("/sessions")
    public Mono<ResponseEntity<List<Map<String, Object>>>> readSessions() {
        return this.service.readSessions().map(ResponseEntity::ok);
    }

    /**
     * The number a product sends from.
     *
     * <p>An empty object rather than an empty body when nothing is linked. That distinction is not
     * cosmetic: an empty body reaches the UI as an empty string, and a binding expecting an object
     * then indexes into it and fails on every field it reads.
     */
    @GetMapping("/sessions/by-product/{productId}")
    public Mono<ResponseEntity<Map<String, Object>>> readSessionForProduct(
            @PathVariable("productId") BigInteger productId) {
        return this.service.readSessionForProduct(productId).map(ResponseEntity::ok);
    }

    /** Live state for one linked number, read from the instance holding it rather than a cached row. */
    @GetMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> readSession(@PathVariable("sessionId") String sessionId) {
        return this.service.readSession(sessionId).map(ResponseEntity::ok);
    }

    /**
     * The current pairing code, polled every couple of seconds while a number is being linked.
     *
     * <p>Polled rather than streamed because a code rotates roughly every 20 seconds and the
     * customer may take minutes to pick up their phone, or never scan at all. Holding a socket open
     * for that buys nothing.
     */
    @GetMapping("/sessions/{sessionId}/qr")
    public Mono<ResponseEntity<Map<String, Object>>> readQr(@PathVariable("sessionId") String sessionId) {
        return this.service.readQr(sessionId).map(ResponseEntity::ok);
    }

    /**
     * How a linked number is placed against every limit that keeps it alive.
     *
     * <p>The standing health panel on the settings page. Pass {@code ticketId} to include the
     * per-deal figures the composer's override panel needs; without it the 24-hour numbers are
     * simply absent, because they are a property of a conversation rather than of the number.
     */
    @GetMapping("/sessions/{sessionId}/health")
    public Mono<ResponseEntity<WhatsappSessionHealth>> readHealth(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "ticketId", required = false) List<ULong> ticketIds) {
        return this.service
                .readHealth(sessionId, ticketIds == null ? List.of() : ticketIds)
                .map(ResponseEntity::ok);
    }

    /**
     * Starts linking a number. Owner only, and the response arrives long before the customer scans.
     *
     * <p>Body carries {@code phoneNumber} and an optional {@code productId}. The declared number is
     * validated against the instance's country here as a courtesy; the check that counts happens on
     * pairing, against the handset that actually scanned.
     */
    @PostMapping("/sessions")
    public Mono<ResponseEntity<Map<String, Object>>> createSession(@RequestBody Map<String, Object> request) {
        return this.service.createSession(request).map(ResponseEntity::ok);
    }

    /** Unlinks a number. Relinking needs a fresh scan; the conversation history is unaffected. */
    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Boolean>> unlinkSession(@PathVariable("sessionId") String sessionId) {
        return this.service.unlinkSession(sessionId).map(ResponseEntity::ok);
    }

    /** Makes one number the tenant's fallback for products that name none. Owner-gated on the service. */
    @PatchMapping("/sessions/{sessionId}/default")
    public Mono<ResponseEntity<Boolean>> markDefaultSession(@PathVariable("sessionId") String sessionId) {
        return this.service.markDefaultSession(sessionId).map(ResponseEntity::ok);
    }
}
