package com.fincity.sass.worker.job;

import com.fincity.sass.worker.model.Task;
import com.fincity.sass.worker.service.TaskService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fincity.sass.worker.service.TaskExecutionService;

@Component
public class TaskExecutorJob implements Job {

    public static final String TASK_ID = "taskId";
    public static final String TASK_DATA = "taskData";

    private TaskExecutionService taskExecutionService;
    private TaskService taskService;
    private final Logger logger = LoggerFactory.getLogger(TaskExecutorJob.class);

    public TaskExecutorJob() {
        // Default constructor required by Quartz
    }

    public TaskExecutorJob(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        logger.info("Executing job: {} in group: {}", jobName, jobGroup);

        logger.debug(
                " __          __        _             \n" +
                " \\ \\        / /       | |            \n" +
                "  \\ \\  /\\  / /__  _ __| | _____ _ __ \n" +
                "   \\ \\/  \\/ / _ \\| '__| |/ / _ \\ '__|\n" +
                "    \\  /\\  / (_) | |  |   <  __/ |   \n" +
                "     \\/  \\/ \\___/|_|  |_|\\_\\___|_|   ");



        String taskId = context.getJobDetail().getJobDataMap().getString(TASK_ID);
        String taskData = context.getJobDetail().getJobDataMap().getString(TASK_DATA);

        if (taskId == null) {
            logger.error("Task ID not found in job data map");
            throw new JobExecutionException("Task ID not found in job data map");
        }

        taskExecutionService.executeTask(taskId)
                .subscribe(
                        result -> logger.info("{} task in {} completed successfully: {}",jobName, jobGroup, result),
                        error -> {
                            logger.error("Error executing task: {}", error.getMessage());
                            throw new RuntimeException(error);
                        }
                );
    }
}
