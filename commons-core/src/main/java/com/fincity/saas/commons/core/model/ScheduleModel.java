package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A schedule as the appbuilder's builder shows it, read in one time zone. See
 * ScheduleConverter for how it maps to and from the stored UTC cron.
 *
 * <p>
 * {@code frequency} is one of off, minutes, hours, weekly, monthly, custom.
 * {@code dayOfMonth} is a string ("1" to "28", or "L" for the last day) so a plain
 * Dropdown can carry it without a number/string mismatch.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ScheduleModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1928374650192837465L;

    private String frequency;
    /** minutes: every N minutes; hours: every N hours. */
    private Integer every;
    /** The minute of the hour a run starts. */
    private Integer minute;
    /** weekly and monthly: the hour; hours: the first hour of the day it runs. */
    private Integer hour;
    /** weekly: 0 (Sunday) to 6. All seven, or none, is every day. */
    private List<Integer> days;
    private String dayOfMonth;
    /** custom: the raw cron, in UTC. */
    private String cron;
}
