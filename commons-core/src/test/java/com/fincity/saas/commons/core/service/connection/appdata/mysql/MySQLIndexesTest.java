package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.document.Storage.StorageIndex;
import com.fincity.saas.commons.core.document.Storage.StorageIndexField;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;

/**
 * Indexes on the MySQL backend, which until now had none at all.
 *
 * Mongo has always honoured {@code storage.indexes} and {@code textIndexFields}.
 * MySQL referenced neither, so every table had exactly one index - the primary key -
 * and every filter was a full scan, while TEXT_SEARCH returned a 501 saying so.
 *
 * Pure, because what gets created and what gets dropped is the part worth being able
 * to assert exhaustively. Dropping the wrong index is as expensive as never creating
 * the right one and much harder to notice.
 */
@DisplayName("MySQL index planning")
class MySQLIndexesTest {

    private static StorageIndex index(boolean unique, String... fields) {
        StorageIndex idx = new StorageIndex();
        idx.setUnique(unique);
        idx.setFields(java.util.Arrays.stream(fields)
                .map(f -> new StorageIndexField().setFieldName(f))
                .toList());
        return idx;
    }

    private static Storage storage(Map<String, StorageIndex> indexes, List<String> textFields) {
        Storage s = new Storage();
        s.setName("orders").setAppCode("testapp").setClientCode("SYSTEM").setVersion(1);
        s.setUniqueName("testapp_orders");
        s.setIndexes(indexes);
        s.setTextIndexFields(textFields);
        return s;
    }

    private static List<MySQLIndexes.Index> desired(Storage s) {
        return MySQLIndexes.desired(s, Set.of("ref", "amount", "note", "customer"));
    }

    @Nested
    @DisplayName("what a definition asks for")
    class Desired {

        @Test
        @DisplayName("a declared index becomes one, under its declared name")
        void declared() {
            List<MySQLIndexes.Index> out = desired(storage(Map.of("byRef", index(false, "ref")), null));

            assertEquals(1, out.size());
            assertEquals("byRef", out.getFirst().name());
            assertEquals(List.of("ref"), out.getFirst().columns());
            assertFalse(out.getFirst().unique());
        }

        @Test
        @DisplayName("a compound index keeps its field order and direction")
        void compound() {
            StorageIndex idx = new StorageIndex();
            idx.setFields(List.of(
                    new StorageIndexField().setFieldName("ref").setDirection(Sort.Direction.ASC),
                    new StorageIndexField().setFieldName("amount").setDirection(Sort.Direction.DESC)));

            MySQLIndexes.Index out = desired(storage(Map.of("byRefAmount", idx), null))
                    .getFirst();

            assertEquals(List.of("ref", "amount"), out.columns());
            assertTrue(out.ddl("db", "testapp_orders").contains("`ref` ASC"), out.ddl("db", "testapp_orders"));
            assertTrue(out.ddl("db", "testapp_orders").contains("`amount` DESC"), out.ddl("db", "testapp_orders"));
        }

        @Test
        @DisplayName("unique is carried through, because it is a constraint and not a hint")
        void unique() {
            assertTrue(desired(storage(Map.of("bySku", index(true, "ref")), null))
                    .getFirst()
                    .unique());
        }

        @Test
        @DisplayName("textIndexFields become one FULLTEXT index, as they are one text index on Mongo")
        void fullText() {
            List<MySQLIndexes.Index> out = desired(storage(null, List.of("ref", "note")));

            assertEquals(1, out.size());
            assertTrue(out.getFirst().fullText());
            assertEquals(List.of("ref", "note"), out.getFirst().columns());
            assertTrue(out.getFirst().ddl("db", "testapp_orders").contains("FULLTEXT"));
        }

        @Test
        @DisplayName("a field the storage does not have is left out rather than failing the table")
        void unknownFieldIgnored() {
            // The definition is overridable, so a client can resolve a schema that
            // no longer has the field an index names. Refusing would make that
            // tenant's table un-creatable for a missing index.
            assertTrue(desired(storage(Map.of("byGhost", index(false, "nosuchfield")), null))
                    .isEmpty());
        }

        @Test
        @DisplayName("a TO_ONE relation column is indexed even when nobody declared it")
        void relationColumns() {
            // The join side is a primary key lookup, so it is already fast. The
            // parent side is not: finding every order for a customer, and every
            // RESTRICT check, reads this column.
            Storage s = storage(null, null);
            Map<String, StorageRelation> relations = new LinkedHashMap<>();
            relations.put(
                    "customer",
                    new StorageRelation().setStorageName("customers").setRelationType(StorageRelationType.TO_ONE));
            relations.put(
                    "labels",
                    new StorageRelation().setStorageName("tags").setRelationType(StorageRelationType.TO_MANY));
            s.setRelations(relations);

            List<String> names = MySQLIndexes.desired(s, Set.of("ref", "customer", "labels")).stream()
                    .map(MySQLIndexes.Index::name)
                    .toList();

            assertEquals(1, names.size(), names.toString());
            // TO_MANY is a JSON array; MySQL cannot index into one.
            assertTrue(names.getFirst().contains("customer"), names.getFirst());
        }
    }

    @Nested
    @DisplayName("the statements")
    class Sync {

        private static final Set<String> NO_FK = Set.of();

        @Test
        @DisplayName("a second run issues nothing")
        void idempotent() {
            List<MySQLIndexes.Index> want = desired(storage(Map.of("byRef", index(false, "ref")), null));

            assertTrue(MySQLIndexes.sync("db", "testapp_orders", want, want, NO_FK).isEmpty());
        }

        @Test
        @DisplayName("an index no longer declared is dropped, as Mongo drops it")
        void droppedWhenRemoved() {
            List<MySQLIndexes.Index> had = desired(storage(Map.of("byRef", index(false, "ref")), null));

            List<String> sql = MySQLIndexes.sync("db", "testapp_orders", had, List.of(), NO_FK);

            assertEquals(1, sql.size());
            assertTrue(sql.getFirst().startsWith("DROP INDEX"), sql.getFirst());
        }

        @Test
        @DisplayName("changing an index drops the old one before creating the new")
        void replaced() {
            List<MySQLIndexes.Index> had = desired(storage(Map.of("byRef", index(false, "ref")), null));
            List<MySQLIndexes.Index> want = desired(storage(Map.of("byRef", index(true, "ref")), null));

            List<String> sql = MySQLIndexes.sync("db", "testapp_orders", had, want, NO_FK);

            assertEquals(2, sql.size());
            assertTrue(sql.get(0).startsWith("DROP INDEX"), sql.get(0));
            assertTrue(sql.get(1).contains("UNIQUE"), sql.get(1));
        }

        @Test
        @DisplayName("PRIMARY is never touched")
        void primaryIsSafe() {
            MySQLIndexes.Index primary = new MySQLIndexes.Index("PRIMARY", List.of("_id"), List.of(), true, false);

            assertTrue(MySQLIndexes.sync("db", "testapp_orders", List.of(primary), List.of(), NO_FK)
                    .isEmpty());
        }

        @Test
        @DisplayName("an index a foreign key depends on is never dropped")
        void foreignKeyIndexIsSafe() {
            // MySQL refuses to drop the index backing a constraint, so attempting
            // it turns an ordinary publish into a failed one.
            MySQLIndexes.Index backing =
                    new MySQLIndexes.Index("fk_testapp_orders_customer", List.of("customer"), List.of(), false, false);

            assertTrue(MySQLIndexes.sync("db", "testapp_orders", List.of(backing), List.of(), Set.of("customer"))
                    .isEmpty());
        }
    }
}
