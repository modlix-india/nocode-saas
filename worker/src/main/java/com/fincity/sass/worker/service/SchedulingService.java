package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.model.Task;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class SchedulingService {

    private final Scheduler scheduler;
    private final TaskDAO taskDAO;
    private final WorkerMessageResourceService workerMessageResourceService;
    private static final Logger logger = LoggerFactory.getLogger(
            SchedulingService.class
    );

    public SchedulingService(
            Scheduler scheduler,
            TaskDAO taskDAO,
            WorkerMessageResourceService workerMessageResourceService
    ) {
        this.scheduler = scheduler;
        this.taskDAO = taskDAO;
        this.workerMessageResourceService = workerMessageResourceService;
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndExecuteTasks() {

        FlatMapUtil.flatMapMono(

                        () -> taskDAO.findTasksDueForExecution(LocalDateTime.now()),

                        tasks -> Flux.fromIterable(tasks)
                                .flatMap(this::scheduleExistingTask)
                                .collectList()
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SchedulingService.checkAndExecuteTasks"));
    }

    private Mono<Void> scheduleExistingTask(Task task) {

        return FlatMapUtil.flatMapMono(
                // Step 1: Create job data
                () -> {
                    JobDataMap jobData = new JobDataMap();
                    jobData.put("taskId", task.getId().toString());
                    jobData.put("taskData", task.getJobName());
                    return Mono.just(jobData);
                },

                // Step 2: Create job
                jobData -> {
                    JobDetail job = JobBuilder.newJob(TaskExecutorJob.class)
                            .withIdentity(
                                    task.getJobName() + "-" + System.currentTimeMillis(),
                                    "worker-jobs"
                            )
                            .usingJobData(jobData)
                            .build();
                    return Mono.just(job);
                },

                // Step 3: Create and schedule trigger
                (jobData, job) -> createTriggerNSchedule(jobData, job, task),

                // Step 4: Update task with next execution time
                (jobDataMap, job, trigger) -> updateNextExecution(task)
        ).contextWrite(
                Context.of(
                        LogUtil.METHOD_NAME,
                        "SchedulingService.scheduleExistingTask"
                )
        );
    }

    private Mono<SimpleTrigger> createTriggerNSchedule(JobDataMap jobDataMap, JobDetail job, Task task) {

        SimpleTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(
                        task.getJobName() + "-trigger-" + System.currentTimeMillis(),
                        "worker-triggers"
                )
                .startNow()
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withMisfireHandlingInstructionFireNow()
                )
                .build();

        try {
            scheduler.scheduleJob(job, trigger);
            logger.info("Scheduled job: {}", task.getJobName());
        } catch (SchedulerException e) {
            logger.error("Error scheduling job: {}", e.getMessage());
            return Mono.error(
                    new RuntimeException("Failed to schedule job", e)
            );
        }

        return Mono.just(trigger);
    }

    private Mono<Void> updateNextExecution(Task task) {
        return FlatMapUtil.flatMapMono(
                () -> {
                    try {
                        CronExpression cronExp = new CronExpression(task.getCronExpression());
                        return Mono.just(cronExp);
                    } catch (ParseException ex) {
                        return Mono.error(new GenericException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid cron expression: " + task.getCronExpression(),
                                ex));
                    }
                },
                cronExp -> {
                    Date nextValidTime = cronExp.getNextValidTimeAfter(new Date());
                    if (nextValidTime == null) {
                        logger.warn("No next execution time for task {}", task.getId());
                        return Mono.empty();
                    }
                    task.setNextExecutionTime(
                            nextValidTime.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    );
                    return taskDAO.update(task);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SchedulingService.updateNextExecution")).then();
    }
}
