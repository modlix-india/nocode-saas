package com.fincity.saas.ui.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.service.AnalyticsService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/analytics/")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("query")
    public Mono<Map<String, Object>> query(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody Map<String, Object> body) {

        return this.analyticsService.query(appCode, clientCode, body);
    }

}
