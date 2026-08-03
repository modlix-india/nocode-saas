package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.service.message.TicketWhatsappConversationService;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{ticketId}/messages")
    public Mono<ResponseEntity<Map<String, Object>>> readTicketThread(
            @PathVariable("ticketId") Identity ticketId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service.readTicketThread(ticketId, page, size).map(ResponseEntity::ok);
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
