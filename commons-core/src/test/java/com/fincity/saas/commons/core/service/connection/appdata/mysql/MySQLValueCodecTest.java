package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A nested field has to come back out the shape it went in.
 *
 * On Mongo it always did, for free. A JSON column takes text and returns text, so
 * every one of these is a place the two backends could quietly disagree about what a
 * stored document contains.
 */
class MySQLValueCodecTest {

    private static final MySQLValueCodec CODEC = new MySQLValueCodec(new ObjectMapper());

    private static final Set<String> JSON = Set.of("address", "tags");

    private static Map<String, Object> row(Object address) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", "01HZZZZZZZZZZZZZZZZZZZZZZZ");
        m.put("name", "Asha");
        m.put("address", address);
        return m;
    }

    @Nested
    @DisplayName("writing")
    class Encoding {

        @Test
        @DisplayName("a map becomes JSON text")
        void mapIsSerialised() {
            Object encoded = CODEC.encode(row(Map.of("city", "Pune")), JSON).get("address");

            assertInstanceOf(String.class, encoded);
            assertTrue(((String) encoded).contains("\"city\""));
        }

        @Test
        @DisplayName("a list becomes JSON text")
        void listIsSerialised() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tags", List.of("a", "b"));

            assertEquals("[\"a\",\"b\"]", CODEC.encode(m, JSON).get("tags"));
        }

        @Test
        @DisplayName("columns that are not JSON are left exactly as they are")
        void scalarsUntouched() {
            Map<String, Object> encoded = CODEC.encode(row(Map.of("city", "Pune")), JSON);

            assertEquals("Asha", encoded.get("name"));
            assertInstanceOf(String.class, encoded.get("_id"));
        }

        @Test
        @DisplayName("null stays null rather than becoming the text \"null\"")
        void nullStaysNull() {
            assertNull(CODEC.encode(row(null), JSON).get("address"));
        }

        @Test
        @DisplayName("text that is already a JSON structure is not encoded twice")
        void alreadyJson() {
            assertEquals("{\"city\":\"Pune\"}", CODEC.encode(row("{\"city\":\"Pune\"}"), JSON).get("address"));
        }

        @Test
        @DisplayName("a plain string is encoded, because MySQL rejects it otherwise")
        void plainStringIsQuoted() {
            // `Pune` on its own is not valid JSON and the insert fails on it. Encoding
            // it is the only way the value survives at all.
            assertEquals("\"Pune\"", CODEC.encode(row("Pune"), JSON).get("address"));
        }

        @Test
        @DisplayName("a bare word that looks like JSON but is not gets encoded")
        void almostJson() {
            assertEquals("\"{not json\"", CODEC.encode(row("{not json"), JSON).get("address"));
        }

        @Test
        @DisplayName("something that cannot be serialised says so rather than writing nonsense")
        void unserialisable() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", new Object() {
                @SuppressWarnings("unused")
                public Object getSelf() {
                    throw new IllegalStateException("no");
                }
            });

            assertThrows(IllegalArgumentException.class, () -> CODEC.encode(m, JSON));
        }
    }

    @Nested
    @DisplayName("reading")
    class Decoding {

        @Test
        @DisplayName("JSON text becomes a map again")
        void objectRoundTrips() {
            Map<String, Object> stored = row("{\"city\":\"Pune\",\"pin\":\"411001\"}");

            Object decoded = CODEC.decode(stored, JSON).get("address");

            assertInstanceOf(Map.class, decoded);
            assertEquals("Pune", ((Map<?, ?>) decoded).get("city"));
        }

        @Test
        @DisplayName("JSON text becomes a list again")
        void arrayRoundTrips() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tags", "[\"a\",\"b\"]");

            assertEquals(List.of("a", "b"), CODEC.decode(m, JSON).get("tags"));
        }

        @Test
        @DisplayName("whole numbers stay whole numbers")
        void numbersKeepTheirType() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", "{\"floor\":3}");

            Object floor = ((Map<?, ?>) CODEC.decode(m, JSON).get("address")).get("floor");

            // Gson's untyped parse would make this 3.0, and a quantity that was an
            // integer going in and a decimal coming out is the kind of difference
            // nobody notices until it is in a total.
            assertInstanceOf(Integer.class, floor);
            assertEquals(3, floor);
        }

        @Test
        @DisplayName("a value the column should never have held is handed back as it is")
        void corruptStays() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", "not json at all");

            // MySQL should not have allowed this. Failing the whole read for one bad
            // row would make the rest of the page unreachable for no gain.
            assertEquals("not json at all", CODEC.decode(m, JSON).get("address"));
        }

        @Test
        @DisplayName("an empty column reads as null, not as an empty string")
        void blankIsNull() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", "  ");

            assertNull(CODEC.decode(m, JSON).get("address"));
        }

        @Test
        @DisplayName("a row with no JSON columns is returned untouched")
        void noJsonColumns() {
            Map<String, Object> m = row("{\"city\":\"Pune\"}");
            assertEquals(m, CODEC.decode(m, Set.of()));
        }
    }

    @Nested
    @DisplayName("date columns")
    class Dates {

        private static final Set<String> DATES = Set.of("placedAt");

        private static Map<String, Object> dated(Object value) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("placedAt", value);
            return m;
        }

        @Test
        @DisplayName("a timestamp comes back as the ISO string that was stored")
        void timestampToIso() {
            // The column is a real DATETIME, which is the whole point of the backend.
            // The schema still calls the field a STRING, and the validator will only
            // accept one back - so read, change one field, write it back has to work.
            Object out = CODEC.decode(dated(java.sql.Timestamp.valueOf("2026-01-15 10:00:00")), Set.of(), DATES)
                    .get("placedAt");

            assertEquals("2026-01-15T10:00:00", out);
        }

        @Test
        @DisplayName("a LocalDateTime does too, whichever the driver hands back")
        void localDateTimeToIso() {
            Object out = CODEC.decode(
                            dated(java.time.LocalDateTime.of(2026, 1, 15, 10, 0)), Set.of(), DATES)
                    .get("placedAt");

            assertEquals("2026-01-15T10:00:00", out);
        }

        @Test
        @DisplayName("a date-only column keeps its shape")
        void dateOnly() {
            Object out = CODEC.decode(dated(java.sql.Date.valueOf("2026-01-15")), Set.of(), DATES)
                    .get("placedAt");

            assertEquals("2026-01-15", out);
        }

        @Test
        @DisplayName("a time-only column keeps its shape")
        void timeOnly() {
            Object out = CODEC.decode(dated(java.sql.Time.valueOf("10:30:00")), Set.of(), DATES)
                    .get("placedAt");

            assertEquals("10:30:00", out);
        }

        @Test
        @DisplayName("null stays null")
        void nullStays() {
            assertNull(CODEC.decode(dated(null), Set.of(), DATES).get("placedAt"));
        }

        @Test
        @DisplayName("an offset on the way in is folded into UTC")
        void offsetToUtc() {
            assertEquals(
                    "2026-01-15T04:30:00",
                    CODEC.encode(dated("2026-01-15T10:00:00+05:30"), Set.of(), DATES).get("placedAt"));
            assertEquals(
                    "2026-01-15T10:00:00.123",
                    CODEC.encode(dated("2026-01-15T10:00:00.123Z"), Set.of(), DATES).get("placedAt"));
            assertEquals(
                    "2026-01-14T23:30:00",
                    CODEC.encode(dated("2026-01-15T05:00:00+05:30"), Set.of(), DATES).get("placedAt"),
                    "the date moves with the instant");
        }

        @Test
        @DisplayName("a time with an offset is folded into UTC too")
        void timeOffsetToUtc() {
            assertEquals("04:30:00", CODEC.encode(dated("10:00:00+05:30"), Set.of(), DATES).get("placedAt"));
        }

        @Test
        @DisplayName("a value with no offset is already UTC and goes through as it is")
        void noOffsetUntouched() {
            assertEquals(
                    "2026-01-15T10:00:00",
                    CODEC.encode(dated("2026-01-15T10:00:00"), Set.of(), DATES).get("placedAt"));
            assertEquals("2026-01-15", CODEC.encode(dated("2026-01-15"), Set.of(), DATES).get("placedAt"));
        }

        @Test
        @DisplayName("what does not parse is left for MySQL to reject")
        void unparseableUntouched() {
            assertEquals("9:00+05:30", CODEC.encode(dated("9:00+05:30"), Set.of(), DATES).get("placedAt"));
        }

        @Test
        @DisplayName("an offset on a column that is not a declared date is left alone")
        void offsetOutsideDatesUntouched() {
            assertEquals(
                    "2026-01-15T10:00:00+05:30",
                    CODEC.encode(dated("2026-01-15T10:00:00+05:30"), Set.of(), Set.of()).get("placedAt"));
        }

        @Test
        @DisplayName("a column that is not a declared date is left alone")
        void untouched() {
            java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2026-01-15 10:00:00");
            assertEquals(ts, CODEC.decode(dated(ts), Set.of(), Set.of()).get("placedAt"));
        }

        @Test
        @DisplayName("left alone, a timestamp serialises to a locale string the validator rejects")
        void whyThisExists() {
            // "Jan 15, 2026, 10:00:00 AM" is what Gson makes of a java.sql.Timestamp,
            // and it differs by machine locale as well as failing the pattern.
            String rendered = new com.google.gson.Gson()
                    .toJson(java.sql.Timestamp.valueOf("2026-01-15 10:00:00"));

            assertFalse(rendered.contains("2026-01-15T10:00:00"), rendered);
        }
    }

    @Test
    @DisplayName("a structure survives a full write and read")
    void roundTrip() {
        Map<String, Object> original = Map.of("city", "Pune", "floor", 3, "lines", List.of("a", "b"));

        Map<String, Object> written = CODEC.encode(row(original), JSON);
        Map<String, Object> readBack = CODEC.decode(written, JSON);

        assertEquals(original, readBack.get("address"));
    }
}
