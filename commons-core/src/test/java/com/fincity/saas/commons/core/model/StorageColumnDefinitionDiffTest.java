package com.fincity.saas.commons.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.enums.MySQLColumnType;

/**
 * The delta a derived client stores, and what it merges back to.
 *
 * Every test here calls {@code base.extractDifference(derived)}, because that is the
 * order the framework uses: {@code DifferenceExtractor} calls
 * {@code existing.extractDifference(incoming)}. The first version of this class had
 * the operands the other way round, and so did the first version of this test - so
 * the test passed while a client's override was silently replaced by its base on
 * every save. Writing the call the way the caller writes it is the whole point.
 */
@DisplayName("Column definition differencing")
class StorageColumnDefinitionDiffTest {

    private static StorageColumnDefinition varchar(Integer length) {
        return new StorageColumnDefinition()
                .setMysql(new StorageColumnDefinition.MySQL()
                        .setType(MySQLColumnType.VARCHAR)
                        .setLength(length));
    }

    /** As the framework calls it: the BASE is the receiver, the derived is the argument. */
    private static StorageColumnDefinition delta(StorageColumnDefinition base, StorageColumnDefinition derived) {
        return base.extractDifference(derived).block();
    }

    @Test
    @DisplayName("the delta keeps the value the DERIVED document chose")
    void deltaKeepsTheDerivedValue() {
        StorageColumnDefinition diff = delta(varchar(120), varchar(333));

        assertNotNull(diff);
        assertNotNull(diff.getMysql(), "the mysql half must survive extraction");
        assertEquals(333, diff.getMysql().getLength(), "333 is what the client asked for; 120 is the base");
    }

    @Test
    @DisplayName("a value identical to the base is dropped, because repeating it is not an override")
    void identicalIsDropped() {
        assertNull(delta(varchar(120), varchar(333)).getMysql().getType(), "the type matches, so it is not stored");
    }

    @Test
    @DisplayName("applying that delta back over the base restores both halves")
    void applyRestores() {
        StorageColumnDefinition merged =
                delta(varchar(120), varchar(333)).applyOverride(varchar(120)).block();

        assertEquals(MySQLColumnType.VARCHAR, merged.getMysql().getType(), "type comes from the base");
        assertEquals(333, merged.getMysql().getLength(), "length is the one the client pinned");
    }

    @Test
    @DisplayName("a derived document that changes nothing produces an empty delta")
    void identicalProducesNothing() {
        StorageColumnDefinition diff = delta(varchar(120), varchar(120));

        assertNull(diff.getMysql().getLength());
        assertNull(diff.getMysql().getType());
    }

    @Test
    @DisplayName("a derived document that says nothing overrides nothing")
    void silentDerivedOverridesNothing() {
        // Not the base's own value: a delta carrying the base back would make every
        // later change to the base invisible to this client.
        StorageColumnDefinition diff = varchar(120).extractDifference(null).block();

        assertNull(diff.getMysql());
    }
}
