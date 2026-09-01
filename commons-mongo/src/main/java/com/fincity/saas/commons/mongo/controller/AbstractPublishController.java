package com.fincity.saas.commons.mongo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.commons.mongo.service.AbstractPublishService;

import reactor.core.publisher.Mono;

/**
 * App-level publish routes. Per-object publish lives on each object's own
 * controller.
 *
 * Neither route takes a draft flag: publishing is an action on the live world,
 * not something performed from the draft surface.
 *
 * Subclasses add the @RestController and @RequestMapping and nothing else, so
 * `ui` and `core` cannot drift apart on the shape of these two calls. All
 * authorization is in the service, which is where it has to be: `ui` routes are
 * permitAll at the HTTP layer.
 */
public abstract class AbstractPublishController<S extends AbstractPublishService> {

    protected final S publishService;

    protected AbstractPublishController(S publishService) {
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
