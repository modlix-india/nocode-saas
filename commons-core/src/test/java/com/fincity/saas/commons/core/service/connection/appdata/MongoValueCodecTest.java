package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.enums.MongoBsonType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

@DisplayName("Storing a Mongo field as the type the author asked for")
class MongoValueCodecTest {

    private static Map<String, StorageColumnDefinition> defs(String field, MongoBsonType type) {
        return Map.of(
                field,
                new StorageColumnDefinition().setMongo(new StorageColumnDefinition.Mongo().setBsonType(type)));
    }

    @Nested
    @DisplayName("writing")
    class Encoding {

        @Test
        @DisplayName("money declared DECIMAL128 stops being a string")
        void decimal() {
            // As a string it cannot be summed, and Mongo compares strings lexically,
            // which puts "9.00" above "10.00".
            Document doc = new Document("price", new BsonString("10.50"));

            MongoValueCodec.encode(doc, defs("price", MongoBsonType.DECIMAL128));

            assertEquals(
                    new Decimal128(new BigDecimal("10.50")),
                    ((BsonDecimal128) doc.get("price")).getValue());
        }

        @Test
        @DisplayName("an ISO date becomes a real BSON date")
        void isoDate() {
            Document doc = new Document("at", new BsonString("2026-01-15T10:00:00Z"));

            MongoValueCodec.encode(doc, defs("at", MongoBsonType.DATE));

            assertInstanceOf(BsonDateTime.class, doc.get("at"));
        }

        @Test
        @DisplayName("an epoch number becomes one too, whichever unit it is in")
        void epochDate() {
            // Dates on this backend have always been epoch numbers, so a field being
            // given a real date is very often one being migrated.
            Document seconds = new Document("at", new BsonInt64(1_767_000_000L));
            Document millis = new Document("at", new BsonInt64(1_767_000_000_000L));

            MongoValueCodec.encode(seconds, defs("at", MongoBsonType.DATE));
            MongoValueCodec.encode(millis, defs("at", MongoBsonType.DATE));

            assertEquals(
                    ((BsonDateTime) seconds.get("at")).getValue(),
                    ((BsonDateTime) millis.get("at")).getValue());
        }

        @Test
        @DisplayName("a list is coerced element by element")
        void arrays() {
            org.bson.BsonArray arr = new org.bson.BsonArray();
            arr.add(new BsonString("1.5"));
            arr.add(new BsonString("2.5"));

            Document doc = new Document("prices", arr);
            MongoValueCodec.encode(doc, defs("prices", MongoBsonType.DECIMAL128));

            assertTrue(((org.bson.BsonArray) doc.get("prices")).get(0) instanceof BsonDecimal128);
        }

        @Test
        @DisplayName("a field with no definition is left exactly as it was")
        void untouched() {
            // The reason this is safe to add to a live backend: 219 storages declare
            // nothing, so nothing moves.
            BsonString value = new BsonString("10.50");
            Document doc = new Document("price", value);

            MongoValueCodec.encode(doc, Map.of());
            MongoValueCodec.encode(doc, defs("other", MongoBsonType.DECIMAL128));

            assertSame(value, doc.get("price"));
        }
    }

    @Nested
    @DisplayName("reading")
    class Decoding {

        @Test
        @DisplayName("a Decimal128 comes back as plain text, because the schema says STRING")
        void decimal() {
            Document doc = new Document("price", new Decimal128(new BigDecimal("10.50")));

            MongoValueCodec.decode(doc);

            assertEquals("10.50", doc.get("price"));
        }

        @Test
        @DisplayName("a BSON date comes back as ISO text")
        void date() {
            // Returned as a Date it would serialise as whatever the default locale
            // renders, which is both machine-dependent and rejected by the
            // validator on the next write.
            Document doc = new Document("at", new Date(1_767_000_000_000L));

            MongoValueCodec.decode(doc);

            assertEquals("2025-12-29T09:20:00", doc.get("at"));
        }

        @Test
        @DisplayName("decoding needs no definitions, so a row written before one still reads")
        void valueDriven() {
            // Deliberate asymmetry: a removed or changed definition cannot strand a
            // row that was written under the old one.
            Document doc = new Document("price", new Decimal128(new BigDecimal("1.25")));

            MongoValueCodec.decode(doc);

            assertEquals("1.25", doc.get("price"));
        }

        @Test
        @DisplayName("everything else passes through")
        void untouched() {
            Document doc = new Document(Map.of("n", 42, "s", "hello", "b", true, "l", List.of(1, 2)));

            MongoValueCodec.decode(doc);

            assertEquals(42, doc.get("n"));
            assertEquals("hello", doc.get("s"));
            assertEquals(Boolean.TRUE, doc.get("b"));
            assertEquals(List.of(1, 2), doc.get("l"));
        }
    }
}
