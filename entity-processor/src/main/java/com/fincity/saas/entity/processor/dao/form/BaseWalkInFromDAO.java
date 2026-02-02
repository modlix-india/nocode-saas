package com.fincity.saas.entity.processor.dao.form;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.form.BaseWalkInFormDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public abstract class BaseWalkInFromDAO<R extends UpdatableRecord<R>, D extends BaseWalkInFormDto<D>>
        extends BaseUpdatableDAO<R, D> {

    private final Field<ULong> productIdField;

    protected BaseWalkInFromDAO(
            Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<ULong> productIdField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.productIdField = productIdField;
    }

    public Mono<D> getByProductId(ProcessorAccess access, ULong productId) {

        return FlatMapUtil.flatMapMono(
                () -> super.processorAccessCondition(null, access), super::filter, (condition, jCondition) -> Mono.from(
                                dslContext
                                        .selectFrom(this.table)
                                        .where(jCondition.and(this.productIdField.eq(productId))))
                        .map(rec -> rec.into(this.pojoClass)));
    }
}
