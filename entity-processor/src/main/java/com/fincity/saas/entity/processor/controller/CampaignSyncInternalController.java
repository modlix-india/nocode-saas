package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.service.CampaignDiscoveryService;
import com.fincity.saas.entity.processor.service.MetricsSyncService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Worker-callable internal endpoints. Path is allow-listed in {@code
 * ProcessorConfiguration} so the worker can hit it without a user JWT. Mirrors
 * the {@code /partners/internal/denorm} pattern.
 */
@RestController
@RequestMapping("api/entity/processor/campaigns/internal")
public class CampaignSyncInternalController {

    private final MetricsSyncService metricsSyncService;
    private final CampaignDiscoveryService discoveryService;

    public CampaignSyncInternalController(
            MetricsSyncService metricsSyncService, CampaignDiscoveryService discoveryService) {
        this.metricsSyncService = metricsSyncService;
        this.discoveryService = discoveryService;
    }

    @PostMapping("/sync-metrics")
    public Mono<ResponseEntity<Map<String, Object>>> syncMetrics() {
        return this.metricsSyncService.syncAllActive().map(ResponseEntity::ok);
    }

    @PostMapping("/sync-discovery")
    public Mono<ResponseEntity<Map<String, Object>>> syncDiscovery() {
        return this.discoveryService.refreshAllActive().map(ResponseEntity::ok);
    }
}
