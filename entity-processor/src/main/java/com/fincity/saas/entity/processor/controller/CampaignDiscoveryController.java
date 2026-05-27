package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import com.fincity.saas.entity.processor.model.request.DiscoverCampaignsRequest;
import com.fincity.saas.entity.processor.model.request.EnableCampaignRequest;
import com.fincity.saas.entity.processor.service.CampaignDiscoveryService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/campaigns/discovery")
public class CampaignDiscoveryController {

    private final CampaignDiscoveryService discoveryService;

    public CampaignDiscoveryController(CampaignDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /** List campaigns available on the connected platform account (read-only against the platform API). */
    @PostMapping("/list")
    public Mono<ResponseEntity<List<DiscoveredCampaign>>> listAvailable(
            @RequestBody DiscoverCampaignsRequest request) {
        return this.discoveryService.listAvailable(request).map(ResponseEntity::ok);
    }

    /** Enable a campaign — upsert local Campaign row and mirror its adsets + ads. */
    @PostMapping("/enable")
    public Mono<ResponseEntity<Campaign>> enable(@RequestBody EnableCampaignRequest request) {
        return this.discoveryService.enable(request).map(ResponseEntity::ok);
    }

    /** Soft-disable a local campaign (preserves adsets, ads, metric history). */
    @DeleteMapping("/{campaignId}")
    public Mono<ResponseEntity<Campaign>> disable(@PathVariable ULong campaignId) {
        return this.discoveryService.disable(campaignId).map(ResponseEntity::ok);
    }
}
