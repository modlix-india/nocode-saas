package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.saas.commons.core.enums.MongoBsonType;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

/**
 * The checks that run at save, before a definition reaches a single tenant.
 *
 * Most of these exist because a column definition is the one thing that can
 * contradict the schema, and a contradiction produces a storage that accepts a write
 * and then refuses the same value coming back out.
 */
@DisplayName("Column definitions checked against the schema")
class StorageColumnDefinitionValidatorTest {

    private static Schema storageSchema() {
        return Schema.ofObject("order")
                .setProperties(Map.of(
                        "price", Schema.ofString("price").setFormat(StringFormat.DECIMAL),
                        "quantity", Schema.ofInteger("quantity"),
                        "placedAt", Schema.ofString("placedAt").setFormat(StringFormat.DATETIME)));
    }

    private static List<String> check(String field, StorageColumnDefinition def) {
        return StorageColumnDefinitionValidator.problems(Map.of(field, def), storageSchema(), java.util.Set.of());
    }

    private static StorageColumnDefinition mysql(MySQLColumnType type, Integer p, Integer s) {
        return new StorageColumnDefinition()
                .setMysql(new StorageColumnDefinition.MySQL().setType(type).setPrecision(p).setScale(s));
    }

    @Test
    @DisplayName("a decimal on a STRING field is exactly what this is for")
    void theMotivatingCase() {
        assertTrue(check("price", mysql(MySQLColumnType.DECIMAL, 10, 2)).isEmpty());
    }

    @Test
    @DisplayName("a derived storage with no schema of its own is not told its fields do not exist")
    void deltaWithoutSchema() {
        // An override that changes only a column definition carries no schema -
        // its fields are declared further up the chain. Checking them against the
        // delta refused every such override with "no such field".
        assertTrue(StorageColumnDefinitionValidator.problems(
                        Map.of("price", mysql(MySQLColumnType.DECIMAL, 10, 2)),
                        new Schema(),
                        java.util.Set.of())
                .isEmpty());
    }

    @Test
    @DisplayName("but the definition itself is still checked when the schema is absent")
    void deltaStillChecksTheDefinition() {
        // A DECIMAL without a precision is wrong whatever the schema says.
        assertTrue(StorageColumnDefinitionValidator.problems(
                        Map.of("price", mysql(MySQLColumnType.DECIMAL, null, null)),
                        new Schema(),
                        java.util.Set.of())
                .stream()
                .anyMatch(p -> p.contains("precision and a scale")));
    }

    @Test
    @DisplayName("a delta that changes only the length is accepted, because the type is the base's")
    void deltaMayOmitTheType() {
        // The difference extractor drops a value identical to the base, so a
        // client narrowing an inherited VARCHAR stores the length alone. Demanding
        // a type here refused every such override - the merged definition has one,
        // this document just does not repeat it.
        StorageColumnDefinition lengthOnly = new StorageColumnDefinition()
                .setMysql(new StorageColumnDefinition.MySQL().setLength(200));

        assertTrue(StorageColumnDefinitionValidator.problems(
                        Map.of("title", lengthOnly), new Schema(), java.util.Set.of())
                .isEmpty());
    }

    @Test
    @DisplayName("a whole definition still has to name its type")
    void wholeDefinitionNeedsAType() {
        // Where the schema IS present this is a real document, and a mysql block
        // with no type is an entry nothing will ever read.
        StorageColumnDefinition lengthOnly = new StorageColumnDefinition()
                .setMysql(new StorageColumnDefinition.MySQL().setLength(200));

        assertTrue(check("price", lengthOnly).stream().anyMatch(p -> p.contains("mysql.type is required")));
    }

    @Test
    @DisplayName("a field that does not exist is refused, because nothing would ever read the entry")
    void unknownField() {
        assertTrue(check("prcie", mysql(MySQLColumnType.DECIMAL, 10, 2)).stream()
                .anyMatch(p -> p.contains("no such field")));
    }

    @Test
    @DisplayName("a relation field counts as a field, even though the schema may not declare it")
    void relationField() {
        // StorageService.validate REFUSES a storage whose schema declares a relation
        // key, so a relation column could never be configured if only the schema
        // counted.
        List<String> problems = StorageColumnDefinitionValidator.problems(
                Map.of(
                        "customer",
                        new StorageColumnDefinition()
                                .setMysql(new StorageColumnDefinition.MySQL()
                                        .setType(MySQLColumnType.CHAR)
                                        .setLength(26))),
                storageSchema(),
                java.util.Set.of("customer"));

        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    @DisplayName("a DECIMAL column on a numeric field is refused, because it comes back as text")
    void decimalNeedsAStringField() {
        // The driver hands back a BigDecimal and the codec renders it as a plain
        // string, so a schema that says INTEGER would reject its own stored value on
        // the next read-modify-write.
        assertTrue(check("quantity", mysql(MySQLColumnType.DECIMAL, 10, 2)).stream()
                .anyMatch(p -> p.contains("read back as text")));
    }

    @Test
    @DisplayName("a DATETIME column on a numeric field is refused for the same reason")
    void temporalNeedsAStringField() {
        assertTrue(check("quantity", mysql(MySQLColumnType.DATETIME, null, null)).stream()
                .anyMatch(p -> p.contains("read back as text")));
    }

    @Test
    @DisplayName("DECIMAL128 on Mongo needs a STRING field")
    void mongoDecimalNeedsAString() {
        StorageColumnDefinition def = new StorageColumnDefinition()
                .setMongo(new StorageColumnDefinition.Mongo().setBsonType(MongoBsonType.DECIMAL128));

        assertTrue(check("price", def).isEmpty());
        assertTrue(check("quantity", def).stream().anyMatch(p -> p.contains("read back as text")));
    }

    @Test
    @DisplayName("a numeric bsonType needs a numeric field")
    void mongoLongNeedsANumber() {
        StorageColumnDefinition def = new StorageColumnDefinition()
                .setMongo(new StorageColumnDefinition.Mongo().setBsonType(MongoBsonType.LONG));

        assertTrue(check("quantity", def).isEmpty());
        assertTrue(check("price", def).stream().anyMatch(p -> p.contains("read back as a number")));
    }

    @Test
    @DisplayName("a mongo half with no bsonType")
    void bsonTypeRequired() {
        StorageColumnDefinition def = new StorageColumnDefinition().setMongo(new StorageColumnDefinition.Mongo());

        assertTrue(check("price", def).stream().anyMatch(p -> p.contains("mongo.bsonType is required")));
    }

    @Test
    @DisplayName("the two halves are independent, and both are reported at once")
    void bothHalves() {
        StorageColumnDefinition def = new StorageColumnDefinition()
                .setMysql(new StorageColumnDefinition.MySQL().setType(MySQLColumnType.DECIMAL))
                .setMongo(new StorageColumnDefinition.Mongo().setBsonType(MongoBsonType.BOOLEAN));

        List<String> problems = check("price", def);

        // One save, every problem. Fixing them one per attempt is the worse version
        // of this.
        assertTrue(problems.size() >= 2, problems.toString());
    }

    @Test
    @DisplayName("no definitions at all is the normal case and costs nothing")
    void empty() {
        assertTrue(StorageColumnDefinitionValidator.problems(null, storageSchema(), java.util.Set.of())
                .isEmpty());
        assertTrue(StorageColumnDefinitionValidator.problems(Map.of(), storageSchema(), java.util.Set.of())
                .isEmpty());
    }
}
