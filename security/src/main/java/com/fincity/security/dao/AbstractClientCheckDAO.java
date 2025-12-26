package com.fincity.security.dao;

import java.io.Serializable;
import java.util.Arrays;

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
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractClientCheckDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    protected AbstractClientCheckDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {

                    SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields()))
                            .from(table);

                    SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count())
                            .from(table);

                    if (ca.getClientTypeCode()
                            .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
                        return Tuples.of(mainQuery, countQuery);

                    return this.addJoinCondition(mainQuery, countQuery, this.getClientIDField());
                });
    }

    protected Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> addJoinCondition(
            SelectJoinStep<Record> mainQuery, SelectJoinStep<Record1<Integer>> countQuery, Field<ULong> clientIdField) {

        return Tuples.of(mainQuery.leftJoin(SECURITY_CLIENT)
                .on(SECURITY_CLIENT.ID.eq(clientIdField))
                .leftJoin(SECURITY_CLIENT_HIERARCHY)
                .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_CLIENT.ID)),
                countQuery.leftJoin(SECURITY_CLIENT)
                        .on(SECURITY_CLIENT.ID.eq(clientIdField))
                        .leftJoin(SECURITY_CLIENT_HIERARCHY)
                        .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_CLIENT.ID)));
    }

    @Override
    public Mono<Condition> filter(AbstractCondition abstractCondition, SelectJoinStep<Record> selectJoinStep) {

        Mono<Condition> condition = super.filter(abstractCondition, selectJoinStep);

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    if (ca.getClientTypeCode()
                            .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
                        return condition;

                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                    return condition.map(c -> DSL.and(c, ClientHierarchyDAO.getManageClientCondition(clientId)));
                })
                .switchIfEmpty(condition);
    }

    public Mono<Boolean> canBeUpdated(I id) {
        return this.getSelectJointStep()
                .map(Tuple2::getT2)
                .flatMap(query -> Mono.from(query.where(this.idField.eq(id))))
                .map(e -> e.value1() == 1);
    }

    protected abstract Field<ULong> getClientIDField();

}
