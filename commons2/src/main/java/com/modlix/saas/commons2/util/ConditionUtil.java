package com.modlix.saas.commons2.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.model.condition.FilterConditionOperator;

public class ConditionUtil {

	public static AbstractCondition parameterMapToMap(Map<String, List<String>> multiValueMap) {

		List<AbstractCondition> conditions = multiValueMap.entrySet()
		        .stream()
		        .map(e ->
				{
			        List<String> value = e.getValue();
			        if (value == null || value.isEmpty())
				        return new FilterCondition().setField(e.getKey())
				                .setOperator(FilterConditionOperator.EQUALS)
				                .setValue("");

			        if (value.size() == 1)
				        return new FilterCondition().setField(e.getKey())
				                .setOperator(FilterConditionOperator.EQUALS)
				                .setValue(value.get(0));

			        return new FilterCondition().setField(e.getKey())
			                .setOperator(FilterConditionOperator.IN)
			                .setValue(value.stream()
			                        .map(v -> v.replace(",", "\\,"))
			                        .collect(Collectors.joining(",")));
		        })
		        .map(AbstractCondition.class::cast)
		        .toList();

		if (conditions.isEmpty())
			return null;

		if (conditions.size() == 1)
			return conditions.get(0);

		return new ComplexCondition().setConditions(conditions)
		        .setOperator(ComplexConditionOperator.AND);
	}

	private ConditionUtil() {
	}
}
