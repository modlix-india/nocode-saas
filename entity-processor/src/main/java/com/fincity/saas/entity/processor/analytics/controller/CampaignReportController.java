package com.fincity.saas.entity.processor.analytics.controller;

import com.fincity.saas.entity.processor.analytics.model.CampaignReport;
import com.fincity.saas.entity.processor.analytics.model.CampaignReportFilter;
import com.fincity.saas.entity.processor.analytics.service.CampaignReportService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @PostMapping("/report")
    @PreAuthorize("hasAuthority('Authorities.Campaign_READ')")
    public Mono<ResponseEntity<Page<CampaignReport>>> getConsolidatedReport(
            Pageable pageable, @RequestBody(required = false) CampaignReportFilter filter) {

        CampaignReportFilter effectiveFilter = (filter == null) ? new CampaignReportFilter() : filter;

        return campaignReportService
                .getConsolidatedReport(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/report/summary")
    @PreAuthorize("hasAuthority('Authorities.Campaign_READ')")
    public Mono<ResponseEntity<List<CampaignReport>>> getConsolidatedReportSummary(
            @RequestBody(required = false) CampaignReportFilter filter) {

        CampaignReportFilter effectiveFilter = (filter == null) ? new CampaignReportFilter() : filter;

        return campaignReportService
                .getConsolidatedReportSummary(effectiveFilter)
                .map(ResponseEntity::ok);
    }
}
