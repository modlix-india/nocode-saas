package com.fincity.saas.entity.processor.service.content;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.service.base.BaseService;

@Service
public class TaskTypeService extends BaseService<EntityProcessorTaskTypesRecord, TaskType, TaskTypeDAO> {

	private static final String TASK_TYPE_CACHE = "taskType";

	@Override
	protected String getCacheName() {
		return TASK_TYPE_CACHE;
	}

	@Override
	public EntitySeries getEntitySeries() {
		return EntitySeries.TASK_TYPE;
	}
}
