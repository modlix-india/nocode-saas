package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FieldExpression implements Serializable {

	@Serial
	private static final long serialVersionUID = 7823456129034561234L;

	private FieldExpressionFunction function;
	private List<String> fields;
	private String separator;

	public boolean isValid() {

		if (function == null || fields == null || fields.isEmpty())
			return false;

		return switch (function) {
			case UPPER, LOWER, TRIM -> fields.size() == 1;
			case CONCAT, COALESCE -> fields.size() >= 2;
		};
	}
}
