package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TASKS;

import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import org.springframework.stereotype.Component;

@Component
public class TaskDAO extends BaseContentDAO<EntityProcessorTasksRecord, Task> {

    protected TaskDAO() {
        super(Task.class, ENTITY_PROCESSOR_TASKS, ENTITY_PROCESSOR_TASKS.ID);
    }
}
