package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.HavingCondition;

/**
 * The rules both backends apply before building anything.
 *
 * This is the security-critical step rather than a convenience: field names become
 * pipeline paths on one backend and column references on the other, and aliases
 * become output keys on both. It lives in one place precisely so the two cannot
 * drift into one being more permissive than the other.
 */
class AggregateQueryValidatorTest {

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    private static AggregateQuery valid() {
        return new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")));
    }

    @Test
    @DisplayName("a well-formed query passes")
    void ok() {
        assertNull(AggregateQueryValidator.check(valid()));
    }

    @Test
    @DisplayName("a query with no measures is refused")
    void noAggregations() {
        assertNotNull(AggregateQueryValidator.check(new AggregateQuery()));
    }

    @Test
    @DisplayName("a HavingCondition is refused rather than misread")
    void havingCondition() {
        // It carries its own aggregate function inline, which is a different shape
        // from the post-group filter this field means.
        String err = AggregateQueryValidator.check(
                valid().setHaving(HavingCondition.make("total", AggregateFunction.SUM, 1)));

        assertTrue(err.contains("HavingCondition"), err);
    }

    @Test
    @DisplayName("a plain condition is accepted as having")
    void plainHaving() {
        assertNull(AggregateQueryValidator.check(valid().setHaving(FilterCondition.make("total", 1))));
    }

    @Test
    @DisplayName("a dollar in a field name is refused")
    void dollarInField() {
        // On Mongo a field name becomes a pipeline path, where a leading $ is an
        // expression rather than a name.
        assertNotNull(AggregateQueryValidator.check(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("$where")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));

        assertNotNull(AggregateQueryValidator.check(new AggregateQuery()
                .setAggregations(List.of(agg(AggregateFunction.SUM, "a$b", "total")))));
    }

    @Test
    @DisplayName("an alias that is not an identifier is refused")
    void badAlias() {
        for (String alias : List.of("has space", "has-dash", "1leading", "has.dot", "has$dollar")) {
            String err = AggregateQueryValidator.check(new AggregateQuery()
                    .setAggregations(List.of(agg(AggregateFunction.COUNT, null, alias))));
            assertNotNull(err, "'" + alias + "' should be refused");
        }
    }

    @Test
    @DisplayName("a blank alias is not an error, it asks for the default")
    void blankAliasFallsBack() {
        // resolvedAlias() derives one from the function and field, which is the
        // documented behaviour and what every caller that omits the field relies on.
        AggregateQuery q = new AggregateQuery()
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "")));

        assertNull(AggregateQueryValidator.check(q));
        assertEquals(Set.of("count"), AggregateQueryValidator.aliases(q));
    }

    @Test
    @DisplayName("two things cannot share an alias")
    void duplicateAlias() {
        String err = AggregateQueryValidator.check(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region").setAlias("x")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "x"))));

        // One would silently overwrite the other in the returned row.
        assertTrue(err.contains("duplicate"), err);
    }

    @Test
    @DisplayName("SUM, AVG, MIN and MAX all need a field; COUNT does not")
    void fieldRequired() {
        for (AggregateFunction f : List.of(
                AggregateFunction.SUM, AggregateFunction.AVG, AggregateFunction.MIN, AggregateFunction.MAX))
            assertNotNull(
                    AggregateQueryValidator.check(new AggregateQuery().setAggregations(List.of(agg(f, null, "a")))),
                    f + " should need a field");

        assertNull(AggregateQueryValidator.check(
                new AggregateQuery().setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));
    }

    @Test
    @DisplayName("sort is only possible on an alias this query produces")
    void sortOnAlias() {
        assertNull(AggregateQueryValidator.check(valid().setSort(Sort.by(Sort.Order.desc("total")))));

        String err = AggregateQueryValidator.check(valid().setSort(Sort.by(Sort.Order.desc("amount"))));
        assertTrue(err.contains("cannot sort on"), err);
    }

    @Test
    @DisplayName("an encoding with no bucket is refused, because it means nothing")
    void encodingWithoutBucket() {
        String err = AggregateQueryValidator.check(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("ts").setEncoding(DateEncoding.EPOCH_SECONDS)))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        assertTrue(err.contains("only meaningful with a bucket"), err);
    }

    @Test
    @DisplayName("a bucket without an encoding is NOT refused here")
    void bucketWithoutEncodingIsBackendSpecific() {
        // On Mongo a date is an untyped number and the encoding is mandatory. On
        // MySQL a date column is a date and an encoding would be wrong. The shared
        // rules cannot decide that, so each backend does.
        assertNull(AggregateQueryValidator.check(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("ts").setBucket(DateBucketUnit.DAY)))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));
    }

    @Test
    @DisplayName("an unknown timezone is refused")
    void unknownZone() {
        String err = AggregateQueryValidator.check(new AggregateQuery()
                .setGroupBy(List.of(new GroupByField()
                        .setField("ts")
                        .setBucket(DateBucketUnit.DAY)
                        .setTimezone("Mars/Olympus")))
                .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

        assertTrue(err.contains("not a known IANA timezone"), err);
    }

    @Test
    @DisplayName("the aliases a query produces can be listed")
    void aliases() {
        assertEquals(Set.of("region", "total"), AggregateQueryValidator.aliases(valid()));
    }

    @Test
    @DisplayName("a default alias is derived from the function and field")
    void defaultAliases() {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("region")))
                .setAggregations(List.of(new Aggregation()
                        .setFunction(AggregateFunction.SUM)
                        .setField("amount")));

        assertNull(AggregateQueryValidator.check(q));
        assertEquals(Set.of("region", "sum_amount"), AggregateQueryValidator.aliases(q));
    }

    @Test
    @DisplayName("a null query is refused rather than throwing")
    void nullQuery() {
        assertNotNull(AggregateQueryValidator.check(null));
    }
}
