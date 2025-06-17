package com.fincity.saas.entity.processor.controller.content;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.service.content.TaskTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tasks/types")
public class TaskTypeController
        extends BaseUpdatableController<EntityProcessorTaskTypesRecord, TaskType, TaskTypeDAO, TaskTypeService> {}
