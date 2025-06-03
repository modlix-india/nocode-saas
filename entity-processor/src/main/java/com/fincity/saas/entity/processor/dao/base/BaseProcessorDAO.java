package com.fincity.saas.entity.processor.dao.base;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;

import reactor.core.publisher.Flux;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends BaseDAO<R, D> {

    protected BaseProcessorDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }

    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }
}
