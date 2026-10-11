package com.fincity.saas.commons.core.scheduler;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One tick of one recurring schedule for one client, and the claim on it.
 *
 * <p>
 * The unique index on (appCode, clientCode, eventName, tickAt) is the whole
 * coordination story: every core instance computes the same tickAt and tries to
 * insert this row, exactly one insert succeeds, and only that instance raises
 * the event. See {@link ScheduleStore#ensureIndexes()}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@Document(collection = ScheduleRun.COLLECTION)
public class ScheduleRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 2297093581208418707L;

    public static final String COLLECTION = "schedule_run";

    public enum Status {
        /** Claimed and raised; the EventAction has not reported back yet. */
        RUNNING,
        /** The EventAction finished. */
        DONE,
        /** Not run: the previous tick was still RUNNING inside its lease. */
        SKIPPED,
        /** The event could not be raised, or the EventAction reported a failure. */
        FAILED
    }

    @Id
    private String id;

    private String appCode;
    private String clientCode;
    private String eventName;
    private Instant tickAt;

    private Status status;

    /** Until when a RUNNING row keeps the next tick from starting. */
    private Instant leaseUntil;

    /** The instance that claimed it, for diagnosis only. */
    private String owner;

    private Instant createdAt;
    private Instant finishedAt;
    private String error;
}
