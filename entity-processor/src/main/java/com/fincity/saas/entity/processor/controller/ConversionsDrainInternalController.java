package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.conversions.ConversionsDrainService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Worker-callable endpoint to drain a batch of pending conversion events from
 * the outbox. Path is allow-listed in {@code ProcessorConfiguration} so the
 * worker can hit it without a user JWT (same pattern as {@code partners/internal/denorm}).
 */
@RestController
@RequestMapping("api/entity/processor/conversions/internal")
public class ConversionsDrainInternalController {

    private final ConversionsDrainService drainService;

    public ConversionsDrainInternalController(ConversionsDrainService drainService) {
        this.drainService = drainService;
    }

    @PostMapping("/drain")
    public Mono<ResponseEntity<Map<String, Object>>> drain(
            @RequestParam(defaultValue = "50") int batchSize) {
        return this.drainService.drainBatch(batchSize).map(ResponseEntity::ok);
    }
}
