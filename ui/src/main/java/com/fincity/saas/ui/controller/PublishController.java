package com.fincity.saas.ui.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.service.PublishService;

import reactor.core.publisher.Mono;

/**
 * App-level publish. Per-object publish lives on each object's own controller.
 *
 * Neither route takes a draft flag: publishing is an action on the live world,
 * not something performed from the draft surface.
 */
@RestController
@RequestMapping("api/ui/publish")
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    @GetMapping("/app/{appCode}/pending")
    public Mono<ResponseEntity<Map<String, List<Map<String, Object>>>>> pending(@PathVariable String appCode,
            @RequestParam(required = false) String clientCode) {

        return this.publishService.pending(appCode, clientCode).map(ResponseEntity::ok);
    }

    @PostMapping("/app/{appCode}")
    public Mono<ResponseEntity<Map<String, Object>>> publishAll(@PathVariable String appCode,
            @RequestParam(required = false) String clientCode) {

        return this.publishService.publishAll(appCode, clientCode).map(ResponseEntity::ok);
    }
}
