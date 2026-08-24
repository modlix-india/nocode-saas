package com.fincity.saas.entity.processor.enums.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Delivery state of a WhatsApp message, as reported by Meta's status webhooks.
 *
 * <p>Ordered by progression, which {@link #isAfter} relies on: a status webhook can arrive out of
 * order, and a late {@code SENT} must not overwrite a {@code READ} already recorded.
 *
 * <p>Serialised lowercase for the same reason as
 * {@link WhatsappMessageType} - the message service published it that way and the deal profile
 * draws its tick marks from {@code Parent.messageStatus = "read"} and friends. Uppercase here left
 * every message showing the failed state's styling or none at all.
 */
public enum WhatsappMessageStatus {
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed"),
    DELETED("deleted");

    private final String value;

    WhatsappMessageStatus(String value) {
        this.value = value;
    }

    /** Accepts either case, so a payload written against either contract still parses. */
    @JsonCreator
    public static WhatsappMessageStatus of(String value) {
        if (value == null || value.isBlank()) return SENT;
        return WhatsappMessageStatus.valueOf(value.toUpperCase());
    }

    /**
     * The wire form, lowercase to match the message service.
     *
     * <p>Jackson only - jOOQ converts through {@link #name()}, so the stored literals are
     * unchanged.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Whether this state is further along than {@code other}, so an out-of-order webhook can be
     * ignored rather than regressing a message.
     *
     * <p>FAILED and DELETED are terminal and always win: they describe an outcome, not a step, and
     * a delivery receipt arriving afterwards does not undo them.
     */
    public boolean isAfter(WhatsappMessageStatus other) {
        if (other == null) return true;
        if (this == FAILED || this == DELETED) return true;
        if (other == FAILED || other == DELETED) return false;
        return this.ordinal() > other.ordinal();
    }
}
