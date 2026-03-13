package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics.ENTITY_PROCESSOR_CAMPAIGN_METRICS;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CampaignMetricDAO {

    private static final EntityProcessorCampaignMetrics METRICS = ENTITY_PROCESSOR_CAMPAIGN_METRICS;

    private static final Field<String> PLATFORM_STR = DSL.field(
            DSL.name(METRICS.getName(), "PLATFORM"), String.class);

    private final DSLContext dslContext;

    public CampaignMetricDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<Void> bulkUpsert(List<CampaignMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Mono.empty();
        }

        var queries = new ArrayList<org.jooq.Query>(metrics.size());

        for (CampaignMetric m : metrics) {
            InsertSetMoreStep<?> insert = dslContext.insertInto(METRICS)
                    .set(METRICS.CODE, m.getCode() != null ? m.getCode() : UniqueUtil.shortUUID())
                    .set(METRICS.APP_CODE, m.getAppCode())
                    .set(METRICS.CLIENT_CODE, m.getClientCode())
                    .set(METRICS.CAMPAIGN_ID, m.getCampaignId())
                    .set(METRICS.ADSET_ID, m.getAdsetId())
                    .set(METRICS.AD_ID, m.getAdId())
                    .set(METRICS.METRIC_DATE, m.getMetricDate())
                    .set(METRICS.IMPRESSIONS, m.getImpressions())
                    .set(METRICS.CLICKS, m.getClicks())
                    .set(METRICS.SPEND, m.getSpend())
                    .set(METRICS.PLATFORM_WL, m.getPlatformWL())
                    .set(METRICS.PLATFORM_FL, m.getPlatformFL())
                    .set(METRICS.CURRENCY, m.getCurrency())
                    .set(PLATFORM_STR, m.getPlatform().getLiteral());

            queries.add(insert
                    .onDuplicateKeyUpdate()
                    .set(METRICS.IMPRESSIONS, m.getImpressions())
                    .set(METRICS.CLICKS, m.getClicks())
                    .set(METRICS.SPEND, m.getSpend())
                    .set(METRICS.PLATFORM_WL, m.getPlatformWL())
                    .set(METRICS.PLATFORM_FL, m.getPlatformFL())
                    .set(METRICS.CURRENCY, m.getCurrency()));
        }

        return Flux.from(dslContext.batch(queries)).then();
    }

    public Flux<CampaignMetric> findByCampaignAndDateRange(
            String appCode, String clientCode, ULong campaignId, LocalDate from, LocalDate to) {

        Condition condition = METRICS.APP_CODE.eq(appCode)
                .and(METRICS.CLIENT_CODE.eq(clientCode))
                .and(METRICS.CAMPAIGN_ID.eq(campaignId))
                .and(METRICS.METRIC_DATE.between(from, to));

        return Flux.from(dslContext.selectFrom(METRICS).where(condition))
                .map(this::mapToCampaignMetric);
    }

    public Flux<CampaignMetric> findByFilters(
            String appCode, String clientCode, List<ULong> campaignIds,
            List<String> platforms, LocalDate from, LocalDate to) {

        Condition condition = METRICS.APP_CODE.eq(appCode)
                .and(METRICS.CLIENT_CODE.eq(clientCode))
                .and(METRICS.METRIC_DATE.between(from, to));

        if (campaignIds != null && !campaignIds.isEmpty()) {
            condition = condition.and(METRICS.CAMPAIGN_ID.in(campaignIds));
        }

        if (platforms != null && !platforms.isEmpty()) {
            condition = condition.and(PLATFORM_STR.in(platforms));
        }

        return Flux.from(dslContext.selectFrom(METRICS).where(condition))
                .map(this::mapToCampaignMetric);
    }

    private CampaignMetric mapToCampaignMetric(org.jooq.Record r) {
        CampaignMetric m = new CampaignMetric();
        m.setId(r.get(METRICS.ID));
        m.setCode(r.get(METRICS.CODE));
        m.setAppCode(r.get(METRICS.APP_CODE));
        m.setClientCode(r.get(METRICS.CLIENT_CODE));
        m.setCampaignId(r.get(METRICS.CAMPAIGN_ID));
        m.setAdsetId(r.get(METRICS.ADSET_ID));
        m.setAdId(r.get(METRICS.AD_ID));
        m.setMetricDate(r.get(METRICS.METRIC_DATE));
        m.setImpressions(r.get(METRICS.IMPRESSIONS));
        m.setClicks(r.get(METRICS.CLICKS));
        m.setSpend(r.get(METRICS.SPEND));
        m.setPlatformWL(r.get(METRICS.PLATFORM_WL));
        m.setPlatformFL(r.get(METRICS.PLATFORM_FL));
        m.setCurrency(r.get(METRICS.CURRENCY));
        String platformStr = r.get(PLATFORM_STR);
        if (platformStr != null) {
            m.setPlatform(CampaignPlatform.lookupLiteral(platformStr));
        }
        return m;
    }
}
