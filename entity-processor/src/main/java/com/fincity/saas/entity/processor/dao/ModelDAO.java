package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorModels.ENTITY_PROCESSOR_MODELS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import org.springframework.stereotype.Component;

@Component
public class ModelDAO extends BaseProcessorDAO<EntityProcessorModelsRecord, Model> {

    protected ModelDAO() {
        super(Model.class, ENTITY_PROCESSOR_MODELS, ENTITY_PROCESSOR_MODELS.ID);
    }
}
