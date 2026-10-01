package com.fincity.saas.message.controller.call;

import com.fincity.saas.message.service.call.CallService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Call recordings for every provider, keyed on our own call code. Authenticated like any route, via header or
 * cookie, so an {@code <audio>} element can play it while the provider's credentials stay here.
 */
@RestController
@RequestMapping("/api/message/call/recording")
public class RecordingController {

    private final CallService callService;

    public RecordingController(CallService callService) {
        this.callService = callService;
    }

    @GetMapping("/{code}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> recording(
            @PathVariable String code, @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return this.callService.recording(code, range);
    }
}
