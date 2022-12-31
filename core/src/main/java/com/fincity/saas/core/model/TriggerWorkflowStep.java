package com.fincity.saas.core.model;

import com.fincity.saas.core.enums.WorkflowStepType;

import java.util.Map;

import com.fincity.saas.core.enums.TriggerType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TriggerWorkflowStep extends WorkflowStep {

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
	}

	@Override
	public WorkflowStepType getStepType() {
		return WorkflowStepType.TRIGGER;
	}
}
