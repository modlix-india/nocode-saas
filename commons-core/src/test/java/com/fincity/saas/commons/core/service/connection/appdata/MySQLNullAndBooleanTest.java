package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumnNames;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLValueCodec;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.GroupByField;

/**
 * Nulls written as SQL NULL, and BOOLEAN fields read back as true / false, without a
 * database. The same rules against a real MySQL are in MySQLNullAndBooleanIntegrationTest.
 */
class MySQLNullAndBooleanTest {

    private static final MySQLValueCodec CODEC = new MySQLValueCodec(new ObjectMapper());

    static Schema schema() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("name", Schema.ofString("name").setMaxLength(100));
        props.put("description", Schema.ofString("description").setMaxLength(200));
        props.put("active", Schema.ofBoolean("active"));
        props.put("optIn", new Schema().setName("optIn").setType(Type.of(SchemaType.BOOLEAN, SchemaType.NULL)));
        props.put("count", Schema.ofInteger("count"));
        props.put("IFSC Flag", Schema.ofBoolean("IFSC Flag"));
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props).setRequired(List.of("name"));
    }

    static Storage storage() {
        Storage s = new Storage();
        s.setName("project").setAppCode("leadzumptwo").setClientCode("SYSTEM").setVersion(1);
        s.setUniqueName("project_t");
        return s;
    }

    private static Map<String, Object> byName(Map<Field<?>, Object> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k.getName(), v));
        return out;
    }

    @Nested
    @DisplayName("the SET clause of an update")
    class UpdateValues {

        @Test
        @DisplayName("PATCH: a field sent as null is set to NULL, and nothing else is touched")
        void patchNull() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_id", "426");
            data.put("description", null);

            Map<String, Object> set = byName(MySQLAppDataService.updateValues(
                    CODEC, storage(), MySQLColumnNames.physical(schema()), data, false));

            assertEquals(1, set.size(), "" + set);
            assertTrue(set.containsKey("description"));
            assertNull(set.get("description"));
        }

        @Test
        @DisplayName("PUT: the row is replaced, so every column left out is cleared too")
        void putReplaces() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_id", "426");
            data.put("name", "Tower A");
            data.put("active", true);

            Map<String, Object> set = byName(MySQLAppDataService.updateValues(
                    CODEC, storage(), MySQLColumnNames.physical(schema()), data, true));

            assertEquals("Tower A", set.get("name"));
            assertEquals(Boolean.TRUE, set.get("active"));
            for (String cleared : List.of("description", "optIn", "count", "IFSC_Flag")) {
                assertTrue(set.containsKey(cleared), cleared + " not cleared: " + set);
                assertNull(set.get(cleared));
            }
            assertFalse(set.containsKey("_id"), "the id is never in SET");
        }

        @Test
        @DisplayName("a null override is a merge, as before")
        void nullOverrideMerges() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_id", "426");
            data.put("name", "Tower A");

            Map<String, Object> set = byName(MySQLAppDataService.updateValues(
                    CODEC, storage(), MySQLColumnNames.physical(schema()), data, null));

            assertEquals(Map.of("name", "Tower A"), set);
        }
    }

    @Nested
    @DisplayName("what a PATCH validates")
    class Merged {

        @Test
        @DisplayName("the stored row with the patch applied: sent fields replace, nulls clear, the id is left out")
        void merged() {
            Map<String, Object> existing = new LinkedHashMap<>();
            existing.put("_id", "426");
            existing.put("name", "Tower A");
            existing.put("description", "unlink-test");
            existing.put("active", true);
            existing.put("count", null);

            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("_id", "426");
            patch.put("description", null);
            patch.put("active", false);

            Map<String, Object> out = MySQLAppDataService.merged(existing, patch);
            assertEquals(Map.of("name", "Tower A", "active", false), out);
        }
    }

    @Nested
    @DisplayName("a null on a required field")
    class RequiredNulls {

        @Test
        @DisplayName("is named, so the caller gets a 400 about that field")
        void named() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", null);
            data.put("description", null);
            assertEquals(List.of("name"), MySQLAppDataService.nullsOnRequired(
                    MySQLColumnNames.physical(schema()), data));
        }

        @Test
        @DisplayName("an optional field may be null")
        void optional() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("description", null);
            data.put("name", "x");
            assertTrue(MySQLAppDataService.nullsOnRequired(MySQLColumnNames.physical(schema()), data).isEmpty());
        }
    }

    @Nested
    @DisplayName("BOOLEAN fields")
    class Booleans {

        @Test
        @DisplayName("are the fields the schema declares BOOLEAN (with or without NULL), by column")
        void columns() {
            assertEquals(
                    Set.of("active", "optIn", "IFSC_Flag"),
                    MySQLTypeMapper.booleanColumns(MySQLColumnNames.physical(schema()), null));
        }

        @Test
        @DisplayName("a BOOLEAN an author stored as text is not converted")
        void retypedToText() {
            StorageColumnDefinition def = new StorageColumnDefinition();
            StorageColumnDefinition.MySQL mysql = new StorageColumnDefinition.MySQL();
            mysql.setType(MySQLColumnType.VARCHAR);
            mysql.setLength(5);
            def.setMysql(mysql);

            assertFalse(MySQLTypeMapper.booleanColumns(MySQLColumnNames.physical(schema()), Map.of("active", def))
                    .contains("active"));
        }

        @Test
        @DisplayName("come back true / false; null stays null; other columns untouched")
        void decoded() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("active", (byte) 1);
            raw.put("optIn", 0);
            raw.put("IFSC_Flag", null);
            raw.put("count", 1);

            Map<String, Object> out = CODEC.decode(
                    raw, Set.of(), Set.of(), Set.of(), Set.of("active", "optIn", "IFSC_Flag"));

            assertEquals(Boolean.TRUE, out.get("active"));
            assertEquals(Boolean.FALSE, out.get("optIn"));
            assertTrue(out.containsKey("IFSC_Flag"));
            assertNull(out.get("IFSC_Flag"));
            assertEquals(1, out.get("count"));
        }

        @Test
        @DisplayName("every integer shape the driver may use converts, a Boolean passes through")
        void shapes() {
            assertEquals(Boolean.TRUE, MySQLValueCodec.bool(1L));
            assertEquals(Boolean.FALSE, MySQLValueCodec.bool((short) 0));
            assertEquals(Boolean.TRUE, MySQLValueCodec.bool(BigDecimal.ONE));
            assertEquals(Boolean.TRUE, MySQLValueCodec.bool(new byte[] { 1 }));
            assertEquals(Boolean.FALSE, MySQLValueCodec.bool(Boolean.FALSE));
            assertNull(MySQLValueCodec.bool(null));
            assertEquals("yes", MySQLValueCodec.bool("yes"));
        }

        @Test
        @DisplayName("a read decodes them with JSON, dates and decimals, and keys the row by field")
        void decodingRow() {
            Schema physical = MySQLColumnNames.physical(schema());
            MySQLAppDataService.Decoding decoding = MySQLAppDataService.Decoding.of(physical, null);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("_id", "426");
            raw.put("IFSC_Flag", 1);
            raw.put("active", 0);

            Map<String, Object> row = decoding.row(CODEC, raw, physical);
            assertEquals(Boolean.TRUE, row.get("IFSC Flag"));
            assertEquals(Boolean.FALSE, row.get("active"));
            assertEquals(Set.of("active", "optIn", "IFSC Flag"), decoding.booleanFields(physical));
        }

        @Test
        @DisplayName("an old version snapshot holding 0 / 1 reads back as true / false")
        void snapshot() {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("active", 1);
            snap.put("count", 0);
            Map<String, Object> out = MySQLValueCodec.booleans(snap, Set.of("active", "optIn"));
            assertEquals(Boolean.TRUE, out.get("active"));
            assertEquals(0, out.get("count"));
            assertFalse(out.containsKey("optIn"), "an absent field is not invented");
        }

        @Test
        @DisplayName("an aggregate grouped on a BOOLEAN column keys its groups true / false; a bucket does not")
        void aggregateGroups() {
            GroupByField byActive = new GroupByField();
            byActive.setField("active");
            GroupByField byCount = new GroupByField();
            byCount.setField("count");
            AggregateQuery q = new AggregateQuery();
            q.setGroupBy(List.of(byActive, byCount));

            assertEquals(
                    Set.of("active"),
                    MySQLAppDataService.booleanAliases(q, Set.of("active", "optIn")));
        }
    }
}
