package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageTriggerType;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;

@DisplayName("Relations the database can enforce")
class MySQLForeignKeysTest {

    private static StorageRelation relation(StorageRelationType type, StorageRelationConstraint onDelete) {
        return new StorageRelation()
                .setStorageName("customers")
                .setRelationType(type)
                .setFieldName("_id")
                .setDeleteConstraint(onDelete);
    }

    @Nested
    @DisplayName("which ones qualify")
    class Enforceable {

        @Test
        @DisplayName("a TO_ONE with a constraint does")
        void toOneWithConstraint() {
            assertTrue(MySQLForeignKeys.enforceable(
                    relation(StorageRelationType.TO_ONE, StorageRelationConstraint.RESTRICT)));
            assertTrue(MySQLForeignKeys.enforceable(
                    relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE)));
        }

        @Test
        @DisplayName("NOTHING does not, so every relation in the fleet keeps working exactly as it did")
        void nothingIsNotAConstraint() {
            // MySQL treats NO ACTION as a synonym for RESTRICT, so mapping NOTHING
            // onto it would turn all 27 live relations into blocking constraints the
            // first time anything published.
            assertFalse(MySQLForeignKeys.enforceable(
                    relation(StorageRelationType.TO_ONE, StorageRelationConstraint.NOTHING)));
            assertFalse(MySQLForeignKeys.enforceable(relation(StorageRelationType.TO_ONE, null)));
        }

        @Test
        @DisplayName("TO_MANY does not, because the column is a JSON array")
        void toManyCannot() {
            assertFalse(MySQLForeignKeys.enforceable(
                    relation(StorageRelationType.TO_MANY, StorageRelationConstraint.RESTRICT)));
        }

        @Test
        @DisplayName("a relation pointing at a field other than _id does not, because only _id is a key")
        void onlyTheRowId() {
            StorageRelation r = relation(StorageRelationType.TO_ONE, StorageRelationConstraint.RESTRICT)
                    .setFieldName("externalRef");

            assertFalse(MySQLForeignKeys.enforceable(r));
        }

        @Test
        @DisplayName("a CASCADE stays in the service when the child owes something on delete")
        void cascadeWithSideEffects() {
            // A row deleted inside MySQL raises no event, runs no AFTER_DELETE
            // trigger and leaves its version rows behind, and none of that can be
            // handed to the database. 39 storages are versioned, so this is not a
            // hypothetical.
            StorageRelation r = relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE);

            Storage plain = new Storage();
            Storage versioned = new Storage();
            versioned.setIsVersioned(Boolean.TRUE);

            assertTrue(MySQLForeignKeys.enforceable(plain, r));
            assertFalse(MySQLForeignKeys.enforceable(versioned, r));
        }

        @Test
        @DisplayName("a RESTRICT stays in the database even then, because it removes nothing")
        void restrictIsAlwaysSafe() {
            Storage versioned = new Storage();
            versioned.setIsVersioned(Boolean.TRUE);

            assertTrue(MySQLForeignKeys.enforceable(
                    versioned, relation(StorageRelationType.TO_ONE, StorageRelationConstraint.RESTRICT)));
        }

        @Test
        @DisplayName("a delete trigger keeps the cascade in the service too")
        void deleteTrigger() {
            Storage triggered = new Storage();
            triggered.setTriggers(Map.of(StorageTriggerType.AFTER_DELETE, List.of("notifyWarehouse")));

            assertFalse(MySQLForeignKeys.enforceable(
                    triggered, relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE)));
        }

        @Test
        @DisplayName("a blank fieldName means _id, which does qualify")
        void blankFieldMeansId() {
            assertTrue(MySQLForeignKeys.enforceable(
                    relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE).setFieldName(null)));
        }
    }

    @Nested
    @DisplayName("the statements")
    class Statements {

        private static List<MySQLForeignKeys.ForeignKey> desired(StorageRelationConstraint onDelete) {
            Map<String, StorageRelation> relations = new LinkedHashMap<>();
            relations.put("customer", relation(StorageRelationType.TO_ONE, onDelete));
            return MySQLForeignKeys.desired(
                    "app_orders", relations, Map.of("customer", "app_customers"));
        }

        @Test
        @DisplayName("the key points at the target's _id and carries the declared action")
        void addClause() {
            String sql = desired(StorageRelationConstraint.CASCADE).getFirst().addClause();

            assertTrue(sql.contains("FOREIGN KEY (`customer`) REFERENCES `app_customers` (`_id`)"), sql);
            assertTrue(sql.contains("ON DELETE CASCADE"), sql);
        }

        @Test
        @DisplayName("ON UPDATE is always RESTRICT, because the referenced key is immutable")
        void onUpdateIsInert() {
            // SQL fires ON UPDATE when the REFERENCED key changes. That key is _id, a
            // ULID assigned at insert that no code path rewrites, so CASCADE here
            // would be a clause that reads as if it does something and never can.
            assertTrue(desired(StorageRelationConstraint.CASCADE)
                    .getFirst()
                    .addClause()
                    .contains("ON UPDATE RESTRICT"));
        }

        @Test
        @DisplayName("a second run issues nothing, because the plan is a difference")
        void idempotent() {
            List<MySQLForeignKeys.ForeignKey> want = desired(StorageRelationConstraint.RESTRICT);

            assertTrue(MySQLForeignKeys.sync("db", "app_orders", want, want).isEmpty());
        }

        @Test
        @DisplayName("changing the action drops the old key before adding the new one")
        void dropsBeforeAdding() {
            List<String> sql = MySQLForeignKeys.sync(
                    "db",
                    "app_orders",
                    desired(StorageRelationConstraint.RESTRICT),
                    desired(StorageRelationConstraint.CASCADE));

            assertEquals(2, sql.size());
            // MySQL will not hold two foreign keys on one column at once, so the
            // order is load-bearing rather than tidy.
            assertTrue(sql.get(0).contains("DROP FOREIGN KEY"), sql.get(0));
            assertTrue(sql.get(1).contains("ADD CONSTRAINT"), sql.get(1));
        }

        @Test
        @DisplayName("a constraint set back to NOTHING is dropped, not left behind")
        void droppedWhenNoLongerWanted() {
            List<String> sql = MySQLForeignKeys.sync(
                    "db", "app_orders", desired(StorageRelationConstraint.RESTRICT), List.of());

            assertEquals(1, sql.size());
            assertTrue(sql.getFirst().contains("DROP FOREIGN KEY"), sql.getFirst());
        }

        @Test
        @DisplayName("the name fits MySQL's 64 characters even for a long table")
        void nameIsBounded() {
            String name = MySQLForeignKeys.name("a".repeat(120), "customer");

            assertTrue(name.length() <= 64, name);
            // The column is the part that tells two keys on one table apart, so the
            // table name is what gets trimmed.
            assertTrue(name.endsWith("_customer"), name);
        }

        @Test
        @DisplayName("the orphan check counts rows pointing at an id that is not there")
        void orphanCount() {
            String sql = MySQLForeignKeys.orphanCount(
                    "db", "app_orders", desired(StorageRelationConstraint.RESTRICT).getFirst());

            assertTrue(sql.contains("NOT EXISTS"), sql);
            assertTrue(sql.contains("IS NOT NULL"), sql);
        }

        @Test
        @DisplayName("a target that does not resolve produces no key rather than a broken one")
        void unresolvedTarget() {
            Map<String, StorageRelation> relations = new LinkedHashMap<>();
            relations.put("customer", relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE));

            assertTrue(MySQLForeignKeys.desired("app_orders", relations, Map.of()).isEmpty());
        }
    }

    /**
     * An AUDITED child owes version rows on delete, exactly as a versioned one does.
     *
     * {@code keepsHistory} in the backends is {@code isAudited || isVersioned}, but
     * owesAnythingOnDelete only ever asked about isVersioned. Since isAudited
     * DEFAULTS TO TRUE, that handed almost every cascade to the database - and
     * {@code ON DELETE CASCADE} writes no version row, raises no event and runs no
     * trigger, because it has no caller.
     *
     * Measured on MySQL 8.4 on 2026-10-04 before the fix: deleting the head of a
     * 4-author / 3-book chain removed all 7 rows and wrote exactly ONE delete
     * version row - the head's, which went through the service. The other six rows
     * left no trace on storages that had asked to be audited.
     */
    @Nested
    @DisplayName("A cascade may only go to the database when the child owes nothing")
    class AuditedChildrenOweHistory {

        @Test
        @DisplayName("An audited child owes history, so its cascade stays in the service")
        void auditedChildIsNotEnforceable() {

            Storage child = new Storage();
            child.setIsAudited(Boolean.TRUE);
            child.setIsVersioned(Boolean.FALSE);

            assertTrue(
                    MySQLForeignKeys.owesAnythingOnDelete(child),
                    "an audited child writes a version row on delete, which ON DELETE CASCADE cannot do");

            assertFalse(
                    MySQLForeignKeys.enforceable(
                            child, relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE)),
                    "a database cascade would silently skip this child's audit rows");
        }

        /**
         * RESTRICT removes nothing, so there is no history to lose and the database
         * is still the better place for it - including against writes that never
         * went through the service.
         */
        @Test
        @DisplayName("RESTRICT on an audited child still belongs to the database")
        void restrictIsStillEnforceable() {

            Storage child = new Storage();
            child.setIsAudited(Boolean.TRUE);

            assertTrue(MySQLForeignKeys.enforceable(
                    child, relation(StorageRelationType.TO_ONE, StorageRelationConstraint.RESTRICT)));
        }

        @Test
        @DisplayName("A child that is neither audited nor versioned can still cascade in the database")
        void plainChildStillCascadesInTheDatabase() {

            Storage child = new Storage();
            child.setIsAudited(Boolean.FALSE);
            child.setIsVersioned(Boolean.FALSE);

            assertFalse(MySQLForeignKeys.owesAnythingOnDelete(child));
            assertTrue(MySQLForeignKeys.enforceable(
                    child, relation(StorageRelationType.TO_ONE, StorageRelationConstraint.CASCADE)));
        }
    }
}
