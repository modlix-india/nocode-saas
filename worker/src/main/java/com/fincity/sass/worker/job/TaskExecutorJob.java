package com.fincity.sass.worker.job;

import com.fincity.sass.worker.service.TaskExecutionService;
import com.fincity.sass.worker.service.WorkerMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutorJob implements Job {

    public static final String TASK_ID = "taskId";
    public static final String TASK_DATA = "taskData";

    private TaskExecutionService taskExecutionService;
    private WorkerMessageResourceService messageResourceService;
    private final Logger logger = LoggerFactory.getLogger(TaskExecutorJob.class);

    public TaskExecutorJob() {
        // Default constructor required by Quartz; dependencies injected via AutowiringSpringBeanJobFactory
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        logger.info("Executing job: {} in group: {}", jobName, jobGroup);

        String taskId = context.getJobDetail().getJobDataMap().getString(TASK_ID);
        String taskData = context.getJobDetail().getJobDataMap().getString(TASK_DATA);

        if (taskId == null) {
            logger.error("Task ID not found in job data map");
            throw new JobExecutionException(new GenericException(
                    HttpStatus.BAD_REQUEST,
                    messageResourceService.getMessage(WorkerMessageResourceService.TASK_ID_NOT_FOUND)));
        }

        try {
            boolean result = taskExecutionService.executeTask(taskId, taskData);
            logger.info("{} task in {} completed successfully: {}", jobName, jobGroup, result);
        } catch (GenericException e) {
            logger.error("Error executing task: {}", e.getMessage());
            throw new JobExecutionException(e);
        } catch (Exception e) {
            logger.error("Error executing task: {}", e.getMessage());
            throw new JobExecutionException(new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e));
        }
    }
}
