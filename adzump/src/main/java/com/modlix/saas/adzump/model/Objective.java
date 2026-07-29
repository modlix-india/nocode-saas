package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.PlatformObjective;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Objective implements Serializable {

    @Serial
    private static final long serialVersionUID = 5912384756102938465L;

    private PlatformObjective platformObjective;
    private String targetMilestone;
    private Money targetCostPerOutcome;
    private String conversionEvent;
}
