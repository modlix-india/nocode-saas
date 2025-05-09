package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.SourceDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import com.fincity.saas.entity.processor.model.request.SourceRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SourceService extends BaseValueService<EntityProcessorSourcesRecord, Source, SourceDAO> {

    private static final String SOURCE_CACHE = "source";

    @Override
    protected String getCacheName() {
        return SOURCE_CACHE;
    }

    public Mono<Source> create(SourceRequest sourceRequest) {}
}
