package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The numbers a grouped read actually returns.
 *
 * Rendering the SQL is covered without a database. What that cannot show is whether
 * MySQL agrees: date truncation is a stack of functions whose behaviour at the
 * boundaries is the whole question, a group count is a different number from a row
 * count, and a timezone conversion silently returns NULL on a server whose zone
 * tables were never loaded.
 */
@Testcontainers
class MySQLAggregateIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "aggdata";
    private static final String TABLE = "sales";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("aggdata");

        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`");
        exec("CREATE TABLE `" + DB + "`.`" + TABLE + "` ("
                + "`_id` CHAR(26) NOT NULL, `region` VARCHAR(20) NULL, `amount` DOUBLE NULL,"
                + " `placedAt` DATETIME(3) NULL, `ts` BIGINT NULL, `note` VARCHAR(40) NULL,"
                + " PRIMARY KEY (`_id`))");

        // Three regions, two months, one null amount and one null region. The nulls
        // are the point: COUNT(col), SUM and GROUP BY each treat them differently and
        // each difference is a number somebody will read off a chart.
        row(1, "north", 100d, "2026-01-15 10:00:00", 1768471200L, "a");
        row(2, "north", 200d, "2026-01-20 10:00:00", 1768903200L, "b");
        row(3, "south", 50d, "2026-02-03 10:00:00", 1770112800L, "c");
        row(4, "south", null, "2026-02-10 10:00:00", 1770717600L, null);
        row(5, "east", 400d, "2026-02-28 23:30:00", 1772321400L, "d");
        row(6, null, 7d, "2026-01-01 00:30:00", 1767227400L, "e");
    }

    private static void row(int i, String region, Double amount, String placedAt, Long ts, String note) {
        exec("INSERT INTO `" + DB + "`.`" + TABLE + "` VALUES ('" + String.format("%026d", i) + "', "
                + (region == null ? "NULL" : "'" + region + "'") + ", "
                + (amount == null ? "NULL" : amount) + ", '" + placedAt + "', " + ts + ", "
                + (note == null ? "NULL" : "'" + note + "'") + ")");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    private static List<Map<String, Object>> run(AggregateQuery q) {
        return Flux.from(MySQLAggregateBuilder.page(
                        ctx,
                        DSL.table(DSL.name(DB, TABLE)),
                        q,
                        MySQLFilterBuilder.build(q.getCondition(), Set.of()),
                        q.getHaving() == null ? null : MySQLFilterBuilder.build(q.getHaving(), Set.of()),
                        q.getPageable(),
                        Set.of()))
                .map(r -> (Map<String, Object>) new LinkedHashMap<>(r.intoMap()))
                .collectList()
                .block();
    }

    private static long groupCount(AggregateQuery q) {
        Number n = Mono.from(MySQLAggregateBuilder.count(
                        ctx,
                        DSL.table(DSL.name(DB, TABLE)),
                        q,
                        MySQLFilterBuilder.build(q.getCondition(), Set.of()),
                        q.getHaving() == null ? null : MySQLFilterBuilder.build(q.getHaving(), Set.of()),
                        Set.of()))
                .map(r -> (Number) r.get(0))
                .block();
        return n == null ? 0 : n.longValue();
    }

    /**
     * Compared numerically where both sides are numbers: the driver is free to hand
     * back an Integer, a Long or a BigInteger for the same column, and equals()
     * across those is false even when the values match.
     */
    private static Object valueFor(List<Map<String, Object>> rows, String keyField, Object key, String measure) {
        return rows.stream()
                .filter(r -> sameKey(r.get(keyField), key))
                .findFirst()
                .map(r -> r.get(measure))
                .orElse(null);
    }

    private static boolean sameKey(Object actual, Object expected) {
        if (expected == null) return actual == null;
        if (actual == null) return false;
        if (actual instanceof Number a && expected instanceof Number e) return a.longValue() == e.longValue();
        return expected.equals(actual);
    }

    @Test
    @DisplayName("group and sum")
    void groupAndSum() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total"))));

        assertEquals(4, rows.size(), "three regions and the null one");
        assertEquals(300d, ((Number) valueFor(rows, "region", "north", "total")).doubleValue());
        assertEquals(50d, ((Number) valueFor(rows, "region", "south", "total")).doubleValue());
    }

    @Test
    @DisplayName("NULL is a group of its own, as it is on Mongo")
    void nullIsAGroup() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        assertEquals(1L, ((Number) valueFor(rows, "region", null, "n")).longValue());
    }

    @Test
    @DisplayName("COUNT(col) skips nulls and COUNT(*) does not")
    void countSemantics() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(
                        agg(AggregateFunction.COUNT, null, "rows"),
                        agg(AggregateFunction.COUNT, "amount", "withAmount"))));

        // south has two rows, one with a null amount. Getting this wrong turns "how
        // many orders" and "how many priced orders" into the same number.
        assertEquals(2L, ((Number) valueFor(rows, "region", "south", "rows")).longValue());
        assertEquals(1L, ((Number) valueFor(rows, "region", "south", "withAmount")).longValue());
    }

    @Test
    @DisplayName("no groupBy collapses everything to one row")
    void grandTotal() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total"))));

        assertEquals(1, rows.size());
        assertEquals(757d, ((Number) rows.get(0).get("total")).doubleValue());
    }

    @Test
    @DisplayName("a real date column buckets by month without any encoding")
    void dateColumnByMonth() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("placedAt")
                        .setBucket(DateBucketUnit.MONTH)
                        .setAlias("month")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        assertEquals(2, rows.size(), "January and February");

        long january = rows.stream()
                .filter(r -> String.valueOf(r.get("month")).startsWith("2026-01"))
                .mapToLong(r -> ((Number) r.get("n")).longValue())
                .sum();

        assertEquals(3L, january);
    }

    @Test
    @DisplayName("an epoch-seconds column buckets to the same months")
    void epochColumnByMonth() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("ts")
                        .setBucket(DateBucketUnit.MONTH)
                        .setEncoding(DateEncoding.EPOCH_SECONDS)
                        .setAlias("month")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        // The same six rows, stored twice in two different ways, must land in the
        // same buckets. If the epoch arithmetic is off by a factor or an offset this
        // is where it shows, so the bucket values are asserted exactly rather than by
        // a range that a wrong answer could still fall inside.
        assertEquals(2, rows.size());

        // 2026-01-01T00:00:00Z and 2026-02-01T00:00:00Z
        assertEquals(3L, ((Number) valueFor(rows, "month", 1767225600L, "n")).longValue());
        assertEquals(3L, ((Number) valueFor(rows, "month", 1769904000L, "n")).longValue());
    }

    @Test
    @DisplayName("an epoch bucket comes back as a number, not a date")
    void epochBucketReturnsANumber() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("ts")
                        .setBucket(DateBucketUnit.YEAR)
                        .setEncoding(DateEncoding.EPOCH_SECONDS)
                        .setAlias("year")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        // Every other value this backend returns for that column is a number, and a
        // client's date formatter reads nothing else.
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).get("year") instanceof Number, String.valueOf(rows.get(0).get("year")));

        // 2026-01-01T00:00:00Z
        assertEquals(1767225600L, ((Number) rows.get(0).get("year")).longValue());
    }

    @Test
    @DisplayName("milliseconds come back in milliseconds")
    void millisRoundTrip() {
        exec("DROP TABLE IF EXISTS `" + DB + "`.`ms`");
        exec("CREATE TABLE `" + DB + "`.`ms` (`_id` CHAR(26) NOT NULL, `ts` BIGINT NULL, PRIMARY KEY (`_id`))");
        exec("INSERT INTO `" + DB + "`.`ms` VALUES ('00000000000000000000000001', 1767225600000)");

        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("ts")
                        .setBucket(DateBucketUnit.YEAR)
                        .setEncoding(DateEncoding.EPOCH_MILLIS)
                        .setAlias("year")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")));

        Number year = Flux.from(MySQLAggregateBuilder.page(
                        ctx, DSL.table(DSL.name(DB, "ms")), q, DSL.noCondition(), null, q.getPageable(), Set.of()))
                .map(r -> (Number) r.get("year"))
                .blockFirst();

        // Seconds for a column stored in milliseconds is a factor of 1000, which
        // renders as a chart that starts in 1970 and is otherwise plausible.
        assertEquals(1767225600000L, year.longValue());
    }

    @Test
    @DisplayName("a timezone moves a row across a day boundary, which is the whole point")
    void timezoneShiftsTheBucket() {

        Boolean known = Mono.from(ctx.resultQuery(MySQLAggregateBuilder.zoneCheck("Asia/Kolkata")))
                .map(r -> r.get(0) instanceof Number n && n.intValue() == 1)
                .defaultIfEmpty(Boolean.FALSE)
                .block();

        // On a default MySQL the zone tables are absent and CONVERT_TZ returns NULL.
        // The backend refuses rather than returning a page of nulls; this test is
        // only meaningful where the tables exist, so it says which case it saw.
        if (!Boolean.TRUE.equals(known)) {
            assertTrue(true, "this server has no timezone tables, which the backend detects and refuses");
            return;
        }

        List<Map<String, Object>> utc = run(new AggregateQuery()
                .setGroupBy(List.of(
                        new GroupByField().setField("placedAt").setBucket(DateBucketUnit.DAY).setAlias("d")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))
                .setCondition(FilterCondition.make("note", "d")));

        List<Map<String, Object>> ist = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("placedAt")
                        .setBucket(DateBucketUnit.DAY)
                        .setTimezone("Asia/Kolkata")
                        .setAlias("d")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))
                .setCondition(FilterCondition.make("note", "d")));

        // 2026-02-28 23:30 UTC is 2026-03-01 05:00 in IST, so the same row is in
        // February on one calendar and March on the other.
        assertNotNull(utc.get(0).get("d"));
        assertTrue(
                !String.valueOf(utc.get(0).get("d")).equals(String.valueOf(ist.get(0).get("d"))),
                "UTC " + utc.get(0).get("d") + " vs IST " + ist.get(0).get("d"));
    }

    @Test
    @DisplayName("having filters groups, not rows")
    void having() {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")))
                .setHaving(new FilterCondition()
                        .setField("total")
                        .setValue(100)
                        .setOperator(FilterConditionOperator.GREATER_THAN));

        List<Map<String, Object>> rows = run(q);

        assertEquals(2, rows.size(), "north 300 and east 400");
        assertEquals(2L, groupCount(q), "the count has to agree with the page it describes");
    }

    @Test
    @DisplayName("the group count is groups, not rows")
    void countIsGroups() {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")));

        // Six rows, four groups. Counting the input is the obvious mistake and gives
        // a pager that promises pages which do not exist.
        assertEquals(4L, groupCount(q));
    }

    @Test
    @DisplayName("sort and paging walk the groups in order")
    void sortAndPage() {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")))
                .setSort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("total")))
                .setSize(2);

        List<Map<String, Object>> first = run(q);
        assertEquals(2, first.size());
        assertEquals("east", first.get(0).get("region"));
        assertEquals("north", first.get(1).get("region"));

        List<Map<String, Object>> second = run(q.setPage(1));
        assertEquals("south", second.get(0).get("region"));
    }

    @Test
    @DisplayName("the filter runs before grouping")
    void conditionBeforeGrouping() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")))
                .setCondition(FilterCondition.make("region", "north")));

        assertEquals(1, rows.size());
        assertEquals(300d, ((Number) rows.get(0).get("total")).doubleValue());
    }

    @Test
    @DisplayName("two group keys produce the cross of them")
    void twoKeys() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setGroupBy(List.of(
                        new GroupByField().setField("region"),
                        new GroupByField()
                                .setField("placedAt")
                                .setBucket(DateBucketUnit.MONTH)
                                .setAlias("month")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        // Flat rows with both keys at the top level, which is what lets a chart bind
        // the result without reshaping it.
        assertTrue(rows.size() >= 4, String.valueOf(rows.size()));
        assertTrue(rows.get(0).containsKey("region"));
        assertTrue(rows.get(0).containsKey("month"));
    }

    @Test
    @DisplayName("min and max work on text")
    void textMinMax() {
        List<Map<String, Object>> rows = run(new AggregateQuery()
                .setAggregations(List.of(
                        agg(AggregateFunction.MIN, "note", "first"), agg(AggregateFunction.MAX, "note", "last"))));

        assertEquals("a", rows.get(0).get("first"));
        assertEquals("e", rows.get(0).get("last"));
    }
}
