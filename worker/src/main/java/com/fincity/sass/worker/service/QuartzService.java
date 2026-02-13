package com.fincity.sass.worker.service;

import com.fincity.sass.worker.configuration.QuartzConfiguration;
import com.fincity.sass.worker.enums.TaskOperationType;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.google.gson.Gson;
import com.fincity.sass.worker.enums.TaskJobType;
import com.fincity.sass.worker.enums.TaskState;
import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.dto.Task;
import java.time.ZoneId;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.SchedulerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

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
     * Initializes a single scheduler from a Scheduler entity.
     *
     * <p><strong>WARNING:</strong> This method is intended for internal use during service restart
     * to restore scheduler persistence. <u>Do not use this method (`initializeScheduler`)
     * in any other context</u>, especially in runtime job creation or updates, as it may lead to
     * duplicate scheduler instances or inconsistent Quartz state.</p>
     *
     * @param workerScheduler the Scheduler entity from the database table worker_scheduler
     * @return a Scheduler Mono that completes when the scheduler is initialized
     */
    public Scheduler initializeSchedulerOnStartUp(Scheduler workerScheduler) throws Exception {

        log.info("Initializing scheduler: {}", workerScheduler.getName());

        // Step 1: Create and initialize the scheduler factory
        SchedulerFactoryBean factory =
                quartzConfiguration.createSchedulerFactory(applicationContext, workerScheduler.getName());

        // fully initialize the factory with properties set on the factory bean
        factory.afterPropertiesSet();

        // Step 2: Get the scheduler instance
        org.quartz.Scheduler quartzScheduler = factory.getScheduler();

        // Step 3: Bind to repository and configure state
        schedulerRepository.bind(quartzScheduler);

        if (workerScheduler.getSchedulerStatus().equals(SchedulerStatus.STARTED)) {
            quartzScheduler.start();
        } else if (workerScheduler.getSchedulerStatus().equals(SchedulerStatus.STANDBY)) {
            quartzScheduler.standby();
        }

        log.info("Scheduler is pushed to Quartz's SchedulerRepository: {}", workerScheduler.getName());
        workerScheduler.setInstanceId(quartzScheduler.getSchedulerInstanceId());

        return workerScheduler;
    }

    public Scheduler startScheduler(Scheduler workerScheduler) throws SchedulerException {

        // Get the scheduler from the repository
        org.quartz.Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Quartz scheduler not found");
        }

        // Start the scheduler
        quartzScheduler.start();
        log.debug("Started scheduler: {}", workerScheduler.getName());

        // Update the Scheduler object
        workerScheduler.setSchedulerStatus(SchedulerStatus.STARTED);

        return workerScheduler;
    }

    public Scheduler pauseScheduler(Scheduler workerScheduler) throws SchedulerException {

        org.quartz.Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Quartz scheduler not found");
        }

        // Pause the scheduler
        quartzScheduler.standby();
        log.debug("Paused scheduler: {}", workerScheduler.getName());

        // Update the Scheduler object
        workerScheduler.setSchedulerStatus(SchedulerStatus.STANDBY);

        return workerScheduler;
    }

    public Scheduler shutdownScheduler(Scheduler workerScheduler) throws SchedulerException {
        // Get the scheduler from the repository
        org.quartz.Scheduler quartzScheduler = schedulerRepository.lookup(workerScheduler.getName());

        if (quartzScheduler == null) {
            throw new SchedulerException("Unable to shutdown the scheduler");
        }

        // Pause the scheduler
        quartzScheduler.shutdown();
        log.debug("shutdown complete scheduler: {}", workerScheduler.getName());

        // Update the Scheduler object
        workerScheduler.setSchedulerStatus(SchedulerStatus.SHUTDOWN);

        return workerScheduler;
    }

    public Task initializeTask(Scheduler workerScheduler, Task task) throws SchedulerException {

        logger.info("Initializing job: {}", task.getName());

        // get the scheduler
        org.quartz.Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Define the JobDetail
        JobBuilder jobBuilder = JobBuilder.newJob(TaskExecutorJob.class)
                .withIdentity(task.getName(), task.getGroupName())
                .withDescription(task.getDescription());

        if (Boolean.TRUE.equals(task.getDurable())) jobBuilder.storeDurably(); // Optional: allows the job to persist without a trigger

        JobDetail jobDetail = jobBuilder.build();

        // Define a Trigger (Simple or Cron)
        TriggerBuilder<Trigger> triggerBuilder =
                TriggerBuilder.newTrigger().withIdentity(task.getName(), task.getGroupName()); // Trigger name and group

        triggerBuilder.withSchedule(getJobSchedule(task));

        if (task.getStartTime() != null)
            triggerBuilder.startAt(
                    Date.from(task.getStartTime().atZone(DEFAULT_ZONE).toInstant()));

        if (task.getEndTime() != null)
            triggerBuilder.endAt(
                    Date.from(task.getEndTime().atZone(DEFAULT_ZONE).toInstant()));

        Trigger trigger = triggerBuilder.build();

        // Register a job and trigger
        qScheduler.scheduleJob(jobDetail, trigger);

        return task;
    }

    /**
     * Adds task ID to the Quartz job's JobDataMap. Call after task is persisted and has an ID.
     */
    public void addTaskIdToJob(Scheduler workerScheduler, Task task) throws SchedulerException {
        org.quartz.Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());
        JobDetail jobDetail = qScheduler.getJobDetail(jobKey);
        if (jobDetail != null && task.getId() != null) {
            jobDetail.getJobDataMap().put(TaskExecutorJob.TASK_ID, task.getId().toString());
            jobDetail.getJobDataMap().put(TaskExecutorJob.TASK_DATA, task.getJobData() != null ? new Gson().toJson(task.getJobData()) : null);
            qScheduler.addJob(jobDetail, true);
        }
    }

    public Task cancelTask(Scheduler workerScheduler, Task task) throws SchedulerException {

        // get the scheduler
        org.quartz.Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Get job details from the scheduler
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        qScheduler.deleteJob(jobKey);

        task.setTaskState(TaskState.COMPLETE);

        return task;
    }

    public Task pauseTask(Scheduler workerScheduler, Task task) throws SchedulerException {

        // get the scheduler
        org.quartz.Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // Get job details from the scheduler
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        qScheduler.deleteJob(jobKey);

        task.setTaskState(TaskState.COMPLETE);

        return task;
    }

    public Task updateTask(Scheduler workerScheduler, Task task, TaskOperationType taskOperationType)
            throws SchedulerException {

        // get the scheduler
        org.quartz.Scheduler qScheduler = schedulerRepository.lookup(workerScheduler.getName());

        // create a job key
        JobKey jobKey = new JobKey(task.getName(), task.getGroupName());

        switch (taskOperationType) {
            case CANCEL -> {
                qScheduler.deleteJob(jobKey);
                task.setTaskState(TaskState.COMPLETE);
            }
            case PAUSE -> {
                qScheduler.pauseJob(jobKey);
                task.setTaskState(TaskState.PAUSED);
            }
            case RESUME -> {
                qScheduler.resumeJob(jobKey);
                task.setTaskState(TaskState.NORMAL);
            }
            default -> throw new SchedulerException("un-authorized task operation");
        }

        return task;
    }

    private ScheduleBuilder<? extends Trigger> getJobSchedule(Task task) {

        if (task.getTaskJobType().equals(TaskJobType.CRON)) {

            return CronScheduleBuilder.cronSchedule(task.getSchedule());
        }

        SimpleScheduleBuilder simpleScheduleBuilder =
                SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(Integer.parseInt(task.getSchedule()));

        if (Boolean.TRUE.equals(task.getRepeatForever())) {
            simpleScheduleBuilder.repeatForever();
        } else {
            Integer count = task.getRepeatCount();
            simpleScheduleBuilder.withRepeatCount(count != null ? count : 0);
        }

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
