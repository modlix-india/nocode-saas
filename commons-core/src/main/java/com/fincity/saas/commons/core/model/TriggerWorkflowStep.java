package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.enums.TriggerType;
import com.fincity.saas.commons.core.enums.WorkflowStepType;
import com.fincity.saas.commons.mongo.util.CloneUtil;
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
public class TriggerWorkflowStep extends WorkflowStep {

    @Serial
    private static final long serialVersionUID = 2178854708270744216L;

    private TriggerType triggerType;
    private String eventName;
    private String cronString;

    private Map<String, Boolean> children;

    public TriggerWorkflowStep(TriggerWorkflowStep step) {
        super(step);
        this.triggerType = step.triggerType;
        this.eventName = step.eventName;
        this.cronString = step.cronString;
        this.children = CloneUtil.cloneMapObject(step.children);
    }

    @Override
    public WorkflowStepType getStepType() {
        return WorkflowStepType.TRIGGER;
    }
}
