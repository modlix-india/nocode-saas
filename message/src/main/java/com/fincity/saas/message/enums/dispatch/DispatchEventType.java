package com.fincity.saas.message.enums.dispatch;

/**
 * What a queued handoff carries to the owning service.
 *
 * <p>Every kind rides the same outbox and keys on the provider's own id for the thing, so an event
 * that overtakes the one it belongs to still reconciles: the consumer upserts on that id rather than
 * assuming the subject already exists.
 */
public enum DispatchEventType {

    /** A customer message. May create a deal on the consumer side if the number is unknown. */
    INBOUND_MESSAGE(DispatchChannel.WHATSAPP),

    /** A delivery state change from Meta: sent, delivered, read or failed. */
    MESSAGE_STATUS(DispatchChannel.WHATSAPP),

    /**
     * A call state change from Exotel: ringing through to completed, with duration and recording.
     *
     * <p>There is no inbound counterpart to {@code INBOUND_MESSAGE} here, because an inbound call
     * reaches the owning service first: it answers the connect applet in order to decide whom to
     * ring, so by the time this service is involved the consumer already has its record.
     */
    CALL_STATUS(DispatchChannel.CALL),

    /**
     * Where an attachment ended up, for a message that already arrived without it.
     *
     * <p>Deliberately not folded into {@code INBOUND_MESSAGE} with the file attached. A photo is two
     * things arriving on different timescales - a message that must appear at once, and bytes that
     * may be tens of megabytes - and holding the first for the second turns every attachment into a
     * gap in the conversation. So the message goes immediately and this follows.
     *
     * <p>Carries the same message id as the event it completes, which is what lets the consumer
     * patch rather than insert. That also makes it safe to redeliver and safe to arrive out of order
     * against a status update, since neither touches the other's fields.
     */
    MEDIA_READY(DispatchChannel.WHATSAPP),

    /**
     * A customer's WhatsApp profile picture.
     *
     * <p>The one event here that belongs to a person rather than to a message, so it carries no
     * message id and is keyed on the customer's number alone. The consumer applies it to whatever
     * that number stands behind, which is more than one record: a customer can hold several deals
     * and they should not show different faces.
     *
     * <p>Its stored file deliberately sits outside the conversation's media tree, because attachment
     * retention must not reach it. An expired photo in a thread is honest history; a deal that
     * silently loses its avatar after thirty days is a bug.
     */
    PROFILE_PICTURE(DispatchChannel.WHATSAPP);

    private final DispatchChannel channel;

    DispatchEventType(DispatchChannel channel) {
        this.channel = channel;
    }

    public DispatchChannel getChannel() {
        return this.channel;
    }
}
