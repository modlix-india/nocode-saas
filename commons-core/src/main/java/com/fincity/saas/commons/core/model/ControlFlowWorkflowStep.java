package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.enums.ControlFlowType;
import com.fincity.saas.commons.core.enums.WorkflowStepType;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import java.io.Serial;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ControlFlowWorkflowStep extends WorkflowStep {

    @Serial
    private static final long serialVersionUID = 2178854708270744216L;

    private ControlFlowType flowType;
    private Integer time;
    private ChronoUnit unit;
    private String cronString;
    private String eventType;
    private Map<String, Boolean> children;

    public ControlFlowWorkflowStep(ControlFlowWorkflowStep step) {

        super(step);
        this.flowType = step.flowType;
        this.time = step.time;
        this.unit = step.unit;
        this.cronString = step.cronString;
        this.eventType = step.eventType;

        if (step.children != null) this.children = CloneUtil.cloneMapObject(step.children);
    }

    @Override
    public WorkflowStepType getStepType() {
        return WorkflowStepType.CONTROL_FLOW;
    }
}
