package com.fincity.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.service.LimitService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/limits")
public class LimitController {

    @Autowired
    private LimitService limitService;

    @GetMapping("/internal/getLimit")
    public Mono<ResponseEntity<Long>> getLimit(
            @RequestParam(required = true) String appCode,
            @RequestParam(required = true) String clientCode,
            @RequestParam(required = true) String urlClientCode,
            @RequestParam(required = true) String objectName) {

        return this.limitService.fetchLimits(appCode, clientCode, urlClientCode, objectName)
                .map(ResponseEntity::ok);
    }

}
