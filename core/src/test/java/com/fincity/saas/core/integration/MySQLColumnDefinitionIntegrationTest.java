package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;

/**
 * What an author declares about a column, all the way down to the table.
 *
 * The mapper's guess is right most of the time and cannot be right always: a price is
 * a STRING with format DECIMAL, and only the author knows whether that is money to
 * two places, a tax rate to six, or a quantity that is never fractional. The default
 * of 19,4 was a choice made in the mapper, and this is how it stops being the only
 * answer.
 */
@DisplayName("Per-backend column definitions, applied to the real table")
class MySQLColumnDefinitionIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String PRODUCTS = "products";
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

    private static String columnType(String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = 'testapp_products'"
                        + " AND COLUMN_NAME = '" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private static String collation(String column) {
        return Mono.from(ctx.resultQuery("SELECT COLLATION_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = 'testapp_products'"
                        + " AND COLUMN_NAME = '" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private void givenStorage(Map<String, StorageColumnDefinition> definitions) {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Storage products = new Storage();
        products.setName(PRODUCTS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        products.setUniqueName("testapp_products");
        products.setColumnDefinitions(definitions);
        products.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of(
                        "price", new HashMap<>(Map.of("type", "STRING", "format", "DECIMAL")),
                        "sku", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)))))));
        this.insertRaw(products);
    }

    private void givenConnection() {

        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "url", mysqlUrl(), "username", "root", "password", "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("000000000000000coldefs01");

        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Map<String, Object> write(String price, String sku) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("price", price);
        row.put("sku", sku);
        return this.asClient(this.appDataService.create(
                APP_CODE, SYSTEM, PRODUCTS, new DataObject().setData(row), false, null));
    }

    private static Map<String, StorageColumnDefinition> mysql(String field, StorageColumnDefinition.MySQL def) {
        return Map.of(field, new StorageColumnDefinition().setMysql(def));
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Timeout(300)
    @DisplayName("with nothing declared, a DECIMAL format still gets the 19,4 default")
    void theDefault() {
        givenStorage(null);
        write("10.50", "A1");

        assertEquals("decimal(19,4)", columnType("price"));
    }

    @Test
    @Timeout(300)
    @DisplayName("a declared precision and scale is what the table gets")
    void declaredDecimal() {
        givenStorage(mysql(
                "price",
                new StorageColumnDefinition.MySQL()
                        .setType(MySQLColumnType.DECIMAL)
                        .setPrecision(10)
                        .setScale(2)));

        write("10.50", "A1");

        assertEquals("decimal(10,2)", columnType("price"));
    }

    @Test
    @Timeout(300)
    @DisplayName("the value comes back as text at the declared scale, so a read and a write back still validate")
    void roundTrip() {
        // The schema calls price a STRING and the validator will only accept one
        // back. The driver hands over a BigDecimal, which serialises as a bare
        // number, so without the decode a caller reading a row and changing the sku
        // would fail on the price they never touched.
        givenStorage(mysql(
                "price",
                new StorageColumnDefinition.MySQL()
                        .setType(MySQLColumnType.DECIMAL)
                        .setPrecision(10)
                        .setScale(2)));

        Map<String, Object> written = write("10.5", "A1");

        assertEquals("10.50", written.get("price"));
        assertTrue(written.get("price") instanceof String, String.valueOf(written.get("price")));

        Map<String, Object> again = this.asClient(this.appDataService.update(
                APP_CODE,
                SYSTEM,
                PRODUCTS,
                new DataObject()
                        .setData(new LinkedHashMap<>(Map.of(
                                "_id", written.get("_id"), "price", written.get("price"), "sku", "A2"))),
                Boolean.TRUE,
                false,
                null));

        assertEquals("10.50", again.get("price"));
        assertEquals("A2", again.get("sku"));
    }

    @Test
    @Timeout(300)
    @DisplayName("a collation reaches the column without ever reaching the type")
    void collation() {
        // Folded into the type string it would never match what
        // information_schema.COLUMN_TYPE reports, and the reconciler would alter the
        // table on every single run for ever.
        givenStorage(mysql(
                "sku",
                new StorageColumnDefinition.MySQL()
                        .setType(MySQLColumnType.VARCHAR)
                        .setLength(32)
                        .setCollation("utf8mb4_bin")));

        write("1.00", "A1");

        assertEquals("varchar(32)", columnType("sku"));
        assertEquals("utf8mb4_bin", collation("sku"));
    }

    @Test
    @Timeout(300)
    @DisplayName("a field with no definition is mapped exactly as it always was")
    void onlyTheDeclaredField() {
        givenStorage(mysql(
                "price",
                new StorageColumnDefinition.MySQL()
                        .setType(MySQLColumnType.DECIMAL)
                        .setPrecision(8)
                        .setScale(3)));

        write("1.000", "A1");

        assertEquals("decimal(8,3)", columnType("price"));
        assertEquals("varchar(20)", columnType("sku"));
    }
}
