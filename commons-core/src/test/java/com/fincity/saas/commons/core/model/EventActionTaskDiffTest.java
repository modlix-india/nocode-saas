package com.fincity.saas.commons.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fincity.saas.commons.core.enums.EventActionTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A nested IDifferentiable is reached as {@code existing.extractDifference(incoming)},
 * so the RECEIVER is the base and the ARGUMENT is the derived. That is the opposite
 * of a document's {@code extractDifference(base)}, and writing one the other's way
 * round is the single mistake this class keeps attracting.
 *
 * These are round-trip tests on purpose. An assertion that only reads the delta can
 * be satisfied by the wrong value; only extract-then-apply says whether a derived
 * client's edit actually survives, which is the thing that was broken.
 */
class EventActionTaskDiffTest {

    private static EventActionTask task(Integer order, EventActionTaskType type) {
        return new EventActionTask().setOrder(order).setType(type);
    }

    @Test
    @DisplayName("A derived order survives the round trip")
    void keepsDerivedOrder() {

        EventActionTask base = task(1, EventActionTaskType.SEND_EMAIL);
        EventActionTask derived = task(7, EventActionTaskType.SEND_EMAIL);

        EventActionTask delta = base.extractDifference(derived).block();

        assertEquals(7, delta.getOrder(), "the delta must carry the DERIVED order, not the base's");

        EventActionTask rebuilt = delta.applyOverride(base).block();
        assertEquals(7, rebuilt.getOrder());
    }

    @Test
    @DisplayName("A derived type survives the round trip")
    void keepsDerivedType() {

        EventActionTask base = task(1, EventActionTaskType.SEND_EMAIL);
        EventActionTask derived = task(1, EventActionTaskType.CALL_CORE_FUNCTION);

        EventActionTask delta = base.extractDifference(derived).block();

        assertEquals(EventActionTaskType.CALL_CORE_FUNCTION, delta.getType());

        EventActionTask rebuilt = delta.applyOverride(base).block();
        assertEquals(EventActionTaskType.CALL_CORE_FUNCTION, rebuilt.getType());
    }

    @Test
    @DisplayName("Nothing changed means nothing stored, so the base stays in charge")
    void storesNothingWhenEqual() {

        EventActionTask base = task(3, EventActionTaskType.SEND_EMAIL);
        EventActionTask derived = task(3, EventActionTaskType.SEND_EMAIL);

        EventActionTask delta = base.extractDifference(derived).block();

        assertNull(delta.getOrder());
        assertNull(delta.getType());

        EventActionTask rebuilt = delta.applyOverride(base).block();
        assertEquals(3, rebuilt.getOrder());
        assertEquals(EventActionTaskType.SEND_EMAIL, rebuilt.getType());
    }

    /**
     * The old order test was {@code inc.order == null || !inc.order.equals(this.order)},
     * so a derived saying nothing wrote the BASE's order into the delta: redundant
     * residue that then stops tracking the base when the base changes.
     */
    @Test
    @DisplayName("A derived that says nothing about order overrides nothing")
    void silentDerivedWritesNoDelta() {

        EventActionTask base = task(5, EventActionTaskType.SEND_EMAIL);
        EventActionTask derived = task(null, EventActionTaskType.SEND_EMAIL);

        EventActionTask delta = base.extractDifference(derived).block();

        assertNull(delta.getOrder(), "a null derived order is not an override of the base's order");
    }
}
