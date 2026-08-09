package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.model.message.WhatsappStreamEvent;
import com.fincity.saas.entity.processor.service.message.WhatsappEventService;
import java.math.BigInteger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The browser's live connection for WhatsApp activity.
 *
 * <p>Thin, like every controller here: the identity resolution, the entitlement filter and the
 * interest narrowing all live in {@link WhatsappEventService}.
 *
 * <p>Events on this stream carry a deal's name and code, which they may because the service applies
 * the same access rule the deal query does before emitting to a connection. That is a deliberate
 * trade described in {@link WhatsappStreamEvent}; it is not an invitation to add the message body.
 */
@RestController
@RequestMapping("api/entity/processor/whatsapp")
public class WhatsappEventController {

    private final WhatsappEventService service;

    public WhatsappEventController(WhatsappEventService service) {
        this.service = service;
    }

    /**
     * @param appCode a request parameter rather than the usual header, because {@code EventSource}
     *     cannot set headers. The notification service's stream does the same for the same reason.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WhatsappStreamEvent>> stream(@RequestParam("appCode") String appCode) {
        return this.service.streamForCurrentUser(appCode);
    }

    /**
     * Tells the server which deal this connection is looking at, so status receipts can be narrowed.
     *
     * <p>{@code connectionId} comes from the {@code INIT} frame the stream opens with. Passing no
     * {@code ticketId} clears the interest and restores every receipt the caller is entitled to.
     *
     * <p>Returns 404 when the connection is unknown or belongs to another user. Unknown is the
     * common case and is not an error worth shouting about: a browser whose stream reconnected holds
     * a stale id until the new {@code INIT} arrives, and the correct behaviour is to keep sending it
     * everything until it re-declares.
     */
    @PutMapping("/stream/{connectionId}/watch")
    public Mono<ResponseEntity<Boolean>> watch(
            @PathVariable String connectionId, @RequestParam(required = false) BigInteger ticketId) {

        return this.service
                .watch(connectionId, ticketId == null ? null : ULongUtil.valueOf(ticketId))
                .map(applied -> Boolean.TRUE.equals(applied)
                        ? ResponseEntity.ok(Boolean.TRUE)
                        : ResponseEntity.notFound().build());
    }
}
