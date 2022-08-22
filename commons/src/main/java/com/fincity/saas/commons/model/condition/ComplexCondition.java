package com.fincity.saas.commons.model.condition;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ComplexCondition extends AbstractCondition {

	private static final long serialVersionUID = -2971120422063853598L;

	private ComplexConditionOperator operator;
	private List<AbstractCondition> conditions;
}
