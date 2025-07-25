package com.fincity.saas.entity.processor.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.relations.RecordEnrichmentService;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Getter
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

    private static AbstractCondition idCondition(ULong id) {
        return FilterCondition.make(AbstractDTO.Fields.id, id).setOperator(FilterConditionOperator.EQUALS);
    }

    private static AbstractCondition codeCondition(String code) {
        return FilterCondition.make(BaseUpdatableDto.Fields.code, code).setOperator(FilterConditionOperator.EQUALS);
    }

    @Autowired
    private void setRecordEnrichmentService(RecordEnrichmentService recordEnrichmentService) {
        this.recordEnrichmentService = recordEnrichmentService;
    }

    protected <T, V> Mono<T> objectNotFoundError(V value) {
        return messageResourceService
                .getMessage(AbstractMessageService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), value)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.NOT_FOUND, msg)));
    }

    public Mono<Map<String, Object>> readByIdAndAppCodeAndClientCodeEager(
            ULong id, ProcessorAccess access, List<String> tableFields, Boolean eager, List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(idField, id, access, tableFields, eager, eagerFields);
    }

    public Mono<Map<String, Object>> readByCodeAndAppCodeAndClientCodeEager(
            String code, ProcessorAccess access, List<String> tableFields, Boolean eager, List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(codeField, code, access, tableFields, eager, eagerFields);
    }

    public Mono<Map<String, Object>> readByIdentityAndAppCodeAndClientCodeEager(
            Identity identity,
            ProcessorAccess access,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {

        if (identity.isId())
            return this.readSingleRecordByIdentityEager(
                    idField, identity.getULongId(), access, tableFields, eager, eagerFields);

        return this.readSingleRecordByIdentityEager(
                codeField, identity.getCode(), access, tableFields, eager, eagerFields);
    }

    public <V> Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            Field<V> identityField,
            V identity,
            ProcessorAccess access,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {

        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(
                        FilterCondition.make(
                                        identityField == codeField
                                                ? BaseUpdatableDto.Fields.code
                                                : AbstractDTO.Fields.id,
                                        identity)
                                .setOperator(FilterConditionOperator.EQUALS),
                        access),
                pCondition -> this.readSingleRecordByIdentityEager(pCondition, tableFields, eager, eagerFields));
    }

    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {
        return Mono.just(this.addAppCodeAndClientCode(condition, access.getAppCode(), access.getClientCode()));
    }

    private Mono<AbstractCondition> processorAccessCondition(ProcessorAccess access, ULong id) {
        return this.processorAccessCondition(idCondition(id), access);
    }

    private Mono<AbstractCondition> processorAccessCondition(ProcessorAccess access, String code) {
        return this.processorAccessCondition(codeCondition(code), access);
    }

    private AbstractCondition addAppCodeAndClientCode(AbstractCondition condition, String appCode, String clientCode) {
        if (condition == null || condition.isEmpty())
            return ComplexCondition.and(
                    FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, appCode)
                            .setOperator(FilterConditionOperator.EQUALS),
                    FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, clientCode)
                            .setOperator(FilterConditionOperator.EQUALS));

        return ComplexCondition.and(
                condition,
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, appCode)
                        .setOperator(FilterConditionOperator.EQUALS),
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, clientCode)
                        .setOperator(FilterConditionOperator.EQUALS));
    }

    public Mono<D> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(this.idField.eq(id)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<D> readInternal(String code) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
                .map(result -> result.into(this.pojoClass));
    }

    public Mono<D> readInternal(ProcessorAccess access, ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(access, id), this::filter, (pCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<D> readInternal(ProcessorAccess access, String code) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(access, code), this::filter, (pCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<Boolean> existsByName(String appCode, String clientCode, String name) {

        if (StringUtil.safeIsBlank(name)) return Mono.just(Boolean.FALSE);

        List<Condition> baseConditions = new ArrayList<>();
        baseConditions.add(super.appCodeField.eq(appCode));
        baseConditions.add(super.clientCodeField.eq(clientCode));
        baseConditions.add(this.nameField.eq(name));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(baseConditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
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
