package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.AdService;
import com.fincity.saas.entity.processor.service.AdsetService;
import com.fincity.saas.entity.processor.service.CampaignService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/campaigns")
public class CampaignController
        extends BaseUpdatableController<EntityProcessorCampaignsRecord, Campaign, CampaignDAO, CampaignService> {

    private final AdsetService adsetService;
    private final AdService adService;

    public CampaignController(AdsetService adsetService, AdService adService) {
        this.adsetService = adsetService;
        this.adService = adService;
    }

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Campaign>> createRequest(@RequestBody CampaignRequest campaignRequest) {
        return this.service.createRequest(campaignRequest).map(ResponseEntity::ok);
    }

    @GetMapping("/list/adsets")
    public Mono<ResponseEntity<List<IdAndValue<ULong, String>>>> getAdsets(
            @RequestParam(required = false) List<ULong> campaignIds) {
        return this.adsetService.readByCampaignIds(campaignIds).map(ResponseEntity::ok);
    }

    @GetMapping("/list/ads")
    public Mono<ResponseEntity<List<IdAndValue<ULong, String>>>> getAds(
            @RequestParam(required = false) List<ULong> campaignIds,
            @RequestParam(required = false) List<ULong> adsetIds) {
        return this.adService.listIdAndName(campaignIds, adsetIds).map(ResponseEntity::ok);
    }
}
