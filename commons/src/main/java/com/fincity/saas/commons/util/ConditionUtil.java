package com.fincity.saas.commons.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;

public class ConditionUtil {

    public static AbstractCondition parameterMapToMap(Map<String, List<String>> multiValueMap, String... ignoreFields) {

        Set<String> ignoreFieldsSet = Set.of(ignoreFields);

        List<AbstractCondition> conditions = multiValueMap.entrySet()
                .stream()
                .filter(e -> !ignoreFieldsSet.contains(e.getKey()))
                .map(e -> {
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
