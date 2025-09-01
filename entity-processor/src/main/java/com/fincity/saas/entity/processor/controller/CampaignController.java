package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.CampaignService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/campaigns")
public class CampaignController
        extends BaseUpdatableController<EntityProcessorCampaignsRecord, Campaign, CampaignDAO, CampaignService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Campaign>> createFromRequest(@RequestBody CampaignRequest campaignRequest) {
        return this.service.create(campaignRequest).map(ResponseEntity::ok);
    }
}
