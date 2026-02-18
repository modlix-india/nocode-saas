package com.fincity.sass.worker.service;

import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.enums.TaskJobType;
import com.fincity.sass.worker.model.common.FunctionExecutionSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.modlix.saas.commons2.jooq.util.ULongUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private final TaskService taskService;
    private final CoreFunctionService coreFunctionService;
    private final SSLCertificateRenewalService sslCertificateRenewalService;
    private final Gson gson;

    private final Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    @Value("${worker.task-execution.timeout-minutes:5}")
    private int timeoutMinutes;

    @Value("${worker.task-execution.max-result-length:2000}")
    private int maxResultLength;

    private final TicketExpirationService ticketExpirationService;

    private TaskExecutionService(
            TaskService taskService,
            CoreFunctionService coreFunctionService,
            SSLCertificateRenewalService sslCertificateRenewalService,
            TicketExpirationService ticketExpirationService,
            Gson gson) {
        this.taskService = taskService;
        this.coreFunctionService = coreFunctionService;
        this.sslCertificateRenewalService = sslCertificateRenewalService;
        this.ticketExpirationService = ticketExpirationService;
        this.gson = gson;
    }

    /**
     * Execute a task with the given ID.
     *
     * @param taskId The ID of the task to execute
     * @param taskData Additional data for the task (currently unused, reserved for future use)
     * @return true when the task execution is complete
     */
    public boolean executeTask(String taskId, String taskData) {
        try {
            Task task = taskService.read(ULongUtil.valueOf(taskId));
            if (task == null) {
                logger.error("Task not found: {}", taskId);
                return false;
            }
            task.setLastFireTime(LocalDateTime.now());
            Task processedTask = processTask(task);
            if (processedTask.getLastFireResult() == null) {
                processedTask.setLastFireResult("Task completed successfully");
            }
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
                task.setLastFireResult(truncateResult(error.getMessage()));
                taskService.update(task);
            }
        } catch (Exception e) {
            logger.error("Could not update task {} with error status: {}", taskId, e.getMessage());
        }
    }

    private String truncateResult(String result) {
        if (result == null) return null;
        if (result.length() <= maxResultLength) return result;
        return result.substring(0, maxResultLength) + "...";
    }

    private Task processTask(Task task) {
        if (TaskJobType.SSL_RENEWAL.equals(task.getTaskJobType())) {
            String result = sslCertificateRenewalService.renewExpiringCertificates(task);
            task.setLastFireResult(truncateResult(result));
            return task;
        }
        if (TaskJobType.TICKET_EXPIRATION.equals(task.getTaskJobType())) {
            String result = ticketExpirationService.runExpiration(task);
            task.setLastFireResult(truncateResult(result));
            return task;
        }

        FunctionExecutionSpec spec = FunctionExecutionSpec.fromJobData(task.getJobData());
        if (spec == null || !spec.hasFunctionSpec()) {
            task.setLastFireResult("No function specified for execution");
            return task;
        }

        try {
            String clientCode = task.getClientCode();
            String appCode = task.getAppCode();
            Map<String, JsonElement> params = spec.getParams() != null ? spec.getParams() : Map.of();
            executeFunction(task, spec, clientCode, appCode, params);
        } catch (Exception e) {
            task.setLastFireResult(truncateResult("Error parsing function parameters: " + e.getMessage()));
            logger.error("Error parsing parameters for task {}: {}", task.getId(), e.getMessage());
        }
        return task;
    }

    private void executeFunction(
            Task task, FunctionExecutionSpec spec, String clientCode, String appCode, Map<String, JsonElement> params) {
        try {
            var output = coreFunctionService
                    .execute(spec.getNamespace(), spec.getName(), appCode, clientCode, params, null)
                    .block(Duration.ofMinutes(timeoutMinutes));
            if (output != null) {
                Object results = output.allResults();
                task.setLastFireResult(truncateResult(
                        "Function executed successfully: " + gson.toJson(results != null ? results : "{}")));
            }
        } catch (Exception error) {
            task.setLastFireResult(truncateResult("Error executing function: " + error.getMessage()));
            logger.error("Error executing function for task {}: {}", task.getId(), error.getMessage());
        }
    }
}
