package com.fincity.saas.message.controller.call.provider.exotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallStatusCallbackResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/call/callback/exotel")
public class ExotelCallBackController {

    private final ExotelCallService exotelCallService;
    private final ObjectMapper objectMapper;

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
    public Mono<ExotelCallStatusCallbackResponse> handleExotelCallback(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            ServerWebExchange exchange) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return exchange.getRequest()
                    .getBody()
                    .next()
                    .map(dataBuffer -> ExotelCallStatusCallback.of(dataBuffer, objectMapper))
                    .flatMap(callback -> exotelCallService.processCallStatusCallback(access, callback))
                    .map(result -> ExotelCallStatusCallbackResponse.success());
        }
        if (contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            return exchange.getFormData()
                    .map(ExotelCallStatusCallback::ofForm)
                    .flatMap(callback -> exotelCallService.processCallStatusCallback(access, callback))
                    .map(result -> ExotelCallStatusCallbackResponse.success());
        }

        if (contentType != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
            return exchange.getMultipartData()
                    .map(ExotelCallStatusCallback::ofMultiPart)
                    .flatMap(callback -> exotelCallService.processCallStatusCallback(access, callback))
                    .map(result -> ExotelCallStatusCallbackResponse.success());
        }

        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Unsupported Content-Type: " + contentType));
    }

    @PostMapping(
            value = "/passthru",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public Mono<ExotelCallStatusCallbackResponse> handleExotelPassThruCallback(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            ServerWebExchange exchange) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);
        return exchange.getFormData()
                .map(ExotelPassThruCallback::of)
                .flatMap(callback -> exotelCallService.processPassThruCallback(access, callback))
                .map(result -> ExotelCallStatusCallbackResponse.success());
    }

    @GetMapping(value = "/passthru")
    public Mono<ExotelCallStatusCallbackResponse> handleExotelPassThruGetCallback(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            ServerWebExchange exchange) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);
        return Mono.just(exchange.getRequest().getQueryParams())
                .map(ExotelPassThruCallback::of)
                .flatMap(callback -> exotelCallService.processPassThruCallback(access, callback))
                .map(result -> ExotelCallStatusCallbackResponse.success());
    }
}
