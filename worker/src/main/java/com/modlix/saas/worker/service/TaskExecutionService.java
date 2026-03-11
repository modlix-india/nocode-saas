package com.modlix.saas.worker.service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.service.execution.SSLCertificateRenewalService;
import com.modlix.saas.worker.service.execution.TokenCleanupService;
import com.modlix.saas.commons2.jooq.util.ULongUtil;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private final Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskService taskService;
    private final SSLCertificateRenewalService sslCertificateRenewalService;
    private final TokenCleanupService tokenCleanupService;

    private TaskExecutionService(
            TaskService taskService,
            SSLCertificateRenewalService sslCertificateRenewalService,
            TokenCleanupService tokenCleanupService) {
        this.taskService = taskService;
        this.sslCertificateRenewalService = sslCertificateRenewalService;
        this.tokenCleanupService = tokenCleanupService;
    }

    public boolean executeTask(String taskId, String taskData) {
        try {
            Task task = taskService.read(ULongUtil.valueOf(taskId));
            if (task == null) {
                logger.error("Task not found: {}", taskId);
                return false;
            }
            task.setLastFireTime(LocalDateTime.now());
            Task processedTask = processTask(task);
            if (processedTask.getLastFireResult() == null)
                processedTask.setLastFireResult("Task completed successfully");

            taskService.update(processedTask);
            return true;
        } catch (Exception error) {
            handleTaskError(taskId, error);
            return false;
        }
    }

    private void handleTaskError(String taskId, Exception error) {
        logger.error("Error executing task {}: {}", taskId, error.getMessage());
        try {
            Task task = taskService.read(ULongUtil.valueOf(taskId));
            if (task != null) {
                task.setLastFireResult(error.getMessage());
                taskService.update(task);
            }
        } catch (Exception e) {
            logger.error("Could not update task {} with error status: {}", taskId, e.getMessage());
        }
    }

    private Task processTask(Task task) {
        logger.info("Processing task: {} [type={}]", task.getName(), task.getTaskJobType());
        String result = switch (task.getTaskJobType()) {
            case SSL_RENEWAL -> sslCertificateRenewalService.execute(task);
            case TOKEN_CLEANUP -> tokenCleanupService.execute(task);
        };
        logger.info("Task completed: {} [type={}] — {}", task.getName(), task.getTaskJobType(), result);
        task.setLastFireResult(result);
        return task;
    }
}
