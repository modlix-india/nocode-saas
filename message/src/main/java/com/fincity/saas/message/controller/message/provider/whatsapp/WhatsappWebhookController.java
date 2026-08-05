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

    /**
     * One callback for every tenant, configured once on the Meta app.
     *
     * <p>Takes no {@code appCode} or {@code clientCode}. It used to take both as headers, which the
     * gateway derived from a tenant-specific URL path, and that was wrong twice over: the path
     * segment in a Modlix URL names the client <b>hosting</b> the application rather than the one
     * consuming it, and giving each tenant its own callback URL meant two tenants sharing a Meta
     * business account each believed they owned the single override that account can hold. The
     * service resolves the tenant from {@code metadata.phone_number_id} instead, which identifies
     * it exactly and cannot be contradicted by how the request was addressed.
     *
     * @param signature Meta's HMAC over the raw body, and the only thing establishing that Meta
     *     sent this at all.
     * @param payload taken as a raw String on purpose. The signature is computed over the exact
     *     bytes received, so binding to a parsed type and re-serialising would break every check.
     */
    @PostMapping
    public Mono<ResponseEntity<MessageResponse>> receiveWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        return FlatMapUtil.flatMapMono(() -> IWebHook.constructEvent(payload), event -> this.whatsappMessageService
                .processWebhookEvent(event, signature, payload)
                .map(response -> response.getStatus().getHttpStatus().is2xxSuccessful()
                        ? ResponseEntity.ok(response)
                        : ResponseEntity.status(response.getStatus().getHttpStatus())
                                .body(response)))
                // An event for a number this platform does not hold, or one carrying nothing we
                // act on. Meta is told 200 on purpose: it did its job, and a non-2xx would have it
                // redeliver something that will never resolve.
                .defaultIfEmpty(ResponseEntity.ok().build());
    }
}
