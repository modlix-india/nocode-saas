package com.fincity.saas.commons.core.enums;

/**
 * What a field should actually be stored as in MongoDB.
 *
 * Mongo has no columns, so the MySQL half of a column definition has no counterpart
 * here - but it does have types, and the default is not always the one you want. A
 * value arrives as JSON and is written as whatever JSON said it was: a number becomes
 * a double, and a string stays a string. That is wrong in two expensive ways.
 *
 * {@link #DECIMAL128} is the one that matters. A price declared {@code STRING} with
 * format {@code DECIMAL} is stored as text, so it cannot be summed, averaged or
 * compared with {@code $gt} at all - Mongo compares strings lexically, which puts
 * "9.00" above "10.00". Stored as Decimal128 it aggregates exactly, with none of the
 * rounding a double brings to money.
 *
 * {@link #DATE} is the second. Dates are epoch numbers on this backend, which is why
 * aggregation has to be told whether a field is in seconds or milliseconds and why
 * getting that wrong silently buckets everything into 1970. A real BSON date removes
 * the question.
 *
 * Nothing here changes unless a field asks for it, so no existing storage moves.
 */
public enum MongoBsonType {

    /** Exact decimal. The right type for money, and summable, unlike a string. */
    DECIMAL128,

    /** 64-bit integer, for counts and epoch values that must not lose precision. */
    LONG,

    INT,

    DOUBLE,

    /** A real BSON date, so date aggregation needs no encoding hint. */
    DATE,

    BOOLEAN,

    STRING
}
