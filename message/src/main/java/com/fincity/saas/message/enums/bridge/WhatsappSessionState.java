package com.fincity.saas.message.enums.bridge;

/**
 * Lifecycle of a linked WhatsApp number, mirroring the bridge's own state machine.
 *
 * <p>The names match the Go side exactly and are surfaced verbatim to the UI. Keep the two in step:
 * a state the bridge reports and this enum does not know is a deserialisation failure on the
 * heartbeat, which would take the whole fleet's state reporting down over one added string.
 */
public enum WhatsappSessionState {

    /** A QR code is being shown and nobody has scanned it yet. */
    PAIRING,

    /** The socket is up and the number can send and receive. */
    CONNECTED,

    /**
     * Transient. whatsmeow reconnects on its own; this exists so the UI can tell "briefly offline"
     * from "gone", which look identical to a customer otherwise.
     */
    DISCONNECTED,

    /** The customer unlinked us from their phone. Terminal: it needs a new QR scan. */
    LOGGED_OUT,

    /**
     * WhatsApp took the number away.
     *
     * <p>Separate from {@link #LOGGED_OUT} because the customer did not do it and cannot undo it,
     * and because it is the outcome the entire pacing design exists to avoid. Worth alerting on
     * differently for that reason alone.
     */
    BANNED,

    /**
     * A number was linked whose country the holding instance does not serve.
     *
     * <p>Its own state on purpose. It is the only failure here a customer can fix in seconds, and
     * only if told which country the instance serves and which number was actually scanned. Shown as
     * a generic error it is unfixable.
     */
    COUNTRY_MISMATCH;

    /** Terminal states hold a slot without being able to use it, so the reaper reclaims them. */
    public boolean isTerminal() {
        return this == LOGGED_OUT || this == BANNED || this == COUNTRY_MISMATCH;
    }

    /** Whether a message may be handed to this session at all. */
    public boolean isSendable() {
        return this == CONNECTED;
    }
}
