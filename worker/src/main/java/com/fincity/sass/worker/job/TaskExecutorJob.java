package com.fincity.sass.worker.job;

import com.fincity.sass.worker.dao.TaskDAO;
import org.jooq.types.ULong;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class TaskExecutorJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutorJob.class);
    private final TaskDAO taskDAO;

    public TaskExecutorJob(TaskDAO taskDAO) {
        this.taskDAO = taskDAO;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobData = context.getJobDetail().getJobDataMap();
        String taskId = jobData.getString("taskId"); // Get the task ID from job data
        String jobName = context.getJobDetail().getKey().getName();

        try {
            logger.info("Starting execution for job: {} with task ID: {}", jobName, taskId);

            Map<String, Object> runningStatus = new HashMap<>();
            runningStatus.put("status", "RUNNING");
            runningStatus.put("lastExecutionTime", LocalDateTime.now());

            taskDAO.update(ULong.valueOf(taskId), runningStatus) // Use ULong for ID
                    .flatMap(task -> {
                        return executeTask(jobData);
                    })
                    .flatMap(result -> {
                        Map<String, Object> finishedStatus = new HashMap<>();
                        finishedStatus.put("status", "FINISHED");
                        finishedStatus.put("lastExecutionResult", result);
                        finishedStatus.put("lastExecutionTime", LocalDateTime.now());
                        return taskDAO.update(ULong.valueOf(taskId), finishedStatus);
                    })
                    .doOnError(error -> {
                        logger.error("Error executing job: {} with task ID: {}", jobName, taskId, error);
                        Map<String, Object> failedStatus = new HashMap<>();
                        failedStatus.put("status", "FAILED");
                        failedStatus.put("lastExecutionResult", error.getMessage());
                        failedStatus.put("lastExecutionTime", LocalDateTime.now());
                        taskDAO.update(ULong.valueOf(taskId), failedStatus).subscribe();
                    })
                    .subscribe(
                            success -> logger.info("Job {} with task ID: {} completed successfully", jobName, taskId),
                            error -> logger.error("Job {} with task ID: {} failed", jobName, taskId, error));

        } catch (Exception e) {
            logger.error("Critical error in job execution: {} with task ID: {}", jobName, taskId, e);
            throw new JobExecutionException(e);
        }
    }

    private reactor.core.publisher.Mono<String> executeTask(JobDataMap jobData) {
        return reactor.core.publisher.Mono.just("Task executed successfully at: " + java.time.LocalDateTime.now());
    }
}