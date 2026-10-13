package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What the schedule builder shows for one schedule: the cron to store and its zone,
 * the builder model in the display zone, a readable line, the next runs, and
 * whether the scheduler can read it at all.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SchedulePreview implements Serializable {

    @Serial
    private static final long serialVersionUID = 5172839405617283940L;

    /** The cron to store; empty for "not scheduled". */
    private String cron;
    /** The zone the cron is read in: always UTC for one the builder made. */
    private String timezone;

    private ScheduleModel model;
    private String displayZone;
    private String description;
    private List<String> nextRuns;

    /** The display zone moves its clocks for daylight saving, so a UTC cron drifts an hour on its local clock. */
    private boolean observesDst;

    private boolean valid;
    private String error;
}
