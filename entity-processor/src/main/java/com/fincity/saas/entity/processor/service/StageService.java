package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.service.base.BaseProductService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StageService extends BaseProductService<EntityProcessorStagesRecord, Stage, StageDAO> {

    private static final String STAGE_CACHE = "stage";

    @Override
    protected String getCacheName() {
        return STAGE_CACHE;
    }

    @Override
    protected Mono<Stage> getDefault() {
        return null;
    }
}
