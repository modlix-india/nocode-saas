package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSources.ENTITY_PROCESSOR_SOURCES;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import org.springframework.stereotype.Component;

@Component
public class SourceDAO extends BaseValueDAO<EntityProcessorSourcesRecord, Source> {

    protected SourceDAO() {
        super(Source.class, ENTITY_PROCESSOR_SOURCES, ENTITY_PROCESSOR_SOURCES.ID);
    }
}
