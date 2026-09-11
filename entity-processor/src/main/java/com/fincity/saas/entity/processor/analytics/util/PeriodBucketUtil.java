package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import lombok.experimental.UtilityClass;

/**
 * Shared, timezone-aware period bucketing for time-series analytics queries.
 *
 * <p>Any query that groups rows into DAYS/WEEKS/MONTHS/QUARTERS/YEARS buckets
 * MUST route its timestamp column through this helper instead of hand-rolling
 * DATE_SUB/DATE expressions. Converting UTC → the caller's timezone BEFORE
 * extracting the calendar date is what keeps independently-bucketed series
 * (e.g. spend by METRIC_DATE vs leads by CREATED_AT) aligned on the same
 * local calendar — skipping it silently shifts boundary events into the
 * neighbouring bucket.
 *
 * <p>Mirrors the logic in TicketBucketDAO#toDateBucketGroupKeyField; migrate
 * those call sites here as a follow-up so only one copy exists.
 */
@UtilityClass
public class PeriodBucketUtil {

    public Field<LocalDateTime> toDateBucketGroupKeyField(
            TimePeriod timePeriod, Field<LocalDateTime> dateTimeField, String timezone) {

        // Validate before handing the zone to MySQL: an invalid IANA name makes
        // CONVERT_TZ return NULL (rows silently vanish from buckets), so fall back
        // to unconverted timestamps — same graceful UTC behavior as DatePair.resolveZoneId.
        Field<LocalDateTime> effectiveDateField = isValidZone(timezone)
                ? DSL.field(
                        "convert_tz({0}, 'UTC', {1})", SQLDataType.LOCALDATETIME, dateTimeField, DSL.inline(timezone))
                : dateTimeField;

        if (timePeriod == null)
            return DSL.field("timestamp(cast({0} as date))", SQLDataType.LOCALDATETIME, effectiveDateField);

        return switch (timePeriod) {
            case WEEKS -> DSL.field(
                    "timestamp(date_sub(cast({0} as date), interval weekday(cast({0} as date)) day))",
                    SQLDataType.LOCALDATETIME, effectiveDateField);
            case MONTHS -> DSL.field(
                    "str_to_date(date_format({0}, '%Y-%m-01 00:00:00'), '%Y-%m-%d %H:%i:%s')",
                    SQLDataType.LOCALDATETIME, effectiveDateField);
            case QUARTERS -> DSL.field(
                    "str_to_date(concat(year({0}), '-', lpad(((quarter({0})-1)*3)+1, 2, '0'), '-01 00:00:00'),"
                            + " '%Y-%m-%d %H:%i:%s')",
                    SQLDataType.LOCALDATETIME, effectiveDateField);
            case YEARS -> DSL.field(
                    "str_to_date(date_format({0}, '%Y-01-01 00:00:00'), '%Y-%m-%d %H:%i:%s')",
                    SQLDataType.LOCALDATETIME, effectiveDateField);
            default -> DSL.field("timestamp(cast({0} as date))", SQLDataType.LOCALDATETIME, effectiveDateField);
        };
    }

    /**
     * A timezone is usable for SQL-side conversion only when it is a non-blank,
     * non-UTC, resolvable IANA name — mirroring {@code DatePair.resolveZoneId}'s
     * fallback-to-UTC semantics so all endpoints degrade identically on bad input.
     */
    private static boolean isValidZone(String timezone) {
        if (StringUtil.safeIsBlank(timezone) || "UTC".equalsIgnoreCase(timezone)) return false;
        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
