package com.fincity.saas.message.dao.call.provider;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public abstract class AbstractCallProviderDAO<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected final Field<String> uniqueProviderField;

    protected AbstractCallProviderDAO(
            Class<D> pojoClass, Table<R> table, Field<ULong> idField, Field<String> uniqueProviderField) {
        super(pojoClass, table, idField);
        this.uniqueProviderField = uniqueProviderField;
    }

    public Mono<D> findByUniqueField(String id) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(uniqueProviderField.eq(id)))
                .map(rec -> rec.into(this.pojoClass));
    }
}
