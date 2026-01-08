package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.model.request.message.provider.whatsapp.TicketWhatsappMessageRequest;
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

    @PostMapping("/send/template")
    public Mono<ResponseEntity<Message>> sendTemplateMessageByTicketId(
            @RequestBody TicketWhatsappTemplateMessageRequest request) {
        return this.ticketWhatsappMessageService
                .sendTemplateMessageByTicketId(request)
                .map(ResponseEntity::ok);
    }
}
