package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.model.request.message.provider.whatsapp.TicketWhatsappMessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.TicketWhatsappQueueTemplateRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.TicketWhatsappTemplateMessageRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.TicketWhatsappMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/ticket")
public class TicketWhatsappMessageController {

    private final TicketWhatsappMessageService ticketWhatsappMessageService;

    public TicketWhatsappMessageController(TicketWhatsappMessageService ticketWhatsappMessageService) {
        this.ticketWhatsappMessageService = ticketWhatsappMessageService;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<Message>> sendMessageByTicketId(@RequestBody TicketWhatsappMessageRequest request) {
        return this.ticketWhatsappMessageService.sendMessageByTicketId(request).map(ResponseEntity::ok);
    }

    // -------------------------------------------------------------------------------------------
    // Internal variants, for entity-processor only.
    //
    // Sending is not gated in this service: it cannot tell whether a caller may act on a given
    // deal, and it no longer holds the message history that Meta's 24-hour window is computed from.
    // entity-processor checks both and then calls these. The endpoints above stay for now so the
    // existing pages keep working, and come out when the UI is repointed and /api/message/** is
    // denied at the edge.
    // -------------------------------------------------------------------------------------------

    @PostMapping("/internal/send")
    public Mono<ResponseEntity<Message>> sendMessageByTicketIdInternal(
            @RequestBody TicketWhatsappMessageRequest request) {
        return this.ticketWhatsappMessageService.sendMessageByTicketId(request).map(ResponseEntity::ok);
    }

    @PostMapping("/internal/template/send")
    public Mono<ResponseEntity<Message>> sendTemplateMessageByTicketIdInternal(
            @RequestBody TicketWhatsappTemplateMessageRequest request) {
        return this.ticketWhatsappMessageService
                .sendTemplateMessageByTicketId(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/template/send")
    public Mono<ResponseEntity<Message>> sendTemplateMessageByTicketId(
            @RequestBody TicketWhatsappTemplateMessageRequest request) {
        return this.ticketWhatsappMessageService
                .sendTemplateMessageByTicketId(request)
                .map(ResponseEntity::ok);
    }

    /**
     * Called by entity-processor's queue listener. Takes a stored template id plus placeholder
     * values rather than a pre-built template message.
     */
    @PostMapping("/template/send-from-queue")
    public Mono<ResponseEntity<Void>> sendTemplateFromQueue(
            @RequestBody TicketWhatsappQueueTemplateRequest request) {
        return this.ticketWhatsappMessageService
                .sendTemplateFromQueue(request)
                .then(Mono.fromCallable(() -> ResponseEntity.noContent().<Void>build()));
    }
}
