package com.fincity.saas.commons.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageRelationType;

/**
 * What a derived client can change about an inherited relation.
 *
 * The framework calls {@code existing.extractDifference(incoming)} - the BASE is the
 * receiver. Written the other way round, the delta keeps the base's value and drops
 * the derived one, so a client that sets a delete constraint saves successfully and
 * keeps the constraint it was trying to change. Since the constraint decides whether
 * a referenced row can be deleted at all, that is not a cosmetic difference.
 */
@DisplayName("Relation differencing across a client chain")
class StorageRelationDiffTest {

    private static StorageRelation relation(StorageRelationConstraint onDelete, String target) {
        return new StorageRelation()
                .setStorageName(target)
                .setRelationType(StorageRelationType.TO_ONE)
                .setFieldName("_id")
                .setDeleteConstraint(onDelete)
                .setUpdateConstraint(StorageRelationConstraint.NOTHING);
    }

    /** As the framework calls it: base first, derived as the argument. */
    private static StorageRelation delta(StorageRelation base, StorageRelation derived) {
        return base.extractDifference(derived).block();
    }

    @Test
    @DisplayName("a client tightening an inherited constraint keeps its own value")
    void tightenedConstraintIsKept() {
        StorageRelation diff = delta(
                relation(StorageRelationConstraint.NOTHING, "customers"),
                relation(StorageRelationConstraint.RESTRICT, "customers"));

        assertEquals(StorageRelationConstraint.RESTRICT, diff.getDeleteConstraint());
    }

    @Test
    @DisplayName("and what it did not change is not repeated in the delta")
    void unchangedIsDropped() {
        StorageRelation diff = delta(
                relation(StorageRelationConstraint.NOTHING, "customers"),
                relation(StorageRelationConstraint.RESTRICT, "customers"));

        assertNull(diff.getStorageName(), "the target is the same, so the delta says nothing about it");
        assertNull(diff.getRelationType());
    }

    @Test
    @DisplayName("applying the delta back gives the client its constraint and the base's target")
    void applyRestores() {
        StorageRelation base = relation(StorageRelationConstraint.NOTHING, "customers");
        StorageRelation merged = delta(base, relation(StorageRelationConstraint.RESTRICT, "customers"))
                .applyOverride(base)
                .block();

        assertEquals(StorageRelationConstraint.RESTRICT, merged.getDeleteConstraint());
        assertEquals("customers", merged.getStorageName());
        assertEquals(StorageRelationType.TO_ONE, merged.getRelationType());
    }

    @Test
    @DisplayName("a client repointing a relation keeps the new target")
    void repointedTargetIsKept() {
        StorageRelation diff = delta(
                relation(StorageRelationConstraint.NOTHING, "customers"),
                relation(StorageRelationConstraint.NOTHING, "accounts"));

        assertEquals("accounts", diff.getStorageName());
    }
}
