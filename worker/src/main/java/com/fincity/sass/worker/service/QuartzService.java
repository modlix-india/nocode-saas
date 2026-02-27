package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dto.ClientScheduleControl;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.fincity.sass.worker.enums.TaskJobType;
import com.fincity.sass.worker.enums.TaskOperationType;
import com.fincity.sass.worker.enums.TaskState;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.google.gson.Gson;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuartzService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final Logger logger = LoggerFactory.getLogger(QuartzService.class);
    private final Scheduler quartzScheduler;
    private final Gson gson;

    public QuartzService(Scheduler quartzScheduler, Gson gson) {
        this.quartzScheduler = quartzScheduler;
        this.gson = gson;
    }

    public void startClientScheduleControl(ClientScheduleControl control) throws SchedulerException {

        String jobGroup = control.getJobGroup();

        quartzScheduler.resumeJobs(GroupMatcher.jobGroupEquals(jobGroup));

        logger.debug("Resumed jobs for group: {}", jobGroup);
        control.setSchedulerStatus(SchedulerStatus.STARTED);
    }

    public void pauseClientScheduleControl(ClientScheduleControl control) throws SchedulerException {

        String jobGroup = control.getJobGroup();

        quartzScheduler.pauseJobs(GroupMatcher.jobGroupEquals(jobGroup));

        logger.debug("Paused jobs for group: {}", jobGroup);
        control.setSchedulerStatus(SchedulerStatus.STANDBY);
    }

    public void shutdownClientScheduleControl(ClientScheduleControl control) throws SchedulerException {

        String jobGroup = control.getJobGroup();

        Set<JobKey> keys = quartzScheduler.getJobKeys(GroupMatcher.jobGroupEquals(jobGroup));

        if (!keys.isEmpty()) quartzScheduler.deleteJobs(new ArrayList<>(keys));

        logger.debug("Deleted jobs for group (shutdown): {}", jobGroup);
        control.setSchedulerStatus(SchedulerStatus.SHUTDOWN);
    }

    public void initializeTask(Task task) throws SchedulerException {
        logger.info("Initializing job: {}", task.getName());

        if (task.getId() == null)
            throw new SchedulerException("Task must be persisted before scheduling; task ID is required");

        if (task.getJobGroup() == null)
            throw new SchedulerException("Task must have clientCode to derive Quartz job group");

        String jobGroup = task.getJobGroup();

        JobBuilder jobBuilder = JobBuilder.newJob(TaskExecutorJob.class)
                .withIdentity(task.getName(), jobGroup)
                .withDescription(task.getDescription())
                .usingJobData(TaskExecutorJob.TASK_ID, task.getId().toString())
                .usingJobData(
                        TaskExecutorJob.TASK_DATA, task.getJobData() != null ? gson.toJson(task.getJobData()) : "");

        if (Boolean.TRUE.equals(task.getDurable())) jobBuilder.storeDurably();

        JobDetail jobDetail = jobBuilder.build();

        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger().withIdentity(task.getName(), jobGroup);

        triggerBuilder.withSchedule(getJobSchedule(task));

        if (task.getStartTime() != null)
            triggerBuilder.startAt(
                    Date.from(task.getStartTime().atZone(DEFAULT_ZONE).toInstant()));

        if (task.getEndTime() != null)
            triggerBuilder.endAt(
                    Date.from(task.getEndTime().atZone(DEFAULT_ZONE).toInstant()));

        Trigger trigger = triggerBuilder.build();

        quartzScheduler.scheduleJob(jobDetail, trigger);
    }

    public void updateTask(Task task, TaskOperationType taskOperationType) throws SchedulerException {

        String jobGroup = task.getJobGroup();

        if (jobGroup == null) throw new SchedulerException("Task must have clientCode to derive Quartz job group");
        JobKey jobKey = new JobKey(task.getName(), jobGroup);

        switch (taskOperationType) {
            case CANCEL -> {
                quartzScheduler.deleteJob(jobKey);
                task.setTaskState(TaskState.COMPLETE);
            }
            case PAUSE -> {
                quartzScheduler.pauseJob(jobKey);
                task.setTaskState(TaskState.PAUSED);
            }
            case RESUME -> {
                quartzScheduler.resumeJob(jobKey);
                task.setTaskState(TaskState.NORMAL);
            }
            default -> throw new SchedulerException("un-authorized task operation");
        }
    }

    private ScheduleBuilder<? extends Trigger> getJobSchedule(Task task) {

        if (task.getTaskJobType().equals(TaskJobType.CRON))
            return CronScheduleBuilder.cronSchedule(task.getSchedule()).withMisfireHandlingInstructionFireAndProceed();

        SimpleScheduleBuilder simpleScheduleBuilder =
                SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(Integer.parseInt(task.getSchedule()));

        if (Boolean.TRUE.equals(task.getRepeatForever())) {
            simpleScheduleBuilder.repeatForever();
        } else {
            Integer count = task.getRepeatCount();
            simpleScheduleBuilder.withRepeatCount(count != null ? count : 0);
        }

        return simpleScheduleBuilder.withMisfireHandlingInstructionFireNow();
    }
}
