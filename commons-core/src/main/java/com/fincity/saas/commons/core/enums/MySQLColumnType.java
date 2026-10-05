package com.fincity.saas.commons.core.enums;

import java.util.Set;

/**
 * The MySQL column types a storage definition is allowed to ask for.
 *
 * An allow-list rather than a free-text column type, and that is a security boundary
 * rather than tidiness. The chosen type is rendered into DDL that cannot be
 * parameterised - no driver binds a column type - so a raw string would put whoever
 * can edit a storage definition one comma away from {@code ALTER TABLE t MODIFY c INT,
 * DROP COLUMN secret}. Every value here is a bare keyword and every argument is an
 * integer, so the rendered fragment is closed by construction.
 *
 * Arity is part of the type for a reason that is not obvious: MySQL reports a column
 * back through {@code information_schema.COLUMN_TYPE} in its own normalised form, and
 * the migration diff compares the declared type against that text. Declare
 * {@code DECIMAL} with no precision and MySQL stores {@code decimal(10,0)}, so every
 * subsequent reconcile would see a difference and ALTER the table again, forever. The
 * types whose defaults are invisible are therefore the ones that REQUIRE their
 * arguments here.
 */
public enum MySQLColumnType {
    TINYINT(Arity.NONE, true),
    SMALLINT(Arity.NONE, true),
    MEDIUMINT(Arity.NONE, true),
    INT(Arity.NONE, true),
    BIGINT(Arity.NONE, true),

    DECIMAL(Arity.PRECISION_SCALE, true),
    FLOAT(Arity.NONE, true),
    DOUBLE(Arity.NONE, true),

    CHAR(Arity.LENGTH, false),
    VARCHAR(Arity.LENGTH, false),
    TINYTEXT(Arity.NONE, false),
    TEXT(Arity.NONE, false),
    MEDIUMTEXT(Arity.NONE, false),
    LONGTEXT(Arity.NONE, false),

    BINARY(Arity.LENGTH, false),
    VARBINARY(Arity.LENGTH, false),
    TINYBLOB(Arity.NONE, false),
    BLOB(Arity.NONE, false),
    MEDIUMBLOB(Arity.NONE, false),
    LONGBLOB(Arity.NONE, false),

    DATE(Arity.NONE, false),
    DATETIME(Arity.FRACTIONAL_SECONDS, false),
    TIME(Arity.FRACTIONAL_SECONDS, false),
    TIMESTAMP(Arity.FRACTIONAL_SECONDS, false),
    YEAR(Arity.NONE, false),

    JSON(Arity.NONE, false);

    public enum Arity {
        /** No arguments at all. */
        NONE,
        /** {@code length} is required, because MySQL's own default is not visible. */
        LENGTH,
        /** Both {@code precision} and {@code scale} are required, for the same reason. */
        PRECISION_SCALE,
        /** {@code precision} is optional and means fractional-second digits, 0 to 6. */
        FRACTIONAL_SECONDS
    }

    /** Types that can hold a string, and so can carry a collation. */
    private static final Set<MySQLColumnType> TEXTUAL =
            Set.of(CHAR, VARCHAR, TINYTEXT, TEXT, MEDIUMTEXT, LONGTEXT);

    /** The widest a VARCHAR may be declared before TEXT is the honest answer. */
    public static final int MAX_VARCHAR = 16000;

    public static final int MAX_PRECISION = 65;

    public static final int MAX_FRACTIONAL_SECONDS = 6;

    private final Arity arity;
    private final boolean numeric;

    MySQLColumnType(Arity arity, boolean numeric) {
        this.arity = arity;
        this.numeric = numeric;
    }

    public Arity arity() {
        return this.arity;
    }

    /** Whether {@code unsigned} means anything for this type. */
    public boolean numeric() {
        return this.numeric;
    }

    public boolean textual() {
        return TEXTUAL.contains(this);
    }

    /** Whether a value in this column comes back from the driver as a BigDecimal. */
    public boolean exactDecimal() {
        return this == DECIMAL;
    }

    public boolean temporal() {
        return this == DATE || this == DATETIME || this == TIME || this == TIMESTAMP;
    }
}
