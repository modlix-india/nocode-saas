package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.model.Task;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class SchedulingService {

    private final Scheduler scheduler;
    private final TaskService taskService;
    private final WorkerMessageResourceService workerMessageResourceService;
    private static final Logger logger = LoggerFactory.getLogger(SchedulingService.class);

    public SchedulingService(
            Scheduler scheduler,
            TaskService taskService,
            WorkerMessageResourceService workerMessageResourceService) {
        this.scheduler = scheduler;
        this.taskService = taskService;
        this.workerMessageResourceService = workerMessageResourceService;
    }

    public Mono<Task> scheduleTask(Task task) {
        return FlatMapUtil.flatMapMono(
                        // Step 1: Create job data
                        () -> {
                            JobDataMap jobData = new JobDataMap();
                            jobData.put("taskId", task.getId().toString());
                            jobData.put("taskData", task.getJobName());
                            return Mono.just(jobData);
                        },

                        // Step 2: Create job and schedule
                        jobData -> {
                            JobDetail job = JobBuilder.newJob(TaskExecutorJob.class)
                                    .withIdentity(task.getJobName(), "worker-jobs")
                                    .usingJobData(jobData)
                                    .storeDurably()
                                    .build();
                            return createCronTriggerAndSchedule(job, task);
                        },

                        // Step 3: Save task
                        (jobData, trigger) -> this.taskService.update(task))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SchedulingService.scheduleTask"));
    }

    private Mono<CronTrigger> createCronTriggerAndSchedule(JobDetail job, Task task) {
        try {
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(task.getJobName() + "-trigger", "worker-triggers")
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .forJob(job)
                    .build();

            scheduler.scheduleJob(job, trigger);
            logger.info("Scheduled cron job: {}", task.getJobName());
            return Mono.just(trigger);
        } catch (SchedulerException e) {
            logger.error("Error scheduling job: {}", e.getMessage());
            return Mono.error(new RuntimeException("Failed to schedule job", e));
        }
    }

    public Mono<Void> unscheduleTask(String jobName) {
        return Mono.fromCallable(() -> {
            scheduler.unscheduleJob(TriggerKey.triggerKey(jobName + "-trigger", "worker-triggers"));
            scheduler.deleteJob(JobKey.jobKey(jobName, "worker-jobs"));
            return null;
        });
    }
}