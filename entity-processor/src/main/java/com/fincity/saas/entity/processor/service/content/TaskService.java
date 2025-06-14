package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import java.time.LocalDateTime;
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

        if (contentRequest.getDueDate() != null && contentRequest.getDueDate().isBefore(LocalDateTime.now()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DATE_IN_PAST,
                    "Due");

        return Mono.just(new Task().of(contentRequest));
    }

    @Override
    protected Mono<Task> updatableEntity(Task entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDueDate(entity.getDueDate());
            existing.setTaskPriority(entity.getTaskPriority());

            if (entity.isCompleted()) {
                existing.setCompleted(Boolean.TRUE);
                existing.setCompletedDate(
                        entity.getCompletedDate() != null ? entity.getCompletedDate() : LocalDateTime.now());
            }

            if (entity.isCancelled()) {
                existing.setCancelled(Boolean.TRUE);
                existing.setCancelledDate(
                        entity.getCancelledDate() != null ? entity.getCancelledDate() : LocalDateTime.now());
            }

            existing.setDelayed(entity.isDelayed());
            existing.setHasReminder(entity.isHasReminder());
            existing.setNextReminder(entity.getNextReminder());

            return Mono.just(existing);
        });
    }

    public Mono<Task> setReminder(Identity taskIdentity, LocalDateTime reminderDate) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.readIdentityInternal(taskIdentity),
                (hasAccess, task) -> this.checkTaskStatus(task, reminderDate, "Reminder"),
                (hasAccess, task, vTask) -> {
                    vTask.setHasReminder(Boolean.TRUE);
                    vTask.setNextReminder(reminderDate);

                    return this.updateInternal(hasAccess.getT1(), vTask);
                });
    }

    public Mono<Task> setTaskCompleted(Identity taskIdentity, Boolean isCompleted, LocalDateTime completedDate) {
        return this.setTaskStatus(taskIdentity, isCompleted, completedDate, true);
    }

    public Mono<Task> setTaskCancelled(Identity taskIdentity, Boolean isCancelled, LocalDateTime cancelledDate) {
        return this.setTaskStatus(taskIdentity, isCancelled, cancelledDate, false);
    }

    private Mono<Task> setTaskStatus(
            Identity taskIdentity, Boolean status, LocalDateTime statusDate, boolean isCompletion) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.readIdentityInternal(taskIdentity),
                (hasAccess, task) -> this.checkTaskStatus(task, statusDate, "Status"),
                (hasAccess, task, vTask) -> {
                    LocalDateTime date = statusDate != null ? statusDate : LocalDateTime.now();

                    if (vTask.getDueDate() != null && vTask.getDueDate().isBefore(date)) vTask.setDelayed(Boolean.TRUE);

                    if (isCompletion) {
                        vTask.setCompleted(status);
                        vTask.setCompletedDate(date);
                    } else {
                        vTask.setCancelled(status);
                        vTask.setCancelledDate(date);
                    }

                    return this.updateInternal(hasAccess.getT1(), vTask);
                });
    }

    private Mono<Task> checkTaskStatus(Task task, LocalDateTime statusDate, String statusName) {

        if (task.isCompleted())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TASK_ALREADY_COMPLETED,
                    task.getId());

        if (task.isCancelled())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TASK_ALREADY_CANCELLED,
                    task.getId());

        if (statusDate != null && statusDate.isBefore(LocalDateTime.now()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DATE_IN_PAST,
                    statusName);

        return Mono.just(task);
    }
}
