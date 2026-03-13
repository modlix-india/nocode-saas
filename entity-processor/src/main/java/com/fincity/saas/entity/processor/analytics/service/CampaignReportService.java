package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.dao.CampaignReportDAO;
import com.fincity.saas.entity.processor.analytics.model.CampaignReport;
import com.fincity.saas.entity.processor.analytics.model.CampaignReportFilter;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignReportService implements IProcessorAccessService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    @Getter
    private final IFeignSecurityService securityService;

    @Getter
    private final ProcessorMessageResourceService msgService;

    private final CampaignReportDAO campaignReportDAO;

    public CampaignReportService(
            IFeignSecurityService securityService,
            ProcessorMessageResourceService msgService,
            CampaignReportDAO campaignReportDAO) {
        this.securityService = securityService;
        this.msgService = msgService;
        this.campaignReportDAO = campaignReportDAO;
    }

    public Mono<Page<CampaignReport>> getConsolidatedReport(Pageable pageable, CampaignReportFilter filter) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> campaignReportDAO.getAdMetrics(access, filter),
                (access, adMetrics) -> campaignReportDAO.getTicketStageCounts(access, filter),
                (access, adMetrics, ticketCounts) -> campaignReportDAO.getTicketSourceCounts(access, filter),
                (access, adMetrics, ticketCounts, sourceCounts) -> {
                    List<CampaignReport> merged = mergeAndCompute(adMetrics, ticketCounts, sourceCounts);
                    int start = (int) pageable.getOffset();
                    int end = Math.min(start + pageable.getPageSize(), merged.size());
                    List<CampaignReport> pageContent = start < merged.size()
                            ? merged.subList(start, end) : List.of();
                    Page<CampaignReport> page = new PageImpl<>(pageContent, pageable, merged.size());
                    return Mono.just(page);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignReportService.getConsolidatedReport"));
    }

    public Mono<List<CampaignReport>> getConsolidatedReportSummary(CampaignReportFilter filter) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> campaignReportDAO.getAdMetrics(access, filter),
                (access, adMetrics) -> campaignReportDAO.getTicketStageCounts(access, filter),
                (access, adMetrics, ticketCounts) -> campaignReportDAO.getTicketSourceCounts(access, filter),
                (access, adMetrics, ticketCounts, sourceCounts) ->
                        Mono.just(mergeAndCompute(adMetrics, ticketCounts, sourceCounts))
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignReportService.getConsolidatedReportSummary"));
    }

    private List<CampaignReport> mergeAndCompute(
            List<CampaignReport> adMetrics,
            Map<ULong, Map<String, Long>> ticketCounts,
            Map<ULong, Map<String, Long>> sourceCounts) {

        List<CampaignReport> results = new ArrayList<>(adMetrics.size());

        for (CampaignReport report : adMetrics) {
            // Merge ticket stage counts
            Map<String, Long> stages = ticketCounts.getOrDefault(report.getCampaignId(), Map.of());
            report.setLeadsByStage(new HashMap<>(stages));

            // Compute total CRM leads from stages
            long totalLeads = stages.values().stream().mapToLong(Long::longValue).sum();

            // Source breakdown
            Map<String, Long> sources = sourceCounts.getOrDefault(report.getCampaignId(), Map.of());
            long metaLeads = sources.getOrDefault("FACEBOOK_FORM", 0L);
            long dcrmLeads = sources.entrySet().stream()
                    .filter(e -> !"FACEBOOK_FORM".equals(e.getKey()) && !"GOOGLE_FORM".equals(e.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            report.setTotalMetaLeads(metaLeads);
            report.setTotalDcrmLeads(dcrmLeads);

            // RL = rejected stage tickets (dynamic — any stage named containing "reject" or "RL")
            long rl = stages.entrySet().stream()
                    .filter(e -> e.getKey() != null && (e.getKey().equalsIgnoreCase("RL")
                            || e.getKey().toLowerCase().contains("reject")))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            report.setRl(rl);

            // Diff = platform leads (WL+FL) - actual CRM leads
            report.setDiff((report.getPlatformWL() + report.getPlatformFL()) - totalLeads);

            // Computed ad metrics
            computeAdMetrics(report, totalLeads);

            // Computed percentage metrics
            computePercentageMetrics(report, stages, totalLeads);

            results.add(report);
        }

        return results;
    }

    private void computeAdMetrics(CampaignReport report, long totalLeads) {
        long impressions = report.getImpressions();
        long clicks = report.getClicks();
        BigDecimal spend = report.getSpend();

        // CTR = clicks / impressions * 100
        if (impressions > 0) {
            report.setCtr(BigDecimal.valueOf(clicks)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP));
        }

        // CPM = spend / impressions * 1000
        if (impressions > 0) {
            report.setCpm(spend.multiply(THOUSAND)
                    .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP));
        }

        // CPC = spend / clicks
        if (clicks > 0) {
            report.setCpc(spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP));
        }

        // CPL = spend / totalLeads
        if (totalLeads > 0) {
            report.setCpl(spend.divide(BigDecimal.valueOf(totalLeads), 2, RoundingMode.HALF_UP));
        }
    }

    private void computePercentageMetrics(CampaignReport report, Map<String, Long> stages, long totalLeads) {
        if (totalLeads <= 0) return;

        BigDecimal totalBd = BigDecimal.valueOf(totalLeads);

        // NC% — dynamic: any stage named "NC" or containing "not connected"
        long nc = stages.entrySet().stream()
                .filter(e -> e.getKey() != null && (e.getKey().equalsIgnoreCase("NC")
                        || e.getKey().toLowerCase().contains("not connected")))
                .mapToLong(Map.Entry::getValue)
                .sum();
        report.setNcPercent(BigDecimal.valueOf(nc).multiply(HUNDRED)
                .divide(totalBd, 2, RoundingMode.HALF_UP));

        // Lost% — dynamic: any stage named "Lost" or containing "lost"
        long lost = stages.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().toLowerCase().contains("lost"))
                .mapToLong(Map.Entry::getValue)
                .sum();
        report.setLostPercent(BigDecimal.valueOf(lost).multiply(HUNDRED)
                .divide(totalBd, 2, RoundingMode.HALF_UP));

        // NC+L% = (NC + Lost) / totalLeads * 100
        report.setNcPlusLPercent(BigDecimal.valueOf(nc + lost).multiply(HUNDRED)
                .divide(totalBd, 2, RoundingMode.HALF_UP));

        // LC% = totalLeads / clicks * 100 (leads per click)
        if (report.getClicks() > 0) {
            report.setLcPercent(totalBd.multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(report.getClicks()), 2, RoundingMode.HALF_UP));
        }
    }
}
