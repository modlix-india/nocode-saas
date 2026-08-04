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
    CALL_STATUS(DispatchChannel.CALL);

    private final DispatchChannel channel;

    DispatchEventType(DispatchChannel channel) {
        this.channel = channel;
    }

    public DispatchChannel getChannel() {
        return this.channel;
    }
}
