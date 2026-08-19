package com.fincity.saas.entity.processor.controller.message;

import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.MediaType;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.TicketWhatsappConversationService;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public entry point for reading WhatsApp conversations. Thin by design: the access check and the
 * call out to the message service both live in the service, per this codebase's convention that
 * authorization belongs on the service layer.
 */
@RestController
@RequestMapping("api/entity/processor/whatsapp/conversations")
public class TicketWhatsappConversationController {

    private final TicketWhatsappConversationService service;

    public TicketWhatsappConversationController(TicketWhatsappConversationService service) {
        this.service = service;
    }

    /**
     * A deal's thread.
     *
     * <p>Returns the conversation, not just this deal's slice of it: every message on any deal
     * sharing the customer's number that the caller can see. That is what keeps the history intact
     * when a customer holds several deals or the business number changes.
     *
     * @param search optional, matches message content
     */
    @GetMapping("/{ticketId}/messages")
    public Mono<ResponseEntity<Page<WhatsappMessage>>> readTicketThread(
            @PathVariable("ticketId") Identity ticketId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service
                .readTicketThread(ticketId, search, PageRequest.of(page, size))
                .map(ResponseEntity::ok);
    }

    /** Clears the unread badge for a conversation, across every deal the caller can see on it. */
    @PostMapping("/{ticketId}/read")
    public Mono<ResponseEntity<Integer>> markRead(@PathVariable("ticketId") Identity ticketId) {
        return this.service.markRead(ticketId).map(ResponseEntity::ok);
    }

    /**
     * How the number this deal sends from is placed against every limit, plus whether anything is
     * currently holding a send.
     *
     * <p>What the composer calls before offering the override, and what fills the override panel.
     * Same figures as the standing panel on the settings page, from the same computation, so the two
     * cannot tell a person different things about the same number.
     */
    @GetMapping("/{ticketId}/health")
    public Mono<ResponseEntity<WhatsappSessionHealth>> readHealth(@PathVariable("ticketId") Identity ticketId) {
        return this.service.readHealth(ticketId).map(ResponseEntity::ok);
    }

    /**
     * Lets a person undo an opt-out that was detected in error.
     *
     * <p>Separate from the send on purpose. Opt-out is the one hold a {@code force} flag will not
     * override, because a checkbox on the send button is how "they asked us to stop" turns into a
     * report. Reversing it is a deliberate act on the deal instead.
     */
    @PostMapping("/{ticketId}/opt-out/clear")
    public Mono<ResponseEntity<Ticket>> clearOptOut(@PathVariable("ticketId") Identity ticketId) {
        return this.service.clearOptOut(ticketId).map(ResponseEntity::ok);
    }

    /**
     * Sends a message the agent typed.
     *
     * <p>409 when a pacing gate is holding it, which is a real state the UI handles by showing the
     * override panel rather than an error toast. Retrying the same call with {@code force: true}
     * sends anyway, for every hold except opt-out and "no number connected".
     *
     * <p>The ticket comes from the path, not the body: the path is what gets access-checked.
     */
    /**
     * Sends an attachment on a conversation.
     *
     * <p>Multipart, so the file rides the same request that names the deal. The alternative - upload
     * to storage from the browser, then send a reference - would need every agent to hold write
     * access to the tenant's secured files, which is a far larger grant than sending a photo.
     */
    @PostMapping(value = "/{ticketId}/send-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> sendMedia(
            @PathVariable("ticketId") Identity ticketId,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "caption", required = false) String caption,
            @RequestPart(value = "kind", required = false) String kind,
            @RequestPart(value = "voiceNote", required = false) String voiceNote,
            @RequestPart(value = "force", required = false) String force) {

        return this.service
                .sendMedia(
                        ticketId,
                        file,
                        caption,
                        kind,
                        Boolean.parseBoolean(voiceNote),
                        Boolean.parseBoolean(force))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{ticketId}/send")
    public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
            @PathVariable("ticketId") Identity ticketId, @RequestBody Map<String, Object> request) {
        return this.service.sendMessage(ticketId, request).map(ResponseEntity::ok);
    }

    /**
     * Pulls a message's media down from Meta and caches it on the row.
     *
     * <p>Takes the message id from the path rather than a media id from the body. A caller-supplied
     * media id would let anyone with one visible deal fetch media from a conversation they cannot
     * see; taking the row id lets the service check the message actually belongs to this
     * conversation first.
     */
    @PostMapping("/{ticketId}/messages/{messageId}/media")
    public Mono<ResponseEntity<WhatsappMessage>> downloadMedia(
            @PathVariable("ticketId") Identity ticketId,
            @PathVariable("messageId") ULong messageId,
            @RequestParam(value = "connectionName", required = false) String connectionName) {
        return this.service.downloadMedia(ticketId, messageId, connectionName).map(ResponseEntity::ok);
    }

    /**
     * The inbox. One row per customer number, ordered by most recent message, falling back to when
     * the deal was last touched so that deals without a conversation are still listed and can be
     * started from.
     */
    @GetMapping
    public Mono<ResponseEntity<Page<WhatsappConversationResponse>>> readConversations(
            @RequestParam(value = "productId", required = false) Identity productId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service
                .readConversations(productId, search, PageRequest.of(page, size))
                .map(ResponseEntity::ok);
    }
}
