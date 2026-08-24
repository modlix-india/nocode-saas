package com.fincity.saas.entity.processor.enums.message;

/**
 * Why a queued message was not released on the last sweep.
 *
 * <p>Constants rather than an enum, matching the column. The set grows whenever a cap is tuned, and
 * an enum would mean a schema change plus a redeploy to record a new reason. Faced with that
 * friction the tempting shortcut is to leave the reason null, and a held row with no reason is
 * exactly what makes this design unexplainable months later.
 *
 * <p>Every one of these is surfaced to a person, either in the override panel or in the outbox view,
 * so they are written to be read rather than parsed.
 */
public final class WhatsappHoldReason {

    /** The 24-hour rule. The lead has not replied and it has not been a day since we last wrote. */
    public static final String WAITING_24H = "WAITING_24H";

    /** This number has already opened as many new conversations today as it is allowed. */
    public static final String NEW_CONTACT_CAP = "NEW_CONTACT_CAP";

    /** The number is inside its fourteen-day ramp and at its band's ceiling for the day. */
    public static final String WARM_UP_CAP = "WARM_UP_CAP";

    /**
     * Replies per send has fallen below the floor.
     *
     * <p>The metric every source agrees on, and the one gate nobody builds. A number sending into
     * silence is the exact profile that gets banned.
     */
    public static final String REPLY_RATE_LOW = "REPLY_RATE_LOW";

    /** Outside the tenant's business hours. Rescheduled to the window opening, not dropped. */
    public static final String QUIET_HOURS = "QUIET_HOURS";

    /**
     * The lead has not answered the last few messages, so the sequence stops.
     *
     * <p>Chasing a silent lead is not merely useless: unanswered messages accumulate on a rolling
     * window and are themselves part of what throttles the account.
     */
    public static final String LEAD_QUIET = "LEAD_QUIET";

    /** The lead asked us to stop. Permanent, and survives a stage change. */
    public static final String OPTED_OUT = "OPTED_OUT";

    /** An earlier message in this packet failed, so the rest are not fired into the same wall. */
    public static final String PREVIOUS_FAILED = "PREVIOUS_FAILED";

    /** No linked session for this product, or the session is not connected. */
    public static final String SESSION_NOT_READY = "SESSION_NOT_READY";

    /** The hourly ceiling on the session. Layer 1 also enforces this; here it avoids a pointless call. */
    public static final String HOURLY_CAP = "HOURLY_CAP";

    private WhatsappHoldReason() {
        // Constants only.
    }

    /** A sentence for the person looking at the deal, rather than the constant. */
    public static String explain(String reason) {
        if (reason == null) return null;

        return switch (reason) {
            case WAITING_24H -> "Waiting 24 hours since the last message, because this lead has not replied yet.";
            case NEW_CONTACT_CAP -> "This number has already started as many new conversations today as it should.";
            case WARM_UP_CAP -> "This number was linked recently and is still building up its daily volume.";
            case REPLY_RATE_LOW -> "Too few of this number's messages are getting replies, so automated sending is"
                    + " throttled.";
            case QUIET_HOURS -> "Outside business hours. It will go when the window opens.";
            case LEAD_QUIET -> "This lead has not answered the last few messages, so the sequence stopped.";
            case OPTED_OUT -> "This lead asked not to be contacted on WhatsApp.";
            case PREVIOUS_FAILED -> "An earlier message in this sequence failed to send.";
            case SESSION_NOT_READY -> "The WhatsApp number for this product is not connected.";
            case HOURLY_CAP -> "This number has sent its maximum for the hour.";
            default -> reason;
        };
    }
}
