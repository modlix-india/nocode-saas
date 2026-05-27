package com.fincity.saas.entity.processor.analytics.controller;

import com.fincity.saas.entity.processor.analytics.model.CampaignTreeRequest;
import com.fincity.saas.entity.processor.analytics.model.CampaignTreeResponse;
import com.fincity.saas.entity.processor.analytics.service.CampaignReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/analytics/campaigns")
public class CampaignReportController {

    private final CampaignReportService campaignReportService;

    public CampaignReportController(CampaignReportService campaignReportService) {
        this.campaignReportService = campaignReportService;
    }

    @PostMapping("/tree")
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<ResponseEntity<CampaignTreeResponse>> getCampaignTree(@RequestBody CampaignTreeRequest request) {
        return campaignReportService.getCampaignTree(request).map(ResponseEntity::ok);
    }
}
