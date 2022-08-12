package com.fincity.security.model.condition;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class FilterCondition extends AbstractCondition {

	private static final long serialVersionUID = -4542270694019365457L;

	private String field;
	private FilterConditionOperator operator;
	private String value;
	private String toValue;
	private boolean isValueField = false;
	private boolean isToValueField = false;
}
