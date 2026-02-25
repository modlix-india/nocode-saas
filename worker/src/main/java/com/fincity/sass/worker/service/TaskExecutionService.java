package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.service.execution.CoreFunctionExecutionService;
import com.fincity.sass.worker.service.execution.SSLCertificateRenewalService;
import com.fincity.sass.worker.service.execution.TicketExpirationService;
import com.modlix.saas.commons2.jooq.util.ULongUtil;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private final Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskService taskService;
    private final CoreFunctionExecutionService coreFunctionExecutionService;
    private final SSLCertificateRenewalService sslCertificateRenewalService;
    private final TicketExpirationService ticketExpirationService;

    private TaskExecutionService(
            TaskService taskService,
            CoreFunctionExecutionService coreFunctionExecutionService,
            SSLCertificateRenewalService sslCertificateRenewalService,
            TicketExpirationService ticketExpirationService) {
        this.taskService = taskService;
        this.coreFunctionExecutionService = coreFunctionExecutionService;
        this.sslCertificateRenewalService = sslCertificateRenewalService;
        this.ticketExpirationService = ticketExpirationService;
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
        String result;
        switch (task.getTaskJobType()) {
            case SSL_RENEWAL -> result = sslCertificateRenewalService.execute(task);
            case TICKET_EXPIRATION -> result = ticketExpirationService.execute(task);
            default -> result = coreFunctionExecutionService.execute(task);
        }
        task.setLastFireResult(result);
        return task;
    }
}
