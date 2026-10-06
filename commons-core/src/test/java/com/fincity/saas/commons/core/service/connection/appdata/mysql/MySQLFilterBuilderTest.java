package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.condition.HavingCondition;

/**
 * JOOQ renders SQL without a connection, so the whole translation is assertable as
 * text. These are the rows a filter will actually select, pinned.
 */
class MySQLFilterBuilderTest {

    private static String sql(AbstractCondition c) {
        return DSL.using(SQLDialect.MYSQL).renderInlined(MySQLFilterBuilder.build(c)).replaceAll("\\s+", " ");
    }

    private static FilterCondition fc(String field, FilterConditionOperator op, Object value) {
        return new FilterCondition().setField(field).setOperator(op).setValue(value);
    }

    @Nested
    @DisplayName("comparison operators")
    class Comparisons {

        @Test
        void equals() {
            assertEquals("`region` = 'North'", sql(fc("region", FilterConditionOperator.EQUALS, "North")));
        }

        @Test
        void orderingOperators() {
            assertEquals("`n` > 5", sql(fc("n", FilterConditionOperator.GREATER_THAN, 5)));
            assertEquals("`n` >= 5", sql(fc("n", FilterConditionOperator.GREATER_THAN_EQUAL, 5)));
            assertEquals("`n` < 5", sql(fc("n", FilterConditionOperator.LESS_THAN, 5)));
            assertEquals("`n` <= 5", sql(fc("n", FilterConditionOperator.LESS_THAN_EQUAL, 5)));
        }

        @Test
        @DisplayName("a value field compares two columns, not a column and a literal")
        void valueFieldComparesColumns() {
            FilterCondition c = fc("a", FilterConditionOperator.EQUALS, "b");
            c.setValueField(true);
            assertEquals("`a` = `b`", sql(c));
        }
    }

    @Nested
    @DisplayName("null and boolean operators")
    class Predicates {

        @Test
        void isNull() {
            assertEquals("`a` is null", sql(fc("a", FilterConditionOperator.IS_NULL, null)));
        }

        @Test
        void isTrueAndIsFalse() {
            assertTrue(sql(fc("a", FilterConditionOperator.IS_TRUE, null)).contains("`a`"));
            assertTrue(sql(fc("a", FilterConditionOperator.IS_FALSE, null)).contains("`a`"));
        }

        @Test
        @DisplayName("these need no value, unlike every comparison")
        void needNoValue() {
            for (FilterConditionOperator op :
                    List.of(FilterConditionOperator.IS_NULL, FilterConditionOperator.IS_TRUE,
                            FilterConditionOperator.IS_FALSE))
                assertTrue(sql(fc("a", op, null)).length() > 0, op + " should not require a value");
        }
    }

    @Nested
    @DisplayName("IN")
    class In {

        @Test
        void explicitMultiValueWins() {
            FilterCondition c = fc("s", FilterConditionOperator.IN, null);
            c.setMultiValue(List.of("A", "B"));
            assertEquals("`s` in ('A', 'B')", sql(c));
        }

        @Test
        @DisplayName("a comma separated single value is split, as the rest of the platform allows")
        void commaSeparatedValueIsSplit() {
            assertEquals("`s` in ('A', 'B', 'C')", sql(fc("s", FilterConditionOperator.IN, "A,B,C")));
        }

        @Test
        void whitespaceAroundItemsIsTrimmed() {
            assertEquals("`s` in ('A', 'B')", sql(fc("s", FilterConditionOperator.IN, " A , B ")));
        }

        @Test
        @DisplayName("an empty IN throws rather than matching nothing or everything")
        void emptyInIsRejected() {
            assertThrows(UnsupportedFilterException.class, () -> sql(fc("s", FilterConditionOperator.IN, " , ")));
            assertThrows(UnsupportedFilterException.class, () -> sql(fc("s", FilterConditionOperator.IN, null)));
        }
    }

    @Nested
    @DisplayName("string and range operators")
    class StringsAndRanges {

        @Test
        void likePassesThePatternThrough() {
            assertEquals("`a` like 'x%'", sql(fc("a", FilterConditionOperator.LIKE, "x%")));
        }

        @Test
        @DisplayName("loose equal wraps the value in wildcards, matching the Mongo backend")
        void looseEqualWrapsInWildcards() {
            assertEquals("`a` like '%x%'", sql(fc("a", FilterConditionOperator.STRING_LOOSE_EQUAL, "x")));
        }

        @Test
        void between() {
            FilterCondition c = fc("n", FilterConditionOperator.BETWEEN, 1);
            c.setToValue(10);
            assertEquals("`n` between 1 and 10", sql(c));
        }

        @Test
        @DisplayName("a half-specified BETWEEN throws instead of silently becoming open-ended")
        void betweenNeedsBothEnds() {
            assertThrows(UnsupportedFilterException.class, () -> sql(fc("n", FilterConditionOperator.BETWEEN, 1)));
        }
    }

    @Nested
    @DisplayName("negation")
    class Negation {

        @Test
        void negatesASingleCondition() {
            FilterCondition c = fc("a", FilterConditionOperator.EQUALS, 1);
            c.setNegate(true);
            assertEquals("not (`a` = 1)", sql(c));
        }

        @Test
        @DisplayName("a negated AND group becomes an OR of negated children, as on Mongo")
        void deMorganOnAnd() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of(fc("a", FilterConditionOperator.EQUALS, 1),
                            fc("b", FilterConditionOperator.EQUALS, 2)));
            cc.setNegate(true);
            assertEquals("(not (`a` = 1) or not (`b` = 2))", sql(cc));
        }

        @Test
        @DisplayName("a negated OR group becomes an AND of negated children")
        void deMorganOnOr() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.OR)
                    .setConditions(List.of(fc("a", FilterConditionOperator.EQUALS, 1),
                            fc("b", FilterConditionOperator.EQUALS, 2)));
            cc.setNegate(true);
            assertEquals("(not (`a` = 1) and not (`b` = 2))", sql(cc));
        }
    }

    @Nested
    @DisplayName("grouping")
    class Grouping {

        @Test
        void andGroup() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of(fc("a", FilterConditionOperator.EQUALS, 1),
                            fc("b", FilterConditionOperator.EQUALS, 2)));
            assertEquals("(`a` = 1 and `b` = 2)", sql(cc));
        }

        @Test
        void orGroup() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.OR)
                    .setConditions(List.of(fc("a", FilterConditionOperator.EQUALS, 1),
                            fc("b", FilterConditionOperator.EQUALS, 2)));
            assertEquals("(`a` = 1 or `b` = 2)", sql(cc));
        }

        @Test
        @DisplayName("groups nest")
        void nestedGroups() {
            ComplexCondition inner = (ComplexCondition) new ComplexCondition()
                    .setOperator(ComplexConditionOperator.OR)
                    .setConditions(List.of(fc("b", FilterConditionOperator.EQUALS, 2),
                            fc("c", FilterConditionOperator.EQUALS, 3)));
            ComplexCondition outer = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of(fc("a", FilterConditionOperator.EQUALS, 1), inner));
            assertEquals("(`a` = 1 and (`b` = 2 or `c` = 3))", sql(outer));
        }

        @Test
        @DisplayName("an empty group matches everything, which is what no filter means")
        void emptyGroupIsNoCondition() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of());
            assertEquals("true", sql(cc));
        }

        @Test
        void nullConditionMatchesEverything() {
            assertEquals("true", sql(null));
        }
    }

    @Nested
    @DisplayName("what this backend refuses, loudly")
    class Unsupported {

        // The JOOQ DAO elsewhere returns noCondition() for these, which turns an
        // unsupported filter into "every row". For app data that is not acceptable:
        // a deleteByFilter with a dropped clause would empty the table.

        @Test
        void textSearchThrows() {
            // Still refused here, but for a different reason than it used to be.
            // The backend can do full-text search now; what it cannot do is guess
            // which columns to search when the storage declares no textIndexFields.
            UnsupportedFilterException e = assertThrows(
                    UnsupportedFilterException.class,
                    () -> sql(fc("a", FilterConditionOperator.TEXT_SEARCH, "x")));
            assertTrue(e.getDetail().contains("textIndexFields"), e.getDetail());
        }

        @Test
        @DisplayName("MATCH still refuses a match operator it has no SQL for")
        void unsupportedMatchOperatorThrows() {
            // MATCH and MATCH_ALL work now, but not with every match operator: IN
            // inside an array has no containment or per-element form here, and
            // answering it wrongly is worse than refusing it.
            FilterCondition c = fc("a", FilterConditionOperator.MATCH, "x");
            c.setMatchOperator(FilterConditionOperator.IN);

            UnsupportedFilterException e = assertThrows(UnsupportedFilterException.class, () -> sql(c));
            assertTrue(e.getDetail().contains("IN"), e.getDetail());
        }

        @Test
        @DisplayName("MATCH_ALL with nothing to match is refused rather than matching everything")
        void matchAllNeedsValues() {
            assertThrows(
                    UnsupportedFilterException.class, () -> sql(fc("a", FilterConditionOperator.MATCH_ALL, null)));
        }

        @Test
        @DisplayName("a HavingCondition is rejected here too, not cast blindly")
        void havingConditionThrows() {
            assertThrows(UnsupportedFilterException.class, () -> sql(new HavingCondition()));
        }

        @Test
        void missingFieldThrows() {
            assertThrows(
                    UnsupportedFilterException.class, () -> sql(fc(null, FilterConditionOperator.EQUALS, 1)));
            assertThrows(UnsupportedFilterException.class, () -> sql(fc("  ", FilterConditionOperator.EQUALS, 1)));
        }

        @Test
        void missingOperatorThrows() {
            FilterCondition c = new FilterCondition().setField("a");
            c.setOperator(null);
            assertThrows(UnsupportedFilterException.class, () -> sql(c));
        }

        @Test
        void comparisonWithoutAValueThrows() {
            assertThrows(
                    UnsupportedFilterException.class, () -> sql(fc("a", FilterConditionOperator.EQUALS, null)));
        }
    }
}
