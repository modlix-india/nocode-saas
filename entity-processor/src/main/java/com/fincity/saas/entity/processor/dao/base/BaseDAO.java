package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public abstract class BaseDAO<R extends UpdatableRecord<R>, D extends BaseDto<D>>
        extends AbstractFlowUpdatableDAO<R, ULong, D> {

    private final Field<String> codeField;

    protected BaseDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<String> codeField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.codeField = codeField;
    }

    public Mono<D> getByCode(String code) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
                .map(result -> result.into(this.pojoClass));
    }

    public Mono<Integer> deleteByCode(String code) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(codeField.eq(code));

        return Mono.from(query);
    }
}
