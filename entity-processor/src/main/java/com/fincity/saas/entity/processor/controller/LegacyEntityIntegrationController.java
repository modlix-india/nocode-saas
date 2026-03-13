package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Legacy backward-compatible controller for entity integrations.
 * Keeps the original /api/entity/collector/integration path
 * so existing gateway routing and clients continue to work.
 */
@RestController
@RequestMapping("/api/entity/collector/integration")
public class LegacyEntityIntegrationController {

    private final EntityIntegrationService entityIntegrationService;

    public LegacyEntityIntegrationController(EntityIntegrationService entityIntegrationService) {
        this.entityIntegrationService = entityIntegrationService;
    }

    @GetMapping
    public Mono<ResponseEntity<Page<EntityIntegration>>> readPage(Pageable pageable) {
        return entityIntegrationService.readPageFilter(pageable, null)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<EntityIntegration>> read(@PathVariable ULong id) {
        return entityIntegrationService.read(id)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<EntityIntegration>> create(@RequestBody EntityIntegration entity) {
        return entityIntegrationService.create(entity)
                .map(ResponseEntity::ok);
    }

    @PutMapping
    public Mono<ResponseEntity<EntityIntegration>> update(@RequestBody EntityIntegration entity) {
        return entityIntegrationService.update(entity)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Integer>> delete(@PathVariable ULong id) {
        return entityIntegrationService.delete(id)
                .map(ResponseEntity::ok);
    }
}
