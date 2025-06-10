package com.fincity.saas.entity.processor.service.content;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;

@Service
public class TaskService extends BaseContentService<EntityProcessorTasksRecord, Task, TaskDAO> {

    private static final String TASK_CACHE = "task";

    @Override
    protected String getCacheName() {
        return TASK_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK;
    }
}
