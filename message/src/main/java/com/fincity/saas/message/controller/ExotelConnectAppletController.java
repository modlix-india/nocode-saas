package com.fincity.saas.message.controller;

import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/exotel/connect")
public class ExotelConnectAppletController {

    private static final Logger logger = LoggerFactory.getLogger(ExotelConnectAppletController.class);

    private final ExotelCallService exotelCallService;

    @Autowired
    public ExotelConnectAppletController(ExotelCallService exotelCallService) {
        this.exotelCallService = exotelCallService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ExotelConnectAppletResponse> handleConnectAppletRequest(ServerWebExchange exchange) {
        return Mono.just(exchange.getRequest().getQueryParams())
                .map(ExotelConnectAppletRequest::fromQueryParams)
                .doOnNext(request -> logger.debug("Received Connect applet request: {}", request))
                .flatMap(exotelCallService::processConnectAppletRequest)
                .doOnNext(response -> logger.debug("Sending Connect applet response: {}", response));
    }
}
