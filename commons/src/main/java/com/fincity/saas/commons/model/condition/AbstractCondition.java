package com.fincity.saas.commons.model.condition;

import java.io.Serializable;

import lombok.Data;

//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
//@JsonSubTypes({ @Type(name = "field", value = FilterCondition.class),
//        @Type(name = "conditions", value = ComplexCondition.class) })
@Data
public abstract class AbstractCondition implements Serializable {

	private static final long serialVersionUID = 5748516741365718190L;

	private boolean negate = false;
}
