package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.enums.TaskOperationType;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.jooq.enums.WorkerTaskJobType;
import com.fincity.sass.worker.jooq.tables.records.WorkerTaskRecord;
import com.fincity.sass.worker.model.Task;
import com.fincity.sass.worker.model.WorkerScheduler;
import org.jooq.types.ULong;
import org.quartz.*;
import org.quartz.impl.SchedulerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class TaskService extends AbstractJOOQUpdatableDataService<WorkerTaskRecord, ULong, Task, TaskDAO> {

    private static final String JOB_NAME = "jobName";
    private static final String CRON_EXPRESSION = "cronExpression";
    private static final String NEXT_EXECUTION_TIME = "nextExecutionTime";
    private static final String STATUS = "status";

    private final SchedulerRepository schedulerRepository;
    private final SchedulerService schedulerService;

    private final QuartzService quartzService;

    private TaskService(
            SchedulerRepository schedulerRepository, SchedulerService schedulerService, QuartzService quartzService) {
        this.schedulerRepository = schedulerRepository;
        this.schedulerService = schedulerService;
        this.quartzService = quartzService;
    }

    @Override
    public Mono<Task> create(Task task) {
        return FlatMapUtil.flatMapMono(
                () -> this.schedulerService.read(task.getSchedulerId()),
                scheduler -> Mono.fromCallable(() -> this.quartzService.initializeTask(scheduler, task))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            logger.error("Error initializing scheduler: {}", task.getName(), e);
                            return Mono.error(new RuntimeException("Failed to initialize scheduler", e));
                        })
                        .flatMap(super::create));
    }

    // TODO exclude delete api
    @Override
    public Mono<Integer> delete(ULong id) {

        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Task deletion is not allowed"));
    }

    // mark task-status as stopped and cancel job triggers
    public Mono<Task> cancelTask(ULong taskId) {

        return FlatMapUtil.flatMapMono(
                () -> this.read(taskId),
                task -> this.schedulerService.read(task.getSchedulerId()),
                (task, workerScheduler) -> Mono.fromCallable(
                                () -> this.quartzService.updateTask(workerScheduler, task, TaskOperationType.CANCEL))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            logger.error("Error canceling task: {}", task.getName(), e);
                            return Mono.error(new RuntimeException("Failed to cancel task", e));
                        })
                        .flatMap(super::update));
    }

    // mark task-status as stopped and pause job triggers
    public Mono<Task> pauseTask(ULong taskId) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(taskId),
                task -> this.schedulerService.read(task.getSchedulerId()),
                (task, workerScheduler) -> Mono.fromCallable(
                                () -> this.quartzService.updateTask(workerScheduler, task, TaskOperationType.PAUSE))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            logger.error("Error pausing task: {}", task.getName(), e);
                            return Mono.error(new RuntimeException("Failed to pause task", e));
                        })
                        .flatMap(super::update));
    }

    public Mono<Task> resumeTask(ULong taskId) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(taskId),
                task -> this.schedulerService.read(task.getSchedulerId()),
                (task, workerScheduler) -> Mono.fromCallable(
                                () -> this.quartzService.updateTask(workerScheduler, task, TaskOperationType.RESUME))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            logger.error("Error resuming task: {}", task.getName(), e);
                            return Mono.error(new RuntimeException("Failed to resume task", e));
                        })
                        .flatMap(super::update));
    }

    // testing jobExecutionContext
    public Mono<Task> test(ULong taskId) {
        return FlatMapUtil.flatMapMono(
                // Step 1: Read the task from database
                () -> this.read(taskId),

                // Step 2: Get job data from JobExecutionContext
                task -> this.schedulerService.read(task.getSchedulerId()),
                (task, ws) -> {
                    logger.info("Testing job: {}", task.getName());

                    try {
                        Scheduler scheduler = schedulerRepository.lookup(ws.getName());

                        // Get job details from the scheduler
                        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());
                        JobDetail jobDetail = scheduler.getJobDetail(jobKey);

                        // Get job data map
                        JobDataMap jobDataMap = jobDetail.getJobDataMap();

                        // If no current context, just use the stored job data
                        logger.info("Job stored data: {}", jobDataMap);

                        // Set task ID in job data map if not present
                        if (!jobDataMap.containsKey(TaskExecutorJob.TASK_ID)) {
                            jobDataMap.put(TaskExecutorJob.TASK_ID, task.getId().toString());
                            scheduler.addJob(jobDetail, true);
                            logger.info("Added task ID to job data map");
                        }

                        return Mono.just(task);
                    } catch (SchedulerException e) {
                        logger.error("Error getting job data: {}", e.getMessage(), e);
                        return Mono.error(new RuntimeException("Failed to get job data", e));
                    }
                });
    }

    public Mono<Boolean> deleteJob(WorkerScheduler workerScheduler, Task workerTask) {
        return Mono.fromCallable(() -> {
                    try {
                        logger.debug(
                                "Attempting to delete job: {} from group: {} in scheduler: {}",
                                workerTask.getName(),
                                workerTask.getGroupName(),
                                workerScheduler.getName());

                        Scheduler scheduler = schedulerRepository.lookup(workerScheduler.getName());
                        if (scheduler == null) {
                            logger.warn("Scheduler not found: {}", workerScheduler.getName());
                            return false;
                        }

                        JobKey jobKey = new JobKey(workerTask.getName(), workerTask.getGroupName());
                        boolean result = scheduler.deleteJob(jobKey);

                        if (result) {
                            logger.info(
                                    "Successfully deleted job: {} from group: {}",
                                    workerTask.getName(),
                                    workerTask.getGroupName());
                        } else {
                            logger.warn(
                                    "Failed to delete job: {} from group: {} (job not found)",
                                    workerTask.getName(),
                                    workerTask.getGroupName());
                        }

                        return result;
                    } catch (SchedulerException e) {
                        logger.error(
                                "Error deleting job: {} from group: {} in scheduler: {}",
                                workerTask.getName(),
                                workerTask.getGroupName(),
                                workerScheduler.getName(),
                                e);
                        throw new RuntimeException(
                                "Failed to delete job: " + workerTask.getName() + " from group: "
                                        + workerTask.getGroupName(),
                                e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Task> findByNameNGroup(String name, String groupName) {

        return this.dao.findByNameNGroup(name, groupName);
    }

    @Override
    protected Mono<Task> updatableEntity(Task entity) {

        return this.read(entity.getId()).map(existing -> {
            existing.setName(entity.getName());
            existing.setNextExecutionTime(entity.getNextExecutionTime());
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });
    }

    // testing trigger status enum values
    public Mono<String> test1(ULong id) {

        return FlatMapUtil.flatMapMono(
                () -> this.read(id), wt -> this.schedulerService.read(wt.getSchedulerId()), (wt, ws) -> {
                    // get qs from scheduler repo

                    Scheduler qs = schedulerRepository.lookup(ws.getName());

                    TriggerKey triggerKey = new TriggerKey(wt.getName(), wt.getGroupName());

                    try {
                        Trigger.TriggerState ts = qs.getTriggerState(triggerKey);
                        return Mono.just(ts.toString());
                    } catch (SchedulerException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
