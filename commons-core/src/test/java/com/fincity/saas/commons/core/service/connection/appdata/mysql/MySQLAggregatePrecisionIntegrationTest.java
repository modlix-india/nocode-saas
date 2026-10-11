package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AggregateFunction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SUM and AVG keep the fractions the column stores.
 *
 * They used to cast the column to Double, which jOOQ renders for MySQL as
 * {@code CAST(x AS DECIMAL)}, and MySQL reads a bare DECIMAL as DECIMAL(10,0): every
 * value was rounded to a whole number before it was added up, and anything past ten
 * digits was clamped. An ad spend of 18532.7307 came back as 18533. Rendering the SQL
 * cannot show that, because the cast looks perfectly reasonable as text; only MySQL
 * doing the arithmetic can.
 */
@Testcontainers
class MySQLAggregatePrecisionIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "aggprecision";
    private static final String TABLE = "metrics";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema(DB);

        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`");
        // spend is what a STRING + DECIMAL field becomes (MySQLTypeMapper.DEFAULT_DECIMAL);
        // rate is a NUMBER/DOUBLE field; qty an INTEGER one; meta a JSON object.
        exec("CREATE TABLE `" + DB + "`.`" + TABLE + "` ("
                + "`_id` CHAR(26) NOT NULL, `region` VARCHAR(20) NULL, `spend` DECIMAL(19,4) NULL,"
                + " `rate` DOUBLE NULL, `qty` INT NULL, `meta` JSON NULL, PRIMARY KEY (`_id`))");

        row(1, "north", "0.1234", "0.25", 1, "{\"cost\": 1.25}");
        row(2, "north", "1.5000", "0.5", 2, "{\"cost\": \"2.5\"}");
        row(3, "north", "2.2500", "0.125", 4, "{\"cost\": 0.75}");
        row(4, "south", "12345678901.5678", "1.75", 3, "{}");
        row(5, "south", null, null, null, null);
    }

    private static void row(int i, String region, String spend, String rate, Integer qty, String meta) {
        exec("INSERT INTO `" + DB + "`.`" + TABLE + "` VALUES ('" + String.format("%026d", i) + "', '" + region
                + "', " + (spend == null ? "NULL" : spend) + ", " + (rate == null ? "NULL" : rate) + ", "
                + (qty == null ? "NULL" : qty) + ", " + (meta == null ? "NULL" : "'" + meta + "'") + ")");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    private static Map<String, Map<String, Object>> byRegion(List<Aggregation> aggs) {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(aggs);
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        Flux.from(MySQLAggregateBuilder.page(
                        ctx, DSL.table(DSL.name(DB, TABLE)), q, DSL.noCondition(), null, q.getPageable(), Set.of("meta")))
                .map(r -> new LinkedHashMap<>(r.intoMap()))
                .toStream()
                .forEach(r -> out.put(String.valueOf(r.get("region")), r));
        return out;
    }

    private static void assertNumber(String expected, Object actual, String what) {
        BigDecimal got = actual == null ? null : new BigDecimal(actual.toString());
        assertEquals(0, new BigDecimal(expected).compareTo(got), what + ": expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("SUM of a DECIMAL column keeps every stored fraction digit")
    void sumDecimal() {
        Map<String, Map<String, Object>> rows = byRegion(List.of(agg(AggregateFunction.SUM, "spend", "spend")));

        assertNumber("3.8734", rows.get("north").get("spend"), "north spend");
        // Past ten digits a DECIMAL(10,0) cast clamps to 9999999999.
        assertNumber("12345678901.5678", rows.get("south").get("spend"), "south spend");
    }

    @Test
    @DisplayName("AVG of a DECIMAL column is the exact average, not one of rounded values")
    void avgDecimal() {
        Map<String, Map<String, Object>> rows = byRegion(List.of(agg(AggregateFunction.AVG, "spend", "avgSpend")));

        // (0.1234 + 1.5 + 2.25) / 3; the rounded values would give 4 / 3 = 1.3333.
        BigDecimal avg = new BigDecimal(rows.get("north").get("avgSpend").toString());
        assertEquals(0, new BigDecimal("1.29113333").compareTo(avg.setScale(8, java.math.RoundingMode.HALF_UP)),
                "north avg was " + avg);
        // The null row is skipped, as SQL and the Mongo backend both do.
        assertNumber("12345678901.5678", rows.get("south").get("avgSpend"), "south avg");
    }

    @Test
    @DisplayName("SUM and AVG of a DOUBLE column keep the fractions")
    void sumDouble() {
        Map<String, Map<String, Object>> rows = byRegion(List.of(
                agg(AggregateFunction.SUM, "rate", "rate"), agg(AggregateFunction.AVG, "rate", "avgRate")));

        assertEquals(0.875d, ((Number) rows.get("north").get("rate")).doubleValue(), 1e-9);
        assertEquals(0.875d / 3, ((Number) rows.get("north").get("avgRate")).doubleValue(), 1e-9);
    }

    @Test
    @DisplayName("SUM and AVG of an INT column are unchanged: whole sums, fractional average")
    void sumInt() {
        Map<String, Map<String, Object>> rows = byRegion(
                List.of(agg(AggregateFunction.SUM, "qty", "qty"), agg(AggregateFunction.AVG, "qty", "avgQty")));

        assertNumber("7", rows.get("north").get("qty"), "north qty");
        assertEquals(7d / 3, ((Number) rows.get("north").get("avgQty")).doubleValue(), 1e-4);
    }

    @Test
    @DisplayName("SUM of a JSON path adds numbers and numeric strings, fractions included")
    void sumJsonPath() {
        Map<String, Map<String, Object>> rows = byRegion(List.of(agg(AggregateFunction.SUM, "meta.cost", "cost")));

        assertEquals(4.5d, ((Number) rows.get("north").get("cost")).doubleValue(), 1e-9);
        // A group whose rows lack the path sums to NULL, not to a quiet zero.
        assertNull(rows.get("south").get("cost"));
    }
}
