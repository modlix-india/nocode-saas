package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.model.request.content.TaskTypeRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

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

    public Flux<TaskType> create(List<TaskTypeRequest> taskTypeRequests) {

        if (taskTypeRequests == null || taskTypeRequests.isEmpty()) return Flux.empty();

        return super.hasAccess().flatMapMany(access -> {
            String[] names =
                    taskTypeRequests.stream().map(TaskTypeRequest::getName).toArray(String[]::new);

            return this.existsByName(access.getT1().getT1(), access.getT1().getT2(), names)
                    .flatMapMany(exists -> Boolean.TRUE.equals(exists)
                            ? this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                    ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                    String.join(", ", names),
                                    this.getEntityName())
                            : Flux.fromIterable(taskTypeRequests)
                                    .flatMap(req -> this.createInternal(TaskType.of(req), access.getT1())));
        });
    }

    public Mono<TaskType> create(TaskTypeRequest taskTypeRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, hasAccess -> this.createInternal(hasAccess.getT1(), taskTypeRequest));
    }

    public Mono<TaskType> createInternal(Tuple3<String, String, ULong> access, TaskTypeRequest taskTypeRequest) {
        return FlatMapUtil.flatMapMono(
                () -> this.existsByName(access.getT1(), access.getT2(), taskTypeRequest.getName()),
                exists -> Boolean.TRUE.equals(exists)
                        ? this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                taskTypeRequest.getName(),
                                this.getEntityName())
                        : this.createInternal(TaskType.of(taskTypeRequest), access));
    }

    public Mono<Boolean> existsByName(String appCode, String clientCode, String... names) {
        return this.dao.existsByName(appCode, clientCode, names);
    }

    @Override
    public Mono<TaskType> create(TaskType taskType) {
        return super.hasAccess().flatMap(hasAccess -> this.createInternal(taskType, hasAccess.getT1()));
    }

    public Mono<TaskType> createPublic(TaskType taskType) {
        return super.hasPublicAccess().flatMap(hasAccess -> this.createInternal(taskType, hasAccess.getT1()));
    }

    private Mono<TaskType> createInternal(TaskType taskType, Tuple3<String, String, ULong> access) {

        taskType.setAppCode(access.getT1());
        taskType.setClientCode(access.getT2());

        taskType.setCreatedBy(access.getT3());

        return super.create(taskType);
    }
}
