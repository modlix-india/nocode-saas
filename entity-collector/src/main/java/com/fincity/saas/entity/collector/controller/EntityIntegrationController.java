package com.fincity.saas.entity.collector.controller;

import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.service.EntityIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entity-collector")
@RequiredArgsConstructor


public class EntityIntegrationController {
    private final EntityIntegrationService service;

    @PostMapping
    public ResponseEntity<String> createIntegration(@RequestBody EntityIntegration dto) {
        service.saveIntegration(dto);
        return ResponseEntity.ok("Saved successfully!");
    }
}
