package com.fincity.saas.entity.processor.service.content;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TaskService extends BaseContentService<TaskRequest, EntityProcessorTasksRecord, Task, TaskDAO> {

    private static final String TASK_CACHE = "task";

    private TaskTypeService taskTypeService;

    @Autowired
    private void setTaskTypeService(TaskTypeService taskTypeService) {
        this.taskTypeService = taskTypeService;
    }

    @Override
    protected String getCacheName() {
        return TASK_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK;
    }

    @Override
    protected Mono<TaskRequest> updateIdentities(TaskRequest taskRequest) {
        return super.updateIdentities(taskRequest).flatMap(updated -> {
            if (updated.getTaskTypeId() != null)
                return taskTypeService
                        .checkAndUpdateIdentity(updated.getTaskTypeId())
                        .map(updated::setTaskTypeId);

            return Mono.just(updated);
        });
    }

    @Override
    protected Mono<Task> createContent(TaskRequest contentRequest) {

        if ((contentRequest.getContent() == null
                        || contentRequest.getContent().trim().isEmpty())
                && contentRequest.getTaskTypeId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.CONTENT_MISSING,
                    this.getEntityName());

        return Mono.just(new Task().of(contentRequest));
    }

    @Override
    protected Mono<Task> updatableEntity(Task entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDueDate(entity.getDueDate());
            existing.setTaskPriority(entity.getTaskPriority());
            existing.setHasReminder(entity.getHasReminder());
            existing.setNextReminder(entity.getNextReminder());

            return Mono.just(existing);
        });
    }
}
