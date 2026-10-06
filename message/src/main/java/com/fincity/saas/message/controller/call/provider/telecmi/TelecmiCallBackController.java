package com.fincity.saas.message.controller.call.provider.telecmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.call.provider.telecmi.TelecmiCallService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * TeleCMI's event and CDR webhooks. Public: TeleCMI signs nothing and posts from several addresses, so the
 * {@code ?t=} token is the authentication. Both routes take either body, so a URL in the wrong dashboard field works.
 */
@RestController
@RequestMapping("/api/message/call/callback/telecmi")
public class TelecmiCallBackController {

    private final TelecmiCallService telecmiCallService;

    public TelecmiCallBackController(TelecmiCallService telecmiCallService) {
        this.telecmiCallService = telecmiCallService;
    }

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> handleEvent(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestParam(name = "t", required = false) String token,
            @RequestBody JsonNode body) {
        return this.process(appCode, clientCode, token, body);
    }

    @PostMapping(value = "/cdr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> handleCdr(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestParam(name = "t", required = false) String token,
            @RequestBody JsonNode body) {
        return this.process(appCode, clientCode, token, body);
    }

    private Mono<ResponseEntity<Void>> process(String appCode, String clientCode, String token, JsonNode body) {
        return this.telecmiCallService
                .processWebhook(MessageAccess.of(appCode, clientCode, true), token, body)
                .then(Mono.fromSupplier(() -> ResponseEntity.ok().<Void>build()));
    }
}
