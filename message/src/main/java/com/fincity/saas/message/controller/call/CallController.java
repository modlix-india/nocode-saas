package com.fincity.saas.message.controller.call;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.service.call.CallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/call")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping("/make")
    public Mono<ResponseEntity<Call>> makeCall(@RequestBody CallRequest callRequest) {
        return this.callService
                .makeCall(callRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
