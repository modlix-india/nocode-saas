package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.modlix.saas.adzump.enums.Cadence;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ScheduleConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = -1928374650192837465L;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String timezone;
    private Cadence optimizationCadence;
}
