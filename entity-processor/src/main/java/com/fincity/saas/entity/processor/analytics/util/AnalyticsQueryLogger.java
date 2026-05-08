package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class AnalyticsQueryLogger {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsQueryLogger.class);

    private AnalyticsQueryLogger() {}

    public static <T> Flux<T> logQuery(
            String queryName, Select<?> select, TicketBucketFilter filter, Flux<T> resultFlux) {

        if (!logger.isDebugEnabled()) return resultFlux;

        String sql = select.getSQL();
        String filterSummary = buildFilterSummary(filter);
        String dateInfo = buildDateInfo(filter);

        AtomicLong startTime = new AtomicLong();
        AtomicInteger rowCount = new AtomicInteger(0);

        return resultFlux
                .doOnSubscribe(s -> {
                    startTime.set(System.currentTimeMillis());
                    logger.debug(
                            "[ANALYTICS-QUERY-START] query={}, filter=[{}], dates=[{}], sql=[{}]",
                            queryName,
                            filterSummary,
                            dateInfo,
                            sql);
                })
                .doOnNext(item -> rowCount.incrementAndGet())
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    logger.debug(
                            "[ANALYTICS-QUERY-END] query={}, rows={}, timeMs={}, dates=[{}]",
                            queryName,
                            rowCount.get(),
                            elapsed,
                            dateInfo);
                })
                .doOnError(err -> {
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    logger.error(
                            "[ANALYTICS-QUERY-ERROR] query={}, timeMs={}, error={}",
                            queryName,
                            elapsed,
                            err.getMessage());
                });
    }

    public static void logDistinctQuery(String queryName, Select<?> select, TicketBucketFilter filter) {

        if (!logger.isDebugEnabled()) return;

        logger.debug(
                "[ANALYTICS-DISTINCT] query={}, dates=[{}], sql=[{}]",
                queryName,
                buildDateInfo(filter),
                select.getSQL());
    }

    private static String buildFilterSummary(TicketBucketFilter filter) {

        if (filter == null) return "null";

        StringBuilder sb = new StringBuilder();

        appendList(sb, "productTemplateIds", filter.getProductTemplateIds());
        appendList(sb, "productIds", filter.getProductIds());
        appendList(sb, "stageIds", filter.getStageIds());
        appendList(sb, "statusIds", filter.getStatusIds());
        appendList(sb, "sources", filter.getSources());
        appendList(sb, "subSources", filter.getSubSources());
        appendList(sb, "clientIds", filter.getClientIds());
        appendList(sb, "assignedUserIds", filter.getAssignedUserIds());
        appendList(sb, "createdByIds", filter.getCreatedByIds());

        if (filter.isOnlyCurrentStageStatus()) sb.append("onlyCurrentStageStatus=true ");
        if (filter.isIncludeAll()) sb.append("includeAll=true ");
        if (filter.isIncludeNone()) sb.append("includeNone=true ");
        if (filter.isIncludeTotal()) sb.append("includeTotal=true ");
        if (filter.isIncludePercentage()) sb.append("includePercentage=true ");

        if (filter.getFieldData() != null && filter.getFieldData().getSelectedProductTemplateIds() != null
                && !filter.getFieldData().getSelectedProductTemplateIds().isEmpty())
            sb.append("selectedTemplateIds=")
                    .append(filter.getFieldData().getSelectedProductTemplateIds())
                    .append(' ');

        if (filter.getTimePeriod() != null)
            sb.append("timePeriod=").append(filter.getTimePeriod()).append(' ');

        return sb.toString().trim();
    }

    private static String buildDateInfo(TicketBucketFilter filter) {

        if (filter == null) return "no-filter";

        LocalDateTime start = filter.getStartDate();
        LocalDateTime end = filter.getEndDate();
        String timezone = filter.getTimezone();

        if (start == null && end == null) return "no-date-range";

        StringBuilder sb = new StringBuilder();

        sb.append("utcStart=").append(start != null ? start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "null");
        sb.append(", utcEnd=").append(end != null ? end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "null");

        if (timezone != null && !timezone.isEmpty()) {
            sb.append(", tz=").append(timezone);

            try {
                ZoneId zoneId = ZoneId.of(timezone);

                if (start != null) {
                    ZonedDateTime zonedStart =
                            start.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId);
                    sb.append(", localStart=").append(zonedStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }

                if (end != null) {
                    ZonedDateTime zonedEnd =
                            end.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId);
                    sb.append(", localEnd=").append(zonedEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            } catch (Exception e) {
                sb.append(", tzConversionError=").append(e.getMessage());
            }
        }

        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String name, java.util.List<?> list) {
        if (list != null && !list.isEmpty()) sb.append(name).append('=').append(list).append(' ');
    }
}
