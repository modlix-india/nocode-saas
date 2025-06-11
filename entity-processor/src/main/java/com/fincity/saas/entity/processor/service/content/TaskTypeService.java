package com.fincity.saas.entity.processor.service.content;

import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

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

    @Override
    public Mono<TaskType> create(TaskType taskType) {
        return super.hasAccess().flatMap(hasAccess -> this.createInternal(taskType, hasAccess));
    }

    public Mono<TaskType> createPublic(TaskType taskType) {
        return super.hasPublicAccess().flatMap(hasAccess -> this.createInternal(taskType, hasAccess));
    }

    private Mono<TaskType> createInternal(TaskType taskType, Tuple2<Tuple3<String, String, ULong>, Boolean> hasAccess) {

        taskType.setAppCode(hasAccess.getT1().getT1());
        taskType.setClientCode(hasAccess.getT1().getT2());

        taskType.setCreatedBy(hasAccess.getT1().getT3());

        return super.create(taskType);
    }
}
