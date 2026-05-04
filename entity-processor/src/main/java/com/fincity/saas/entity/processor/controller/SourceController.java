package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.service.SourceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/sources")
public class SourceController {

    private final SourceService service;

    public SourceController(SourceService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<List<Source>>> getAvailableSources(
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        return this.service.getAvailableSources(onlyActive).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<List<Source>>> saveAllSources(@RequestBody List<Source> sources) {
        return this.service.saveAllSources(sources).map(ResponseEntity::ok);
    }
}
