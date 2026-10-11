package com.fincity.saas.commons.core.scheduler;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A one-off event to raise at {@link #runAt}, written by
 * CoreServices.Event.ScheduleEvent.
 *
 * <p>
 * Delivery is at least once. An instance claims a due row atomically, raises
 * the event, and the row goes DONE when the EventAction reports success. A
 * failure goes back to PENDING with backoff, and a claim whose lease runs out
 * (the instance died) is simply claimed again, so the action has to tolerate a
 * repeat; {@link #key} is the handle for that.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@Document(collection = ScheduledEvent.COLLECTION)
public class ScheduledEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -3530424914712512385L;

    public static final String COLLECTION = "scheduled_event";

    public enum Status {
        PENDING,
        CLAIMED,
        DONE,
        FAILED,
        CANCELLED
    }

    @Id
    private String id;

    private String appCode;
    private String clientCode;

    /**
     * Raised from the draft surface. Keys are separate per surface, and the
     * event is raised back onto the surface it was scheduled from, the same way
     * EventQueObject carries the marker for an immediate event.
     */
    private boolean draft;

    /** Unique per (appCode, clientCode, draft). Scheduling the same key again replaces the row. */
    private String key;

    private String eventName;
    private Map<String, Object> data; // NOSONAR

    private Instant runAt;
    private Status status;

    /** Claims made so far, including ones whose lease ran out. */
    private int attempts;
    private int maxAttempts;

    private String owner;
    private String claimToken;
    private Instant leaseUntil;

    private String lastError;

    /** The user whose function scheduled it, for audit. The event itself runs without that user. */
    private String scheduledBy;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;
}
