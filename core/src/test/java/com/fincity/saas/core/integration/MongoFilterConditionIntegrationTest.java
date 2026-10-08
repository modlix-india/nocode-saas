package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Filter building on the Mongo backend, against rows.
 *
 * Four of these were live bugs, each one silent in its own way: BETWEEN matched
 * EVERY row and a negated BETWEEN matched none; a comma separated IN dropped its
 * last value, so a one-value list matched nothing at all; the same IN on {@code _id}
 * threw and came back a 500; and STRING_LOOSE_EQUAL put the caller's text straight
 * into a regex, so searching a phone number for "+91" failed the whole read.
 *
 * They are asserted on returned ROWS rather than on the generated BSON. Every one
 * of them produced a perfectly well-formed filter - that was the problem, and a
 * test comparing documents would have agreed with the bug.
 */
@DisplayName("Mongo filter conditions")
class MongoFilterConditionIntegrationTest extends AbstractIntegrationTest {

    private static final String STORAGE_NAME = "filterlab";
    private static final String APP = APP_CODE;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        this.givenStorage();

        // The base class drops the DEFINITION collections between tests; app data
        // lives in its own database and survives, so without this the five rows are
        // seeded again on top of the last test's five.
        this.asClient(this.appDataService
                .clearAllRows(APP, SYSTEM, STORAGE_NAME, Boolean.FALSE)
                .onErrorResume(e -> Mono.just(0L)));

        this.write("alpha", 5, "+91 98000 11111");
        this.write("bravo", 15, "+91 98000 22222");
        this.write("charlie", 25, "(080) 4000-1234");
        this.write("delta", 35, "50% off line");
        this.write("echo", 45, "plain12345");
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorage() {

        Map<String, Object> properties = new HashMap<>();
        properties.put("name", new HashMap<>(Map.of("type", "STRING", "maxLength", 60)));
        properties.put("amount", new HashMap<>(Map.of("type", "INTEGER")));
        properties.put("phone", new HashMap<>(Map.of("type", "STRING", "maxLength", 40)));

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP).setClientCode(SYSTEM).setVersion(1);
        storage.setUniqueName("testapp_filterlab");
        storage.setIsAudited(Boolean.FALSE);
        storage.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", properties)));
        this.insertRaw(storage);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private String write(String name, int amount, String phone) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("amount", amount);
        row.put("phone", phone);

        Map<String, Object> created = this.asClient(
                this.appDataService.create(APP, SYSTEM, STORAGE_NAME, new DataObject().setData(row), false, null));

        return created == null ? null : String.valueOf(created.get("_id"));
    }

    /** The names a condition selects, sorted, so an assertion reads as a set. */
    private List<String> names(AbstractCondition condition) {
        Page<Map<String, Object>> page = this.asClient(this.appDataService.readPage(
                APP, SYSTEM, STORAGE_NAME, new Query().setCondition(condition).setSize(50)));

        return page.getContent().stream()
                .map(r -> String.valueOf(r.get("name")))
                .sorted()
                .toList();
    }

    private static FilterCondition fc(String field, FilterConditionOperator op, Object value) {
        return (FilterCondition) new FilterCondition().setField(field).setOperator(op).setValue(value);
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("BETWEEN")
    class Between {

        private FilterCondition between(Object from, Object to) {
            return (FilterCondition) fc("amount", FilterConditionOperator.BETWEEN, from).setToValue(to);
        }

        @Test
        @Timeout(300)
        @DisplayName("selects the rows inside the range")
        void inRange() {
            // It used to AND and OR the wrong way round: "x >= 10 or x <= 30" is
            // true for every row, so BETWEEN was a filter that filtered nothing.
            assertEquals(List.of("bravo", "charlie"), names(between(10, 30)));
        }

        @Test
        @Timeout(300)
        @DisplayName("negated, selects the rows outside it")
        void negated() {
            // And the complement was "x < 10 and x > 30", true for nothing.
            FilterCondition c = between(10, 30);
            c.setNegate(true);
            assertEquals(List.of("alpha", "delta", "echo"), names(c));
        }

        @Test
        @Timeout(300)
        @DisplayName("the bounds are inclusive at both ends")
        void inclusive() {
            assertEquals(List.of("alpha", "bravo"), names(between(5, 15)));
        }

        @Test
        @Timeout(300)
        @DisplayName("a missing toValue is refused, not read as an open range")
        void missingToValue() {
            // lte(field, null) matches nothing, so this silently became ">= 10" and
            // returned four rows the caller never asked for.
            assertThrows(Exception.class, () -> names(between(10, null)));
        }
    }

    @Nested
    @DisplayName("IN with a comma separated value")
    class InList {

        private FilterCondition in(String value) {
            return fc("name", FilterConditionOperator.IN, value);
        }

        @Test
        @Timeout(300)
        @DisplayName("keeps the value after the last comma")
        void keepsTheTail() {
            // The loop only added on finding a separator, so the tail was dropped.
            assertEquals(List.of("alpha", "bravo", "charlie"), names(in("alpha,bravo,charlie")));
        }

        @Test
        @Timeout(300)
        @DisplayName("a single value with no comma is a list of one, not an empty list")
        void singleValue() {
            // This was the sharpest edge: no comma meant nothing was ever added, so
            // the filter became $in: [] and matched NOTHING, with no error.
            assertEquals(List.of("alpha"), names(in("alpha")));
        }

        @Test
        @Timeout(300)
        @DisplayName("blanks and padding are ignored")
        void trimsAndSkipsBlanks() {
            assertEquals(List.of("alpha", "bravo"), names(in(" alpha , ,bravo ")));
        }

        /**
         * Refused, not answered. $in: [] matches no rows, which hides a caller's
         * mistake; NEGATED it becomes $nin: [], which matches EVERY row, so a
         * malformed filter silently returned the whole table. MySQL already refused
         * this, and a filter that quietly stops filtering is the one failure this
         * backend must not have.
         */
        @Test
        @Timeout(300)
        @DisplayName("a value that comes to nothing is refused, not read as 'no rows'")
        void emptyListRefused() {
            assertThrows(Exception.class, () -> names(in(" , , ")));
            assertThrows(Exception.class, () -> names(in("")));
        }

        @Test
        @Timeout(300)
        @DisplayName("and refused when negated too, rather than matching everything")
        void emptyListNegatedRefused() {
            FilterCondition c = in(" , , ");
            c.setNegate(true);
            assertThrows(Exception.class, () -> names(c));
        }

        @Test
        @Timeout(300)
        @DisplayName("an explicit multiValue is unaffected")
        void multiValueStillWorks() {
            FilterCondition c = (FilterCondition) new FilterCondition()
                    .setField("name")
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(new ArrayList<>(List.of("alpha", "bravo")));
            assertEquals(List.of("alpha", "bravo"), names(c));
        }

        /**
         * The id coercion ran before the IN branch, so the whole "a,b" string was
         * handed to new ObjectId(..) and threw. A list of ids is the ordinary way to
         * fetch a selection, and it answered 500.
         */
        @Test
        @Timeout(300)
        @DisplayName("on _id, a comma separated list of ids works instead of throwing")
        void idsAsCommaSeparated() {
            List<Map<String, Object>> all = this.allRows();
            String first = String.valueOf(all.get(0).get("_id"));
            String second = String.valueOf(all.get(1).get("_id"));

            FilterCondition c = fc("_id", FilterConditionOperator.IN, first + "," + second);

            assertEquals(2, names(c).size());
        }

        private List<Map<String, Object>> allRows() {
            return MongoFilterConditionIntegrationTest.this
                    .asClient(appDataService.readPage(APP, SYSTEM, STORAGE_NAME, new Query().setSize(50)))
                    .getContent();
        }
    }

    @Nested
    @DisplayName("STRING_LOOSE_EQUAL")
    class LooseEqual {

        private FilterCondition loose(String value) {
            return fc("phone", FilterConditionOperator.STRING_LOOSE_EQUAL, value);
        }

        /**
         * The operator means "contains this text", and the text comes from a search
         * box. Unquoted it was a regex: "+91" is a dangling quantifier, Mongo
         * refuses it with error 51091, and the read 500s - taking the page's whole
         * chain with it.
         */
        @Test
        @Timeout(300)
        @DisplayName("a leading + is text, not a quantifier")
        void plusIsLiteral() {
            assertEquals(List.of("alpha", "bravo"), names(loose("+91")));
        }

        @Test
        @Timeout(300)
        @DisplayName("brackets are text, not a group")
        void bracketsAreLiteral() {
            // Unquoted, "(080)" is a capture group matching the bare digits, so it
            // also matched a row with no brackets at all. Right answer here, wrong
            // reason, and wrong answer on data that differs.
            assertEquals(List.of("charlie"), names(loose("(080)")));
            assertTrue(names(loose("(080) 4000")).contains("charlie"));
        }

        @Test
        @Timeout(300)
        @DisplayName("it is still a contains, not an exact match")
        void stillContains() {
            assertEquals(List.of("delta"), names(loose("50%")));

            // Both phones carry "123" inside a longer run of digits, which is the
            // point: this matches anywhere, not at a boundary.
            assertEquals(List.of("charlie", "echo"), names(loose("123")));
        }

        @Test
        @Timeout(300)
        @DisplayName("and still case insensitive")
        void caseInsensitive() {
            assertEquals(List.of("delta"), names(loose("OFF LINE")));
        }
    }
}
