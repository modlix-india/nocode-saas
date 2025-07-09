package com.fincity.saas.commons.core.model;

import com.fincity.nocode.kirun.engine.model.ParameterReference;
import com.fincity.saas.commons.core.enums.WorkflowStepType;
import com.fincity.saas.commons.util.CloneUtil;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ActionWorkflowStep extends WorkflowStep {

    @Serial
    private static final long serialVersionUID = 2178854708270744216L;

    private String actionName;
    private Map<String, Map<String, ParameterReference>> parameterMap;
    private Map<String, Boolean> children;

    public ActionWorkflowStep(ActionWorkflowStep step) {
        super(step);
        this.actionName = step.actionName;
        if (step.parameterMap != null) this.parameterMap = CloneUtil.cloneMapObject(step.parameterMap);
        if (step.children != null) this.children = CloneUtil.cloneMapObject(step.children);
    }

    @Override
    public WorkflowStepType getStepType() {
        return WorkflowStepType.ACTION;
    }
}
