package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.util.List;

import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;

@Data
@Getter
@Setter
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class FilterCondition extends AbstractCondition {

	@Serial
	private static final long serialVersionUID = -4542270694019365457L;

	private String field;
	private FilterConditionOperator operator = FilterConditionOperator.EQUALS;
	private Object value; // NOSONAR
	private Object toValue; // NOSONAR
	private List<?> multiValue; // NOSONAR
	private boolean isValueField = false;
	private boolean isToValueField = false;
	private FilterConditionOperator matchOperator = FilterConditionOperator.EQUALS;

	public static FilterCondition make(String field, Object value) {

		return new FilterCondition().setField(field)
				.setValue(value);
	}

	public static FilterCondition of(String field, Object value, FilterConditionOperator operator) {

		return new FilterCondition().setField(field)
				.setValue(value).setOperator(operator);
	}

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

	// Explicit getters and setters
	public String getField() {
		return field;
	}

	public FilterCondition setField(String field) {
		this.field = field;
		return this;
	}

	public FilterConditionOperator getOperator() {
		return operator;
	}

	public FilterCondition setOperator(FilterConditionOperator operator) {
		this.operator = operator;
		return this;
	}

	public Object getValue() {
		return value;
	}

	public FilterCondition setValue(Object value) {
		this.value = value;
		return this;
	}

	public Object getToValue() {
		return toValue;
	}

	public FilterCondition setToValue(Object toValue) {
		this.toValue = toValue;
		return this;
	}

	public List<?> getMultiValue() {
		return multiValue;
	}

	public FilterCondition setMultiValue(List<?> multiValue) {
		this.multiValue = multiValue;
		return this;
	}

	public boolean isValueField() {
		return isValueField;
	}

	public FilterCondition setValueField(boolean valueField) {
		this.isValueField = valueField;
		return this;
	}

	public boolean isToValueField() {
		return isToValueField;
	}

	public FilterCondition setToValueField(boolean toValueField) {
		this.isToValueField = toValueField;
		return this;
	}

	public FilterConditionOperator getMatchOperator() {
		return matchOperator;
	}

	public FilterCondition setMatchOperator(FilterConditionOperator matchOperator) {
		this.matchOperator = matchOperator;
		return this;
	}
}
