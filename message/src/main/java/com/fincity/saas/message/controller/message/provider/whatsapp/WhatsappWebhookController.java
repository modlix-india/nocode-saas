package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.model.message.whatsapp.webhook.WebHook;
import com.fincity.saas.message.model.message.whatsapp.webhook.WebHookEvent;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@RestController
@RequestMapping("/api/message/whatsapp/webhook")
public class WhatsappWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappWebhookController.class);

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

        return whatsappMessageService
                .verifyWebhook(mode, token, challenge)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping
    public Mono<ResponseEntity<String>> receiveWebhook(@RequestBody String payload) {
        return Mono.fromCallable(() -> {
                    try {
                        return WebHook.constructEvent(payload);
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing webhook payload", e);
                        throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid payload");
                    }
                })
                .flatMap(this::processWebhookEvent)
                .then(Mono.fromCallable(() -> ResponseEntity.ok("Webhook received")))
                .onErrorResume(e -> {
                    logger.error("Error processing webhook", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error processing webhook"));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappWebhookController.receiveWebhook"));
    }

    private Mono<Void> processWebhookEvent(WebHookEvent event) {
        logger.info("Processing webhook event: {}", event);

        return Mono.empty();
    }
}
