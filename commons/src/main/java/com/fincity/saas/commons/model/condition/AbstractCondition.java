package com.fincity.saas.commons.model.condition;

import java.io.Serializable;

import lombok.Data;
import reactor.core.publisher.Flux;

@Data
public abstract class AbstractCondition implements Serializable {

	private static final long serialVersionUID = 5748516741365718190L;

	private boolean negate = false;
	
	public abstract Flux<AbstractCondition> findConditionWithField(String fieldName);
}
