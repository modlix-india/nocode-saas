package com.fincity.saas.message.controller.call;

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
@RequestMapping("/api/message/call/callback")
public class CallbackController {

    private final ExotelCallService exotelCallService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CallbackController(ExotelCallService exotelCallService, ObjectMapper objectMapper) {
        this.exotelCallService = exotelCallService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = ExotelCallService.EXOTEL_PROVIDER_URI,
            consumes = {
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.MULTIPART_FORM_DATA_VALUE
            })
    public Mono<ExotelCallStatusCallbackResponse> handleExotelCallback(ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        if (contentType != null && contentType.toString().equals(MediaType.APPLICATION_JSON_VALUE)) {
            return exchange.getRequest()
                    .getBody()
                    .next()
                    .map(dataBuffer -> ExotelCallStatusCallback.of(dataBuffer, objectMapper))
                    .flatMap(exotelCallService::processCallStatusCallback)
                    .map(result -> ExotelCallStatusCallbackResponse.success())
                    .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
        } else {
            return exchange.getFormData()
                    .map(ExotelCallStatusCallback::of)
                    .flatMap(exotelCallService::processCallStatusCallback)
                    .map(result -> ExotelCallStatusCallbackResponse.success())
                    .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
        }
    }

    @PostMapping(
            value = ExotelCallService.EXOTEL_PROVIDER_URI + "/passthru",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public Mono<ExotelCallStatusCallbackResponse> handleExotelPassThruCallback(ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(ExotelPassThruCallback::of)
                .flatMap(exotelCallService::processPassThruCallback)
                .map(result -> ExotelCallStatusCallbackResponse.success())
                .onErrorResume(e -> Mono.just(ExotelCallStatusCallbackResponse.error(e.getMessage())));
    }
}
