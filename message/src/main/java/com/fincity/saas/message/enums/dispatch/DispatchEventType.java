package com.fincity.saas.message.enums.message.provider.whatsapp;

/**
 * What a queued handoff carries to the owning service.
 *
 * <p>Both kinds ride the same outbox and key on the same Meta message id, so a status update that
 * overtakes the message it belongs to still reconciles: the consumer upserts on that id rather than
 * assuming the message already exists.
 */
public enum WhatsappOutboxEventType {

    /** A customer message. May create a deal on the consumer side if the number is unknown. */
    INBOUND_MESSAGE,

    /** A delivery state change from Meta: sent, delivered, read or failed. */
    MESSAGE_STATUS
}
