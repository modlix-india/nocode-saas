package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import reactor.core.publisher.Flux;

@Data
public abstract class AbstractCondition implements Serializable {

	@Serial
    private static final long serialVersionUID = 5748516741365718190L;

    private boolean negate = false;

    @JsonIgnore
    public abstract Flux<FilterCondition> findConditionWithField(String fieldName);

    @JsonIgnore
    public abstract boolean isEmpty();
}
