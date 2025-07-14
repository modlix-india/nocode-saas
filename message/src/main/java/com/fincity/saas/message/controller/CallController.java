package com.fincity.saas.message.controller;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.message.dto.CallRequest;
import com.fincity.saas.message.service.connection.call.CallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/call")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

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
}
