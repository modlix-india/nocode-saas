package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;

/**
 * Turns an {@link AggregateQuery} into SQL.
 *
 * This is the thing the whole move to MySQL was for. On Mongo a grouped read is a
 * pipeline that can only ever see one collection; here it is a SELECT, and a SELECT
 * is one join away from spanning two.
 *
 * Pure: JOOQ renders without a connection, so every expression this produces can be
 * asserted as text. That matters more here than for the ordinary read path, because
 * the group key for a bucketed date is a nest of date arithmetic that is very easy
 * to get subtly wrong and almost impossible to notice afterwards - a chart renders
 * either way.
 *
 * Validation is NOT done here. {@code AggregateQueryValidator} has already rejected
 * anything whose field names or aliases are not plain identifiers, and this class
 * would be unsafe called without it.
 */
public final class MySQLAggregateBuilder {

    /**
     * The datetime zero point, written as a literal so no session setting can move it.
     *
     * {@code FROM_UNIXTIME} and {@code UNIX_TIMESTAMP} both interpret their arguments
     * in the SESSION time zone, which a pooled connection does not promise to set and
     * an operator can change. Converting with arithmetic against a fixed literal takes
     * the session out of it entirely, which is the difference between a chart that is
     * right and one that is right on your machine.
     */
    private static final String EPOCH = "TIMESTAMP('1970-01-01 00:00:00')";

    private MySQLAggregateBuilder() {
    }

    /**
     * The grouped SELECT, without paging.
     *
     * Paging is left off so the same query can be counted by wrapping it, which is
     * what {@link #count} does.
     */
    public static SelectHavingStep<? extends Record> grouped(
            DSLContext ctx, Table<?> table, AggregateQuery query, Condition where, Set<String> jsonColumns) {
        return grouped(ctx, table, query, where, MySQLFieldResolver.of(jsonColumns));
    }

    public static SelectHavingStep<? extends Record> grouped(
            DSLContext ctx, Table<?> table, AggregateQuery query, Condition where, MySQLFieldResolver resolver) {

        List<Field<?>> select = new ArrayList<>();
        List<Field<?>> groupBy = new ArrayList<>();

        if (query.getGroupBy() != null)
            for (GroupByField g : query.getGroupBy()) {
                Field<?> key = groupKey(g, resolver);
                select.add(key.as(g.resolvedAlias()));
                // Grouped by the EXPRESSION, not the alias. MySQL would accept the
                // alias, but only MySQL does, and the expression is what the next
                // backend will need.
                groupBy.add(key);
            }

        for (Aggregation a : query.getAggregations())
            select.add(measure(a, resolver).as(a.resolvedAlias()));

        var step = ctx.select(select).from(table).where(where);

        return groupBy.isEmpty() ? step : step.groupBy(groupBy);
    }

    /** The full query: grouped, filtered after grouping, sorted and paged. */
    public static Select<? extends Record> page(
            DSLContext ctx,
            Table<?> table,
            AggregateQuery query,
            Condition where,
            Condition having,
            Pageable pageable,
            Set<String> jsonColumns) {
        return page(ctx, table, query, where, having, pageable, MySQLFieldResolver.of(jsonColumns));
    }

    public static Select<? extends Record> page(
            DSLContext ctx,
            Table<?> table,
            AggregateQuery query,
            Condition where,
            Condition having,
            Pageable pageable,
            MySQLFieldResolver resolver) {

        SelectHavingStep<? extends Record> grouped = grouped(ctx, table, query, where, resolver);

        var afterHaving = having == null ? grouped : grouped.having(having);

        List<OrderField<?>> order = order(query, pageable.getSort());

        return (order.isEmpty() ? afterHaving : afterHaving.orderBy(order))
                .limit(pageable.getPageSize())
                .offset((int) pageable.getOffset());
    }

    /**
     * How many groups there are, which is not how many rows there are.
     *
     * A grouped query cannot be counted by counting its input, so the query is
     * wrapped and the wrapper counts its output. The inner query keeps its HAVING,
     * because a group the caller filtered out is not one of their groups.
     */
    public static Select<? extends Record> count(
            DSLContext ctx,
            Table<?> table,
            AggregateQuery query,
            Condition where,
            Condition having,
            Set<String> jsonColumns) {
        return count(ctx, table, query, where, having, MySQLFieldResolver.of(jsonColumns));
    }

    public static Select<? extends Record> count(
            DSLContext ctx,
            Table<?> table,
            AggregateQuery query,
            Condition where,
            Condition having,
            MySQLFieldResolver resolver) {

        SelectHavingStep<? extends Record> grouped = grouped(ctx, table, query, where, resolver);
        Select<? extends Record> inner = having == null ? grouped : grouped.having(having);

        return ctx.selectCount().from(inner.asTable("grouped"));
    }

    /**
     * Sort only on aliases, which the validator has already guaranteed.
     *
     * Referring to the output name rather than rebuilding the expression keeps a
     * bucketed sort from computing the whole date nest a second time.
     */
    static List<OrderField<?>> order(AggregateQuery query, Sort sort) {

        List<OrderField<?>> out = new ArrayList<>();
        if (sort == null || sort.isUnsorted()) return out;

        for (Sort.Order o : sort) {
            Field<Object> f = DSL.field(DSL.name(o.getProperty()));
            out.add(o.isAscending() ? f.asc() : f.desc());
        }
        return out;
    }

    // ------------------------------------------------------------------ measures

    static Field<?> measure(Aggregation a, Set<String> jsonColumns) {
        return measure(a, MySQLFieldResolver.of(jsonColumns));
    }

    static Field<?> measure(Aggregation a, MySQLFieldResolver resolver) {

        if (a.getField() == null || a.getField().isBlank()) return DSL.count();

        Field<Object> f = resolver.resolve(a.getField());

        return switch (a.getFunction()) {
            // COUNT(col) skips nulls, which is the SQL contract and the one the Mongo
            // backend goes out of its way to reproduce.
            case COUNT -> DSL.count(f);
            case SUM -> DSL.sum(f.cast(Double.class));
            case AVG -> DSL.avg(f.cast(Double.class));
            case MIN -> DSL.min(f);
            case MAX -> DSL.max(f);
        };
    }

    // ------------------------------------------------------------------ group keys

    static Field<?> groupKey(GroupByField g, Set<String> jsonColumns) {
        return groupKey(g, MySQLFieldResolver.of(jsonColumns));
    }

    static Field<?> groupKey(GroupByField g, MySQLFieldResolver resolver) {

        Field<Object> raw = resolver.resolve(g.getField());

        if (g.getBucket() == null) return raw;

        // A real date column is already a date. A number is an epoch value and has to
        // be told which one, which is the whole reason DateEncoding exists - and the
        // reason it is optional here and mandatory on Mongo.
        String utc = g.getEncoding() == null
                ? "{0}"
                : EPOCH + " + INTERVAL FLOOR({0} * " + g.getEncoding().getToMillis() + " / 1000) SECOND";

        String zone = g.resolvedTimezone();
        boolean shifted = !"UTC".equals(zone);

        String local = shifted ? "CONVERT_TZ(" + utc + ", '+00:00', '" + zone + "')" : utc;
        String truncated = truncate(g.getBucket(), local);
        String backToUtc = shifted ? "CONVERT_TZ(" + truncated + ", '" + zone + "', '+00:00')" : truncated;

        // A date column gets a date back. An epoch number gets a number back, in the
        // encoding it was stored in, because every other value this backend returns
        // for that column is one and a client that suddenly got a date would not know
        // what to do with it.
        if (g.getEncoding() == null) return DSL.field(backToUtc, java.time.LocalDateTime.class, raw);

        String seconds = "TIMESTAMPDIFF(SECOND, " + EPOCH + ", " + backToUtc + ")";
        String value = g.getEncoding() == DateEncoding.EPOCH_MILLIS ? "(" + seconds + ") * 1000" : seconds;

        return DSL.field(value, Long.class, raw);
    }

    /**
     * Truncate a datetime expression to a boundary.
     *
     * Written as arithmetic that yields a datetime rather than DATE_FORMAT, which
     * yields a string: a string group key sorts lexically, and "2026-10-01" before
     * "2026-9-01" is the kind of wrong that looks right in nine months out of twelve.
     */
    static String truncate(DateBucketUnit unit, String d) {
        return switch (unit) {
            case YEAR -> "MAKEDATE(YEAR(" + d + "), 1)";
            case QUARTER -> "MAKEDATE(YEAR(" + d + "), 1) + INTERVAL (QUARTER(" + d + ") - 1) QUARTER";
            case MONTH -> "DATE_SUB(DATE(" + d + "), INTERVAL DAYOFMONTH(" + d + ") - 1 DAY)";
            // DAYOFWEEK is 1 for Sunday, which is where $dateTrunc starts a week too.
            // Two backends disagreeing about which day a week begins on would put the
            // same order in different buckets depending on where it is stored.
            case WEEK -> "DATE_SUB(DATE(" + d + "), INTERVAL DAYOFWEEK(" + d + ") - 1 DAY)";
            case DAY -> "DATE(" + d + ")";
            case HOUR -> "DATE_ADD(DATE(" + d + "), INTERVAL HOUR(" + d + ") HOUR)";
        };
    }

    /**
     * Does this server know the named zone?
     *
     * CONVERT_TZ with a named zone returns NULL when MySQL's time zone tables have
     * never been loaded, which they are not on a default install. Every bucket would
     * come back null and the query would succeed, so this is asked first and answered
     * with an error rather than a page of nulls.
     */
    public static String zoneCheck(String zone) {
        return "SELECT CONVERT_TZ(" + EPOCH + ", '+00:00', '" + zone + "') IS NOT NULL";
    }

    /** Named zones used by this query, UTC excluded because it needs no conversion. */
    public static Set<String> namedZones(AggregateQuery query) {

        Set<String> zones = new java.util.LinkedHashSet<>();
        if (query.getGroupBy() == null) return zones;

        for (GroupByField g : query.getGroupBy())
            if (g.getBucket() != null && !"UTC".equals(g.resolvedTimezone())) zones.add(g.resolvedTimezone());

        return zones;
    }
}
