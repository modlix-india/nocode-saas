package com.fincity.saas.entity.processor.controller.billing;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.entity.processor.service.billing.EntityProcessorBillingMeteringService;

import reactor.core.publisher.Mono;

/**
 * Cluster-only token-metering endpoints triggered by the worker (nginx blocks
 * public {@code /internal/**}).
 */
@RestController
@RequestMapping("api/entity/processor/internal/billing")
public class EntityProcessorInternalBillingController {

    private final EntityProcessorBillingMeteringService meteringService;

    public EntityProcessorInternalBillingController(EntityProcessorBillingMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    @PostMapping("/meter")
    public Mono<ResponseEntity<Boolean>> meter() {
        return this.meteringService.meterCurrentWindow().map(ResponseEntity::ok);
    }

    @PostMapping("/reconcile")
    public Mono<ResponseEntity<Boolean>> reconcile(@RequestParam(required = false) String date) {
        LocalDate day = date == null ? null : LocalDate.parse(date);
        return this.meteringService.reconcile(day).map(ResponseEntity::ok);
    }
}
