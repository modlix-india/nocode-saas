package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.ModelDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import com.fincity.saas.entity.processor.service.ModelService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/models")
public class ModelController
        extends BaseProcessorController<EntityProcessorModelsRecord, Model, ModelDAO, ModelService> {}
