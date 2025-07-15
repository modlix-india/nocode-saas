package com.fincity.saas.message.controller;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.message.model.request.CallRequest;
import com.fincity.saas.message.model.request.call.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.response.call.exotel.ExotelCallStatusCallbackResponse;
import com.fincity.saas.message.service.call.CallService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller for making calls.
 */
@RestController
@RequestMapping("/api/call")
public class CallController {

    private static final Logger logger = LoggerFactory.getLogger(CallController.class);

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    /**
     * Makes an outgoing call connecting two phone numbers.
     *
     * @param request The call request
     * @return A Mono that emits a ResponseEntity with a success message
     */
    @PostMapping
    public Mono<ResponseEntity<String>> makeCall(@RequestBody CallRequest request) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(auth -> callService.makeCall(
                        request.getAppCode(),
                        request.getClientCode(),
                        request.getFromNumber(),
                        request.getToNumber(),
                        request.getCallerId(),
                        request.getConnectionName()))
                .map(result -> ResponseEntity.ok("Call initiated successfully"));
    }

    /**
     * Makes an outgoing call connecting two phone numbers and saves the call details.
     *
     * @param request The call request
     * @return A Mono that emits a ResponseEntity with the saved call details
     */
    @PostMapping("/save")
    public Mono<ResponseEntity<Map<String, Object>>> makeCallAndSave(@RequestBody CallRequest request) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(auth -> callService.makeCallAndSave(
                        request.getAppCode(),
                        request.getClientCode(),
                        request.getFromNumber(),
                        request.getToNumber(),
                        request.getCallerId(),
                        request.getConnectionName()))
                .map(ResponseEntity::ok);
    }

    /**
     * Handles status callbacks from Exotel.
     * This endpoint is called by Exotel when a call's status changes.
     *
     * @param callback The status callback data from Exotel
     * @return A Mono that emits a ResponseEntity with a success message
     */
    @PostMapping("/callback/exotel")
    public Mono<ResponseEntity<ExotelCallStatusCallbackResponse>> handleExotelCallback(
            @RequestBody ExotelCallStatusCallback callback) {
        logger.info("Received callback from Exotel: {}", callback);

        return callService
                .processExotelCallback(callback)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok(ExotelCallStatusCallbackResponse.success());
                    } else {
                        return ResponseEntity.ok(ExotelCallStatusCallbackResponse.error("Failed to process callback"));
                    }
                })
                .onErrorResume(e -> {
                    logger.error("Error processing Exotel callback", e);
                    return Mono.just(ResponseEntity.ok(
                            ExotelCallStatusCallbackResponse.error("Error processing callback: " + e.getMessage())));
                });
    }
}
