package com.fincity.saas.message.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.eager.EagerUtil;
import com.fincity.saas.message.eager.IEagerDAO;
import com.fincity.saas.message.eager.relations.RecordEnrichmentService;
import com.fincity.saas.message.eager.relations.resolvers.RelationResolver;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
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
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Getter
public abstract class BaseUpdatableDAO<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>>
        extends AbstractUpdatableDAO<R, ULong, D> implements IEagerDAO<R> {

    private static final String APP_CODE = "APP_CODE";
    private static final String CLIENT_CODE = "CLIENT_CODE";
    private static final String CODE = "CODE";
    private static final String IS_ACTIVE = "IS_ACTIVE";

    protected final Field<String> appCodeField;
    protected final Field<String> clientCodeField;
    protected final Field<String> codeField;
    protected final Field<Boolean> isActiveField;

    private final Map<String, Tuple2<Table<?>, String>> relationMap;
    private final SetValuedMap<Class<? extends RelationResolver>, String> relationResolverMap;

    private RecordEnrichmentService recordEnrichmentService;

    protected BaseUpdatableDAO(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
        super(pojoClass, table, idField);
        this.appCodeField = table.field(APP_CODE, String.class);
        this.clientCodeField = table.field(CLIENT_CODE, String.class);
        this.codeField = table.field(CODE, String.class);
        this.isActiveField = table.field(IS_ACTIVE, Boolean.class);

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
            ULong id, MessageAccess access, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.readSingleRecordByIdentityEager(idField, id, access, tableFields, queryParams);
    }

    public Mono<Map<String, Object>> readByCodeAndAppCodeAndClientCodeEager(
            String code, MessageAccess access, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.readSingleRecordByIdentityEager(codeField, code, access, tableFields, queryParams);
    }

    public Mono<Map<String, Object>> readByIdentityAndAppCodeAndClientCodeEager(
            Identity identity,
            MessageAccess access,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {

        if (identity.isId())
            return this.readSingleRecordByIdentityEager(
                    idField, identity.getULongId(), access, tableFields, queryParams);

        return this.readSingleRecordByIdentityEager(codeField, identity.getCode(), access, tableFields, queryParams);
    }

    public <V> Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            Field<V> identityField,
            V identity,
            MessageAccess access,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {

        return FlatMapUtil.flatMapMono(
                () -> this.messageAccessCondition(
                        FilterCondition.make(
                                        identityField == codeField
                                                ? BaseUpdatableDto.Fields.code
                                                : AbstractDTO.Fields.id,
                                        identity)
                                .setOperator(FilterConditionOperator.EQUALS),
                        access),
                pCondition -> this.readSingleRecordByIdentityEager(pCondition, tableFields, queryParams));
    }

    public Mono<AbstractCondition> messageAccessCondition(AbstractCondition condition, MessageAccess access) {
        return Mono.just(this.addAppCodeAndClientCode(condition, access.getAppCode(), access.getClientCode()));
    }

    private Mono<AbstractCondition> messageAccessCondition(MessageAccess access, ULong id) {
        return this.messageAccessCondition(idCondition(id), access);
    }

    private Mono<AbstractCondition> messageAccessCondition(MessageAccess access, String code) {
        return this.messageAccessCondition(codeCondition(code), access);
    }

    protected AbstractCondition addAppCodeAndClientCode(
            AbstractCondition condition, String appCode, String clientCode) {
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

    public Mono<D> readInternal(MessageAccess access, ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> this.messageAccessCondition(access, id), this::filter, (pCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<D> readInternal(MessageAccess access, String code) {
        return FlatMapUtil.flatMapMono(
                () -> this.messageAccessCondition(access, code), this::filter, (pCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<Integer> deleteByCode(String code) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(codeField.eq(code));

        return Mono.from(query);
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
