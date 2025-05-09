package com.fincity.sass.worker.service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.sass.worker.configuration.QuartzConfiguration;
import com.fincity.sass.worker.enums.TaskOperationType;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.jooq.enums.WorkerSchedulerStatus;
import com.fincity.sass.worker.jooq.enums.WorkerTaskJobType;
import com.fincity.sass.worker.model.Task;
import com.fincity.sass.worker.model.WorkerScheduler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.SchedulerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.ZoneId;
import java.util.Date;

@Service
@Slf4j
public class QuartzService {
    private final QuartzConfiguration quartzConfiguration;
    private final ApplicationContext applicationContext;
    private final SchedulerRepository schedulerRepository;

    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    public QuartzService(
            QuartzConfiguration quartzConfiguration,
            ApplicationContext applicationContext,
            SchedulerRepository schedulerRepository) {
        this.quartzConfiguration = quartzConfiguration;
        this.applicationContext = applicationContext;
        this.schedulerRepository = schedulerRepository;
    }

    private static final Logger logger = LoggerFactory.getLogger(QuartzService.class);

    /**
     * Initializes a single scheduler from a WorkerScheduler entity.
     *
     * <p><strong>WARNING:</strong> This method is intended for internal use during service restart
     * to restore scheduler persistence. <u>Do not use this method (`initializeScheduler`)
     * in any other context</u>, especially in runtime job creation or updates, as it may lead to
     * duplicate scheduler instances or inconsistent Quartz state.</p>
     *
     * @param workerScheduler the WorkerScheduler entity from the database table worker_scheduler
     * @return a WorkerScheduler Mono that completes when the scheduler is initialized
     */
    public WorkerScheduler initializeSchedulerOnStartUp(WorkerScheduler workerScheduler) throws Exception {

        log.info("Initializing scheduler: {}", workerScheduler.getName());

        // Step 1: Create and initialize the scheduler factory
        SchedulerFactoryBean factory =
                quartzConfiguration.createSchedulerFactory(applicationContext, workerScheduler.getName());

        factory.afterPropertiesSet();

        // Step 2: Get the scheduler instance
        Scheduler scheduler = factory.getScheduler();

        // Step 3: Bind to repository and configure state
        schedulerRepository.bind(scheduler);

        if (workerScheduler.getStatus().equals(WorkerSchedulerStatus.STARTED)) {
            scheduler.start();
        } else if (workerScheduler.getStatus().equals(WorkerSchedulerStatus.STANDBY)) {
            scheduler.standby();
        }

        log.info("Scheduler is pushed to Quartz's SchedulerRepository: {}", workerScheduler.getName());
        workerScheduler.setInstanceId(scheduler.getSchedulerInstanceId());

        return workerScheduler;
    }

    public WorkerScheduler startScheduler(WorkerScheduler workerScheduler) throws SchedulerException {

        // Get the scheduler from the repository
        Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Quartz scheduler not found");
        }

        // Pause the scheduler
        quartzScheduler.start();
        log.debug("Started scheduler: {}", workerScheduler.getName());

        // Update the WorkerScheduler object
        workerScheduler.setStatus(WorkerSchedulerStatus.STARTED);

        return workerScheduler;
    }

    public WorkerScheduler pauseScheduler(WorkerScheduler workerScheduler) throws SchedulerException {

        Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Quartz scheduler not found");
        }

        // Pause the scheduler
        quartzScheduler.standby();
        log.debug("Paused scheduler: {}", workerScheduler.getName());

        // Update the WorkerScheduler object
        workerScheduler.setStatus(WorkerSchedulerStatus.STANDBY);

        return workerScheduler;
    }

    public WorkerScheduler shutdownScheduler(WorkerScheduler workerScheduler) throws SchedulerException {
        // Get the scheduler from the repository
        Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Unable to shutdown the scheduler");
        }

        // Pause the scheduler
        quartzScheduler.shutdown();
        log.debug("shutdown complete scheduler: {}", workerScheduler.getName());

        // Update the WorkerScheduler object
        workerScheduler.setStatus(WorkerSchedulerStatus.SHUTDOWN);

        return workerScheduler;
    }

    public Task initializeTask(WorkerScheduler workerScheduler, Task task) throws SchedulerException {

        logger.info("Initializing job: {}", task.getName());

        // get the scheduler
        Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Define the JobDetail
        JobBuilder jobBuilder = JobBuilder.newJob(TaskExecutorJob.class)
                .withIdentity(task.getName(), task.getGroupName())
                .withDescription(task.getDescription());

        if (task.getDurable())
            jobBuilder.storeDurably(); // Optional: allows the job to persist without a trigger

        JobDetail jobDetail = jobBuilder.build();

        // Define a Trigger (Simple or Cron)
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(task.getName(), task.getGroupName()); // Trigger name and group

        triggerBuilder.withSchedule(getJobSchedule(task));

        if (task.getStartTime() != null)
            triggerBuilder.startAt(Date.from(
                    task.getStartTime().atZone(DEFAULT_ZONE).toInstant()));

        if (task.getEndTime() != null)
            triggerBuilder.endAt(
                    Date.from(task.getEndTime().atZone(DEFAULT_ZONE).toInstant()));

        Trigger trigger = triggerBuilder.build();

        // Register a job and trigger
        qScheduler.scheduleJob(jobDetail, trigger);

        return task;
    }

    public Task cancelTask(WorkerScheduler workerScheduler, Task task) throws SchedulerException {

        // get the scheduler
        Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Get job details from the scheduler
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        qScheduler.deleteJob(jobKey);

        task.setStatus("CANCELLED");

        return task;

    }

    public Task pauseTask(WorkerScheduler workerScheduler, Task task) throws SchedulerException {

        // get the scheduler
        Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Get job details from the scheduler
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        qScheduler.deleteJob(jobKey);

        task.setStatus("CANCELLED");

        return task;

    }

    public Task updateTask(WorkerScheduler workerScheduler, Task task, TaskOperationType taskOperationType) throws SchedulerException {

        // get the scheduler
        Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // create a job key
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        switch (taskOperationType) {
            case CANCEL -> {
                qScheduler.deleteJob(jobKey);
                task.setStatus("CANCELLED");
            }
            case PAUSE -> {
                qScheduler.pauseJob(jobKey);
                task.setStatus("PAUSED");
            }
            case RESUME -> {
                qScheduler.resumeJob(jobKey);
                task.setStatus("RESUMED");
            }
            default -> {
                 throw new SchedulerException("un-authorized task operation");
            }
        }

        return task;

    }


    private ScheduleBuilder<? extends Trigger> getJobSchedule(Task task) {

        if (task.getJobType().equals(WorkerTaskJobType.CRON)) {

            return CronScheduleBuilder.cronSchedule(task.getSchedule());
        }

        SimpleScheduleBuilder simpleScheduleBuilder =
                SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(Integer.parseInt(task.getSchedule()));

        if (task.getRepeatForever()) simpleScheduleBuilder.repeatForever();
        else simpleScheduleBuilder.withRepeatCount(task.getRepeatCount());

        return simpleScheduleBuilder;
    }

    //    public QuartzService(
    //            @Qualifier("defaultQuartzScheduler") Scheduler scheduler,
    //            TaskService taskService,
    //            WorkerMessageResourceService workerMessageResourceService) {
    //        this.scheduler = scheduler;
    //        this.taskService = taskService;
    //        this.workerMessageResourceService = workerMessageResourceService;
    //    }

    //    public Mono<Void> unscheduleTask(String jobName) {
    //        return Mono.fromCallable(() -> {
    //            scheduler.unscheduleJob(TriggerKey.triggerKey(jobName + "-trigger", "worker-triggers"));
    //            scheduler.deleteJob(JobKey.jobKey(jobName, "worker-jobs"));
    //            return null;
    //        });
    //    }
}
