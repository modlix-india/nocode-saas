package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import com.fincity.saas.entity.processor.service.message.WhatsappInboundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Where the message service hands over WhatsApp events for numbers this service owns.
 *
 * <p>Behind {@code /internal}, which in this codebase means permitAll at the application layer and
 * blocked by nginx. The real protection is upstream: the message service only calls this after
 * verifying Meta's signature over the raw webhook body, so an event reaching here has already been
 * proven to come from Meta.
 *
 * <p>A non-2xx is meaningful. The caller keeps its outbox row and retries with backoff, so failing
 * loudly here is correct and swallowing an error is not.
 */
@RestController
@RequestMapping("api/entity/processor/whatsapp/internal")
public class WhatsappInboundController {

    private final WhatsappInboundService service;

    public WhatsappInboundController(WhatsappInboundService service) {
        this.service = service;
    }

    @PostMapping("/inbound")
    public Mono<ResponseEntity<Void>> accept(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody WhatsappInboundRequest request) {
        return this.service
                .accept(appCode, clientCode, request)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
