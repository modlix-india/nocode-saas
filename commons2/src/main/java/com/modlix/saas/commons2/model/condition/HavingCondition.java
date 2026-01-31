package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.util.List;

import com.modlix.saas.commons2.util.CloneUtil;
import com.modlix.saas.commons2.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class HavingCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = 7197831386933667761L;

    private AggregateFunction aggregateFunction = AggregateFunction.MAX;
    private FilterCondition condition;

    public HavingCondition() {
        super();
    }

    public HavingCondition(HavingCondition condition) {
        super();
        this.aggregateFunction = condition.getAggregateFunction();
        this.condition = CloneUtil.cloneObject(condition.getCondition());
        this.setNegate(condition.isNegate());
    }

    public static HavingCondition make(String field, AggregateFunction function, Object value) {
        return new HavingCondition().setAggregateFunction(function).setCondition(FilterCondition.make(field, value));
    }

    public static HavingCondition of(
            String field, AggregateFunction function, Object value, FilterConditionOperator operator) {
        FilterCondition fc = FilterCondition.of(field, value, operator);
        return new HavingCondition().setAggregateFunction(function).setCondition(fc);
    }

    @Override
    public boolean isEmpty() {
        return this.aggregateFunction == null || this.condition == null || this.condition.isEmpty();
    }

    @Override
    public List<FilterCondition> findConditionWithField(String fieldName) {

        if (this.condition == null || StringUtil.safeIsBlank(fieldName)) return List.of();
        return this.condition.findConditionWithField(fieldName);
    }

    @Override
    public AbstractCondition removeConditionWithField(String fieldName) {

        if (this.condition == null || StringUtil.safeIsBlank(fieldName)) return this;

        AbstractCondition removed = this.condition.removeConditionWithField(fieldName);

        if (removed == null) return null;

        return new HavingCondition(this).setCondition((FilterCondition) removed);
    }
}
