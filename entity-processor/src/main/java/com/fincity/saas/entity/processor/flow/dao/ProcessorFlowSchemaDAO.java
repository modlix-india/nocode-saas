package com.fincity.saas.entity.processor.flow.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorFlowSchema.ENTITY_PROCESSOR_FLOW_SCHEMA;

import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.flow.dto.ProcessorFlowSchema;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorFlowSchemaRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProcessorFlowSchemaDAO extends FlowSchemaDAO<EntityProcessorFlowSchemaRecord, ULong, ProcessorFlowSchema> {

    protected ProcessorFlowSchemaDAO() {
        super(ProcessorFlowSchema.class, ENTITY_PROCESSOR_FLOW_SCHEMA, ENTITY_PROCESSOR_FLOW_SCHEMA.ID);
    }

    public Mono<ProcessorFlowSchema> getFlowSchema(ProcessorAccess access, String dbSchema, String dbTableName) {
        return super.getFlowSchema(
                ComplexCondition.and(
                        FilterCondition.make(ProcessorFlowSchema.Fields.appCode, access.getAppCode()),
                        FilterCondition.make(ProcessorFlowSchema.Fields.clientCode, access.getEffectiveClientCode())),
                dbSchema,
                dbTableName);
    }

    public Mono<ProcessorFlowSchema> getFlowSchema(
            ProcessorAccess access, String dbSchema, String dbTableName, ULong dbEntityPkId) {
        return super.getFlowSchema(
                ComplexCondition.and(
                        FilterCondition.make(ProcessorFlowSchema.Fields.appCode, access.getAppCode()),
                        FilterCondition.make(ProcessorFlowSchema.Fields.clientCode, access.getEffectiveClientCode())),
                dbSchema,
                dbTableName,
                dbEntityPkId);
    }
}
