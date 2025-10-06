package com.fincity.saas.message.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.model.common.MessageAccess;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public abstract class BaseProviderDAO<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected final Field<String> uniqueProviderField;

    protected BaseProviderDAO(
            Class<D> pojoClass, Table<R> table, Field<ULong> idField, Field<String> uniqueProviderField) {
        super(pojoClass, table, idField);
        this.uniqueProviderField = uniqueProviderField;
    }

    public Mono<D> findByUniqueField(String id) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(uniqueProviderField.eq(id)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<D> findByUniqueField(MessageAccess access, String id) {

        return FlatMapUtil.flatMapMono(
                () -> this.messageAccessCondition(null, access), this::filter, (pCondition, jCondition) -> Mono.from(
                                this.dslContext
                                        .selectFrom(this.table)
                                        .where(jCondition)
                                        .and(uniqueProviderField.eq(id)))
                        .map(e -> e.into(this.pojoClass)));
    }
}
