package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fincity.saas.commons.core.dto.EventRun;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One client's view of an event's schedule, for the appbuilder's Clients tab: what
 * it inherits, what it overrides, what is waiting in a draft, whether the scheduler
 * actually runs it, and how its last run went.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ClientSchedule implements Serializable {

    @Serial
    private static final long serialVersionUID = 6203948571203948571L;

    public enum State {
        /** The scheduler runs it. */
        RUNS,
        PAUSED,
        /** No cron anywhere in its chain. */
        NOT_SCHEDULED,
        /** A cron the scheduler cannot read, or a zone it does not know. */
        INVALID,
        /** Scheduled, but the scheduler never picks this client; see stateReason. */
        NOT_RUNNING
    }

    private String clientCode;
    private String clientName;

    /** Its own copy has no base: a definition of its own rather than an override. */
    private boolean owner;

    /** Its own copy of the definition, null when it only inherits. */
    private String copyId;

    /** The client its copy overrides; null for a definition of its own or no copy. */
    private String baseClientCode;

    /** The owner's copy, which a client without one is forked from. */
    private String baseId;

    /** The schedule fields its own copy stores; null where it inherits. */
    private EventSchedule ownSchedule;

    /** What the scheduler uses for it now: its copy merged over every ancestor's. */
    private EventSchedule liveSchedule;

    /** Its unpublished draft's schedule, when it has a draft. */
    private EventSchedule draftSchedule;
    private Integer draftVersion;

    private boolean hasWriteAccess;

    private State state;
    private String stateReason;

    /** The live schedule in words, in the requested zone. */
    private String description;
    private String draftDescription;

    /** The next few live runs, formatted in the requested zone. */
    private List<String> nextRuns;

    private EventRun lastRun;
}
