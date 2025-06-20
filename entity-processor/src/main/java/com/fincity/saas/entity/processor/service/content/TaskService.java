package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TaskService extends BaseContentService<EntityProcessorTasksRecord, Task, TaskDAO> {

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

    public Mono<Task> create(TaskRequest taskRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.updateIdentities(access, taskRequest),
                (access, uRequest) -> this.createContent(uRequest),
                (access, uRequest, content) -> content.isTicketContent()
                        ? this.createTicketContent(access, content)
                        : createOwnerContent(access, content));
    }

    private Mono<TaskRequest> updateIdentities(ProcessorAccess access, TaskRequest taskRequest) {
        return FlatMapUtil.flatMapMono(
                () -> taskRequest.getTicketId() != null
                        ? this.checkTicket(access, taskRequest.getTicketId())
                        : Mono.just(Identity.ofNull()),
                ticketId -> taskRequest.getOwnerId() != null
                        ? this.checkOwner(access, taskRequest.getOwnerId(), ticketId)
                        : Mono.just(Identity.ofNull()),
                (ticketId, ownerId) -> taskRequest.getTaskTypeId() != null
                        ? this.taskTypeService.checkAndUpdateIdentityWithAccess(access, taskRequest.getTaskTypeId())
                        : Mono.just(Identity.ofNull()),
                (ticketId, ownerId, taskTypeId) -> Mono.just(
                        taskRequest.setTicketId(ticketId).setOwnerId(ownerId).setTaskTypeId(taskTypeId)));
    }

    private Mono<Task> createContent(TaskRequest taskRequest) {

        if ((taskRequest.getContent() == null || taskRequest.getContent().trim().isEmpty())
                && taskRequest.getTaskTypeId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.CONTENT_MISSING,
                    this.getEntityName());

        if (taskRequest.getDueDate() != null && taskRequest.getDueDate().isBefore(LocalDateTime.now()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DATE_IN_PAST,
                    "Due");

        return Mono.just(Task.of(taskRequest));
    }

    @Override
    protected Mono<Task> updatableEntity(Task entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDueDate(entity.getDueDate());
            existing.setTaskPriority(entity.getTaskPriority());

            LocalDateTime now = LocalDateTime.now();

            if (entity.isCompleted()) {
                existing.setCompleted(Boolean.TRUE);
                existing.setCompletedDate(entity.getCompletedDate() != null ? entity.getCompletedDate() : now);
            }

            if (entity.isCancelled()) {
                existing.setCancelled(Boolean.TRUE);
                existing.setCancelledDate(entity.getCancelledDate() != null ? entity.getCancelledDate() : now);
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
                access -> this.readIdentityWithAccess(access, taskIdentity),
                (access, task) -> this.checkTaskStatus(task, reminderDate, LocalDateTime.now(), "Reminder"),
                (access, task, vTask) -> {
                    vTask.setHasReminder(Boolean.TRUE);
                    vTask.setNextReminder(reminderDate);

                    return this.updateInternal(access, vTask);
                },
                (access, task, vTask, uTask) ->
                        this.activityService.acReminderSet(uTask).then(Mono.just(uTask)));
    }

    public Mono<Task> setTaskCompleted(Identity taskIdentity, Boolean isCompleted, LocalDateTime completedDate) {
        return this.setTaskStatus(taskIdentity, isCompleted, completedDate, true)
                .flatMap(task -> this.activityService.acTaskComplete(task).then(Mono.just(task)));
    }

    public Mono<Task> setTaskCancelled(Identity taskIdentity, Boolean isCancelled, LocalDateTime cancelledDate) {
        return this.setTaskStatus(taskIdentity, isCancelled, cancelledDate, false)
                .flatMap(task -> this.activityService.acTaskCancelled(task).then(Mono.just(task)));
    }

    private Mono<Task> setTaskStatus(
            Identity taskIdentity, Boolean status, LocalDateTime statusDate, boolean isCompletion) {

        LocalDateTime now = LocalDateTime.now();

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.readIdentityWithAccess(access, taskIdentity),
                (access, task) -> this.checkTaskStatus(task, statusDate, now, "Status"),
                (access, task, vTask) -> {
                    LocalDateTime date = statusDate != null ? statusDate : now;

                    if (vTask.getDueDate() != null && vTask.getDueDate().isBefore(date)) vTask.setDelayed(Boolean.TRUE);

                    if (isCompletion) {
                        vTask.setCompleted(status);
                        vTask.setCompletedDate(date);
                    } else {
                        vTask.setCancelled(status);
                        vTask.setCancelledDate(date);
                    }

                    return this.updateInternal(access, vTask);
                });
    }

    private Mono<Task> checkTaskStatus(Task task, LocalDateTime statusDate, LocalDateTime now, String statusName) {

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

        if (statusDate != null && statusDate.isBefore(now))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DATE_IN_PAST,
                    statusName);

        return Mono.just(task);
    }
}
