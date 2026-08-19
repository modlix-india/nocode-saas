package com.fincity.saas.message.controller.bridge;

import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.bridge.BridgeSessionSnapshot;
import com.fincity.saas.message.service.bridge.BridgeNumberAlreadyLinkedException;
import com.fincity.saas.message.service.bridge.BridgeSessionService;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
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
 * What entity-processor calls to drive a WhatsApp session.
 *
 * <p>Entity-processor never learns that a fleet exists. It has a session id and asks for things to
 * happen to it; placement, routing and instance health are all resolved on this side. That is the
 * main simplification the control-plane decision bought, and it is why this controller is short.
 *
 * <p>The UI does not reach these directly either. It goes UI to gateway to entity-processor to here,
 * because {@code /api/message/**} is meant to be denied at the edge.
 */
@RestController
@RequestMapping("/api/message/whatsapp/sessions/internal")
public class BridgeSessionInternalController {

    private final BridgeSessionService sessionService;

    public BridgeSessionInternalController(BridgeSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Starts linking a number.
     *
     * <p>Returns as soon as the instance is pairing, not when the customer has scanned. The QR code
     * is then polled from the endpoint below, because a code rotates roughly every 20 seconds and
     * holding a socket open for it buys nothing.
     *
     * <p>The number arrives as {@code phoneNumber}, which is what entity-processor documents and
     * what the page sends. It is forwarded to the bridge as {@code phone}, which is that hop's own
     * contract and is tested on both sides. The two names are not a mistake to tidy away: this is
     * the boundary between them.
     *
     * <p>This read {@code phone} until 2026-08-07, so every attempt to link a number failed with an
     * empty 400 and nothing anywhere said which field was missing. Hence the message below.
     */
    @PostMapping
    public Mono<ResponseEntity<BridgeSessionSnapshot>> create(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody Map<String, Object> request) {

        Object phone = request.get("phoneNumber");
        if (phone == null || phone.toString().isBlank())
            return Mono.just(ResponseEntity.badRequest()
                    .header("X-Reason", "phoneNumber is required")
                    .build());

        Object productId = request.get("productId");
        Object ownerService = request.get("ownerService");

        return this.sessionService
                .createSession(
                        MessageAccess.of(appCode, clientCode, Boolean.TRUE),
                        phone.toString(),
                        productId == null ? null : ULong.valueOf(productId.toString()),
                        ownerService == null ? null : ownerService.toString())
                .map(ResponseEntity::ok)
                // CONFLICT rather than a 500, because this is a normal thing to do: click Link
                // twice, or link again after abandoning a pairing attempt. The reason travels in a
                // header so the caller can say something specific without this endpoint growing an
                // error body shape it does not otherwise have.
                .onErrorResume(BridgeNumberAlreadyLinkedException.class, e -> Mono.just(ResponseEntity.status(
                                HttpStatus.CONFLICT)
                        .header("X-Reason", "already-linked")
                        .build()));
    }

    /** Every session the tenant has, for the integration page's list. */
    @GetMapping
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> list(
            @RequestParam("appCode") String appCode, @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .listSessions(MessageAccess.of(appCode, clientCode, Boolean.TRUE))
                .map(ResponseEntity::ok);
    }

    /**
     * The session a product sends from.
     *
     * <p>Answers 200 with an empty body rather than 404 when the product has no session and the
     * tenant has no default. Having no linked number is a state the caller has to handle anyway, and
     * making it an error means every send path has to distinguish "not configured" from "the message
     * service is broken".
     */
    @GetMapping("/by-product/{productId}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> byProduct(
            @PathVariable BigInteger productId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .getByProduct(MessageAccess.of(appCode, clientCode, Boolean.TRUE), ULong.valueOf(productId))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok().build());
    }

    /**
     * The session a caller's code resolves to, falling back to the tenant default.
     *
     * <p>The send path's entry point now that the product holds the mapping. Same empty-body-not-404
     * contract as {@link #byProduct} and for the same reason.
     *
     * <p>{@code sessionCode} is optional: absent means the product named no number, which resolves
     * to the default. Deliberately a query parameter rather than a path segment so that "no code" is
     * expressible without a second route.
     */
    @GetMapping("/resolve")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> resolve(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam(value = "sessionCode", required = false) String sessionCode) {

        return this.sessionService
                .resolve(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionCode)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok().build());
    }

    /** Makes one number the tenant's fallback for products that name none. */
    @PatchMapping("/{sessionId}/default")
    public Mono<ResponseEntity<Boolean>> markDefault(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .markDefault(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{sessionId}")
    public Mono<ResponseEntity<BridgeSessionSnapshot>> read(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .getSession(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{sessionId}/qr")
    public Mono<ResponseEntity<Map<String, Object>>> qr(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .getQr(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{sessionId}")
    public Mono<ResponseEntity<Void>> unlink(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode) {

        return this.sessionService
                .unlink(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionId)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /**
     * Sends a text.
     *
     * <p>Slow by design. The bridge holds this open for the randomised 5-15 second gap and the
     * typing indicator, so the caller's own timeout has to allow for it. A client that gives up
     * early does not cancel the send; it just stops hearing about it, and the message goes out
     * anyway with nobody holding the id it returned.
     */
    @PostMapping("/{sessionId}/messages")
    public Mono<ResponseEntity<Map<String, Object>>> send(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody Map<String, Object> request) {

        // Object, not String. The map was typed Map<String,String> and any structured body - an
        // attachment's file details, for instance - failed to bind before a line of this ran, with
        // a 400 that said nothing about why.
        String to = str(request.get("to"));
        String text = str(request.get("text"));
        String filePath = str(request.get("filePath"));

        if (to == null || to.isBlank()) return Mono.just(ResponseEntity.badRequest().build());

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        if (filePath != null && !filePath.isBlank())
            return this.sessionService
                    .sendMedia(
                            access,
                            sessionId,
                            to,
                            filePath,
                            str(request.get("kind")),
                            str(request.get("mimeType")),
                            str(request.get("fileName")),
                            text,
                            Boolean.TRUE.equals(request.get("voiceNote")))
                    .map(ResponseEntity::ok);

        // Text with no attachment still needs a body; an empty message is not a thing WhatsApp
        // accepts, and sending one would burn a paced slot on nothing.
        if (text == null || text.isBlank()) return Mono.just(ResponseEntity.badRequest().build());

        return this.sessionService.sendText(access, sessionId, to, text).map(ResponseEntity::ok);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /** Called when a person opens a thread, never when a message arrives. */
    @PostMapping("/{sessionId}/read")
    public Mono<ResponseEntity<Void>> markRead(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody Map<String, Object> request) {

        Object chat = request.get("chat");
        Object ids = request.get("messageIds");

        if (chat == null || !(ids instanceof List<?> list) || list.isEmpty())
            return Mono.just(ResponseEntity.badRequest().build());

        Object sender = request.get("sender");

        return this.sessionService
                .markRead(
                        MessageAccess.of(appCode, clientCode, Boolean.TRUE),
                        sessionId,
                        chat.toString(),
                        sender == null ? null : sender.toString(),
                        list.stream().map(Object::toString).toList())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /** Whether a number is reachable, checked before a lead is messaged at all. */
    @GetMapping("/{sessionId}/on-whatsapp")
    public Mono<ResponseEntity<Map<String, Object>>> onWhatsApp(
            @PathVariable String sessionId,
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("number") String number) {

        return this.sessionService
                .onWhatsApp(MessageAccess.of(appCode, clientCode, Boolean.TRUE), sessionId, number)
                .map(ResponseEntity::ok);
    }
}
