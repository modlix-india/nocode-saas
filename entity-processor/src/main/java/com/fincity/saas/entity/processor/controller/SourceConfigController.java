package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.SourceConfig;
import com.fincity.saas.entity.processor.service.SourceConfigService;
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
@RequestMapping("api/entity/processor/source-configs")
public class SourceConfigController {

    private final SourceConfigService service;

    public SourceConfigController(SourceConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<List<SourceConfig>>> getAvailableSources(
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        return this.service.getAvailableSources(onlyActive).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<List<SourceConfig>>> saveAllSources(
            @RequestBody List<SourceConfig> sources) {
        return this.service.saveAllSources(sources).map(ResponseEntity::ok);
    }
}
