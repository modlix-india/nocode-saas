package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dao.ModelDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import com.fincity.saas.entity.processor.service.ModelService;

public class ModelController
        extends BaseProcessorController<EntityProcessorModelsRecord, Model, ModelDAO, ModelService> {}
