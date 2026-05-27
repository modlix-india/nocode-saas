package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAds.ENTITY_PROCESSOR_ADS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAdsets.ENTITY_PROCESSOR_ADSETS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics.ENTITY_PROCESSOR_CAMPAIGN_METRICS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.analytics.model.CampaignReport;
import com.fincity.saas.entity.processor.analytics.model.CampaignReport.Level;
import com.fincity.saas.entity.processor.analytics.model.StageNode;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAds;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAdsets;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignMetrics;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static final EntityProcessorAdsets ADSETS = ENTITY_PROCESSOR_ADSETS;
    private static final EntityProcessorAds ADS = ENTITY_PROCESSOR_ADS;

    private final DSLContext dslContext;

    public CampaignReportDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /* ---------------- Stage tree ---------------- */

    /**
     * Fetch the stage hierarchy for a product's template. Returns a list of
     * top-level (parent) stages with their substages nested under {@code children},
     * sorted by {@code ORDER}. Only 2 levels — the data model supports up to
     * {@code PARENT_LEVEL_1} but no template uses it today.
     */
    public Mono<List<StageNode>> getStageTreeForProductTemplate(ULong productTemplateId) {

        return Flux.from(dslContext
                        .select(STAGES.ID, STAGES.NAME, STAGES.PARENT_LEVEL_0, STAGES.ORDER, STAGES.FUNNEL_STAGE)
                        .from(STAGES)
                        .where(STAGES.PRODUCT_TEMPLATE_ID.eq(productTemplateId).and(STAGES.IS_ACTIVE.isTrue()))
                        .orderBy(
                                DSL.coalesce(STAGES.PARENT_LEVEL_0, STAGES.ID).asc(),
                                STAGES.ORDER.asc()))
                .map(r -> {
                    StageNode n = new StageNode();
                    n.setId(r.get(STAGES.ID));
                    n.setName(r.get(STAGES.NAME));
                    n.setOrder(r.get(STAGES.ORDER) == null ? 0 : r.get(STAGES.ORDER));
                    FunnelStage fs = r.get(STAGES.FUNNEL_STAGE);
                    n.setFunnelStage(fs == null ? null : fs.getLiteral());

                    ULong parent = r.get(STAGES.PARENT_LEVEL_0);
                    // Stash parent id on a transient list via a wrapper record below; we
                    // re-thread it in collectList by joining a parallel structure.
                    return new IdParentNode(parent, n);
                })
                .collectList()
                .map(this::nest);
    }

    private record IdParentNode(ULong parent, StageNode node) {}

    private List<StageNode> nest(List<IdParentNode> rows) {

        Map<ULong, StageNode> byId = new LinkedHashMap<>();
        List<StageNode> roots = new ArrayList<>();

        // First pass: index all nodes.
        for (IdParentNode r : rows) byId.put(r.node().getId(), r.node());

        // Second pass: attach children to parents (or collect as roots; orphaned children
        // whose parent is inactive/deleted also bubble up so we never silently drop them).
        for (IdParentNode r : rows) {
            StageNode parent = r.parent() == null ? null : byId.get(r.parent());
            if (parent == null) {
                roots.add(r.node());
            } else {
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(r.node());
            }
        }
        return roots;
    }

    /* ---------------- Campaign-level metrics ---------------- */

    /**
     * One row per active campaign linked to {@code productId}. Metrics are
     * aggregated from {@code entity_processor_campaign_metrics} rows where
     * {@code ADSET_ID IS NULL AND AD_ID IS NULL} (campaign-level rollup) and
     * {@code METRIC_DATE} falls in the local-date range of [from, to].
     *
     * <p>{@code stageCells}, computed metrics, and {@code leadsByFunnelStage}
     * are left null/empty — service layer fills them after joining with the
     * stage-count query.
     */
    public Mono<List<CampaignReport>> getCampaignLevelRows(
            ProcessorAccess access,
            ULong productId,
            LocalDate fromDate,
            LocalDate toDate,
            List<String> platforms) {

        Field<BigDecimal> sumImpressions = DSL.sum(METRICS.IMPRESSIONS).as("imps");
        Field<BigDecimal> sumClicks = DSL.sum(METRICS.CLICKS).as("clk");
        Field<BigDecimal> sumSpend = DSL.sum(METRICS.SPEND).as("spd");
        Field<BigDecimal> sumWL = DSL.sum(METRICS.PLATFORM_WL).as("wl");
        Field<BigDecimal> sumFL = DSL.sum(METRICS.PLATFORM_FL).as("fl");

        Condition metricsRange = (fromDate != null && toDate != null)
                ? METRICS.METRIC_DATE.between(fromDate, toDate)
                : DSL.noCondition();

        Condition campaignScope = CAMPAIGNS.PRODUCT_ID
                .eq(productId)
                .and(CAMPAIGNS.IS_ACTIVE.isTrue())
                .and(CAMPAIGNS.APP_CODE.eq(access.getAppCode()))
                .and(CAMPAIGNS.CLIENT_CODE.eq(access.getClientCode()));

        if (platforms != null && !platforms.isEmpty()) {
            List<CampaignPlatform> enums = platforms.stream()
                    .map(this::parsePlatform)
                    .filter(p -> p != null)
                    .toList();
            if (!enums.isEmpty()) campaignScope = campaignScope.and(CAMPAIGNS.CAMPAIGN_PLATFORM.in(enums));
        }

        return Flux.from(dslContext
                        .select(
                                CAMPAIGNS.ID,
                                CAMPAIGNS.CAMPAIGN_ID,
                                CAMPAIGNS.CAMPAIGN_NAME,
                                CAMPAIGNS.CAMPAIGN_PLATFORM,
                                CAMPAIGNS.PRODUCT_ID,
                                sumImpressions, sumClicks, sumSpend, sumWL, sumFL)
                        .from(CAMPAIGNS)
                        .leftJoin(METRICS)
                        .on(METRICS.CAMPAIGN_ID
                                .eq(CAMPAIGNS.ID)
                                .and(METRICS.ADSET_ID.isNull())
                                .and(METRICS.AD_ID.isNull())
                                .and(metricsRange))
                        .where(campaignScope)
                        .groupBy(
                                CAMPAIGNS.ID,
                                CAMPAIGNS.CAMPAIGN_ID,
                                CAMPAIGNS.CAMPAIGN_NAME,
                                CAMPAIGNS.CAMPAIGN_PLATFORM,
                                CAMPAIGNS.PRODUCT_ID)
                        .orderBy(CAMPAIGNS.CAMPAIGN_NAME.asc()))
                .map(r -> {
                    CampaignReport row = new CampaignReport();
                    row.setLevel(Level.CAMPAIGN);
                    row.setId(r.get(CAMPAIGNS.ID));
                    row.setCampaignId(r.get(CAMPAIGNS.ID));
                    row.setExternalId(r.get(CAMPAIGNS.CAMPAIGN_ID));
                    row.setName(r.get(CAMPAIGNS.CAMPAIGN_NAME));
                    CampaignPlatform cp = r.get(CAMPAIGNS.CAMPAIGN_PLATFORM);
                    row.setPlatform(cp == null ? null : cp.getLiteral());
                    row.setProductId(r.get(CAMPAIGNS.PRODUCT_ID));
                    row.setImpressions(toLong(r.get("imps")));
                    row.setClicks(toLong(r.get("clk")));
                    row.setSpend(toBigDecimal(r.get("spd")));
                    row.setPlatformWL(toLong(r.get("wl")));
                    row.setPlatformFL(toLong(r.get("fl")));
                    return row;
                })
                .collectList();
    }

    /* ---------------- Stage counts ---------------- */

    /**
     * For each campaign linked to {@code productId}, returns a map keyed by
     * stage ID with ticket counts. Tickets are filtered by {@code CREATED_AT}
     * within the supplied datetime range.
     */
    public Mono<Map<ULong, Map<ULong, Long>>> getStageCountsByCampaign(
            ProcessorAccess access, ULong productId, LocalDateTime startDate, LocalDateTime endDate) {

        Field<Integer> cnt = DSL.count().as("cnt");

        Condition condition = TICKETS.APP_CODE
                .eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.PRODUCT_ID.eq(productId))
                .and(TICKETS.CAMPAIGN_ID.isNotNull())
                .and(TICKETS.STAGE.isNotNull())
                .and(TICKETS.IS_ACTIVE.isTrue());

        if (startDate != null && endDate != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(startDate, endDate));
        }

        return Flux.from(dslContext
                        .select(TICKETS.CAMPAIGN_ID, TICKETS.STAGE, cnt)
                        .from(TICKETS)
                        .where(condition)
                        .groupBy(TICKETS.CAMPAIGN_ID, TICKETS.STAGE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<ULong, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        ULong campaignId = r.get(TICKETS.CAMPAIGN_ID);
                        ULong stageId = r.get(TICKETS.STAGE);
                        long count = r.get("cnt", Long.class);
                        result.computeIfAbsent(campaignId, k -> new HashMap<>()).put(stageId, count);
                    }
                    return result;
                });
    }

    /**
     * Per-campaign rollup grouped by {@code STAGES.FUNNEL_STAGE} (LEAD/MQL/SQL/
     * WON/LOST/CUSTOM). Tickets whose stage has no funnel tag bucket under
     * {@code "UNTAGGED"}. Date filter and tenant scope match
     * {@link #getStageCountsByCampaign}.
     */
    public Mono<Map<ULong, Map<String, Long>>> getFunnelCountsByCampaign(
            ProcessorAccess access, ULong productId, LocalDateTime startDate, LocalDateTime endDate) {

        Field<Integer> cnt = DSL.count().as("cnt");

        Condition condition = TICKETS.APP_CODE
                .eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.PRODUCT_ID.eq(productId))
                .and(TICKETS.CAMPAIGN_ID.isNotNull())
                .and(TICKETS.STAGE.isNotNull())
                .and(TICKETS.IS_ACTIVE.isTrue());

        if (startDate != null && endDate != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(startDate, endDate));
        }

        return Flux.from(dslContext
                        .select(TICKETS.CAMPAIGN_ID, STAGES.FUNNEL_STAGE, cnt)
                        .from(TICKETS)
                        .leftJoin(STAGES)
                        .on(TICKETS.STAGE.eq(STAGES.ID))
                        .where(condition)
                        .groupBy(TICKETS.CAMPAIGN_ID, STAGES.FUNNEL_STAGE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<String, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        ULong campaignId = r.get(TICKETS.CAMPAIGN_ID);
                        FunnelStage fs = r.get(STAGES.FUNNEL_STAGE);
                        String key = fs == null ? "UNTAGGED" : fs.getLiteral();
                        long count = r.get("cnt", Long.class);
                        result.computeIfAbsent(campaignId, k -> new HashMap<>())
                                .merge(key, count, (a, b) -> a + b);
                    }
                    return result;
                });
    }

    /* ---------------- Adset-level rows ---------------- */

    /**
     * One row per active adset under any of {@code campaignIds}. Metrics today
     * are stored campaign-level, so adset rows surface {@code 0} aggregates and
     * carry ticket counts only (via {@link #getStageCountsByAdset}). Phase-7 E1
     * will land adset-level metric breakdowns.
     */
    public Mono<List<CampaignReport>> getAdsetRowsForCampaigns(
            ProcessorAccess access, List<ULong> campaignIds, LocalDate fromDate, LocalDate toDate) {

        if (campaignIds == null || campaignIds.isEmpty()) return Mono.just(List.of());

        Field<BigDecimal> sumImpressions = DSL.sum(METRICS.IMPRESSIONS).as("imps");
        Field<BigDecimal> sumClicks = DSL.sum(METRICS.CLICKS).as("clk");
        Field<BigDecimal> sumSpend = DSL.sum(METRICS.SPEND).as("spd");

        Condition metricsRange = (fromDate != null && toDate != null)
                ? METRICS.METRIC_DATE.between(fromDate, toDate)
                : DSL.noCondition();

        Condition adsetScope = ADSETS.CAMPAIGN_ID
                .in(campaignIds)
                .and(ADSETS.IS_ACTIVE.isTrue())
                .and(ADSETS.APP_CODE.eq(access.getAppCode()))
                .and(ADSETS.CLIENT_CODE.eq(access.getClientCode()));

        return Flux.from(dslContext
                        .select(ADSETS.ID, ADSETS.CAMPAIGN_ID, ADSETS.ADSET_ID, ADSETS.ADSET_NAME,
                                sumImpressions, sumClicks, sumSpend)
                        .from(ADSETS)
                        .leftJoin(METRICS)
                        .on(METRICS.ADSET_ID
                                .eq(ADSETS.ID)
                                .and(METRICS.AD_ID.isNull())
                                .and(metricsRange))
                        .where(adsetScope)
                        .groupBy(ADSETS.ID, ADSETS.CAMPAIGN_ID, ADSETS.ADSET_ID, ADSETS.ADSET_NAME)
                        .orderBy(ADSETS.ADSET_NAME.asc()))
                .map(r -> {
                    CampaignReport row = new CampaignReport();
                    row.setLevel(Level.ADSET);
                    row.setId(r.get(ADSETS.ID));
                    row.setCampaignId(r.get(ADSETS.CAMPAIGN_ID));
                    row.setExternalId(r.get(ADSETS.ADSET_ID));
                    row.setName(r.get(ADSETS.ADSET_NAME));
                    row.setImpressions(toLong(r.get("imps")));
                    row.setClicks(toLong(r.get("clk")));
                    row.setSpend(toBigDecimal(r.get("spd")));
                    return row;
                })
                .collectList();
    }

    public Mono<Map<ULong, Map<ULong, Long>>> getStageCountsByAdset(
            ProcessorAccess access,
            ULong productId,
            List<ULong> campaignIds,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (campaignIds == null || campaignIds.isEmpty()) return Mono.just(Map.of());

        Field<Integer> cnt = DSL.count().as("cnt");
        Condition condition = TICKETS.APP_CODE
                .eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.PRODUCT_ID.eq(productId))
                .and(TICKETS.CAMPAIGN_ID.in(campaignIds))
                .and(TICKETS.ADSET_ID.isNotNull())
                .and(TICKETS.STAGE.isNotNull())
                .and(TICKETS.IS_ACTIVE.isTrue());

        if (startDate != null && endDate != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(startDate, endDate));
        }

        return Flux.from(dslContext
                        .select(TICKETS.ADSET_ID, TICKETS.STAGE, cnt)
                        .from(TICKETS)
                        .where(condition)
                        .groupBy(TICKETS.ADSET_ID, TICKETS.STAGE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<ULong, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        result.computeIfAbsent(r.get(TICKETS.ADSET_ID), k -> new HashMap<>())
                                .put(r.get(TICKETS.STAGE), r.get("cnt", Long.class));
                    }
                    return result;
                });
    }

    /* ---------------- Ad-level rows ---------------- */

    public Mono<List<CampaignReport>> getAdRowsForAdsets(
            ProcessorAccess access, List<ULong> adsetIds, LocalDate fromDate, LocalDate toDate) {

        if (adsetIds == null || adsetIds.isEmpty()) return Mono.just(List.of());

        Field<BigDecimal> sumImpressions = DSL.sum(METRICS.IMPRESSIONS).as("imps");
        Field<BigDecimal> sumClicks = DSL.sum(METRICS.CLICKS).as("clk");
        Field<BigDecimal> sumSpend = DSL.sum(METRICS.SPEND).as("spd");

        Condition metricsRange = (fromDate != null && toDate != null)
                ? METRICS.METRIC_DATE.between(fromDate, toDate)
                : DSL.noCondition();

        Condition adScope = ADS.ADSET_ID
                .in(adsetIds)
                .and(ADS.IS_ACTIVE.isTrue())
                .and(ADS.APP_CODE.eq(access.getAppCode()))
                .and(ADS.CLIENT_CODE.eq(access.getClientCode()));

        return Flux.from(dslContext
                        .select(ADS.ID, ADS.ADSET_ID, ADS.AD_ID, ADS.AD_NAME, ADS.THUMBNAIL_URL,
                                sumImpressions, sumClicks, sumSpend)
                        .from(ADS)
                        .leftJoin(METRICS)
                        .on(METRICS.AD_ID.eq(ADS.ID).and(metricsRange))
                        .where(adScope)
                        .groupBy(ADS.ID, ADS.ADSET_ID, ADS.AD_ID, ADS.AD_NAME, ADS.THUMBNAIL_URL)
                        .orderBy(ADS.AD_NAME.asc()))
                .map(r -> {
                    CampaignReport row = new CampaignReport();
                    row.setLevel(Level.AD);
                    row.setId(r.get(ADS.ID));
                    row.setAdsetId(r.get(ADS.ADSET_ID));
                    row.setExternalId(r.get(ADS.AD_ID));
                    row.setName(r.get(ADS.AD_NAME));
                    String thumb = r.get(ADS.THUMBNAIL_URL);
                    row.setThumbnailUrl(thumb);
                    row.setHasCreative(thumb != null && !thumb.isBlank());
                    row.setImpressions(toLong(r.get("imps")));
                    row.setClicks(toLong(r.get("clk")));
                    row.setSpend(toBigDecimal(r.get("spd")));
                    return row;
                })
                .collectList();
    }

    public Mono<Map<ULong, Map<ULong, Long>>> getStageCountsByAd(
            ProcessorAccess access,
            ULong productId,
            List<ULong> adsetIds,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (adsetIds == null || adsetIds.isEmpty()) return Mono.just(Map.of());

        Field<Integer> cnt = DSL.count().as("cnt");
        Condition condition = TICKETS.APP_CODE
                .eq(access.getAppCode())
                .and(TICKETS.CLIENT_CODE.eq(access.getClientCode()))
                .and(TICKETS.PRODUCT_ID.eq(productId))
                .and(TICKETS.ADSET_ID.in(adsetIds))
                .and(TICKETS.AD_ID.isNotNull())
                .and(TICKETS.STAGE.isNotNull())
                .and(TICKETS.IS_ACTIVE.isTrue());

        if (startDate != null && endDate != null) {
            condition = condition.and(TICKETS.CREATED_AT.between(startDate, endDate));
        }

        return Flux.from(dslContext
                        .select(TICKETS.AD_ID, TICKETS.STAGE, cnt)
                        .from(TICKETS)
                        .where(condition)
                        .groupBy(TICKETS.AD_ID, TICKETS.STAGE))
                .collectList()
                .map(records -> {
                    Map<ULong, Map<ULong, Long>> result = new HashMap<>();
                    for (Record r : records) {
                        result.computeIfAbsent(r.get(TICKETS.AD_ID), k -> new HashMap<>())
                                .put(r.get(TICKETS.STAGE), r.get("cnt", Long.class));
                    }
                    return result;
                });
    }

    /* ---------------- helpers ---------------- */

    private CampaignPlatform parsePlatform(String s) {
        try {
            return CampaignPlatform.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}
