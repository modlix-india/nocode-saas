package com.fincity.saas.entity.processor.dao.base;

import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.util.EagerUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends BaseUpdatableDAO<R, D> {

    private static final String CREATED_BY = "CREATED_BY";

    protected final Field<ULong> userAccessField;
    protected final String jUserAccessField;

    protected BaseProcessorDAO(
            Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<ULong> userAccessField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.userAccessField = userAccessField;
        this.jUserAccessField = EagerUtil.fromJooqField(userAccessField.getName());
    }

    protected BaseProcessorDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.userAccessField = flowTable.field(CREATED_BY, ULong.class);
        this.jUserAccessField = EagerUtil.fromJooqField(CREATED_BY);
    }

    @Override
    public <V> Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            Field<V> identityField,
            V identity,
            ProcessorAccess access,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        AbstractCondition condition = this.addAppCodeAndClientCode(
                FilterCondition.make(
                                identityField == codeField ? BaseUpdatableDto.Fields.code : AbstractDTO.Fields.id,
                                identity)
                        .setOperator(FilterConditionOperator.EQUALS),
                access);

        AbstractCondition uCondition = this.addUserIds(condition, access);

        return this.readSingleRecordByIdentityEager(uCondition, tableFields, eager, eagerFields)
                .switchIfEmpty(Mono.defer(() -> objectNotFoundError(identity)));
    }

    public AbstractCondition addUserIds(AbstractCondition condition, ProcessorAccess access) {
        if (condition == null || condition.isEmpty())
            return FilterCondition.make(this.jUserAccessField, access.getSubOrg())
                    .setOperator(FilterConditionOperator.IN);

        return ComplexCondition.and(
                condition,
                FilterCondition.make(this.jUserAccessField, access.getSubOrg())
                        .setOperator(FilterConditionOperator.IN));
    }

    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }
}
