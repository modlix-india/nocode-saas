package com.fincity.saas.message.controller.call.provider.exotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallStatusCallbackResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/call/callback/exotel")
public class ExotelCallBackController {

    private final ExotelCallService exotelCallService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExotelCallBackController(ExotelCallService exotelCallService, ObjectMapper objectMapper) {
        this.exotelCallService = exotelCallService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            consumes = {
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.MULTIPART_FORM_DATA_VALUE
            })
    public Mono<ExotelCallStatusCallbackResponse> handleExotelCallback(ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return exchange.getRequest()
                    .getBody()
                    .next()
                    .map(dataBuffer -> ExotelCallStatusCallback.of(dataBuffer, objectMapper))
                    .flatMap(exotelCallService::processCallStatusCallback)
                    .map(result -> ExotelCallStatusCallbackResponse.success())
                    .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
        }
        if (contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            return exchange.getFormData()
                    .map(ExotelCallStatusCallback::ofForm)
                    .flatMap(exotelCallService::processCallStatusCallback)
                    .map(result -> ExotelCallStatusCallbackResponse.success())
                    .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
        }

        if (contentType != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
            return exchange.getMultipartData()
                    .map(ExotelCallStatusCallback::ofMultiPart)
                    .flatMap(exotelCallService::processCallStatusCallback)
                    .map(result -> ExotelCallStatusCallbackResponse.success())
                    .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
        }

        return Mono.just(ExotelCallStatusCallbackResponse.error("Unsupported Content-Type: " + contentType));
    }

    @PostMapping(
            value = "/passthru",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public Mono<ExotelCallStatusCallbackResponse> handleExotelPassThruCallback(ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(ExotelPassThruCallback::of)
                .flatMap(exotelCallService::processPassThruCallback)
                .map(result -> ExotelCallStatusCallbackResponse.success())
                .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
    }
}
