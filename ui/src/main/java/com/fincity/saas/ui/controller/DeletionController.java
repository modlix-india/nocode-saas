package com.fincity.saas.ui.controller;

import com.fincity.saas.ui.service.DeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui")
public class DeletionController {

    public final DeletionService deletionService;

    public DeletionController(DeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping
    public Mono<ResponseEntity<Boolean>> deleteEverything(
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestParam("deleteAppCode") String deleteAppCode) {
        return this.deletionService.deleteEverything(
            forwardedHost,
            forwardedPort,
            clientCode,
            headerAppCode,
            deleteAppCode).map(ResponseEntity::ok);
    }
}
