package com.modlix.saas.adzump.service.feedback;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.CrmOutcomes;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.leadzump.OutcomeQuery;
import com.modlix.saas.adzump.model.leadzump.OutcomeRow;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.platform.AdPlatform;
import com.modlix.saas.adzump.platform.AdPlatformRegistry;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.MilestoneMappingService;
import com.modlix.saas.adzump.service.PerformancePolicyService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.adzump.service.leadzump.LeadzumpClient;

/**
 * The J10 join, the whole differentiator. For a campaign over a window it pulls the FAST platform
 * signal (SPI {@code insights}, per targeted platform, at each grain) and the SLOW leadzump CRM
 * signal (J11 {@code getOutcomes}, folded onto milestone keys by the template MilestoneMapping), then
 * <b>left-joins them by the ad-grain id</b> (not by name — the legacy name-join is insufficient) at
 * the campaign / adset / ad grain. Both reads carry the <b>same window + account timezone</b> so fast
 * and slow numbers are comparable. A platform row with no CRM row yet is left {@code crm == null}
 * (a {@link com.modlix.saas.adzump.model.snapshot.SignalMaturity#FAST_ONLY} row).
 *
 * <p>All logic, no CRUD: the build resolves the plan (J1), the effective PerformancePolicy /
 * MilestoneMapping (J5/§2), and the platform tokens (J2), scores each row via {@link PolicyScorer},
 * and returns an unpersisted {@link PerformanceSnapshot}; {@code FeedbackService} owns persistence.
 */
@Service
public class SnapshotBuilder {

    private static final String MILESTONES = "milestones";
    private static final int UNIT_COST_SCALE = 4;

    private final CampaignPlanService campaignPlanService;
    private final PerformancePolicyService performancePolicyService;
    private final MilestoneMappingService milestoneMappingService;
    private final ConnectionService connectionService;
    private final AdPlatformRegistry registry;
    private final LeadzumpClient leadzumpClient;
    private final PolicyScorer policyScorer;

    public SnapshotBuilder(CampaignPlanService campaignPlanService,
            PerformancePolicyService performancePolicyService, MilestoneMappingService milestoneMappingService,
            ConnectionService connectionService, AdPlatformRegistry registry, LeadzumpClient leadzumpClient,
            PolicyScorer policyScorer) {

        this.campaignPlanService = campaignPlanService;
        this.performancePolicyService = performancePolicyService;
        this.milestoneMappingService = milestoneMappingService;
        this.connectionService = connectionService;
        this.registry = registry;
        this.leadzumpClient = leadzumpClient;
        this.policyScorer = policyScorer;
    }

    /**
     * Builds (does not persist) the snapshot for a plan over a window. Resolves the plan (which
     * enforces the by-id managed-client tenant gate), the effective policy + milestone mapping, and
     * fans the platform reads out across every platform the plan targets and every grain, joining the
     * CRM outcomes in by ad-grain id.
     */
    public PerformanceSnapshot build(ULong campaignPlanId, SnapshotWindow window, String targetClientCode) {

        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId); // PLAN_NOT_FOUND + managed-client gate
        String clientCode = plan.getClientCode();
        String templateId = plan.getProductTemplateId();

        PerformancePolicy policy = this.performancePolicyService.getEffective(campaignPlanId, targetClientCode);
        MilestoneMapping mapping = this.milestoneMappingService.getEffective(campaignPlanId, targetClientCode);

        LocalDate from = window == null ? null : window.getFrom();
        LocalDate to = window == null ? null : window.getTo();
        String tz = window == null ? null : window.getTimezone();

        List<SnapshotRow> rows = new ArrayList<>();

        for (Platform platform : plan.getPlatforms()) {

            String[] link = campaignLink(plan, platform);
            String accountId = link[0];
            String campaignId = link[1];
            if (campaignId == null || campaignId.isBlank())
                continue; // not launched on this platform yet -> no metrics to read

            Token token = toToken(this.connectionService.resolve(platform));
            String account = (accountId != null && !accountId.isBlank()) ? accountId : token.accountId();
            AdPlatform adPlatform = this.registry.get(platform);

            for (Grain grain : Grain.values()) {

                List<PlatformInsight> insights = adPlatform.insights(token,
                        new InsightQuery(account, List.of(campaignId), from, to, grain, tz));
                if (insights == null || insights.isEmpty())
                    continue;

                // The CRM read is scoped to exactly the ad-grain ids the platform returned, so the
                // left-join is by id (the differentiator), same window + tz on both sides.
                List<AdGrainId> ids = new ArrayList<>(insights.size());
                for (PlatformInsight pi : insights)
                    ids.add(adGrainId(grain, campaignId, refId(pi)));

                OutcomeQuery oq = new OutcomeQuery()
                        .setClientCode(clientCode)
                        .setProductTemplateId(templateId)
                        .setIds(ids)
                        .setTimezone(tz)
                        .setFrom(from)
                        .setTo(to)
                        .setGrain(grain);
                CrmOutcomes outcomes = this.leadzumpClient.getOutcomes(oq);

                Map<String, OutcomeRow> crmByKey = indexByGrainId(grain, outcomes);

                for (PlatformInsight pi : insights) {
                    String key = refId(pi);
                    OutcomeRow rawCrm = key == null ? null : crmByKey.get(key);
                    CrmMetrics crm = rawCrm == null ? null : foldByMilestone(rawCrm, mapping);

                    SnapshotRow row = new SnapshotRow()
                            .setGrain(grain)
                            .setAdGrainId(adGrainId(grain, campaignId, key))
                            .setPlatform(platformMetrics(pi))
                            .setCrm(crm);
                    row.setSignalMaturity(this.policyScorer.classifyMaturity(row, policy));
                    row.setBlendedScore(this.policyScorer.score(row, policy));
                    rows.add(row);
                }
            }
        }

        return new PerformanceSnapshot()
                .setCampaignPlanId(campaignPlanId)
                .setClientCode(clientCode)
                .setProductTemplateId(templateId)
                .setWindow(window)
                .setTakenAt(LocalDateTime.now())
                .setRollupScore(this.policyScorer.rollup(rows))
                .setGrainRows(rows);
    }

    // ------------------------------------------------------------------------------------------
    // Platform metrics
    // ------------------------------------------------------------------------------------------

    /** Derives ctr + cpc from the raw platform insight so the app and scorer do not each recompute. */
    private static PlatformMetrics platformMetrics(PlatformInsight pi) {

        long impressions = pi.impressions();
        long clicks = pi.clicks();
        Money spend = pi.spend();

        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0d;

        Money cpc = null;
        if (clicks > 0 && spend != null && spend.getAmount() != null) {
            BigDecimal perClick = spend.getAmount().divide(BigDecimal.valueOf(clicks), UNIT_COST_SCALE,
                    RoundingMode.HALF_UP);
            cpc = new Money(perClick, spend.getCurrency());
        }

        return new PlatformMetrics()
                .setImpressions(impressions)
                .setClicks(clicks)
                .setSpend(spend)
                .setCtr(ctr)
                .setCpc(cpc)
                .setPlatformConversions(pi.platformConversions());
    }

    // ------------------------------------------------------------------------------------------
    // The join keys (ad-grain id at each grain)
    // ------------------------------------------------------------------------------------------

    private static String refId(PlatformInsight pi) {
        PlatformRef ref = pi == null ? null : pi.ref();
        return ref == null ? null : ref.id();
    }

    /** The platform ref id at a grain becomes the grain-appropriate ad-grain id part. */
    private static AdGrainId adGrainId(Grain grain, String campaignId, String id) {
        AdGrainId adGrainId = new AdGrainId();
        switch (grain) {
            case CAMPAIGN -> adGrainId.setCampaignId(id);
            case ADSET -> adGrainId.setCampaignId(campaignId).setAdSetId(id);
            case AD -> adGrainId.setCampaignId(campaignId).setAdId(id);
        }
        return adGrainId;
    }

    /** The join key of an ad-grain id at a grain: the grain's own id part. */
    private static String grainKey(Grain grain, AdGrainId id) {
        if (id == null)
            return null;
        return switch (grain) {
            case CAMPAIGN -> id.getCampaignId();
            case ADSET -> id.getAdSetId();
            case AD -> id.getAdId();
        };
    }

    private static Map<String, OutcomeRow> indexByGrainId(Grain grain, CrmOutcomes outcomes) {
        Map<String, OutcomeRow> index = new LinkedHashMap<>();
        if (outcomes == null || outcomes.getRows() == null)
            return index;
        for (OutcomeRow row : outcomes.getRows()) {
            String key = grainKey(grain, row.getId());
            if (key != null)
                index.put(key, row);
        }
        return index;
    }

    // ------------------------------------------------------------------------------------------
    // MilestoneMapping aggregation: fold leadzump raw keys onto the vertical's milestone keys
    // ------------------------------------------------------------------------------------------

    /**
     * Folds a CRM {@link OutcomeRow} (keyed by leadzump raw stage/status keys) onto the vertical's
     * milestone keys using the template {@link MilestoneMapping} body
     * ({@code {"milestones": {"<milestone>": ["<rawKey>", ...]}}}). Counts sum; per-milestone unit
     * cost is the count-weighted average of the contributing raw keys' unit costs. When no mapping is
     * configured the raw maps pass through unchanged (identity mapping), so a template whose leadzump
     * keys already equal its milestone keys needs no config.
     */
    private static CrmMetrics foldByMilestone(OutcomeRow raw, MilestoneMapping mapping) {

        JsonNode milestones = mapping == null || mapping.getBody() == null ? null : mapping.getBody().get(MILESTONES);

        if (milestones == null || !milestones.isObject()) {
            // Identity: the raw keys are already the milestone keys.
            return new CrmMetrics()
                    .setCountByMilestone(raw.getCountByMilestone() == null
                            ? Map.of()
                            : new LinkedHashMap<>(raw.getCountByMilestone()))
                    .setCostByMilestone(raw.getCostByMilestone() == null
                            ? Map.of()
                            : new LinkedHashMap<>(raw.getCostByMilestone()))
                    .setJunkRate(raw.getJunkRate());
        }

        Map<String, Long> countByMilestone = new LinkedHashMap<>();
        Map<String, Money> costByMilestone = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = milestones.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String milestone = field.getKey();
            JsonNode rawKeys = field.getValue();
            if (rawKeys == null || !rawKeys.isArray())
                continue;

            long count = 0L;
            double costWeighted = 0.0d;
            long costBasis = 0L;
            String currency = null;

            for (JsonNode rawKeyNode : rawKeys) {
                String rawKey = rawKeyNode.asText(null);
                if (rawKey == null || rawKey.isBlank())
                    continue;

                long c = rawCount(raw, rawKey);
                count += c;

                Money unit = rawCost(raw, rawKey);
                if (unit != null && unit.getAmount() != null && c > 0) {
                    costWeighted += unit.getAmount().doubleValue() * c;
                    costBasis += c;
                    currency = unit.getCurrency();
                }
            }

            countByMilestone.put(milestone, count);
            if (costBasis > 0) {
                BigDecimal unitCost = BigDecimal.valueOf(costWeighted / costBasis)
                        .setScale(UNIT_COST_SCALE, RoundingMode.HALF_UP);
                costByMilestone.put(milestone, new Money(unitCost, currency));
            }
        }

        return new CrmMetrics()
                .setCountByMilestone(countByMilestone)
                .setCostByMilestone(costByMilestone)
                .setJunkRate(raw.getJunkRate());
    }

    private static long rawCount(OutcomeRow raw, String rawKey) {
        if (raw.getCountByMilestone() == null)
            return 0L;
        for (Map.Entry<String, Long> e : raw.getCountByMilestone().entrySet()) {
            if (rawKey.equalsIgnoreCase(e.getKey()))
                return e.getValue() == null ? 0L : e.getValue();
        }
        return 0L;
    }

    private static Money rawCost(OutcomeRow raw, String rawKey) {
        if (raw.getCostByMilestone() == null)
            return null;
        for (Map.Entry<String, Money> e : raw.getCostByMilestone().entrySet()) {
            if (rawKey.equalsIgnoreCase(e.getKey()))
                return e.getValue();
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Plan links + token (mirrors CampaignService)
    // ------------------------------------------------------------------------------------------

    /** {@code [adAccountId, campaignId]} for a platform from the plan links, or nulls when absent. */
    private static String[] campaignLink(CampaignPlan plan, Platform platform) {

        CampaignPlanBody body = plan.getBody();
        Links links = body == null ? null : body.getLinks();
        if (links == null)
            return new String[] { null, null };

        return switch (platform) {
            case GOOGLE -> links.getGoogle() == null
                    ? new String[] { null, null }
                    : new String[] { links.getGoogle().getAdAccountId(), links.getGoogle().getCampaignId() };
            case META -> links.getMeta() == null
                    ? new String[] { null, null }
                    : new String[] { links.getMeta().getAdAccountId(), links.getMeta().getCampaignId() };
        };
    }

    private static Token toToken(PlatformCredential credential) {
        Map<String, String> attributes = credential.getAttributes() == null ? Map.of() : credential.getAttributes();
        String loginCustomerId = attributes.get("loginCustomerId");
        return new Token(credential.getAccessToken(), credential.getAccountId(), loginCustomerId, attributes);
    }
}
