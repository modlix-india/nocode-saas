package com.fincity.saas.commons.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fincity.saas.commons.model.condition.AbstractCondition;

public class AbstractConditionSerializationModule extends SimpleModule {
	
	private static final long serialVersionUID = 6242981337057158018L;

	public AbstractConditionSerializationModule() {
		super();

		this.addDeserializer(AbstractCondition.class, new AbstractCondtionDeserializer());
	}
}
