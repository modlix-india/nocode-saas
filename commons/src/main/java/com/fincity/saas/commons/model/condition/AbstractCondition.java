package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
@Accessors(chain = true)
public abstract class AbstractCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 5748516741365718190L;

    private boolean negate = false;

    public abstract boolean isEmpty();

    public boolean isNonEmpty() {
        return !isEmpty();
    }

    public abstract Flux<FilterCondition> findConditionWithField(String fieldName);

    public abstract Flux<FilterCondition> findConditionWithPrefix(String prefix);

    public abstract Flux<FilterCondition> findAndTrimPrefix(String prefix);

    public abstract Flux<FilterCondition> findAndCreatePrefix(String prefix);

    public abstract Mono<AbstractCondition> removeConditionWithField(String fieldName);
}
