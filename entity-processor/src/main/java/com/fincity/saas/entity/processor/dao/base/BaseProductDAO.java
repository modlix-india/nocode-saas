package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.model.base.IdAndValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseProductDAO<R extends UpdatableRecord<R>, D extends BaseProductDto<D>> extends BaseDAO<R, D> {

    private static final String PRODUCT_ID = "PRODUCT_ID";
    private static final String IS_PARENT = "IS_PARENT";

    protected final Field<ULong> productIdField;
    protected final Field<Boolean> isParentField;

    protected BaseProductDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.productIdField = flowTable.field(PRODUCT_ID, ULong.class);
        this.isParentField = flowTable.field(IS_PARENT, Boolean.class);
    }

    public Mono<Boolean> existsById(String appCode, String clientCode, ULong productId, ULong... productEntityIds) {
        return Mono.just(this.dslContext.fetchExists(this.dslContext
                .selectFrom(this.table)
                .where(DSL.and(
                        this.getBaseProductConditions(appCode, clientCode, productId, null, productEntityIds)))));
    }

    public Mono<List<D>> getAllProducts(String appCode, String clientCode, ULong productId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseProductConditions(appCode, clientCode, productId, isParent))))
                .map(e -> e.into(super.pojoClass))
                .collectList();
    }

    public Mono<List<IdAndValue<Integer, String>>> getAllProductIdAndNames(
            String appCode, String clientCode, ULong productId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(this.getBaseProductConditions(appCode, clientCode, productId, isParent))))
                .map(e -> new IdAndValue<>(e.get(this.idField).intValue(), e.get(super.nameField)))
                .collectList();
    }

    public Mono<List<IdAndValue<Integer, String>>> getAllProductIdAndNames(
            String appCode, String clientCode, ULong productId) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(this.getBaseProductConditions(appCode, clientCode, productId, null))))
                .map(e -> new IdAndValue<>(e.get(this.idField).intValue(), e.get(super.nameField)))
                .collectList();
    }

    private List<Condition> getBaseProductConditions(
            String appCode, String clientCode, ULong productId, Boolean onlyParent, ULong... productEntityIds) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));
        conditions.add(this.productIdField.eq(productId));

        if (onlyParent != null) conditions.add(this.isParentField.eq(onlyParent));

        if (productEntityIds.length > 0)
            conditions.add(this.idField.in(
                    Arrays.stream(productEntityIds).filter(Objects::nonNull).toArray(ULong[]::new)));

        // we always need Active product entities
        conditions.add(this.isActiveTrue());

        return conditions;
    }
}
