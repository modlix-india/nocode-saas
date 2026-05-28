package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.service.MetricsSyncService;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/analytics/campaigns")
public class CampaignMetricsSyncController {

    private final MetricsSyncService metricsSyncService;

    public CampaignMetricsSyncController(MetricsSyncService metricsSyncService) {
        this.metricsSyncService = metricsSyncService;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('Authorities.Campaign_UPDATE')")
    public Mono<ResponseEntity<Map<String, String>>> syncCampaigns(
            @RequestBody Map<String, List<Long>> body) {

        List<ULong> campaignIds = body.getOrDefault("campaignIds", List.of())
                .stream()
                .map(ULong::valueOf)
                .toList();

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    String appCode = ca.getUrlAppCode();
                    String clientCode = ca.getClientCode();
                    return metricsSyncService.syncCampaigns(campaignIds, appCode, clientCode);
                })
                .thenReturn(ResponseEntity.ok(Map.of("status", "sync_initiated")));
    }
}
