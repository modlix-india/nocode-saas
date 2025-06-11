package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TASK_TYPES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import org.springframework.stereotype.Component;

@Component
public class TaskTypeDAO extends BaseDAO<EntityProcessorTaskTypesRecord, TaskType> {

    protected TaskTypeDAO() {
        super(TaskType.class, ENTITY_PROCESSOR_TASK_TYPES, ENTITY_PROCESSOR_TASK_TYPES.ID);
    }
}
