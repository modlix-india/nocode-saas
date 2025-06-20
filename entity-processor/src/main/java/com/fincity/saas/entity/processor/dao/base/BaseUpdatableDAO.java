package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.relations.RecordEnrichmentService;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public abstract class BaseUpdatableDAO<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>>
        extends AbstractFlowUpdatableDAO<R, ULong, D> implements IEagerDAO<R> {

    private static final String CODE = "CODE";
    private static final String NAME = "NAME";
    private static final String TEMP_ACTIVE = "TEMP_ACTIVE";
    private static final String IS_ACTIVE = "IS_ACTIVE";

    protected final Field<String> codeField;
    protected final Field<String> nameField;
    protected final Field<Boolean> tempActiveField;
    protected final Field<Boolean> isActiveField;

    private final Map<String, Tuple2<Table<?>, String>> relationMap;
    private final SetValuedMap<Class<? extends RelationResolver>, String> relationResolverMap;

    private RecordEnrichmentService recordEnrichmentService;

    protected BaseUpdatableDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.codeField = flowTable.field(CODE, String.class);
        this.nameField = flowTable.field(NAME, String.class);
        this.tempActiveField = flowTable.field(TEMP_ACTIVE, Boolean.class);
        this.isActiveField = flowTable.field(IS_ACTIVE, Boolean.class);

        this.relationMap = EagerUtil.getRelationMap(this.pojoClass);
        this.relationResolverMap = EagerUtil.getRelationResolverMap(this.pojoClass);
    }

    @Override
    public Map<String, Tuple2<Table<?>, String>> getRelationMap() {
        return this.relationMap;
    }

    @Override
    public SetValuedMap<Class<? extends RelationResolver>, String> getRelationResolverMap() {
        return this.relationResolverMap;
    }

    @Override
    public RecordEnrichmentService getRecordEnrichmentService() {
        return this.recordEnrichmentService;
    }

    @Autowired
    private void setRecordEnrichmentService(RecordEnrichmentService recordEnrichmentService) {
        this.recordEnrichmentService = recordEnrichmentService;
    }

    private <T, V> Mono<T> objectNotFoundError(V value) {
        return messageResourceService
                .getMessage(AbstractMessageService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), value)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.NOT_FOUND, msg)));
    }

    public Mono<Map<String, Object>> readByIdAndAppCodeAndClientCodeEager(
            ULong id,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(idField, id, appCode, clientCode, tableFields, eager, eagerFields);
    }

    public Mono<Map<String, Object>> readByCodeAndAppCodeAndClientCodeEager(
            String code,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(
                codeField, code, appCode, clientCode, tableFields, eager, eagerFields);
    }

    public Mono<Map<String, Object>> readByIdentityAndAppCodeAndClientCodeEager(
            Identity identity,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {

        if (identity.isId())
            return this.readSingleRecordByIdentityEager(
                    idField, identity.getULongId(), appCode, clientCode, tableFields, eager, eagerFields);

        return this.readSingleRecordByIdentityEager(
                codeField, identity.getCode(), appCode, clientCode, tableFields, eager, eagerFields);
    }

    private <V> Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            Field<V> identityField,
            V identity,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {

        AbstractCondition condition = ComplexCondition.and(
                FilterCondition.of(identityField.getName(), identity, FilterConditionOperator.EQUALS),
                FilterCondition.of(appCodeField.getName(), appCode, FilterConditionOperator.EQUALS),
                FilterCondition.of(clientCodeField.getName(), clientCode, FilterConditionOperator.EQUALS));

        return this.readSingleRecordByIdentityEager(condition, tableFields, eager, eagerFields)
                .switchIfEmpty(Mono.defer(() -> objectNotFoundError(identity)));
    }

    public Mono<D> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(this.idField.eq(id)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<D> readInternal(String code) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
                .map(result -> result.into(this.pojoClass));
    }

    public Mono<D> readInternal(String appCode, String clientCode, ULong id) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(this.idField.eq(id).and(appCodeField.eq(appCode)).and(clientCodeField.eq(clientCode))))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<D> readInternal(String appCode, String clientCode, String code) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(codeField.eq(code).and(appCodeField.eq(appCode)).and(clientCodeField.eq(clientCode))))
                .map(result -> result.into(this.pojoClass));
    }

    public Mono<Integer> deleteByCode(String code) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(codeField.eq(code));

        return Mono.from(query);
    }

    public Mono<Integer> deleteMultiple(List<ULong> ids) {
        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(this.idField.in(ids));
        return Mono.from(query);
    }

    public Mono<Integer> deleteMultiple(Flux<ULong> ids) {
        return ids.collectList().flatMap(this::deleteMultiple);
    }

    protected Condition isActiveTrue() {
        return isActiveField.eq(Boolean.TRUE);
    }

    protected Condition isActiveFalse() {
        return isActiveField.eq(Boolean.FALSE);
    }

    protected Condition isActive(Boolean isActive) {
        if (isActive == null) return DSL.trueCondition();
        return isActiveField.eq(isActive);
    }

    protected Condition isActiveWithFalse(Boolean isActive) {
        if (isActive == null) return DSL.falseCondition();
        return isActiveField.eq(isActive);
    }
}
