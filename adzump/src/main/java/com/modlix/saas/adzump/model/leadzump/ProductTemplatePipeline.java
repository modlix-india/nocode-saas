package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The pipeline (stages + statuses) of a product template - the source the
 * MilestoneMapping and PerformancePolicy stage keys are validated against.
 */
@Data
@Accessors(chain = true)
public class ProductTemplatePipeline implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387614L;

    private String templateId;
    private List<Stage> stages;
    private List<Status> statuses;
}
