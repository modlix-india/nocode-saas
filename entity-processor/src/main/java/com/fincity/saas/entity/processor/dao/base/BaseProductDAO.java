package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import reactor.core.publisher.Flux;

public abstract class BaseProductDAO<R extends UpdatableRecord<R>, D extends BaseProductDto<D>> extends BaseDAO<R, D> {

    private final Field<ULong> productField;
    private final Field<Byte> isParentField;

    protected BaseProductDAO(
            Class<D> flowPojoClass,
            Table<R> flowTable,
            Field<ULong> flowTableId,
            Field<String> codeField,
            Field<ULong> productField,
            Field<Byte> isParentField) {
        super(flowPojoClass, flowTable, flowTableId, codeField);
        this.productField = productField;
        this.isParentField = isParentField;
    }

    public Flux<D> getAllProducts(ULong appId, ULong clientId, ULong productId, Boolean onlyParent) {
        return Flux.from(this.dslContext.selectFrom(this.table).where(this.productField.eq(productId)))
                .map(e -> e.into(super.pojoClass));
    }
}
