package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.CampaignMetricDAO;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import java.time.LocalDate;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CampaignMetricService {

    private final CampaignMetricDAO campaignMetricDAO;

    public CampaignMetricService(CampaignMetricDAO campaignMetricDAO) {
        this.campaignMetricDAO = campaignMetricDAO;
    }

    public Mono<Void> bulkUpsert(List<CampaignMetric> metrics) {
        return campaignMetricDAO.bulkUpsert(metrics);
    }

    public Flux<CampaignMetric> findByCampaignAndDateRange(
            String appCode, String clientCode, ULong campaignId, LocalDate from, LocalDate to) {
        return campaignMetricDAO.findByCampaignAndDateRange(appCode, clientCode, campaignId, from, to);
    }

    public Flux<CampaignMetric> findByFilters(
            String appCode, String clientCode, List<ULong> campaignIds,
            List<String> platforms, LocalDate from, LocalDate to) {
        return campaignMetricDAO.findByFilters(appCode, clientCode, campaignIds, platforms, from, to);
    }
}
