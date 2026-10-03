package com.fincity.saas.commons.model;

/**
 * Truncation boundary for a date group key.
 *
 * $group groups by exact value, so grouping rows on a raw timestamp that carries
 * a time component yields one group per row. Bucketing truncates first, which is
 * the difference between "revenue by month" and a crash.
 */
public enum DateBucketUnit {
    YEAR("year"),
    QUARTER("quarter"),
    MONTH("month"),
    WEEK("week"),
    DAY("day"),
    HOUR("hour");

    private final String mongoUnit;

    DateBucketUnit(String mongoUnit) {
        this.mongoUnit = mongoUnit;
    }

    /** The literal $dateTrunc accepts for this unit. */
    public String getMongoUnit() {
        return this.mongoUnit;
    }
}
