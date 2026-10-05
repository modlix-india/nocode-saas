package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;

/**
 * The SQL a grouped read turns into.
 *
 * Asserted as text because the date arithmetic behind a bucketed group key is a nest
 * of nested functions that is very easy to get subtly wrong and nearly impossible to
 * notice afterwards: a chart renders either way, and the numbers are simply in the
 * wrong buckets.
 */
class MySQLAggregateBuilderTest {

    private static final DSLContext CTX = DSL.using(SQLDialect.MYSQL);

    private static final org.jooq.Table<?> TABLE = DSL.table(DSL.name("db", "orders"));

    private static AggregateQuery query(List<GroupByField> groupBy, List<Aggregation> aggs) {
        return new AggregateQuery().setGroupBy(groupBy).setAggregations(aggs);
    }

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    private static String sql(AggregateQuery q) {
        return MySQLAggregateBuilder.grouped(CTX, TABLE, q, DSL.noCondition(), Set.of())
                .getSQL(org.jooq.conf.ParamType.INLINED);
    }

    @Nested
    @DisplayName("measures")
    class Measures {

        @Test
        @DisplayName("COUNT with no field counts rows")
        void countRows() {
            assertTrue(sql(query(null, List.of(agg(AggregateFunction.COUNT, null, "n")))).contains("count(*)"));
        }

        @Test
        @DisplayName("COUNT with a field skips nulls, which is the SQL contract")
        void countColumn() {
            // $sum: 1 would not, which is why the Mongo backend goes out of its way to
            // reproduce this. Here it is the default and the test is what keeps it so.
            String s = sql(query(null, List.of(agg(AggregateFunction.COUNT, "amount", "n"))));
            assertTrue(s.contains("count(`amount`)"), s);
        }

        @Test
        @DisplayName("SUM and AVG cast, because the column may be wider than the measure")
        void sumCasts() {
            String s = sql(query(null, List.of(agg(AggregateFunction.SUM, "amount", "total"))));
            assertTrue(s.toLowerCase().contains("sum("), s);
            assertTrue(s.contains("total"), s);
        }

        @Test
        @DisplayName("MIN and MAX do not cast, so they work on text too")
        void minMax() {
            String s = sql(query(null, List.of(agg(AggregateFunction.MIN, "code", "lowest"))));
            assertTrue(s.contains("min(`code`)"), s);
        }

        @Test
        @DisplayName("every measure is aliased")
        void aliased() {
            String s = sql(query(
                    null,
                    List.of(agg(AggregateFunction.SUM, "amount", "total"), agg(AggregateFunction.COUNT, null, "n"))));
            assertTrue(s.contains("`total`"), s);
            assertTrue(s.contains("`n`"), s);
        }
    }

    @Nested
    @DisplayName("group keys")
    class GroupKeys {

        @Test
        @DisplayName("an unbucketed key is the column itself")
        void plain() {
            String s = sql(query(
                    List.of(new GroupByField().setField("region")),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            assertTrue(s.contains("group by `region`"), s);
            assertTrue(s.contains("`region`"), s);
        }

        @Test
        @DisplayName("grouping is by the expression, not the alias")
        void groupsByExpression() {
            // MySQL would accept the alias. Only MySQL does, and the expression is
            // what the next backend will need.
            String s = sql(query(
                    List.of(new GroupByField().setField("region").setAlias("r")),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            assertTrue(s.contains("group by `region`"), s);
            assertFalse(s.contains("group by `r`"), s);
        }

        @Test
        @DisplayName("a date column buckets without an encoding")
        void dateColumnBucket() {
            String s = sql(query(
                    List.of(new GroupByField().setField("createdAt").setBucket(DateBucketUnit.MONTH)),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // No epoch arithmetic at all: the column is already a date, which is the
            // single biggest reason to be on a relational backend.
            assertFalse(s.contains("1970-01-01"), s);
            assertTrue(s.contains("DAYOFMONTH"), s);
        }

        @Test
        @DisplayName("an epoch-seconds column is lifted to a datetime first")
        void epochSecondsBucket() {
            String s = sql(query(
                    List.of(new GroupByField()
                            .setField("ts")
                            .setBucket(DateBucketUnit.DAY)
                            .setEncoding(DateEncoding.EPOCH_SECONDS)),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            assertTrue(s.contains("1970-01-01"), s);
            assertTrue(s.contains("* 1000 / 1000"), s);
            // And converted back, so the caller gets the same kind of value it stored.
            assertTrue(s.contains("TIMESTAMPDIFF(SECOND"), s);
        }

        @Test
        @DisplayName("an epoch-millis column comes back in milliseconds")
        void epochMillisBucket() {
            String s = sql(query(
                    List.of(new GroupByField()
                            .setField("ts")
                            .setBucket(DateBucketUnit.DAY)
                            .setEncoding(DateEncoding.EPOCH_MILLIS)),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // Returning seconds for a column stored in milliseconds is a factor of
            // 1000, which renders as a chart starting in 1970.
            assertTrue(s.contains(") * 1000"), s);
        }

        @Test
        @DisplayName("UTC needs no timezone conversion at all")
        void utcIsPlain() {
            String s = sql(query(
                    List.of(new GroupByField().setField("createdAt").setBucket(DateBucketUnit.DAY)),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // CONVERT_TZ needs MySQL's time zone tables, which a default install does
            // not have. Not reaching for it unless asked keeps the common case working
            // on an unprepared server.
            assertFalse(s.contains("CONVERT_TZ"), s);
        }

        @Test
        @DisplayName("a named zone converts there and back")
        void namedZone() {
            String s = sql(query(
                    List.of(new GroupByField()
                            .setField("createdAt")
                            .setBucket(DateBucketUnit.DAY)
                            .setTimezone("Asia/Kolkata")),
                    List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // There to truncate on the user's calendar, back so the stored frame is
            // what comes out. An order at 03:00 IST is the previous day in UTC, and a
            // month total computed in the wrong one is simply wrong with no error.
            assertTrue(s.contains("'+00:00', 'Asia/Kolkata'"), s);
            assertTrue(s.contains("'Asia/Kolkata', '+00:00'"), s);
        }

        @Test
        @DisplayName("truncation yields a datetime, never a formatted string")
        void truncationIsNotAString() {
            for (DateBucketUnit unit : DateBucketUnit.values()) {
                String t = MySQLAggregateBuilder.truncate(unit, "d");
                // A string key sorts lexically, and "2026-10-01" before "2026-9-01" is
                // the kind of wrong that looks right nine months out of twelve.
                assertFalse(t.contains("DATE_FORMAT"), unit + " -> " + t);
            }
        }

        @Test
        @DisplayName("a week starts on Sunday, the same day Mongo starts it")
        void weekStart() {
            // Two backends disagreeing about which day a week begins on puts the same
            // order in different buckets depending on where it happens to be stored.
            assertTrue(MySQLAggregateBuilder.truncate(DateBucketUnit.WEEK, "d").contains("DAYOFWEEK"));
        }

        @Test
        @DisplayName("a JSON path can be a group key")
        void jsonPathKey() {
            String s = MySQLAggregateBuilder.grouped(
                            CTX,
                            TABLE,
                            query(
                                    List.of(new GroupByField().setField("address.city").setAlias("city")),
                                    List.of(agg(AggregateFunction.COUNT, null, "n"))),
                            DSL.noCondition(),
                            Set.of("address"))
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            assertTrue(s.contains("json_extract"), s);
        }
    }

    @Nested
    @DisplayName("the rest of the query")
    class Shape {

        @Test
        @DisplayName("no groupBy collapses everything into one row")
        void noGroupBy() {
            String s = sql(query(null, List.of(agg(AggregateFunction.SUM, "amount", "total"))));
            assertFalse(s.contains("group by"), s);
        }

        @Test
        @DisplayName("having filters on aliases, after grouping")
        void having() {
            AggregateQuery q = query(
                            List.of(new GroupByField().setField("region")),
                            List.of(agg(AggregateFunction.SUM, "amount", "total")))
                    .setHaving(FilterCondition.make("total", 100));

            String s = MySQLAggregateBuilder.page(
                            CTX,
                            TABLE,
                            q,
                            DSL.noCondition(),
                            MySQLFilterBuilder.build(q.getHaving(), Set.of()),
                            q.getPageable(),
                            Set.of())
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            assertTrue(s.contains("having"), s);
            assertTrue(s.contains("`total`"), s);
        }

        @Test
        @DisplayName("sort names the alias rather than rebuilding the expression")
        void sortByAlias() {
            AggregateQuery q = query(
                            List.of(new GroupByField().setField("region")),
                            List.of(agg(AggregateFunction.SUM, "amount", "total")))
                    .setSort(Sort.by(Sort.Order.desc("total")));

            String s = MySQLAggregateBuilder.page(
                            CTX, TABLE, q, DSL.noCondition(), null, q.getPageable(), Set.of())
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            assertTrue(s.contains("order by `total` desc"), s);
        }

        @Test
        @DisplayName("counting groups wraps the query rather than counting rows")
        void countWraps() {
            AggregateQuery q = query(
                    List.of(new GroupByField().setField("region")),
                    List.of(agg(AggregateFunction.COUNT, null, "n")));

            String s = MySQLAggregateBuilder.count(CTX, TABLE, q, DSL.noCondition(), null, Set.of())
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            // Counting the input would answer how many rows there are, which is not
            // the question. The caller is paging through groups.
            assertTrue(s.contains("grouped"), s);
            assertTrue(s.contains("group by"), s);
        }

        @Test
        @DisplayName("the count keeps the having, because a filtered-out group is not a group")
        void countKeepsHaving() {
            AggregateQuery q = query(
                            List.of(new GroupByField().setField("region")),
                            List.of(agg(AggregateFunction.SUM, "amount", "total")))
                    .setHaving(FilterCondition.make("total", 100));

            String s = MySQLAggregateBuilder.count(
                            CTX, TABLE, q, DSL.noCondition(), MySQLFilterBuilder.build(q.getHaving(), Set.of()),
                            Set.of())
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            assertTrue(s.contains("having"), s);
        }

        @Test
        @DisplayName("paging is applied, bounded by the query's own ceiling")
        void paging() {
            AggregateQuery q = query(
                            List.of(new GroupByField().setField("region")),
                            List.of(agg(AggregateFunction.COUNT, null, "n")))
                    .setSize(5_000)
                    .setPage(2);

            String s = MySQLAggregateBuilder.page(
                            CTX, TABLE, q, DSL.noCondition(), null, q.getPageable(), Set.of())
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            assertTrue(s.contains("limit " + AggregateQuery.MAX_SIZE), s);
        }

        @Test
        @DisplayName("named zones are reported so they can be checked before use")
        void namedZonesListed() {
            AggregateQuery q = query(
                    List.of(
                            new GroupByField()
                                    .setField("a")
                                    .setBucket(DateBucketUnit.DAY)
                                    .setTimezone("Asia/Kolkata"),
                            new GroupByField().setField("b").setBucket(DateBucketUnit.DAY)),
                    List.of(agg(AggregateFunction.COUNT, null, "n")));

            assertEquals(Set.of("Asia/Kolkata"), MySQLAggregateBuilder.namedZones(q));
        }
    }
}
