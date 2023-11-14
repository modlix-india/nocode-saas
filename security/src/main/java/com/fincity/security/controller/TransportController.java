package com.fincity.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.TransportPOJO;
import com.fincity.security.service.TransportService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/transports")
public class TransportController {

    private final TransportService transportService;

    public TransportController(TransportService transportService) {
        this.transportService = transportService;
    }

    @GetMapping("/makeTransport")
    public Mono<ResponseEntity<TransportPOJO>> makeTransport(@RequestParam String applicationCode) {
        return transportService.makeTransport(applicationCode).map(ResponseEntity::ok);
    }
}
