package com.fincity.saas.entity.processor.enums.message;

/**
 * Delivery state of a WhatsApp message, as reported by Meta's status webhooks.
 *
 * <p>Ordered by progression, which {@link #isAfter} relies on: a status webhook can arrive out of
 * order, and a late {@code SENT} must not overwrite a {@code READ} already recorded.
 */
public enum WhatsappMessageStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED,
    DELETED;

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
