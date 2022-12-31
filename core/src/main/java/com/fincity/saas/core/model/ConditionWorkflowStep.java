package com.fincity.saas.core.model;

import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.core.enums.WorkflowStepType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConditionWorkflowStep extends WorkflowStep {

	private static final long serialVersionUID = 2178854708270744216L;

	private String expression;
	private List<String> possibleValues;
	private Map<String, Map<String, Boolean>> children;

	public ConditionWorkflowStep(ConditionWorkflowStep step) {

		super(step);

		this.expression = step.expression;
		this.children = CloneUtil.cloneMapObject(step.children);
		this.possibleValues = CloneUtil.cloneMapList(step.possibleValues);
	}

	@Override
	public WorkflowStepType getStepType() {

		return WorkflowStepType.CONDITION;
	}
}
