package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.document.Storage.StorageIndex;
import com.fincity.saas.commons.core.document.Storage.StorageIndexField;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Indexes on the MySQL backend.
 *
 * Mongo has honoured {@code storage.indexes} and {@code textIndexFields} all along.
 * This backend referenced neither, so a table arrived with one index - the primary
 * key - and every filter on anything else read the whole table. TEXT_SEARCH did not
 * even degrade: it returned a 501 explaining that no FULLTEXT index was ever created.
 */
@DisplayName("Indexes, on the backend that had none")
class MySQLIndexIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String BOOKS = "books";
    private static final String AUTHORS = "authors";
    private static final String TENANT = SYSTEM + "_" + APP_CODE;

    private static DSLContext ctx;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();
        exec("DROP DATABASE IF EXISTS `" + TENANT + "`");
        this.givenConnection();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static List<String> indexNames(String table) {
        return Flux.from(ctx.resultQuery("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = '" + table + "'"
                        + " ORDER BY INDEX_NAME"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();
    }

    private static List<String> indexColumns(String table, String index) {
        return Flux.from(ctx.resultQuery("SELECT COLUMN_NAME FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = '" + table + "'"
                        + " AND INDEX_NAME = '" + index + "' ORDER BY SEQ_IN_INDEX"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();
    }

    private static String indexType(String table, String index) {
        return Mono.from(ctx.resultQuery("SELECT INDEX_TYPE FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = '" + table + "'"
                        + " AND INDEX_NAME = '" + index + "' LIMIT 1"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorages(Map<String, StorageIndex> indexes, List<String> textFields, boolean withRelation) {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Storage authors = new Storage();
        authors.setName(AUTHORS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        authors.setUniqueName("testapp_authors");
        authors.setSchema(schema(Map.of("name", 40)));
        this.insertRaw(authors);

        Storage books = new Storage();
        books.setName(BOOKS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        books.setUniqueName("testapp_books");
        books.setIndexes(indexes);
        books.setTextIndexFields(textFields);
        books.setSchema(schema(Map.of("title", 120, "blurb", 400, "isbn", 20)));

        if (withRelation)
            books.setRelations(new LinkedHashMap<>(Map.of(
                    "author",
                    new StorageRelation()
                            .setStorageName(AUTHORS)
                            .setRelationType(StorageRelationType.TO_ONE)
                            .setFieldName("_id"))));

        this.insertRaw(books);
    }

    private static Map<String, Object> schema(Map<String, Integer> fields) {
        Map<String, Object> properties = new HashMap<>();
        fields.forEach((name, len) -> properties.put(
                name, new HashMap<>(Map.of("type", "STRING", "maxLength", len))));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        return schema;
    }

    private static StorageIndex index(boolean unique, String... fields) {
        StorageIndex idx = new StorageIndex();
        idx.setUnique(unique);
        idx.setFields(java.util.Arrays.stream(fields)
                .map(f -> new StorageIndexField().setFieldName(f))
                .toList());
        return idx;
    }

    private void givenConnection() {
        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "url", mysqlUrl(), "username", "root", "password", "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("0000000000000indexes0001");
        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Map<String, Object> writeBook(String title, String blurb, String isbn) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("blurb", blurb);
        row.put("isbn", isbn);
        return this.asClient(this.appDataService.create(
                APP_CODE, SYSTEM, BOOKS, new DataObject().setData(row), false, null));
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("declared indexes")
    class Declared {

        @Test
        @Timeout(300)
        @DisplayName("a declared index reaches the table under its own name")
        void created() {
            givenStorages(Map.of("byTitle", index(false, "title")), null, false);
            writeBook("Dune", "sand", "1");

            assertTrue(indexNames("testapp_books").contains("byTitle"), indexNames("testapp_books").toString());
            assertEquals(List.of("title"), indexColumns("testapp_books", "byTitle"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a compound index keeps the order it was declared in")
        void compound() {
            // Column order is what decides whether an index can serve a query at
            // all, so it is not a detail that can be normalised away.
            givenStorages(Map.of("byTitleIsbn", index(false, "title", "isbn")), null, false);
            writeBook("Dune", "sand", "1");

            assertEquals(List.of("title", "isbn"), indexColumns("testapp_books", "byTitleIsbn"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a unique index actually refuses a duplicate")
        void uniqueIsEnforced() {
            givenStorages(Map.of("byIsbn", index(true, "isbn")), null, false);
            writeBook("Dune", "sand", "1");

            assertThrows(Exception.class, () -> writeBook("Dune Again", "more sand", "1"));
        }

        @Test
        @Timeout(300)
        @DisplayName("an index no longer declared is dropped, as it is on Mongo")
        void droppedWhenRemoved() {
            givenStorages(Map.of("byTitle", index(false, "title")), null, false);
            writeBook("Dune", "sand", "1");
            assertTrue(indexNames("testapp_books").contains("byTitle"));

            givenStorages(Map.of(), null, false);
            this.cacheService().evictAllCaches().block();
            writeBook("Elantris", "chalk", "2");

            assertTrue(!indexNames("testapp_books").contains("byTitle"), indexNames("testapp_books").toString());
        }

        @Test
        @Timeout(300)
        @DisplayName("a second write creates nothing, because the plan is a difference")
        void idempotent() {
            givenStorages(Map.of("byTitle", index(false, "title")), null, false);
            writeBook("Dune", "sand", "1");

            List<String> after = indexNames("testapp_books");
            writeBook("Elantris", "chalk", "2");

            assertEquals(after, indexNames("testapp_books"));
        }

        private com.fincity.saas.commons.service.CacheService cacheService() {
            return MySQLIndexIntegrationTest.this.cacheService;
        }
    }

    @Nested
    @DisplayName("text search")
    class TextSearch {

        @Test
        @Timeout(300)
        @DisplayName("textIndexFields become one FULLTEXT index")
        void fullTextIndex() {
            givenStorages(null, List.of("title", "blurb"), false);
            writeBook("Dune", "a story about sand", "1");

            assertTrue(indexNames("testapp_books").contains("ft_all"), indexNames("testapp_books").toString());
            assertEquals("FULLTEXT", indexType("testapp_books", "ft_all"));
        }

        @Test
        @Timeout(300)
        @DisplayName("and TEXT_SEARCH finds rows instead of returning a 501")
        void textSearchWorks() {
            // Before this the filter threw UnsupportedFilterException saying a
            // FULLTEXT index was needed and that storage definitions never create
            // one. Both halves are now false.
            givenStorages(null, List.of("title", "blurb"), false);
            writeBook("Dune", "a story about sand", "1");
            writeBook("Elantris", "a story about chalk", "2");

            Query query = new Query()
                    .setPage(0)
                    .setSize(10)
                    .setCondition(new FilterCondition()
                            .setField("blurb")
                            .setOperator(FilterConditionOperator.TEXT_SEARCH)
                            .setValue("sand"));

            List<Map<String, Object>> rows =
                    this.asClientPage(query);

            assertEquals(1, rows.size(), rows.toString());
            assertEquals("Dune", rows.getFirst().get("title"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a storage that declares no text fields still says exactly why")
        void refusedWithoutDeclaration() {
            givenStorages(null, null, false);
            writeBook("Dune", "sand", "1");

            Query query = new Query()
                    .setPage(0)
                    .setSize(10)
                    .setCondition(new FilterCondition()
                            .setField("blurb")
                            .setOperator(FilterConditionOperator.TEXT_SEARCH)
                            .setValue("sand"));

            Exception e = assertThrows(Exception.class, () -> this.asClientPage(query));
            assertTrue(String.valueOf(e.getMessage()).contains("textIndexFields"), String.valueOf(e.getMessage()));
        }

        private List<Map<String, Object>> asClientPage(Query query) {
            return MySQLIndexIntegrationTest.this
                    .asClient(MySQLIndexIntegrationTest.this
                            .appDataService
                            .readPage(APP_CODE, SYSTEM, BOOKS, query)
                            .map(p -> p.getContent()));
        }
    }

    @Nested
    @DisplayName("relation columns")
    class Relations {

        @Test
        @Timeout(300)
        @DisplayName("a TO_ONE relation column is indexed without anyone declaring it")
        void relationIndexed() {
            // The join direction is a primary key lookup and was always fast. The
            // reverse - and every RESTRICT check - reads this column.
            givenStorages(null, null, true);
            writeBook("Dune", "sand", "1");

            assertTrue(
                    indexColumns("testapp_books", "rel_author").contains("author"),
                    indexNames("testapp_books").toString());
        }

        @Test
        @Timeout(300)
        @DisplayName("a declared index on the same column wins, and no second one is added")
        void declaredWins() {
            givenStorages(Map.of("byAuthor", index(false, "author")), null, true);
            writeBook("Dune", "sand", "1");

            List<String> names = indexNames("testapp_books");

            assertTrue(names.contains("byAuthor"), names.toString());
            assertTrue(!names.contains("rel_author"), names.toString());
        }
    }
}
