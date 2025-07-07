package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.service.DeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core")
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
        return this.deletionService
                .deleteEverything(forwardedHost, forwardedPort, clientCode, headerAppCode, deleteAppCode)
                .map(ResponseEntity::ok);
    }
}
