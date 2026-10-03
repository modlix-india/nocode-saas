package com.fincity.saas.commons.model;

/**
 * How a date field is physically stored, which the caller must declare because
 * nothing downstream can infer it.
 *
 * App data dates are not BSON dates. BJsonUtil.from has no Date branch and maps
 * JSON primitives straight through, so a date field holds whatever JSON handed
 * it. The Calendar component's storageFormat writes 'x' as epoch MILLISECONDS
 * and 'X' as epoch SECONDS, and the storage schema calls both LONG. Multiplying
 * milliseconds by 1000 lands in the year 56000 without erroring, so the encoding
 * is declared rather than guessed.
 *
 * String-stored dates are deliberately unsupported: they are written through
 * local-time getters and so are already wall-clock in the writer's timezone,
 * which inverts the timezone semantics rather than merely changing the parse.
 */
public enum DateEncoding {

    /** Seconds since the epoch. The platform convention for app data timestamps. */
    EPOCH_SECONDS(1000L),

    /** Milliseconds since the epoch, as Calendar's 'x' storageFormat writes. */
    EPOCH_MILLIS(1L);

    private final long toMillis;

    DateEncoding(long toMillis) {
        this.toMillis = toMillis;
    }

    /** Multiplier that converts a stored value into epoch milliseconds. */
    public long getToMillis() {
        return this.toMillis;
    }
}
