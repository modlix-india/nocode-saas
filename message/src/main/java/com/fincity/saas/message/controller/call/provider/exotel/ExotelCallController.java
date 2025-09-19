package com.fincity.saas.message.controller.call.provider.exotel;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/call/exotel")
public class ExotelCallController {

    private static final Logger logger = LoggerFactory.getLogger(ExotelCallController.class);

    private final ExotelCallService exotelCallService;

    public ExotelCallController(ExotelCallService exotelCallService) {
        this.exotelCallService = exotelCallService;
    }

    @PostMapping("/connect")
    public Mono<ExotelConnectAppletResponse> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest request) {
        return exotelCallService.connectCall(appCode, clientCode, request).onErrorResume(e -> {
            logger.error("Error in connectCall: {}", e.getMessage(), e);
            return Mono.empty();
        });
    }

    @PostMapping("/make")
    public Mono<ResponseEntity<Call>> makeCall(@RequestBody CallRequest request) {
        return exotelCallService.makeCall(request).map(ResponseEntity::ok).onErrorResume(e -> {
            logger.error("Error in makeCall: {}", e.getMessage(), e);
            return Mono.empty();
        });
    }
}
