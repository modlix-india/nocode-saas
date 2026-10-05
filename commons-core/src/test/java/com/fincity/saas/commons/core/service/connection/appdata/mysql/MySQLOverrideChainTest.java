package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.SchemaChange.Kind;

/**
 * A storage definition is overridable per client, so SYSTEM -> MID -> LEAF each resolve
 * to a DIFFERENT merged schema and therefore a DIFFERENT table.
 *
 * That is the thing to get right before any of this ships. A publish is not one DDL plan
 * applied to N tenants; it is N plans, one per client, each diffed against what that
 * client's table actually looks like. A change that is safe for the client who made it
 * can be narrowing for a descendant that overrode the same field.
 *
 * The merge itself is the platform's job and is covered by
 * StorageOverrideChainIntegrationTest. These tests start from the merged shapes and
 * assert what the MySQL backend does with them.
 */
class MySQLOverrideChainTest {

    // ---------- helpers ----------

    /** Merge child over parent, which is what the override chain resolves to. */
    private static Schema merged(Schema parent, Map<String, Schema> childProps, List<String> childRequired) {
        Map<String, Schema> props = new LinkedHashMap<>(parent.getProperties());
        props.putAll(childProps);

        Schema s = new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
        if (childRequired != null) s.setRequired(childRequired);
        else if (parent.getRequired() != null) s.setRequired(parent.getRequired());
        return s;
    }

    private static Schema object(Map<String, Schema> props) {
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(new LinkedHashMap<>(props));
    }

    private static List<String> names(List<MySQLColumn> cols) {
        return cols.stream().map(MySQLColumn::name).toList();
    }

    private static MySQLColumn named(List<MySQLColumn> cols, String name) {
        return cols.stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }

    private static SchemaChange changeFor(List<SchemaChange> changes, String column) {
        return changes.stream().filter(c -> c.column().equals(column)).findFirst().orElse(null);
    }

    // SYSTEM owns the base definition.
    private static Schema systemSchema() {
        return object(Map.of(
                "code", Schema.ofString("code").setMaxLength(20),
                "amount", Schema.ofString("amount").setMaxLength(40)));
    }

    // ---------- tests ----------

    @Nested
    @DisplayName("CH3: each level resolves to its own table shape")
    class ThreeLevelStructure {

        @Test
        @DisplayName("every level keeps what its ancestors declared")
        void inheritsDownTheChain() {
            Schema system = systemSchema();
            Schema mid = merged(system, Map.of("region", Schema.ofString("region").setMaxLength(30)), null);
            Schema leaf = merged(mid, Map.of("tier", Schema.ofInteger("tier")), null);

            assertEquals(List.of("amount", "code"), names(MySQLTypeMapper.columns(system)));
            assertEquals(List.of("amount", "code", "region"), names(MySQLTypeMapper.columns(mid)));
            assertEquals(List.of("amount", "code", "region", "tier"), names(MySQLTypeMapper.columns(leaf)));
        }

        @Test
        @DisplayName("a mid-level column does not leak upward to the base")
        void doesNotLeakUpward() {
            Schema system = systemSchema();
            Schema mid = merged(system, Map.of("region", Schema.ofString("region").setMaxLength(30)), null);

            assertFalse(names(MySQLTypeMapper.columns(system)).contains("region"));
            assertTrue(names(MySQLTypeMapper.columns(mid)).contains("region"));
        }

        @Test
        @DisplayName("a sibling's override does not reach the other branch")
        void siblingsAreIndependent() {
            Schema system = systemSchema();
            Schema branchA = merged(system, Map.of("aOnly", Schema.ofInteger("aOnly")), null);
            Schema branchB = merged(system, Map.of("bOnly", Schema.ofInteger("bOnly")), null);

            assertFalse(names(MySQLTypeMapper.columns(branchA)).contains("bOnly"));
            assertFalse(names(MySQLTypeMapper.columns(branchB)).contains("aOnly"));
        }

        @Test
        @DisplayName("the id column is identical at every level")
        void idIsUniformAcrossTheChain() {
            // Row ids must not vary by client, or a relation across levels breaks.
            Schema system = systemSchema();
            Schema leaf = merged(merged(system, Map.of("x", Schema.ofInteger("x")), null),
                    Map.of("y", Schema.ofInteger("y")), null);

            for (Schema s : List.of(system, leaf))
                assertTrue(MySQLTablePlanner.createTable("t", MySQLTypeMapper.columns(s))
                        .contains("`_id` CHAR(26) NOT NULL"));
        }
    }

    @Nested
    @DisplayName("CH3: a child overriding an inherited field's type")
    class OverrideTypeChanges {

        @Test
        @DisplayName("a child tightening an inherited string to a number is narrowing for that child")
        void childNarrowsInheritedField() {
            Schema system = systemSchema();
            Schema mid = merged(system, Map.of("amount", Schema.ofDouble("amount")), null);

            SchemaChange c = changeFor(
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(system), MySQLTypeMapper.columns(mid)), "amount");

            assertNotNull(c);
            assertEquals(Kind.NARROWING, c.kind());
            assertTrue(c.needsDataCheck(), "the child's own rows must be counted before this applies");
        }

        @Test
        @DisplayName("a child loosening an inherited field is safe")
        void childWidensInheritedField() {
            Schema system = object(Map.of("note", Schema.ofString("note").setMaxLength(20)));
            Schema mid = merged(system, Map.of("note", Schema.ofString("note").setMaxLength(500)), null);

            SchemaChange c = changeFor(
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(system), MySQLTypeMapper.columns(mid)), "note");
            assertEquals(Kind.WIDENING, c.kind());
        }

        @Test
        @DisplayName("the leaf wins over the mid, which wins over the base")
        void mostDerivedTypeWins() {
            Schema system = object(Map.of("v", Schema.ofString("v").setMaxLength(10)));
            Schema mid = merged(system, Map.of("v", Schema.ofInteger("v")), null);
            Schema leaf = merged(mid, Map.of("v", Schema.ofLong("v")), null);

            assertEquals("VARCHAR(10)", named(MySQLTypeMapper.columns(system), "v").type());
            assertEquals("INT", named(MySQLTypeMapper.columns(mid), "v").type());
            assertEquals("BIGINT", named(MySQLTypeMapper.columns(leaf), "v").type());
        }

        @Test
        @DisplayName("a child making an inherited field required is narrowing")
        void childAddsRequired() {
            Schema system = object(Map.of("code", Schema.ofString("code").setMaxLength(20)));
            Schema mid = merged(system, Map.of(), List.of("code"));

            SchemaChange c = changeFor(
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(system), MySQLTypeMapper.columns(mid)), "code");
            assertEquals(Kind.NARROWING, c.kind());
            assertTrue(c.reason().contains("null"));
        }

        @Test
        @DisplayName("a date declared at the base is a real date column at every level")
        void dateTypingSurvivesTheChain() {
            // The whole reason for the relational backend. A declared date must not
            // degrade to a number somewhere down the chain.
            Schema system = object(Map.of("when", Schema.ofString("when").setFormat(StringFormat.DATETIME)));
            Schema leaf = merged(merged(system, Map.of("a", Schema.ofInteger("a")), null),
                    Map.of("b", Schema.ofInteger("b")), null);

            assertEquals("DATETIME(3)", named(MySQLTypeMapper.columns(leaf), "when").type());
        }
    }

    @Nested
    @DisplayName("CH3: a base change propagating to every descendant")
    class BaseChangePropagation {

        @Test
        @DisplayName("a widening at the base is safe for all three levels at once")
        void wideningPropagatesSafely() {
            Schema sysBefore = object(Map.of("code", Schema.ofString("code").setMaxLength(20)));
            Schema sysAfter = object(Map.of("code", Schema.ofString("code").setMaxLength(100)));

            for (Map<String, Schema> childAdds :
                    List.of(Map.<String, Schema>of(), Map.of("region", Schema.ofString("region").setMaxLength(5)))) {
                List<SchemaChange> cs = MySQLTablePlanner.diff(
                        MySQLTypeMapper.columns(merged(sysBefore, childAdds, null)),
                        MySQLTypeMapper.columns(merged(sysAfter, childAdds, null)));
                assertTrue(cs.stream().allMatch(c -> c.kind() == Kind.WIDENING), cs.toString());
            }
        }

        @Test
        @DisplayName("a narrowing at the base needs a data check at EVERY descendant, not just the base")
        void narrowingPropagatesAsNarrowingEverywhere() {
            // This is the fan-out: one edit at SYSTEM becomes N independent checks, and
            // one descendant's data can block while the others are clean.
            Schema sysBefore = object(Map.of("amount", Schema.ofString("amount").setMaxLength(40)));
            Schema sysAfter = object(Map.of("amount", Schema.ofDouble("amount")));

            List<Map<String, Schema>> descendants = List.of(
                    Map.of(),
                    Map.of("region", Schema.ofString("region").setMaxLength(5)),
                    Map.of("region", Schema.ofString("region").setMaxLength(5), "tier", Schema.ofInteger("tier")));

            for (Map<String, Schema> adds : descendants) {
                SchemaChange c = changeFor(
                        MySQLTablePlanner.diff(
                                MySQLTypeMapper.columns(merged(sysBefore, adds, null)),
                                MySQLTypeMapper.columns(merged(sysAfter, adds, null))),
                        "amount");
                assertEquals(Kind.NARROWING, c.kind(), "descendant with " + adds.keySet() + " must also be checked");
            }
        }

        @Test
        @DisplayName("a child that overrode the field is unaffected by the base changing it")
        void childOverrideShieldsItFromTheBase() {
            // The child pinned its own type, so the base's edit is invisible there and
            // must NOT produce a change for that client.
            Schema sysBefore = object(Map.of("amount", Schema.ofString("amount").setMaxLength(40)));
            Schema sysAfter = object(Map.of("amount", Schema.ofDouble("amount")));
            Map<String, Schema> childPins = Map.of("amount", Schema.ofLong("amount"));

            List<SchemaChange> cs = MySQLTablePlanner.diff(
                    MySQLTypeMapper.columns(merged(sysBefore, childPins, null)),
                    MySQLTypeMapper.columns(merged(sysAfter, childPins, null)));

            assertTrue(cs.isEmpty(), "a pinned override must absorb the base change: " + cs);
        }

        @Test
        @DisplayName("dropping a field at the base is destructive for every descendant that did not override it")
        void baseDropIsDestructiveDownstream() {
            Schema sysBefore = object(Map.of("code", Schema.ofString("code").setMaxLength(20),
                    "legacy", Schema.ofString("legacy").setMaxLength(10)));
            Schema sysAfter = object(Map.of("code", Schema.ofString("code").setMaxLength(20)));

            SchemaChange c = changeFor(
                    MySQLTablePlanner.diff(
                            MySQLTypeMapper.columns(merged(sysBefore, Map.of("x", Schema.ofInteger("x")), null)),
                            MySQLTypeMapper.columns(merged(sysAfter, Map.of("x", Schema.ofInteger("x")), null))),
                    "legacy");

            assertEquals(Kind.DESTRUCTIVE, c.kind());
            assertTrue(c.needsDataCheck());
        }
    }

    @Nested
    @DisplayName("Draft and live surfaces diverge until publish")
    class DraftAndLive {

        @Test
        @DisplayName("a draft-only field is absent from the live table")
        void draftMayBeAheadOfLive() {
            Schema live = systemSchema();
            Schema draft = merged(live, Map.of("experiment", Schema.ofString("experiment").setMaxLength(10)), null);

            assertFalse(names(MySQLTypeMapper.columns(live)).contains("experiment"));
            assertTrue(names(MySQLTypeMapper.columns(draft)).contains("experiment"));
        }

        @Test
        @DisplayName("publish is the diff from live to draft, and that is where the gate belongs")
        void publishAppliesTheDraftToLive() {
            Schema live = systemSchema();
            Schema draft = merged(live, Map.of("experiment", Schema.ofString("experiment").setMaxLength(10)), null);

            List<SchemaChange> cs =
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(live), MySQLTypeMapper.columns(draft));

            assertEquals(1, cs.size());
            assertEquals("experiment", cs.getFirst().column());
            assertEquals(Kind.WIDENING, cs.getFirst().kind());
        }

        @Test
        @DisplayName("a change that is clean against draft data can still be narrowing against live")
        void draftCleanDoesNotMeanLiveClean() {
            // Draft rows are a sandbox. The classification is identical either way, which
            // is exactly why the gate has to run the COUNT against live rows rather than
            // trusting that the author's draft survived the edit.
            Schema before = object(Map.of("amount", Schema.ofString("amount").setMaxLength(40)));
            Schema after = object(Map.of("amount", Schema.ofDouble("amount")));

            List<SchemaChange> cs =
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(before), MySQLTypeMapper.columns(after));

            assertEquals(Kind.NARROWING, cs.getFirst().kind());
            assertTrue(cs.getFirst().needsDataCheck(), "the surface does not change the classification");
        }

        @Test
        @DisplayName("publishing an unchanged draft is a no-op")
        void unchangedDraftProducesNoDdl() {
            Schema s = systemSchema();
            assertTrue(MySQLTablePlanner.diff(MySQLTypeMapper.columns(s), MySQLTypeMapper.columns(s)).isEmpty());
        }

        @Test
        @DisplayName("reverting a draft field before publish leaves live untouched")
        void revertedDraftIsANoOp() {
            Schema live = systemSchema();
            Schema draftAdded = merged(live, Map.of("tmp", Schema.ofInteger("tmp")), null);
            Schema draftReverted = systemSchema();

            assertFalse(MySQLTablePlanner.diff(MySQLTypeMapper.columns(live), MySQLTypeMapper.columns(draftAdded))
                    .isEmpty());
            assertTrue(MySQLTablePlanner.diff(MySQLTypeMapper.columns(live), MySQLTypeMapper.columns(draftReverted))
                    .isEmpty());
        }
    }

    @Nested
    @DisplayName("Plans stay re-runnable, which is the whole recovery story")
    class Recovery {

        @Test
        @DisplayName("applying a plan twice is a no-op the second time")
        void planIsIdempotent() {
            Schema before = systemSchema();
            Schema after = merged(before, Map.of("extra", Schema.ofInteger("extra")), null);

            List<MySQLColumn> applied = MySQLTypeMapper.columns(after);
            assertTrue(MySQLTablePlanner.diff(applied, applied).isEmpty(), "re-running must find nothing left to do");
        }

        @Test
        @DisplayName("a half-applied plan is detectable as the remaining diff")
        void partialApplicationLeavesTheRestVisible() {
            // Recovery is re-running the same plan, so a partially applied migration must
            // show up as exactly the steps still outstanding.
            Schema before = object(Map.of("a", Schema.ofInteger("a")));
            Schema after = object(Map.of(
                    "a", Schema.ofInteger("a"), "b", Schema.ofInteger("b"), "c", Schema.ofInteger("c")));

            Schema halfway = object(Map.of("a", Schema.ofInteger("a"), "b", Schema.ofInteger("b")));

            assertEquals(2, MySQLTablePlanner.diff(
                    MySQLTypeMapper.columns(before), MySQLTypeMapper.columns(after)).size());
            assertEquals(List.of("c"), MySQLTablePlanner.diff(
                    MySQLTypeMapper.columns(halfway), MySQLTypeMapper.columns(after))
                    .stream().map(SchemaChange::column).toList());
        }

        @Test
        @DisplayName("a rename is seen as add-then-drop, in that order")
        void renameIsAddThenDrop() {
            // There is no rename in the schema model, so the plan must add the new column
            // before dropping the old one. Any prefix of that plan still has the data.
            Schema before = object(Map.of("oldName", Schema.ofInteger("oldName")));
            Schema after = object(Map.of("newName", Schema.ofInteger("newName")));

            List<SchemaChange> cs =
                    MySQLTablePlanner.diff(MySQLTypeMapper.columns(before), MySQLTypeMapper.columns(after));

            assertEquals(2, cs.size());
            assertEquals("newName", cs.get(0).column());
            assertEquals(Kind.DESTRUCTIVE, cs.get(1).kind());
            assertEquals("oldName", cs.get(1).column());
        }
    }
}
