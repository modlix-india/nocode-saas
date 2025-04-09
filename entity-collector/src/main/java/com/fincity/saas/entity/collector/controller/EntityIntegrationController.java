package com.fincity.saas.entity.collector.controller;

import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.service.EntityIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entity-integration")
@RequiredArgsConstructor
public class EntityIntegrationController {

    private final EntityIntegrationService service;

    @PostMapping
    public ResponseEntity<EntityIntegration> create(@RequestBody EntityIntegration dto) {
        return ResponseEntity.ok(service.createIntegration(dto));
    }

    @GetMapping
    public ResponseEntity<List<EntityIntegration>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityIntegration> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityIntegration>> searchByClientApp(
            @RequestParam String clientCode,
            @RequestParam String appCode
    ) {
        return ResponseEntity.ok(service.findByClientAndApp(clientCode, appCode));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityIntegration> update(@PathVariable Long id, @RequestBody EntityIntegration dto) {
        dto.setId(id);
        return ResponseEntity.ok(service.updateIntegration(dto));
    }

    @GetMapping("/client/{clientCode}")
    public ResponseEntity<List<EntityIntegration>> getByClientCode(@PathVariable String clientCode) {
        return ResponseEntity.ok(service.getByClientCode(clientCode));
    }
}
