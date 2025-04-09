package com.fincity.saas.commons.core.model;

import com.fincity.nocode.kirun.engine.model.Position;
import com.fincity.saas.commons.core.enums.WorkflowStepType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public abstract class WorkflowStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 4819827598636692079L;

    private String comment;
    private String description;
    private Position position;
    private String stepName;

    protected WorkflowStep(WorkflowStep step) {
        this.comment = step.comment;
        this.description = step.description;
        this.position = step.position;
        this.stepName = step.stepName;
    }

    public abstract WorkflowStepType getStepType();
}
