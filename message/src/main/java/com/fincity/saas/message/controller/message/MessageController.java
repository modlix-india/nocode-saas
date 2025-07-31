package com.fincity.saas.message.controller.message;

import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.model.request.message.WhatsappMessageRequest;
import com.fincity.saas.message.service.message.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<Message>> sendMessage(@RequestBody MessageRequest messageRequest) {
        return this.messageService
                .sendMessage(messageRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/send/whatsapp")
    public Mono<ResponseEntity<Message>> sendWhatsappMessage(
            @RequestBody WhatsappMessageRequest whatsappMessageRequest) {
        return this.messageService
                .sendWhatsappMessage(whatsappMessageRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
