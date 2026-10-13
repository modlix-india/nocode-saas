package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How one task of an EventAction went in one run. Stored as an element of
 * core_event_runs.TASKS, in the order the tasks ran.
 *
 * <p>
 * Times are epoch milliseconds rather than LocalDateTime: the shared Jackson
 * module writes LocalDateTime as whole seconds, and a task that took 40ms would
 * read as taking none.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EventRunTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 7391054682219036781L;

    public enum Status {
        SUCCESS,
        FAILED
    }

    /** The task's key in the action's task map. */
    private String key;
    private String type;
    private Integer order;
    private Status status;
    private Long startedAt;
    private Long durationMs;
    private String error;
    /** Display text, filled with the run's; see EventRun. */
    private String durationText;
}
