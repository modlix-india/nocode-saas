package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.Platform;
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

public abstract class BaseValueDAO<R extends UpdatableRecord<R>, D extends BaseValueDto<D>>
        extends BaseUpdatableDAO<R, D> {

    private static final String PRODUCT_TEMPLATE_ID = "PRODUCT_TEMPLATE_ID";
    private static final String IS_PARENT = "IS_PARENT";
    private static final String PLATFORM = "PLATFORM";
    private static final String ORDER = "ORDER";

    protected final Field<ULong> productTemplateIdField;
    protected final Field<Boolean> isParentField;
    protected final Field<Platform> platformField;
    protected final Field<Integer> orderField;

    protected BaseValueDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.productTemplateIdField = flowTable.field(PRODUCT_TEMPLATE_ID, ULong.class);
        this.isParentField = flowTable.field(IS_PARENT, Boolean.class);
        this.platformField = flowTable.field(PLATFORM, Platform.class);
        this.orderField = flowTable.field(ORDER, Integer.class);
    }

    public Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... valueEntityIds) {

        if (valueEntityIds == null || valueEntityIds.length == 0) return Mono.just(Boolean.FALSE);

        List<Condition> baseConditions =
                this.getBaseValueConditions(appCode, clientCode, platform, productTemplateId, null, valueEntityIds);

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(baseConditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<Boolean> existsByName(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            ULong entityId,
            String... valueEntityNames) {

        if (valueEntityNames == null || valueEntityNames.length == 0) return Mono.just(Boolean.FALSE);

        List<Condition> baseConditions = this.getBaseValueNameConditions(
                appCode, clientCode, platform, productTemplateId, null, valueEntityNames);

        if (entityId != null) baseConditions.add(this.idField.ne(entityId));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(baseConditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<List<D>> getValues(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            Boolean isParent,
            ULong... valueEntityIds) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseValueConditions(
                                appCode, clientCode, platform, productTemplateId, isParent, valueEntityIds))))
                .map(e -> e.into(super.pojoClass))
                .collectList();
    }

    public Flux<D> getAllValuesFlux(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseValueConditions(
                                appCode, clientCode, platform, productTemplateId, isParent))))
                .map(e -> e.into(super.pojoClass));
    }

    protected List<Condition> getBaseCommonConditions(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, Boolean onlyParent) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(super.appCodeField.eq(appCode));
        conditions.add(super.clientCodeField.eq(clientCode));
        if (productTemplateId != null) conditions.add(this.productTemplateIdField.eq(productTemplateId));
        if (platform != null) conditions.add(this.platformField.eq(platform));
        if (onlyParent != null) conditions.add(this.isParentField.eq(onlyParent));
        return conditions;
    }

    protected List<Condition> getBaseValueConditions(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            Boolean onlyParent,
            ULong... valueEntityIds) {
        List<Condition> conditions =
                this.getBaseCommonConditions(appCode, clientCode, platform, productTemplateId, onlyParent);
        if (valueEntityIds != null && valueEntityIds.length > 0)
            conditions.add(super.idField.in(
                    Arrays.stream(valueEntityIds).filter(Objects::nonNull).toArray(ULong[]::new)));
        return conditions;
    }

    protected List<Condition> getBaseValueNameConditions(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            Boolean onlyParent,
            String... valeEntityNames) {
        List<Condition> conditions =
                this.getBaseCommonConditions(appCode, clientCode, platform, productTemplateId, onlyParent);
        if (valeEntityNames.length > 0)
            conditions.add(super.nameField.in(
                    Arrays.stream(valeEntityNames).filter(Objects::nonNull).toArray(String[]::new)));
        return conditions;
    }
}
