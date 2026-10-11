package com.fincity.saas.commons.core.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.util.BooleanUtil;

/**
 * A client's fork of a storage stores only what differs from its base, and reads back by
 * filling in the nulls from the base. The four flags used to default to false, so a fork read
 * from Mongo came back not audited, not app level and not KIRun-only whatever the base said:
 * no _version table, real ON DELETE CASCADE foreign keys instead of the service cascade, and
 * direct REST access to a table the base had closed.
 */
@DisplayName("Storage flags on a client fork")
class StorageFlagsOverrideTest {

    private static Storage base() {
        Storage s = new Storage().setIsAudited(true).setIsVersioned(false).setIsAppLevel(true)
                .setOnlyThruKIRun(true);
        s.setName("deal").setAppCode("app").setClientCode("SYSTEM");
        return s;
    }

    @Test
    @DisplayName("an unset flag is null, the way Spring Data reads a field the document does not carry")
    void unsetIsNull() {
        Storage s = new Storage();

        assertNull(s.getIsAudited());
        assertNull(s.getIsVersioned());
        assertNull(s.getIsAppLevel());
        assertNull(s.getOnlyThruKIRun());
        assertFalse(BooleanUtil.safeValueOf(s.getIsAudited()), "null still reads as false for a base storage");
    }

    @Test
    @DisplayName("a fork that says nothing inherits every flag from its base")
    void forkInherits() {
        Storage fork = new Storage();
        fork.setName("deal").setAppCode("app").setClientCode("FIN2").setBaseClientCode("SYSTEM");

        Storage merged = fork.applyActualOverride(base()).block();

        assertEquals(Boolean.TRUE, merged.getIsAudited());
        assertEquals(Boolean.FALSE, merged.getIsVersioned());
        assertEquals(Boolean.TRUE, merged.getIsAppLevel());
        assertEquals(Boolean.TRUE, merged.getOnlyThruKIRun());
    }

    @Test
    @DisplayName("a fork may still turn a flag off, and only that flag")
    void forkOverridesOne() {
        Storage fork = new Storage().setIsAudited(false);
        fork.setName("deal").setAppCode("app").setClientCode("FIN2").setBaseClientCode("SYSTEM");

        Storage merged = fork.applyActualOverride(base()).block();

        assertEquals(Boolean.FALSE, merged.getIsAudited());
        assertEquals(Boolean.TRUE, merged.getOnlyThruKIRun());
    }

    @Test
    @DisplayName("saving a fork equal to its base stores no flags, so later base changes still reach it")
    void equalFlagsAreNotStored() {
        Storage fork = new Storage(base());
        fork.setClientCode("FIN2").setBaseClientCode("SYSTEM");

        Storage diff = fork.makeActualOverride(base()).block();

        assertNull(diff.getIsAudited());
        assertNull(diff.getIsVersioned());
        assertNull(diff.getIsAppLevel());
        assertNull(diff.getOnlyThruKIRun());
    }
}
