package com.fincity.saas.entity.processor.flow.service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.flow.dao.ProcessorFlowSchemaDAO;
import com.fincity.saas.entity.processor.flow.dto.ProcessorFlowSchema;
import com.fincity.saas.entity.processor.jooq.EntityProcessor;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorFlowSchemaRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import lombok.Getter;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProcessorFlowSchemaService
        extends FlowSchemaService<EntityProcessorFlowSchemaRecord, ULong, ProcessorFlowSchema, ProcessorFlowSchemaDAO>
        implements IProcessorAccessService, IEntitySeries {

    private static final String PROCESSOR_FLOW_SCHEMA_CACHE = "processorFlowSchema";

    @Getter
    protected ProcessorMessageResourceService msgService;

    @Getter
    protected IFeignSecurityService securityService;

    private String getCacheName() {
        return PROCESSOR_FLOW_SCHEMA_CACHE;
    }

    @Override
    protected String getDbSchemaName() {
        return EntityProcessor.ENTITY_PROCESSOR.getName();
    }

    @Override
    protected Mono<Schema> getSchema(String dbTableName) {
        return this.hasAccess().flatMap(access -> this.getSchema(access, dbTableName));
    }

    @Override
    protected Mono<Schema> getEntityIdSchema(String dbTableName, ULong dbEntityId) {
        return this.hasAccess().flatMap(access -> this.getSchema(access, dbTableName, dbEntityId));
    }

    @Override
    protected Mono<Boolean> evictCache(ProcessorFlowSchema entity) {

        if (entity.getDbEntityPkId() != null)
            return Mono.zip(
                    super.cacheService.evict(
                            this.getCacheName(),
                            super.getCacheKey(
                                    super.getSchemaCache(),
                                    entity.getAppCode(),
                                    entity.getAppCode(),
                                    entity.getDbSchema(),
                                    entity.getDbTableName())),
                    super.cacheService.evict(
                            this.getCacheName(),
                            super.getCacheKey(
                                    super.getSchemaCache(),
                                    entity.getAppCode(),
                                    entity.getAppCode(),
                                    entity.getDbSchema(),
                                    entity.getDbEntityPkId())),
                    (schemaEvicted, schemaIdEvicted) -> schemaEvicted && schemaIdEvicted);

        return super.cacheService.evict(
                this.getCacheName(),
                super.getCacheKey(
                        super.getSchemaCache(),
                        entity.getAppCode(),
                        entity.getAppCode(),
                        entity.getDbSchema(),
                        entity.getDbTableName()));
    }

    @Autowired
    public void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    public void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    private Mono<ProcessorFlowSchema> getFlowSchema(ProcessorAccess access, String dbTableName) {
        return this.dao.getFlowSchema(access, this.getDbSchemaName(), dbTableName);
    }

    private Mono<ProcessorFlowSchema> getFlowSchema(ProcessorAccess access, String dbTableName, ULong dbEntityPkId) {
        return this.dao.getFlowSchema(access, this.getDbSchemaName(), dbTableName, dbEntityPkId);
    }

    private Mono<Schema> getSchema(ProcessorAccess access, String dbTableName) {
        return super.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getFlowSchema(access, dbTableName).map(super::toSchema),
                super.getCacheKey(
                        super.getSchemaCache(),
                        access.getAppCode(),
                        access.getAppCode(),
                        this.getDbSchemaName(),
                        dbTableName));
    }

    private Mono<Schema> getSchema(ProcessorAccess access, String dbTableName, ULong dbEntityPkId) {
        return super.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getFlowSchema(access, dbTableName, dbEntityPkId).map(super::toSchema),
                super.getCacheKey(
                        super.getSchemaCache(),
                        access.getAppCode(),
                        access.getAppCode(),
                        this.getDbSchemaName(),
                        dbTableName,
                        dbEntityPkId));
    }
}
