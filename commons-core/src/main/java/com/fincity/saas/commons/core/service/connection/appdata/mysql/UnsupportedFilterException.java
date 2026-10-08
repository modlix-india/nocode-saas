package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.io.Serial;

/**
 * A filter the MySQL backend cannot express yet.
 *
 * Deliberately an exception rather than a silently-dropped clause. The JOOQ DAO
 * elsewhere in the platform returns {@code DSL.noCondition()} for operators it does not
 * handle, which turns an unsupported filter into "match every row": a delete or a page
 * read then quietly operates on the whole table. That failure mode is not acceptable
 * for app data.
 */
public class UnsupportedFilterException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 7418360114471682934L;

    private final transient String detail;

    /**
     * Whether the CALLER's condition is wrong, rather than this backend being
     * short of a feature.
     *
     * The two deserve different answers and were getting the same one. "BETWEEN
     * without a toValue" is a malformed condition - no future version of this
     * backend will support it, because there is nothing to support - yet it came
     * back as 501 "not supported on the MySQL storage backend yet", which reads
     * as a promise. Mongo already answers 400 for the same condition. A caller
     * comparing the two backends should not have to learn that they mean the same
     * thing by different codes.
     */
    private final boolean malformed;

    public UnsupportedFilterException(String detail) {
        this(detail, false);
    }

    private UnsupportedFilterException(String detail, boolean malformed) {
        super(detail);
        this.detail = detail;
        this.malformed = malformed;
    }

    /** The condition itself is wrong: a missing value, an empty list, a bad path. */
    public static UnsupportedFilterException malformed(String detail) {
        return new UnsupportedFilterException(detail, true);
    }

    public String getDetail() {
        return this.detail;
    }

    public boolean isMalformed() {
        return this.malformed;
    }
}
