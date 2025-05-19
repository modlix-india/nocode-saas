package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import org.springframework.stereotype.Component;

@Component
public class StageDAO extends BaseValueDAO<EntityProcessorStagesRecord, Stage> {

    protected StageDAO() {
        super(Stage.class, ENTITY_PROCESSOR_STAGES, ENTITY_PROCESSOR_STAGES.ID);
    }
}
