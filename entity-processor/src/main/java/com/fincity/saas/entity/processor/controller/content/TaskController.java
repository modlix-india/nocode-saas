package com.fincity.saas.entity.processor.controller.content;

import com.fincity.saas.entity.processor.controller.content.base.BaseContentController;
import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.service.content.TaskService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tasks")
public class TaskController
        extends BaseContentController<TaskRequest, EntityProcessorTasksRecord, Task, TaskDAO, TaskService> {}
