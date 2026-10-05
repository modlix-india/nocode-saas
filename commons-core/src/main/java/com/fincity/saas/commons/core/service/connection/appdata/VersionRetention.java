package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.saas.commons.core.document.Storage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * How much row history a storage keeps, resolved once for both backends.
 *
 * Version tables grew without bound: every update to an audited or versioned row
 * added a row that nothing ever removed. On a storage written to continuously the
 * history outgrows the data it describes, and the first anyone hears of it is disk.
 *
 * Both bounds apply together and a version row must satisfy BOTH to survive -
 * within the most recent {@link #count()} AND newer than {@link #cutoff}. Either
 * alone leaves a hole: a count alone keeps a decade of history for a row touched
 * twice a year, an age alone keeps a million versions of a row rewritten every
 * minute.
 *
 * {@code null} on the definition means "use the installation default"; {@code 0}
 * means unlimited, which is the only way to say "keep everything" now that
 * something finally removes rows. A negative value is read as 0 rather than
 * rejected: this runs on the write path, and refusing a write because a retention
 * number is odd would be a worse failure than keeping too much history.
 *
 * The installation default is configuration, not a constant - see
 * {@link VersionRetentionDefaults}. The numbers below are only what applies when
 * nobody has configured anything, and trimming history is irreversible, so an
 * installation that has not yet looked at its version volumes should be able to
 * start at {@code 0/0} and switch retention on deliberately.
 */
public record VersionRetention(int days, int count) {

    /** What applies when the installation has configured nothing. */
    public static final int DEFAULT_DAYS = 90;

    public static final int DEFAULT_COUNT = 50;

    /**
     * Compile-time constants so the property name and its fallback are written once
     * and read straight into the {@code @Value} on {@link VersionRetentionDefaults}.
     */
    public static final String DAYS_PROPERTY = "${core.appdata.versionRetention.days:" + DEFAULT_DAYS + "}";

    public static final String COUNT_PROPERTY = "${core.appdata.versionRetention.count:" + DEFAULT_COUNT + "}";

    /** The fallback as a policy, for the paths that have no configuration to read. */
    public static final VersionRetention COMPILED_DEFAULT = new VersionRetention(DEFAULT_DAYS, DEFAULT_COUNT);

    /**
     * The policy for one storage, falling back per field to the installation
     * default.
     *
     * Per field, not per storage: a storage that sets only a count still wants the
     * configured age bound, and reading one set field as "this storage has opted out
     * of the other bound" is how an unbounded history comes back.
     */
    public static VersionRetention of(Storage storage, VersionRetention defaults) {

        VersionRetention fallback = defaults == null ? COMPILED_DEFAULT : defaults;

        if (storage == null) return fallback;

        return new VersionRetention(
                resolve(storage.getVersionRetentionDays(), fallback.days()),
                resolve(storage.getVersionRetentionCount(), fallback.count()));
    }

    /** Clamps a configured or declared pair, so no caller can build a negative bound. */
    public static VersionRetention clamped(int days, int count) {
        return new VersionRetention(Math.max(days, 0), Math.max(count, 0));
    }

    private static int resolve(Integer declared, int fallback) {
        if (declared == null) return fallback;
        return Math.max(declared, 0);
    }

    public boolean trimsByAge() {
        return this.days > 0;
    }

    public boolean trimsByCount() {
        return this.count > 0;
    }

    /** Nothing to do at all, so the write path can skip the trim entirely. */
    public boolean keepsEverything() {
        return !this.trimsByAge() && !this.trimsByCount();
    }

    /** Version rows older than this go. UTC, because that is what the rows carry. */
    public LocalDateTime cutoff() {
        return LocalDateTime.now(ZoneOffset.UTC).minusDays(this.days);
    }

    /** The same instant as {@link #cutoff()}, for Mongo, which stores epoch millis. */
    public long cutoffEpochMillis() {
        return this.cutoff().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
