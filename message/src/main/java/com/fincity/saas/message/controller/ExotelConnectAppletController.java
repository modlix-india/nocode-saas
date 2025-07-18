package com.fincity.saas.message.controller;

import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/exotel/connect")
public class ExotelConnectAppletController {

    private static final Logger logger = LoggerFactory.getLogger(ExotelConnectAppletController.class);

    private final ExotelCallService exotelCallService;

    public ExotelConnectAppletController(ExotelCallService exotelCallService) {
        this.exotelCallService = exotelCallService;
    }

    @PostMapping("/call")
    public Mono<ExotelConnectAppletResponse> connectCall(@RequestBody IncomingCallRequest request) {
        return exotelCallService.connectCall(request).onErrorResume(e -> {
            logger.error("Error in connectCall: {}", e.getMessage(), e);
            return Mono.empty();
        });
    }
}
