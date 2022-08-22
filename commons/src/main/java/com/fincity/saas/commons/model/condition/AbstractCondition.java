package com.fincity.saas.commons.model.condition;

import java.io.Serializable;

import lombok.Data;

@Data
public abstract class AbstractCondition implements Serializable {

	private static final long serialVersionUID = 5748516741365718190L;

	private boolean negate = false;
}
