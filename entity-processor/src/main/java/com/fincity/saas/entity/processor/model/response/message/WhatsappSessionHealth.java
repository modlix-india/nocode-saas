package com.fincity.saas.entity.processor.model.response.message;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How a linked number is doing, and how close it is to the limits that keep it alive.
 *
 * <p>One shape feeding two surfaces on purpose: the standing panel on the integration page and the
 * override panel in the composer. They must not be able to disagree, because the whole point of the
 * override panel is that somebody is about to make a decision on the strength of these numbers.
 *
 * <p>Everything here is computed rather than stored. Reply rate, today's first contacts and the
 * warm-up band all derive from the message table, which means they cannot drift out of step with
 * what actually happened.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappSessionHealth implements Serializable {

    @Serial
    private static final long serialVersionUID = 4471553288845163930L;

    private String sessionId;
    private String phoneNumber;

    /** PAIRING, CONNECTED, DISCONNECTED, LOGGED_OUT, BANNED or COUNTRY_MISMATCH. */
    private String state;

    private String stateReason;
    private LocalDateTime linkedAt;

    /**
     * Day within the fourteen-day ramp, 1-based, or null once the number is past it.
     *
     * <p>Derived from {@code linkedAt} and deliberately not settable. Cold start is when a ban is
     * most likely, so a field somebody can raise is a field somebody will raise on the afternoon it
     * matters most.
     */
    private Integer warmUpDay;

    /**
     * Total messages allowed today, or null once the number is past its ramp.
     *
     * <p>A different limit from {@link #firstContactCap} and easy to conflate. This bounds every
     * message a young number sends; that one bounds only conversations started from scratch, and
     * applies for the life of the number.
     */
    private Integer warmUpCap;

    /** Every message sent today, replies included. Measured against {@link #warmUpCap}. */
    private Integer sentToday;

    /** New conversations opened today, against the cap. The number that matters most when forcing. */
    private Integer firstContactsToday;

    private Integer firstContactCap;

    private Integer sentLastHour;
    private Integer hourlyCap;

    /**
     * Replies divided by sends over a rolling window.
     *
     * <p>The primary health signal. Above the floor it is informational; below it, it halves the
     * caps, and below the lower floor it suspends automated outreach entirely and raises it to a
     * person.
     */
    private Double replyRate;

    /** HEALTHY, LOW or CRITICAL, so the UI does not have to know where the thresholds are. */
    private String replyRateBand;

    /** Messages with no reply inside 48 hours, over a rolling 30 days. Part of what throttles a number. */
    private Integer unansweredRolling30d;

    private Integer recentFailures;

    private LocalDateTime lastOutboundAt;
    private LocalDateTime lastInboundAt;

    /** When the 24-hour rule would release on its own, or null if nothing is being held. */
    private LocalDateTime heldUntil;

    private String holdReason;

    /** Human-readable form of {@link #holdReason}, so two surfaces cannot word it differently. */
    private String holdExplanation;

    private String quietHoursStart;
    private String quietHoursEnd;

    /**
     * The clock those hours were judged on, which is the lead's and not ours.
     *
     * <p>Worth showing rather than leaving implicit. A salesperson in Bangalore told a message is
     * held until 09:00 will read that as 09:00 their time, and for a Gulf lead it is not: the two
     * are ninety minutes apart. Naming the zone is the difference between a countdown somebody can
     * check and one they learn to distrust.
     */
    private String quietHoursZone;

    /** Whether this deal has asked not to be contacted. Shown even when nothing is queued. */
    private Boolean optedOut;

    /** Whether automated sending is currently suspended for this number, for any reason. */
    private Boolean automationSuspended;
}
