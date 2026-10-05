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

    public UnsupportedFilterException(String detail) {
        super(detail);
        this.detail = detail;
    }

    public String getDetail() {
        return this.detail;
    }
}
