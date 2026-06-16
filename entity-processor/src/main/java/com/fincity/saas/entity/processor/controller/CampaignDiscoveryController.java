package com.fincity.saas.entity.processor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import com.fincity.saas.entity.processor.model.request.DiscoverCampaignsRequest;
import com.fincity.saas.entity.processor.model.request.EnableCampaignRequest;
import com.fincity.saas.entity.processor.service.CampaignDiscoveryService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Lists every Meta ad account the OAuth token can see. Backs the operator-facing
     * ad-account picker in campaignConfig, used to rebind a campaign to a sibling
     * account that has a pixel assigned.
     */
    @GetMapping("/meta/accounts")
    public Mono<ResponseEntity<JsonNode>> listMetaAccounts() {
        return this.discoveryService.listMetaAccounts().map(ResponseEntity::ok);
    }

    /**
     * Lists every Google Ads customer (sub-account) the client's OAuth token can
     * reach, with friendly name + parent MCC. Drives the customer-picker dropdown
     * in the conversion-action mapping UI -- conversion actions live inside one
     * customer, so the operator picks which customer a mapping targets.
     */
    @GetMapping("/google/customers")
    public Mono<ResponseEntity<java.util.List<
            com.fincity.saas.entity.processor.platform.GooglePlatformService.DiscoveredGoogleCustomer>>>
            listGoogleCustomers() {
        return this.discoveryService.listGoogleCustomers().map(ResponseEntity::ok);
    }

    /**
     * Lists pixels/datasets available on a Meta ad account. Operator UI uses this
     * to populate the pixel picker in campaignConfig. Mirrors the Google
     * conversion-action listing pattern.
     */
    @GetMapping("/meta/pixels")
    public Mono<ResponseEntity<JsonNode>> listMetaPixels(@RequestParam String accountId) {
        return this.discoveryService.listMetaPixels(accountId).map(ResponseEntity::ok);
    }

    /**
     * Creates a new pixel/dataset on a Meta ad account. Body: {@code {accountId, name}}.
     * Returns the new pixel as {@code {id, name}} JSON.
     */
    @PostMapping("/meta/pixels")
    public Mono<ResponseEntity<JsonNode>> createMetaPixel(@RequestBody CreatePixelRequest request) {
        return this.discoveryService.createMetaPixel(request.accountId(), request.name())
                .map(ResponseEntity::ok);
    }

    public record CreatePixelRequest(String accountId, String name) {}
}
