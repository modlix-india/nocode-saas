package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected BaseProcessorDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }

    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }

    public Mono<Boolean> existsById(String appCode, String clientCode, ULong entityId) {
        if (appCode == null || clientCode == null || entityId == null) return Mono.just(Boolean.TRUE);

        return Mono.from(this.dslContext
                        .selectOne()
                        .from(this.table)
                        .where(this.idField.eq(entityId))
                        .and(this.isActiveTrue())
                        .limit(1))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
