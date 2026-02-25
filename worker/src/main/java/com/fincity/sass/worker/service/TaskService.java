package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.enums.TaskOperationType;
import com.fincity.sass.worker.jooq.tables.records.WorkerTasksRecord;
import com.fincity.sass.worker.model.task.FunctionExecutionTask;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import java.time.LocalDateTime;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskService extends AbstractJOOQUpdatableDataService<WorkerTasksRecord, ULong, Task, TaskDAO> {

    private final SchedulerService schedulerService;
    private final QuartzService quartzService;
    private final WorkerMessageResourceService messageResourceService;

    private TaskService(
            SchedulerService schedulerService,
            QuartzService quartzService,
            WorkerMessageResourceService messageResourceService) {
        this.schedulerService = schedulerService;
        this.quartzService = quartzService;
        this.messageResourceService = messageResourceService;
    }

    @Override
    public Task create(Task task) {
        if (task instanceof FunctionExecutionTask fet) fet.prepareForPersistence();

        Scheduler scheduler = this.schedulerService.read(task.getSchedulerId());
        if (scheduler == null)
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));

        Task created = super.create(task);
        try {
            this.quartzService.initializeTask(scheduler, created);
        } catch (Exception e) {
            logger.error("Error initializing task in Quartz: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        return created;
    }

    @Override
    public Integer delete(ULong id) {
        throw new GenericException(
                HttpStatus.BAD_REQUEST,
                messageResourceService.getMessage(WorkerMessageResourceService.TASK_DELETION_NOT_ALLOWED));
    }

    public Task cancelTask(ULong taskId) {
        return executeTaskOperation(
                taskId, TaskOperationType.CANCEL, WorkerMessageResourceService.FAILED_TO_CANCEL_TASK);
    }

    public Task pauseTask(ULong taskId) {
        return executeTaskOperation(taskId, TaskOperationType.PAUSE, WorkerMessageResourceService.FAILED_TO_PAUSE_TASK);
    }

    public Task resumeTask(ULong taskId) {
        return executeTaskOperation(
                taskId, TaskOperationType.RESUME, WorkerMessageResourceService.FAILED_TO_RESUME_TASK);
    }

    private Task executeTaskOperation(ULong taskId, TaskOperationType operationType, String failureMessageKey) {
        Task task = read(taskId);
        if (task == null)
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_NOT_FOUND));

        Scheduler scheduler = schedulerService.read(task.getSchedulerId());
        if (scheduler == null)
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));

        try {
            quartzService.updateTask(scheduler, task, operationType);
        } catch (Exception e) {
            logger.error("Error {} task: {}", operationType.name().toLowerCase() + "ing", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, messageResourceService.getMessage(failureMessageKey), e);
        }
        return super.update(task);
    }

    @Override
    protected Task updatableEntity(Task entity) {
        Task existing = this.read(entity.getId());
        if (existing == null) return null;

        if (entity.getName() != null) existing.setName(entity.getName());

        existing.setTaskState(entity.getTaskState());
        existing.setNextFireTime(entity.getNextFireTime());
        existing.setLastFireTime(entity.getLastFireTime());
        existing.setTaskLastFireStatus(entity.getTaskLastFireStatus());
        existing.setLastFireResult(entity.getLastFireResult());
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }
}
