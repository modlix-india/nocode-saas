package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One pipeline stage of a product template (drives MilestoneMapping and
 * PerformancePolicy stages).
 */
@Data
@Accessors(chain = true)
public class Stage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387612L;

    private String key;
    private String name;
    private int order;
}
