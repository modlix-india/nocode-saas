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
        if (task instanceof FunctionExecutionTask fet) {
            fet.prepareForPersistence();
        }
        Scheduler scheduler = this.schedulerService.read(task.getSchedulerId());
        if (scheduler == null) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.initializeTask(scheduler, task);
        } catch (Exception e) {
            logger.error("Error initializing scheduler: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        Task created = super.create(task);
        try {
            this.quartzService.addTaskIdToJob(scheduler, created);
        } catch (Exception e) {
            logger.error("Error adding task ID to job: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        return created;
    }

    /**
     * Create a task from FunctionExecutionTask model. Converts API fields (functionName, functionNamespace,
     * functionParams) into jobData via prepareForPersistence(), then persists as Task.
     */
    public Task createFunctionExecutionTask(FunctionExecutionTask task) {
        task.prepareForPersistence();
        return create(task);
    }

    @Override
    public Integer delete(ULong id) {
        throw new GenericException(
                HttpStatus.BAD_REQUEST,
                messageResourceService.getMessage(WorkerMessageResourceService.TASK_DELETION_NOT_ALLOWED));
    }

    public Task cancelTask(ULong taskId) {
        Task task = this.read(taskId);
        if (task == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_NOT_FOUND));
        }
        Scheduler scheduler = this.schedulerService.read(task.getSchedulerId());
        if (scheduler == null) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.updateTask(scheduler, task, TaskOperationType.CANCEL);
        } catch (Exception e) {
            logger.error("Error canceling task: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_CANCEL_TASK),
                    e);
        }
        return super.update(task);
    }

    public Task pauseTask(ULong taskId) {
        Task task = this.read(taskId);
        if (task == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_NOT_FOUND));
        }
        Scheduler scheduler = this.schedulerService.read(task.getSchedulerId());
        if (scheduler == null) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.updateTask(scheduler, task, TaskOperationType.PAUSE);
        } catch (Exception e) {
            logger.error("Error pausing task: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_PAUSE_TASK),
                    e);
        }
        return super.update(task);
    }

    public Task resumeTask(ULong taskId) {
        Task task = this.read(taskId);
        if (task == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_NOT_FOUND));
        }
        Scheduler scheduler = this.schedulerService.read(task.getSchedulerId());
        if (scheduler == null) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.updateTask(scheduler, task, TaskOperationType.RESUME);
        } catch (Exception e) {
            logger.error("Error resuming task: {}", task.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_RESUME_TASK),
                    e);
        }
        return super.update(task);
    }

    @Override
    protected Task updatableEntity(Task entity) {
        Task existing = this.read(entity.getId());
        if (existing == null) {
            return null;
        }
        existing.setName(entity.getName());
        existing.setTaskState(entity.getTaskState());
        existing.setNextFireTime(entity.getNextFireTime());
        existing.setLastFireTime(entity.getLastFireTime());
        existing.setTaskLastFireStatus(entity.getTaskLastFireStatus());
        existing.setLastFireResult(entity.getLastFireResult());
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }
}
