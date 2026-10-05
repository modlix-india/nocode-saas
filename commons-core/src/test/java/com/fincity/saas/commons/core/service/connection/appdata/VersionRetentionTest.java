package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.document.Storage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersionRetentionTest {

    /** The installation default when nobody has configured one. */
    private static VersionRetention of(Storage storage) {
        return VersionRetention.of(storage, VersionRetention.COMPILED_DEFAULT);
    }

    private static Storage storage(Integer days, Integer count) {
        Storage s = new Storage();
        s.setVersionRetentionDays(days);
        s.setVersionRetentionCount(count);
        return s;
    }

    @Test
    @DisplayName("An unset policy falls back to the suggested 90 days / 50 versions")
    void defaultsWhenUnset() {

        VersionRetention r = of(storage(null, null));

        assertEquals(VersionRetention.DEFAULT_DAYS, r.days());
        assertEquals(VersionRetention.DEFAULT_COUNT, r.count());
        assertTrue(r.trimsByAge());
        assertTrue(r.trimsByCount());
    }

    @Test
    @DisplayName("A null storage still yields the defaults rather than throwing on a write path")
    void defaultsForNullStorage() {
        assertEquals(VersionRetention.DEFAULT_DAYS, of(null).days());
    }

    @Test
    @DisplayName("Each bound is set independently")
    void honoursEachBound() {

        assertEquals(7, of(storage(7, null)).days());
        assertEquals(VersionRetention.DEFAULT_COUNT, of(storage(7, null)).count());

        assertEquals(3, of(storage(null, 3)).count());
        assertEquals(VersionRetention.DEFAULT_DAYS, of(storage(null, 3)).days());
    }

    /**
     * Zero has to mean unlimited, because once something finally deletes version
     * rows there must be a way to say "keep everything" - and the obvious way to
     * write that is 0, not a sentinel nobody would guess.
     */
    @Test
    @DisplayName("Zero means unlimited, on either bound or both")
    void zeroMeansUnlimited() {

        assertFalse(of(storage(0, 10)).trimsByAge());
        assertTrue(of(storage(0, 10)).trimsByCount());

        assertFalse(of(storage(10, 0)).trimsByCount());

        assertTrue(of(storage(0, 0)).keepsEverything());
        assertFalse(of(storage(0, 10)).keepsEverything());
    }

    /**
     * Read as unlimited, not rejected. This is evaluated on the write path, and
     * failing a perfectly good write because a retention number is negative would
     * be a worse outcome than keeping too much history.
     */
    @Test
    @DisplayName("A negative value is read as unlimited, never as an error")
    void negativeIsTreatedAsUnlimited() {

        VersionRetention r = of(storage(-5, -1));

        assertEquals(0, r.days());
        assertEquals(0, r.count());
        assertTrue(r.keepsEverything());
    }

    @Test
    @DisplayName("The age cutoff is that many days back, in UTC")
    void cutoffIsDaysBackInUtc() {

        LocalDateTime cutoff = of(storage(30, null)).cutoff();
        LocalDateTime expected = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);

        assertTrue(
                Math.abs(java.time.Duration.between(cutoff, expected).toSeconds()) < 5,
                "cutoff should be ~30 days before now in UTC");
    }

    /**
     * The point of the whole exercise: the installation decides, and a deployment
     * that has not looked at its version volumes yet can keep everything until it
     * has. A compiled constant here would delete history on the first write.
     */
    @Test
    @DisplayName("The configured default replaces the compiled one for an unset storage")
    void configuredDefaultWins() {

        VersionRetention configured = new VersionRetention(7, 3);

        VersionRetention r = VersionRetention.of(storage(null, null), configured);

        assertEquals(7, r.days());
        assertEquals(3, r.count());
    }

    @Test
    @DisplayName("An installation configured at 0/0 keeps every version")
    void configuredUnlimitedKeepsEverything() {

        VersionRetention r = VersionRetention.of(storage(null, null), new VersionRetention(0, 0));

        assertTrue(r.keepsEverything());
    }

    @Test
    @DisplayName("A storage still overrides the configured default, per bound")
    void storageOverridesConfiguredDefault() {

        VersionRetention configured = new VersionRetention(7, 3);

        assertEquals(30, VersionRetention.of(storage(30, null), configured).days());
        assertEquals(3, VersionRetention.of(storage(30, null), configured).count());

        assertEquals(7, VersionRetention.of(storage(null, 400), configured).days());
        assertEquals(400, VersionRetention.of(storage(null, 400), configured).count());
    }

    /**
     * A storage asking for unlimited has to win over a configured bound, or an
     * installation-wide default silently trims the one storage that said not to.
     */
    @Test
    @DisplayName("A storage at 0 keeps everything even when the installation bounds it")
    void storageZeroBeatsConfiguredBound() {

        VersionRetention r = VersionRetention.of(storage(0, 0), new VersionRetention(90, 50));

        assertTrue(r.keepsEverything());
    }

    @Test
    @DisplayName("A null default falls back to the compiled one rather than NPEing a write")
    void nullDefaultFallsBackToCompiled() {

        VersionRetention r = VersionRetention.of(storage(null, null), null);

        assertEquals(VersionRetention.DEFAULT_DAYS, r.days());
        assertEquals(VersionRetention.DEFAULT_COUNT, r.count());
    }

    @Test
    @DisplayName("The bean clamps a negative configured value to unlimited, not to an error")
    void beanClampsNegativeConfiguration() {

        assertTrue(new VersionRetentionDefaults(-1, -1).get().keepsEverything());

        VersionRetentionDefaults defaults = new VersionRetentionDefaults(14, 5);

        assertEquals(14, defaults.get().days());
        assertEquals(5, defaults.get().count());

        assertEquals(14, defaults.forStorage(storage(null, null)).days());
        assertEquals(5, defaults.forStorage(storage(null, null)).count());

        assertEquals(2, defaults.forStorage(storage(2, null)).days());
        assertEquals(5, defaults.forStorage(storage(2, null)).count());
    }

    /**
     * The property strings carry the fallback inline because an annotation needs a
     * compile-time constant. If the two ever drift, an unconfigured install stops
     * matching what the constants say it does.
     */
    @Test
    @DisplayName("The property defaults match the declared constants")
    void propertyDefaultsMatchConstants() {

        assertEquals("${core.appdata.versionRetention.days:90}", VersionRetention.DAYS_PROPERTY);
        assertEquals("${core.appdata.versionRetention.count:50}", VersionRetention.COUNT_PROPERTY);

        assertTrue(VersionRetention.DAYS_PROPERTY.endsWith(":" + VersionRetention.DEFAULT_DAYS + "}"));
        assertTrue(VersionRetention.COUNT_PROPERTY.endsWith(":" + VersionRetention.DEFAULT_COUNT + "}"));
    }
}
