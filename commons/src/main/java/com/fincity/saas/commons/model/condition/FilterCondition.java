package com.fincity.saas.commons.model.condition;

import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.util.StringUtil;

import ch.qos.logback.core.filter.Filter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class FilterCondition extends AbstractCondition {

	private static final long serialVersionUID = -4542270694019365457L;

	private String field;
	private FilterConditionOperator operator = FilterConditionOperator.EQUALS;
	private Object value; // NOSONAR
	private Object toValue; // NOSONAR
	private List<Object> multiValue; // NOSONAR
	private boolean isValueField = false;
	private boolean isToValueField = false;
	private FilterConditionOperator matchOperator = FilterConditionOperator.EQUALS;

	@Override
	public Flux<FilterCondition> findConditionWithField(String fieldName) {

		if (StringUtil.safeEquals(field, fieldName))
			return Flux.just(this);

		return Flux.empty();
	}

	@Override
	public boolean isEmpty() {

		return false;
	}

	public static FilterCondition make(String field, Object value) {

		return new FilterCondition().setField(field)
				.setValue(value);
	}
}
