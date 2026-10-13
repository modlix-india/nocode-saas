package com.fincity.saas.commons.core.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsTriggerType;
import com.fincity.saas.commons.core.model.EventRunTask;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One run of an event's EventAction, or an outcome that kept it from running.
 * See V9__Event Runs.sql for what each column means. Times are UTC.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EventRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 2861150843710357921L;

    private ULong id;
    private String appCode;
    private String clientCode;
    private String eventName;
    private CoreEventRunsTriggerType triggerType;
    private CoreEventRunsStatus status;
    private String scheduleRunId;
    private LocalDateTime scheduledFor;
    private Integer attempt;
    private Boolean isDraft;
    private ULong triggeredBy;
    private Integer taskCount;
    private Integer failedTaskCount;
    private List<EventRunTask> tasks;
    private String error;
    private String instanceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    /**
     * Display text, filled when the reader names a time zone: the run log screen
     * is built from basic components, which cannot format a time or a duration.
     */
    private String startedText;
    private String scheduledForText;
    private String durationText;
}
