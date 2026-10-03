package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One group key in an {@link AggregateQuery}.
 *
 * With {@code bucket} unset the rows group on the field's exact value. With it
 * set the field is treated as a date and truncated to that boundary first, which
 * requires {@code encoding} because app data dates are numbers rather than BSON
 * dates and seconds cannot be told from milliseconds by the schema.
 */
@Data
@Accessors(chain = true)
public class GroupByField implements Serializable {

    @Serial
    private static final long serialVersionUID = 2290511855513364023L;

    private String field;

    /** Output key; defaults to the field name with dots replaced. */
    private String alias;

    /** Null groups on the exact value. Anything else truncates the date first. */
    private DateBucketUnit bucket;

    /** Required when {@code bucket} is set, rejected otherwise. */
    private DateEncoding encoding;

    /**
     * IANA zone the truncation happens in, defaulting to UTC.
     *
     * Stored instants are UTC and users are not. An order at 2026-10-01 03:00 IST
     * is 2026-09-30 21:30 UTC, so a month bucket computed in UTC files it under
     * September while the business calls it October. Nothing errors; the totals
     * are simply wrong.
     */
    private String timezone;

    public String resolvedAlias() {
        if (this.alias != null && !this.alias.isBlank()) return this.alias;
        return this.field == null ? null : this.field.replace('.', '_');
    }

    public String resolvedTimezone() {
        return this.timezone == null || this.timezone.isBlank() ? "UTC" : this.timezone;
    }
}
