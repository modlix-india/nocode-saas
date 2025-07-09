package com.fincity.saas.entity.processor.service.content;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.TaskTypeRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TaskTypeService extends BaseUpdatableService<EntityProcessorTaskTypesRecord, TaskType, TaskTypeDAO> {

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
        return super.hasAccess()
                .flatMap(access -> super.createInternal(access, taskType))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create"));
    }

    public Flux<TaskType> create(List<TaskTypeRequest> taskTypeRequests) {

        if (taskTypeRequests == null || taskTypeRequests.isEmpty()) return Flux.empty();

        return super.hasAccess()
                .flatMapMany(access -> {
                    String[] names = taskTypeRequests.stream()
                            .map(TaskTypeRequest::getName)
                            .toArray(String[]::new);

                    return this.existsByName(access.getAppCode(), access.getClientCode(), names)
                            .flatMapMany(exists -> Boolean.TRUE.equals(exists)
                                    ? this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                            ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                            String.join(", ", names),
                                            this.getEntityName())
                                    : Flux.fromIterable(taskTypeRequests)
                                            .flatMap(req -> super.createInternal(access, TaskType.of(req))));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create[List<TaskTypeRequest>]"));
    }

    public Mono<TaskType> create(TaskTypeRequest taskTypeRequest) {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> this.createInternal(access, taskTypeRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create[TaskTypeRequest]"));
    }

    public Mono<TaskType> createInternal(ProcessorAccess access, TaskTypeRequest taskTypeRequest) {

        TaskType taskType = TaskType.of(taskTypeRequest);

        return this.checkExistsByName(access, taskType)
                .flatMap(cEntity -> super.createInternal(access, taskType))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "TaskTypeService.createInternal[ProcessorAccess, TaskTypeRequest]"));
    }

    public Mono<Boolean> existsByName(String appCode, String clientCode, String... names) {
        return this.dao
                .existsByName(appCode, clientCode, names)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.existsByName"));
    }
}
