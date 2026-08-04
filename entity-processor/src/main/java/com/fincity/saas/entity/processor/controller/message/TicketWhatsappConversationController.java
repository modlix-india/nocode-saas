package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.service.message.TicketWhatsappConversationService;
import com.fincity.saas.entity.processor.service.message.WhatsappCswService;
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
     * Whether Meta's 24-hour window is open, so the UI knows whether to offer a free-text composer
     * or force a template.
     */
    @GetMapping("/{ticketId}/csw")
    public Mono<ResponseEntity<WhatsappCswService.CswStatus>> readCswStatus(
            @PathVariable("ticketId") Identity ticketId) {
        return this.service.readCswStatus(ticketId).map(ResponseEntity::ok);
    }

    /**
     * Sends a free-form message. 409 when the 24-hour window has closed, which is a real state the
     * UI has to handle by offering a template rather than an error toast.
     *
     * <p>The ticket comes from the path, not the body: the path is what gets access-checked, so any
     * ticket id in the payload is overwritten before the send.
     */
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

    /** Sends an approved template, which is the only thing permitted outside the window. */
    @PostMapping("/{ticketId}/send/template")
    public Mono<ResponseEntity<Map<String, Object>>> sendTemplate(
            @PathVariable("ticketId") Identity ticketId, @RequestBody Map<String, Object> request) {
        return this.service.sendTemplate(ticketId, request).map(ResponseEntity::ok);
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
