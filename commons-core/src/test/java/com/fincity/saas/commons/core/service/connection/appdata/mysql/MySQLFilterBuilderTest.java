package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** TEXT_SEARCH needs a resolver carrying the storage's text columns. */
    private static String textSql(String term, boolean stemming) {
        MySQLFieldResolver resolver = MySQLFieldResolver.of(java.util.Set.of())
                .withTextColumns(new java.util.LinkedHashSet<>(List.of("title", "body")))
                .withStemming(stemming);

        return DSL.using(SQLDialect.MYSQL)
                .renderInlined(MySQLFilterBuilder.build(
                        fc("title", FilterConditionOperator.TEXT_SEARCH, term), resolver))
                .replaceAll("\\s+", " ");
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

    /**
     * Two different answers, because they are two different problems. A condition
     * the caller got wrong can be corrected by the caller; a feature this backend
     * lacks cannot. They were both 501 "not supported on the MySQL storage backend
     * yet", which is a promise about the wrong one of the two - and Mongo already
     * answered 400 for the same malformed condition.
     */
    @Nested
    @DisplayName("Malformed conditions are told apart from unsupported ones")
    class MalformedVsUnsupported {

        @Test
        @DisplayName("BETWEEN without a toValue is the caller's mistake")
        void betweenWithoutToValue() {
            FilterCondition c = fc("a", FilterConditionOperator.BETWEEN, 1);

            UnsupportedFilterException e = assertThrows(UnsupportedFilterException.class, () -> sql(c));

            assertTrue(e.isMalformed(), "a missing toValue is malformed, not unsupported");
            assertTrue(e.getDetail().contains("toValue"), e.getDetail());
            assertTrue(e.getDetail().contains("'a'"), "the message should name the field: " + e.getDetail());
        }

        @Test
        @DisplayName("an empty IN list is the caller's mistake")
        void emptyInList() {
            assertTrue(assertThrows(
                            UnsupportedFilterException.class,
                            () -> sql(fc("a", FilterConditionOperator.IN, " , , ")))
                    .isMalformed());
        }

        @Test
        @DisplayName("a missing field or operator is the caller's mistake")
        void missingFieldOrOperator() {
            assertTrue(assertThrows(
                            UnsupportedFilterException.class,
                            () -> sql(fc(null, FilterConditionOperator.EQUALS, 1)))
                    .isMalformed());
        }

        /**
         * The other half, and the reason this is a flag rather than a blanket
         * change: a filter this backend genuinely cannot express is still a 501.
         */
        @Test
        @DisplayName("a filter this backend cannot express is NOT malformed")
        void genuinelyUnsupported() {
            FilterCondition c = fc("a", FilterConditionOperator.MATCH, "x");
            c.setMatchOperator(FilterConditionOperator.IN);

            assertFalse(assertThrows(UnsupportedFilterException.class, () -> sql(c)).isMalformed());
        }

        @Test
        @DisplayName("TEXT_SEARCH on a storage declaring no text fields stays unsupported")
        void textSearchStaysUnsupported() {
            assertFalse(assertThrows(
                            UnsupportedFilterException.class,
                            () -> sql(fc("a", FilterConditionOperator.TEXT_SEARCH, "x")))
                    .isMalformed());
        }
    }


    /**
     * InnoDB does not stem, Mongo does, so the same search over the same rows
     * answered differently depending on the backend. The query is stemmed and
     * sent with the truncation operator, which is the only thing that widens a
     * stem back over the forms actually stored.
     */
    @Nested
    @DisplayName("TEXT_SEARCH stemming")
    class Stemming {

        @Test
        @DisplayName("the term is stemmed and truncated, in boolean mode")
        void stemmedAndTruncated() {
            String sql = textSql("guides", true);
            assertTrue(sql.contains("IN BOOLEAN MODE"), sql);
            assertTrue(sql.contains("guid*"), sql);
        }

        /**
         * The single most dangerous detail. A leading + makes a term REQUIRED,
         * which would silently turn every multi word search into AND - where
         * both natural language mode and Mongo's $text match on ANY term.
         */
        @Test
        @DisplayName("terms stay optional, so multi-word search is still OR")
        void termsStayOptional() {
            String sql = textSql("southern regions", true);
            assertTrue(sql.contains("southern*"), sql);
            assertTrue(sql.contains("region*"), sql);
            assertFalse(sql.contains("+"), "a leading + would make every term required: " + sql);
        }

        /**
         * Boolean mode reads + - > < ( ) ~ * " @ as operators, so raw user text
         * would be a syntax error. The same class of bug as the unescaped regex
         * in STRING_LOOSE_EQUAL.
         */
        @Test
        @DisplayName("boolean operators in the search text never reach the SQL")
        void operatorsAreNeutralised() {
            String sql = textSql("C++ (best) ~thing", true);

            // The only * are the ones we appended; no stray operators survive.
            assertFalse(sql.contains("++"), sql);
            assertFalse(sql.contains("~"), sql);
            assertFalse(sql.contains("(best)"), sql);
        }

        @Test
        @DisplayName("a y-ending word keeps a usable prefix rather than stemming to i")
        void yEndingStaysAPrefix() {
            // Porter gives "quarterli", which matches nothing. The prefix must
            // be something stored text can actually start with.
            String sql = textSql("quarterly", true);
            assertTrue(sql.contains("quarterl*"), sql);
            assertFalse(sql.contains("quarterli*"), sql);
        }

        @Test
        @DisplayName("a query of only stopwords and punctuation matches nothing, rather than erroring")
        void nothingUsable() {
            // An empty AGAINST is a syntax error. No rows is what MySQL already
            // answers for a stopword, so that is what this keeps answering.
            String sql = textSql("+++ ---", true);
            assertFalse(sql.contains("AGAINST"), sql);
        }

        @Test
        @DisplayName("switched off, it is the literal natural-language search it always was")
        void offIsUnchanged() {
            String sql = textSql("guides", false);
            assertTrue(sql.contains("IN NATURAL LANGUAGE MODE"), sql);
            assertTrue(sql.contains("guides"), sql);
            assertFalse(sql.contains("guid*"), sql);
        }
    }
}
