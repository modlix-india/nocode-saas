package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
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
 * MATCH and MATCH_ALL over an array, which this backend used to refuse outright.
 *
 * Mongo answers them with {@code $elemMatch} and {@code $all}. Here an ARRAY is one
 * JSON column - the type mapper stores it "whole as JSON" - so the question was only
 * which SQL means the same thing, and the answers are not interchangeable:
 * containment is JSON_CONTAINS, a LIKE over elements is JSON_SEARCH, and a
 * comparison has to unroll the array with JSON_TABLE.
 *
 * Every case is asserted on the ROWS that come back rather than on the SQL, because
 * the one that nearly shipped broken - the comparison - produced perfectly valid SQL
 * that silently matched nothing.
 */
@DisplayName("Matching inside an array on MySQL")
class MySQLArrayMatchIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String STORAGE_NAME = "articles";
    private static final String TABLE = "testapp_articles";

    private static DSLContext ctx;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();
        exec("DROP DATABASE IF EXISTS `" + SYSTEM + "_" + APP_CODE + "`");

        this.givenStorage();
        this.givenConnection();

        this.write("a", List.of("red", "blue"), List.of(1, 5, 9));
        this.write("b", List.of("green"), List.of(2, 3));
        this.write("c", List.of("red", "green"), List.of(10, 20));
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorage() {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Map<String, Object> properties = new HashMap<>();
        properties.put("title", new HashMap<>(Map.of("type", "STRING", "maxLength", 40)));
        properties.put("tags", new HashMap<>(Map.of("type", "ARRAY")));
        properties.put("scores", new HashMap<>(Map.of("type", "ARRAY")));

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        storage.setUniqueName(TABLE);
        storage.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", properties)));
        this.insertRaw(storage);
    }

    private void givenConnection() {
        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(
                        new HashMap<>(Map.of("url", mysqlUrl(), "username", "root", "password", "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("000000000000arraymatch01");
        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private void write(String title, List<String> tags, List<Integer> scores) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("tags", new ArrayList<>(tags));
        row.put("scores", new ArrayList<>(scores));
        this.asClient(
                this.appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, new DataObject().setData(row), false, null));
    }

    /** The titles a filter returns, sorted, so an assertion reads as a set. */
    private List<String> titles(FilterCondition condition) {
        Page<Map<String, Object>> page = this.asClient(this.appDataService.readPage(
                APP_CODE, SYSTEM, STORAGE_NAME, new Query().setCondition(condition).setSize(50)));

        return page.getContent().stream()
                .map(r -> String.valueOf(r.get("title")))
                .sorted()
                .toList();
    }

    private static FilterCondition match(String field, FilterConditionOperator matchOp, Object value) {
        return (FilterCondition) new FilterCondition()
                .setField(field)
                .setOperator(FilterConditionOperator.MATCH)
                .setMatchOperator(matchOp)
                .setValue(value);
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("MATCH: at least one element")
    class Match {

        @Test
        @Timeout(300)
        @DisplayName("containment finds every row whose array holds the value")
        void equals() {
            assertEquals(List.of("a", "c"), titles(match("tags", FilterConditionOperator.EQUALS, "red")));
        }

        @Test
        @Timeout(300)
        @DisplayName("a value in no array matches nothing, rather than everything")
        void equalsMiss() {
            assertTrue(titles(match("tags", FilterConditionOperator.EQUALS, "purple")).isEmpty());
        }

        @Test
        @Timeout(300)
        @DisplayName("LIKE matches a pattern against the elements")
        void like() {
            assertEquals(List.of("b", "c"), titles(match("tags", FilterConditionOperator.LIKE, "gre%")));
        }

        @Test
        @Timeout(300)
        @DisplayName("a loose equal matches a substring anywhere in an element")
        void looseEqual() {
            // "lu" is inside "blue" and starts nothing, so a prefix-only
            // implementation would return nothing here and still pass the LIKE test
            // above.
            assertEquals(List.of("a"), titles(match("tags", FilterConditionOperator.STRING_LOOSE_EQUAL, "lu")));

            // And it is a substring, not an exact match: every tag here has an "e".
            assertEquals(
                    List.of("a", "b", "c"),
                    titles(match("tags", FilterConditionOperator.STRING_LOOSE_EQUAL, "e")));
        }

        /**
         * The case that nearly shipped broken. The obvious SQL - an EXISTS over a
         * JSON_TABLE of the outer column - is accepted by MySQL 8.4 and silently
         * matches NOTHING. Asserting on rows rather than on generated SQL is what
         * catches that.
         */
        @Test
        @Timeout(300)
        @DisplayName("greater-than compares each element, not the array")
        void greaterThan() {
            assertEquals(List.of("a", "c"), titles(match("scores", FilterConditionOperator.GREATER_THAN, 8)));
        }

        @Test
        @Timeout(300)
        @DisplayName("the comparison is numeric, so 9 is not greater than 10")
        void comparisonIsNumeric() {
            // Compared as text, '9' > '10' and row a would come back. It is the
            // mistake that looks right until the data has two digits in it.
            assertEquals(List.of("c"), titles(match("scores", FilterConditionOperator.GREATER_THAN_EQUAL, 10)));
        }

        @Test
        @Timeout(300)
        @DisplayName("less-than finds the rows holding a small element")
        void lessThan() {
            assertEquals(List.of("a", "b"), titles(match("scores", FilterConditionOperator.LESS_THAN, 4)));
        }

        @Test
        @Timeout(300)
        @DisplayName("negating a match excludes the rows that matched")
        void negated() {
            FilterCondition c = match("tags", FilterConditionOperator.EQUALS, "red");
            c.setNegate(true);
            assertEquals(List.of("b"), titles(c));
        }
    }

    @Nested
    @DisplayName("MATCH_ALL: every value present")
    class MatchAll {

        private FilterCondition all(List<?> values) {
            return (FilterCondition) new FilterCondition()
                    .setField("tags")
                    .setOperator(FilterConditionOperator.MATCH_ALL)
                    .setMultiValue(new ArrayList<>(values));
        }

        @Test
        @Timeout(300)
        @DisplayName("both values have to be in the same array")
        void allPresent() {
            assertEquals(List.of("c"), titles(all(List.of("red", "green"))));
        }

        /**
         * The difference from MATCH, and the reason they are two operators: a row
         * holding one of the two is a match for MATCH and not for MATCH_ALL.
         */
        @Test
        @Timeout(300)
        @DisplayName("holding only one of them is not enough")
        void partialIsNotEnough() {
            assertEquals(List.of("a", "c"), titles(match("tags", FilterConditionOperator.EQUALS, "red")));
            assertEquals(List.of("c"), titles(all(List.of("red", "green"))));
        }

        @Test
        @Timeout(300)
        @DisplayName("a single value behaves like containment")
        void single() {
            assertEquals(List.of("b", "c"), titles(all(List.of("green"))));
        }
    }
}
