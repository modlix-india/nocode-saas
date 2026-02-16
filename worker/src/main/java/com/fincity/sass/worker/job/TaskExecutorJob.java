package com.fincity.sass.worker.job;

import com.fincity.sass.worker.service.TaskExecutionService;
import com.fincity.sass.worker.service.WorkerMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutorJob implements Job {

    public static final String TASK_ID = "taskId";
    public static final String TASK_DATA = "taskData";
    private final Logger logger = LoggerFactory.getLogger(TaskExecutorJob.class);

    @Autowired
    private TaskExecutionService taskExecutionService;

    @Autowired
    private WorkerMessageResourceService messageResourceService;

    public TaskExecutorJob() {
        // Default constructor required by Quartz; dependencies injected via AutowiringSpringBeanJobFactory
    }

    private static String getTaskId(JobExecutionContext context) {
        return context.getJobDetail().getJobDataMap().getString(TASK_ID);
    }

    private static String getTaskData(JobExecutionContext context) {
        return context.getJobDetail().getJobDataMap().getString(TASK_DATA);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobKey jobKey = context.getJobDetail().getKey();
        String jobName = jobKey.getName();
        String jobGroup = jobKey.getGroup();

        logger.info("Executing job: {} in group: {}", jobName, jobGroup);

        String taskId = getTaskId(context);
        String taskData = getTaskData(context);

        if (taskId == null || taskId.isBlank()) {
            logger.error("Task ID not found in job data map");
            throw new JobExecutionException(new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_ID_NOT_FOUND)));
        }

        try {
            taskExecutionService.executeTask(taskId, taskData);
            logger.info("Task {} in group {} completed successfully", jobName, jobGroup);
        } catch (Exception e) {
            logger.error("Error executing task {} in group {}: {}", jobName, jobGroup, e.getMessage());
            Throwable cause = e instanceof GenericException
                    ? e
                    : new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
            throw new JobExecutionException(cause);
        }
    }
}
