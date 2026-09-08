package com.fincity.saas.entity.processor.enums.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import lombok.Getter;
import org.jooq.EnumType;

/**
 * Where a queued automated message has got to.
 *
 * <p>Lowercase on the wire, matching {@link WhatsappMessageType} and {@link WhatsappMessageStatus}.
 * Not a stylistic choice: the deal profile compares these strings directly in page expressions, and
 * uppercase JSON would leave every comparison false while every service returned 200. That has
 * happened once on this codebase already.
 */
@Getter
public enum WhatsappOutboxStatus implements EnumType {

    /**
     * Queued, and the resting state for anything being held.
     *
     * <p>There is no separate HELD state on purpose. Which gate is holding a row lives in {@code
     * HOLD_REASON}, so the sweeper has one predicate to select on and a row cannot get stranded in
     * HELD after the reason that caused it has cleared.
     */
    PENDING("pending"),

    SENT("sent"),

    /** Send attempts exhausted, or the session refused it in a way retrying will not fix. */
    FAILED("failed"),

    /**
     * Stopped before it ever went out.
     *
     * <p>Covers the lead going quiet, opting out, or an earlier message in the same packet failing.
     * Distinct from FAILED because nothing went wrong: we decided not to send, and the difference
     * matters when reading back why a customer never got their brochure.
     */
    CANCELLED("cancelled");

    private final String literal;

    WhatsappOutboxStatus(String literal) {
        this.literal = literal;
    }

    @JsonValue
    public String getValue() {
        return this.literal;
    }

    /** Accepts either casing, so an older caller sending "PENDING" still resolves. */
    @JsonCreator
    public static WhatsappOutboxStatus fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        return WhatsappOutboxStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Whether the sweeper should still be looking at this row. */
    public boolean isOpen() {
        return this == PENDING;
    }

    @Override
    public String getLiteral() {
        // The database stores the uppercase name; only JSON is lowercase.
        return this.name();
    }

    @Override
    public String getName() {
        return null;
    }
}
