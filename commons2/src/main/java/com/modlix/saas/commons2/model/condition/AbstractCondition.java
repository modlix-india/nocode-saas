package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class AbstractCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 5748516741365718190L;

    private boolean negate = false;

    public abstract boolean isEmpty();

    public abstract List<FilterCondition> findConditionWithField(String fieldName);

    public abstract AbstractCondition removeConditionWithField(String fieldName);
}
