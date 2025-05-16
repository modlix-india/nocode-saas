package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.util.List;

import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ComplexCondition extends AbstractCondition {

	@Serial
	private static final long serialVersionUID = -2971120422063853598L;

	private ComplexConditionOperator operator;
	private List<AbstractCondition> conditions;

	public Flux<FilterCondition> findConditionWithField(String fieldName) {

		if (StringUtil.safeIsBlank(fieldName))
			return Flux.empty();

		return Flux.fromIterable(this.conditions)
		        .flatMap(c -> c.findConditionWithField(fieldName));
	}

	@Override
	public boolean isEmpty() {

		return conditions.isEmpty();
	}

	public static ComplexCondition and(AbstractCondition... conditions) {
		return new ComplexCondition().setConditions(List.of(conditions))
		        .setOperator(ComplexConditionOperator.AND);
	}

	public static ComplexCondition or(AbstractCondition... conditions) {
		return new ComplexCondition().setConditions(List.of(conditions))
		        .setOperator(ComplexConditionOperator.OR);
	}
}
