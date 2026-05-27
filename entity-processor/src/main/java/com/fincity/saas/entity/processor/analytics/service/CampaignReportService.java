package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.dao.CampaignReportDAO;
import com.fincity.saas.entity.processor.analytics.model.CampaignReport;
import com.fincity.saas.entity.processor.analytics.model.CampaignReport.StageCell;
import com.fincity.saas.entity.processor.analytics.model.CampaignTreeRequest;
import com.fincity.saas.entity.processor.analytics.model.CampaignTreeResponse;
import com.fincity.saas.entity.processor.analytics.model.StageNode;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.util.DatePair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignReportService implements IProcessorAccessService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int SCALE = 2;

    @Getter
    private final IFeignSecurityService securityService;

    @Getter
    private final ProcessorMessageResourceService msgService;

    private final CampaignReportDAO campaignReportDAO;
    private final ProductService productService;

    public CampaignReportService(
            IFeignSecurityService securityService,
            ProcessorMessageResourceService msgService,
            CampaignReportDAO campaignReportDAO,
            ProductService productService) {
        this.securityService = securityService;
        this.msgService = msgService;
        this.campaignReportDAO = campaignReportDAO;
        this.productService = productService;
    }

    /**
     * Build the campaign report tree for a product. Access is gated by
     * {@code productService.readByIdentity} — the caller must be able to see
     * the product, and the controller layer enforces {@code ROLE_Owner}.
     *
     * <p>This iteration only fills {@link CampaignReport.Level#CAMPAIGN} rows
     * (one per active campaign linked to the product). Adset and ad expansion
     * are pending — when implemented they'll populate {@link CampaignReport#getChildren()}
     * on the campaign rows based on {@link CampaignTreeRequest#getDepth()}.
     */
    public Mono<CampaignTreeResponse> getCampaignTree(CampaignTreeRequest request) {

        if (request == null || request.getProductId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "productId");
        }

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> productService.readByIdentity(access, request.getProductId()),
                        (access, product) -> {
                            if (!product.isActive())
                                return msgService.<CampaignTreeResponse>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return buildTree(access, product, request);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignReportService.getCampaignTree"));
    }

    private Mono<CampaignTreeResponse> buildTree(
            com.fincity.saas.entity.processor.model.common.ProcessorAccess access,
            Product product,
            CampaignTreeRequest request) {

        // UI sends startDate/endDate as epoch seconds, which Jackson deserializes into
        // UTC LocalDateTime values. Use those directly for TIMESTAMP comparisons
        // (TICKETS.CREATED_AT is stored in UTC). For the DATE-only METRIC_DATE column,
        // shift to the caller's timezone first so day boundaries match the user's clock —
        // otherwise an IST user filtering "Oct 1" loses Sep 30 18:30+ UTC rows.
        LocalDateTime startUtc = request.getStartDate();
        LocalDateTime endUtc = request.getEndDate();
        LocalDate metricFrom = toLocalDate(startUtc, request.getTimezone());
        LocalDate metricTo = toLocalDate(endUtc, request.getTimezone());

        CampaignTreeRequest.Depth depth =
                request.getDepth() == null ? CampaignTreeRequest.Depth.CAMPAIGN : request.getDepth();

        return Mono.zip(
                        campaignReportDAO.getStageTreeForProductTemplate(product.getProductTemplateId()),
                        campaignReportDAO.getCampaignLevelRows(
                                access, product.getId(), metricFrom, metricTo, request.getPlatforms()),
                        campaignReportDAO.getStageCountsByCampaign(
                                access, product.getId(), startUtc, endUtc),
                        campaignReportDAO.getFunnelCountsByCampaign(
                                access, product.getId(), startUtc, endUtc))
                .flatMap(t -> {
                    List<StageNode> stageTree = t.getT1();
                    List<CampaignReport> rows = t.getT2();
                    Map<ULong, Map<ULong, Long>> stageCounts = t.getT3();
                    Map<ULong, Map<String, Long>> funnelCounts = t.getT4();

                    for (CampaignReport row : rows) {
                        row.setProductName(product.getName());
                        enrichRow(row, stageCounts.get(row.getId()), funnelCounts.get(row.getId()));
                    }

                    // Drop campaigns with no activity in the selected window: no
                    // impressions/clicks/spend and no leads. They ran outside the range,
                    // so showing them as all-zero rows is just noise.
                    rows = rows.stream()
                            .filter(row -> hasWindowActivity(row, stageCounts.get(row.getId())))
                            .toList();

                    if (depth == CampaignTreeRequest.Depth.CAMPAIGN || rows.isEmpty()) {
                        return Mono.just(new CampaignTreeResponse(stageTree, rows));
                    }

                    return attachAdsets(access, product, rows, depth, startUtc, endUtc, metricFrom, metricTo)
                            .thenReturn(new CampaignTreeResponse(stageTree, rows));
                });
    }

    /** Fetches adsets per campaign, enriches with stage counts, and (for AD depth) recursively attaches ads. */
    private Mono<Void> attachAdsets(
            com.fincity.saas.entity.processor.model.common.ProcessorAccess access,
            Product product,
            List<CampaignReport> campaignRows,
            CampaignTreeRequest.Depth depth,
            LocalDateTime startUtc,
            LocalDateTime endUtc,
            LocalDate metricFrom,
            LocalDate metricTo) {

        List<ULong> campaignIds = campaignRows.stream().map(CampaignReport::getId).toList();

        return Mono.zip(
                        campaignReportDAO.getAdsetRowsForCampaigns(access, campaignIds, metricFrom, metricTo),
                        campaignReportDAO.getStageCountsByAdset(access, product.getId(), campaignIds, startUtc, endUtc))
                .flatMap(t -> {
                    List<CampaignReport> adsets = t.getT1();
                    Map<ULong, Map<ULong, Long>> adsetStageCounts = t.getT2();

                    for (CampaignReport adset : adsets) {
                        enrichRow(adset, adsetStageCounts.get(adset.getId()), null);
                    }

                    Map<ULong, List<CampaignReport>> adsetsByCampaign = adsets.stream()
                            .collect(java.util.stream.Collectors.groupingBy(CampaignReport::getCampaignId));
                    for (CampaignReport campaign : campaignRows) {
                        List<CampaignReport> kids = adsetsByCampaign.get(campaign.getId());
                        if (kids != null) kids.forEach(k -> k.setPlatform(campaign.getPlatform()));
                        campaign.setChildren(kids == null ? List.of() : kids);
                    }

                    if (depth != CampaignTreeRequest.Depth.AD || adsets.isEmpty()) return Mono.empty();
                    return attachAds(access, product, adsets, startUtc, endUtc, metricFrom, metricTo);
                });
    }

    private Mono<Void> attachAds(
            com.fincity.saas.entity.processor.model.common.ProcessorAccess access,
            Product product,
            List<CampaignReport> adsetRows,
            LocalDateTime startUtc,
            LocalDateTime endUtc,
            LocalDate metricFrom,
            LocalDate metricTo) {

        List<ULong> adsetIds = adsetRows.stream().map(CampaignReport::getId).toList();

        return Mono.zip(
                        campaignReportDAO.getAdRowsForAdsets(access, adsetIds, metricFrom, metricTo),
                        campaignReportDAO.getStageCountsByAd(access, product.getId(), adsetIds, startUtc, endUtc))
                .doOnNext(t -> {
                    List<CampaignReport> ads = t.getT1();
                    Map<ULong, Map<ULong, Long>> adStageCounts = t.getT2();

                    for (CampaignReport ad : ads) {
                        enrichRow(ad, adStageCounts.get(ad.getId()), null);
                    }

                    Map<ULong, List<CampaignReport>> adsByAdset = ads.stream()
                            .collect(java.util.stream.Collectors.groupingBy(CampaignReport::getAdsetId));
                    for (CampaignReport adset : adsetRows) {
                        List<CampaignReport> kids = adsByAdset.get(adset.getId());
                        if (kids != null) kids.forEach(k -> k.setPlatform(adset.getPlatform()));
                        adset.setChildren(kids == null ? List.of() : kids);
                    }
                })
                .then();
    }

    /**
     * Compute per-stage CPL, funnel cost metrics (CPMQL/CPSQL/CPW), and the
     * standard ad ratios (CTR/CPM/CPC/CPL). Mutates {@code row} in place.
     */
    /** A campaign is "active in the window" if it had any impressions, clicks, spend, or leads there. */
    private static boolean hasWindowActivity(CampaignReport row, Map<ULong, Long> stageCounts) {
        if (row.getImpressions() > 0 || row.getClicks() > 0) return true;
        if (row.getSpend() != null && row.getSpend().signum() > 0) return true;
        if (stageCounts != null) {
            for (Long v : stageCounts.values()) {
                if (v != null && v > 0) return true;
            }
        }
        return false;
    }

    private void enrichRow(CampaignReport row, Map<ULong, Long> stageCounts, Map<String, Long> funnelCounts) {

        BigDecimal spend = row.getSpend() == null ? BigDecimal.ZERO : row.getSpend();
        long totalLeads = applyStageCells(row, stageCounts, spend);

        row.setLeadsByFunnelStage(funnelCounts == null ? Map.of() : new HashMap<>(funnelCounts));

        applyAdRatios(row, spend, totalLeads);
        applyFunnelCosts(row, funnelCounts, spend);
    }

    /** Populates {@code row.stageCells} and returns the total ticket count across stages. */
    private long applyStageCells(CampaignReport row, Map<ULong, Long> stageCounts, BigDecimal spend) {
        Map<String, StageCell> cells = new HashMap<>();
        long total = 0L;
        if (stageCounts != null) {
            boolean hasSpend = spend.signum() > 0;
            for (Map.Entry<ULong, Long> e : stageCounts.entrySet()) {
                long count = e.getValue();
                total += count;
                StageCell cell = new StageCell().setCount(count);
                if (count > 0 && hasSpend) {
                    cell.setCpl(spend.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP));
                }
                cells.put(e.getKey().toString(), cell);
            }
        }
        row.setStageCells(cells);
        return total;
    }

    private void applyAdRatios(CampaignReport row, BigDecimal spend, long totalLeads) {
        long impressions = row.getImpressions();
        long clicks = row.getClicks();
        if (impressions > 0) {
            row.setCtr(BigDecimal.valueOf(clicks)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(impressions), SCALE, RoundingMode.HALF_UP));
            row.setCpm(spend.multiply(THOUSAND)
                    .divide(BigDecimal.valueOf(impressions), SCALE, RoundingMode.HALF_UP));
        }
        if (clicks > 0) row.setCpc(spend.divide(BigDecimal.valueOf(clicks), SCALE, RoundingMode.HALF_UP));
        if (totalLeads > 0 && spend.signum() > 0)
            row.setCpl(spend.divide(BigDecimal.valueOf(totalLeads), SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Convert a UTC {@code LocalDateTime} to the {@code LocalDate} as seen in
     * {@code timezone}. Used for filters against the DATE-only METRIC_DATE column
     * so day boundaries match the user's clock instead of UTC.
     */
    private static LocalDate toLocalDate(LocalDateTime utc, String timezone) {
        if (utc == null) return null;
        return DatePair.convertUtcToTimezone(utc, timezone).toLocalDate();
    }

    private void applyFunnelCosts(CampaignReport row, Map<String, Long> funnelCounts, BigDecimal spend) {
        if (funnelCounts == null || spend.signum() <= 0) return;
        long mql = funnelCounts.getOrDefault("MQL", 0L);
        long sql = funnelCounts.getOrDefault("SQL", 0L);
        long won = funnelCounts.getOrDefault("WON", 0L);
        if (mql > 0) row.setCpmql(spend.divide(BigDecimal.valueOf(mql), SCALE, RoundingMode.HALF_UP));
        if (sql > 0) row.setCpsql(spend.divide(BigDecimal.valueOf(sql), SCALE, RoundingMode.HALF_UP));
        if (won > 0) row.setCpw(spend.divide(BigDecimal.valueOf(won), SCALE, RoundingMode.HALF_UP));
    }
}
