package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics.ENTITY_PROCESSOR_CAMPAIGN_METRICS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.analytics.model.CampaignReport;
import com.fincity.saas.entity.processor.analytics.model.CampaignReportFilter;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CampaignReportDAO {

    private static final EntityProcessorCampaignMetrics METRICS = ENTITY_PROCESSOR_CAMPAIGN_METRICS;
    private static final EntityProcessorCampaigns CAMPAIGNS = ENTITY_PROCESSOR_CAMPAIGNS;
    private static final EntityProcessorTickets TICKETS = ENTITY_PROCESSOR_TICKETS;
    private static final EntityProcessorStages STAGES = ENTITY_PROCESSOR_STAGES;
    private static final EntityProcessorProducts PRODUCTS = ENTITY_PROCESSOR_PRODUCTS;

    private static final Field<String> METRICS_PLATFORM_STR = DSL.field(
            DSL.name(METRICS.getName(), "PLATFORM"), String.class);

    private final DSLContext dslContext;

    public CampaignReportDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<List<CampaignReport>> getAdMetrics(ProcessorAccess access, CampaignReportFilter filter) {

        Field<BigDecimal> sumImpressions = DSL.sum(METRICS.IMPRESSIONS).as("total_impressions");
        Field<BigDecimal> sumClicks = DSL.sum(METRICS.CLICKS).as("total_clicks");
        Field<BigDecimal> sumSpend = DSL.sum(METRICS.SPEND).as("total_spend");
        Field<BigDecimal> sumWL = DSL.sum(METRICS.PLATFORM_WL).as("total_wl");
        Field<BigDecimal> sumFL = DSL.sum(METRICS.PLATFORM_FL).as("total_fl");

        Condition condition = METRICS.APP_CODE.eq(access.getAppCode())
                .and(METRICS.CLIENT_CODE.eq(access.getClientCode()));

        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            condition = condition.and(METRICS.METRIC_DATE.between(
                    filter.getStartDate().toLocalDate(), filter.getEndDate().toLocalDate()));
        }

        if (filter.getCampaignIds() != null && !filter.getCampaignIds().isEmpty()) {
            condition = condition.and(METRICS.CAMPAIGN_ID.in(filter.getCampaignIds()));
        }

        if (filter.getPlatforms() != null && !filter.getPlatforms().isEmpty()) {
            condition = condition.and(METRICS_PLATFORM_STR.in(filter.getPlatforms()));
        }

        return Flux.from(dslContext
                        .select(
                                METRICS.CAMPAIGN_ID,
                                METRICS.ADSET_ID,
                                METRICS.AD_ID,
                                CAMPAIGNS.CAMPAIGN_NAME,
                                CAMPAIGNS.CAMPAIGN_PLATFORM,
                                PRODUCTS.NAME.as("product_name"),
                                sumImpressions, sumClicks, sumSpend, sumWL, sumFL)
                        .from(METRICS)
                        .join(CAMPAIGNS).on(METRICS.CAMPAIGN_ID.eq(CAMPAIGNS.ID))
                        .leftJoin(PRODUCTS).on(CAMPAIGNS.PRODUCT_ID.eq(PRODUCTS.ID))
                        .where(condition)
                        .groupBy(METRICS.CAMPAIGN_ID, METRICS.ADSET_ID, METRICS.AD_ID))
                .map(r -> {
                    CampaignReport report = new CampaignReport();
                    report.setCampaignName(r.get(CAMPAIGNS.CAMPAIGN_NAME));
                    CampaignPlatform cp = r.get(CAMPAIGNS.CAMPAIGN_PLATFORM);
                    report.setCampaignPlatform(cp != null ? cp.getLiteral() : null);
                    report.setProductName(r.get("product_name", String.class));
                    report.setImpressions(longValue(r.get("total_impressions")));
                    report.setClicks(longValue(r.get("total_clicks")));
                    report.setSpend(r.get("total_spend", BigDecimal.class));
                    report.setPlatformWL(longValue(r.get("total_wl")));
                    report.setPlatformFL(longValue(r.get("total_fl")));
                    return report;
                })
                .collectList();
    }

    public Mono<Map<ULong, Map<String, Long>>> getTicketStageCounts(
            ProcessorAccess access, CampaignReportFilter filter) {

        Field<Integer> cnt = DSL.count().as("cnt");

        Condition condition = TICKETS.APP_CODE.eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.CAMPAIGN_ID.isNotNull());

        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(filter.getStartDate(), filter.getEndDate()));
        }

        if (filter.getCampaignIds() != null && !filter.getCampaignIds().isEmpty()) {
            condition = condition.and(TICKETS.CAMPAIGN_ID.in(filter.getCampaignIds()));
        }

        return Flux.from(dslContext
                        .select(TICKETS.CAMPAIGN_ID, STAGES.NAME.as("stage_name"), cnt)
                        .from(TICKETS)
                        .leftJoin(STAGES).on(TICKETS.STAGE.eq(STAGES.ID))
                        .where(condition)
                        .groupBy(TICKETS.CAMPAIGN_ID, TICKETS.STAGE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<String, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        ULong campaignId = r.get(TICKETS.CAMPAIGN_ID);
                        String stageName = r.get("stage_name", String.class);
                        long count = r.get("cnt", Long.class);
                        result.computeIfAbsent(campaignId, k -> new HashMap<>())
                                .put(stageName != null ? stageName : "UNKNOWN", count);
                    }
                    return result;
                });
    }

    public Mono<Map<ULong, Map<String, Long>>> getTicketSourceCounts(
            ProcessorAccess access, CampaignReportFilter filter) {

        Field<Integer> cnt = DSL.count().as("cnt");

        Condition condition = TICKETS.APP_CODE.eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.CAMPAIGN_ID.isNotNull());

        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(filter.getStartDate(), filter.getEndDate()));
        }

        if (filter.getCampaignIds() != null && !filter.getCampaignIds().isEmpty()) {
            condition = condition.and(TICKETS.CAMPAIGN_ID.in(filter.getCampaignIds()));
        }

        return Flux.from(dslContext
                        .select(TICKETS.CAMPAIGN_ID, TICKETS.SOURCE, cnt)
                        .from(TICKETS)
                        .where(condition)
                        .groupBy(TICKETS.CAMPAIGN_ID, TICKETS.SOURCE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<String, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        ULong campaignId = r.get(TICKETS.CAMPAIGN_ID);
                        String source = r.get(TICKETS.SOURCE);
                        long count = r.get("cnt", Long.class);
                        result.computeIfAbsent(campaignId, k -> new HashMap<>())
                                .put(source != null ? source : "UNKNOWN", count);
                    }
                    return result;
                });
    }

    private static long longValue(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }
}
