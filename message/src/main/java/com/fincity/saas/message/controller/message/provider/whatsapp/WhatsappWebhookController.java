package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.model.message.whatsapp.webhook.IWebHook;
import com.fincity.saas.message.model.response.MessageResponse;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/webhooks/whatsapp")
public class WhatsappWebhookController {

    private final WhatsappMessageService whatsappMessageService;

    @Autowired
    public WhatsappWebhookController(WhatsappMessageService whatsappMessageService) {
        this.whatsappMessageService = whatsappMessageService;
    }

    @GetMapping
    public Mono<ResponseEntity<String>> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        return this.whatsappMessageService
                .verifyMetaWebhook(mode, token, challenge)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<MessageResponse>> receiveWebhook(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody String payload) {

        return FlatMapUtil.flatMapMono(() -> IWebHook.constructEvent(payload), event -> this.whatsappMessageService
                .processWebhookEvent(appCode, clientCode, event)
                .map(response -> response.getStatus().getHttpStatus().is2xxSuccessful()
                        ? ResponseEntity.ok(response)
                        : ResponseEntity.status(response.getStatus().getHttpStatus())
                                .body(response)));
    }
}
