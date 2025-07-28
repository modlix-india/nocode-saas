package com.fincity.saas.message.controller;

import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.service.message.MessageConnectionService;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp")
public class WhatsappController {

    private final WhatsappMessageService whatsappMessageService;
    private final MessageConnectionService messageConnectionService;

    @Autowired
    public WhatsappController(
            WhatsappMessageService whatsappMessageService, MessageConnectionService messageConnectionService) {
        this.whatsappMessageService = whatsappMessageService;
        this.messageConnectionService = messageConnectionService;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<Message>> sendTextMessage(@RequestBody MessageRequest messageRequest) {
        return this.messageConnectionService
                .getConnection(
                        messageRequest.getAppCode(), messageRequest.getConnectionName(), messageRequest.getClientCode())
                .flatMap(connection -> this.whatsappMessageService.sendTextMessage(
                        messageRequest.getMessageAccess(),
                        messageRequest.getTo(),
                        messageRequest.getText(),
                        connection))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
