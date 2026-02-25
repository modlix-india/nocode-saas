package com.fincity.security.dao.clientcheck;

import java.io.Serializable;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public abstract class AbstractUpdatableClientCheckDAO<
                R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    protected AbstractUpdatableClientCheckDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {
        return ClientCheckDAOHelper.getSelectJointStep(dslContext, table, this.getClientIDField());
    }

    @Override
    protected Mono<Condition> complexConditionFilter(ComplexCondition cc, SelectJoinStep<Record> selectJoinStep) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty())
            return Mono.just(DSL.noCondition());

        return Flux.concat(cc.getConditions().stream()
                .map(condition -> super.filter(condition, selectJoinStep))
                .toList())
                .collectList()
                .map(conditions -> cc.getOperator() == ComplexConditionOperator.AND
                        ? DSL.and(conditions)
                        : DSL.or(conditions));
    }

    @Override
    public Mono<Condition> filter(AbstractCondition abstractCondition, SelectJoinStep<Record> selectJoinStep) {
        Mono<Condition> condition = super.filter(abstractCondition, selectJoinStep);
        return ClientCheckDAOHelper.applyClientFilter(condition);
    }

    public Mono<Boolean> canBeUpdated(I id) {
        return this.getSelectJointStep()
                .map(Tuple2::getT2)
                .flatMap(query -> Mono.from(query.where(this.idField.eq(id))))
                .map(e -> e.value1() == 1);
    }

    protected abstract Field<ULong> getClientIDField();
}
