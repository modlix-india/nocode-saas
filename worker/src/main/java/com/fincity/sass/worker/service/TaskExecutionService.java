package com.fincity.sass.worker.service;

import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.model.common.FunctionExecutionSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.modlix.saas.commons2.jooq.util.ULongUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private final TaskService taskService;
    private final CoreFunctionService coreFunctionService;
    private final Gson gson;

    private final Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    private TaskExecutionService(TaskService taskService, CoreFunctionService coreFunctionService, Gson gson) {
        this.taskService = taskService;
        this.coreFunctionService = coreFunctionService;
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
            taskService.update(task);

            Task processedTask = processTask(task);
            processedTask.setLastFireResult("Task completed successfully");
            taskService.update(processedTask);
            return true;
        } catch (Throwable error) {
            handleTaskError(taskId, error);
            return false;
        }
    }

    private void handleTaskError(String taskId, Throwable error) {
        logger.error("Error executing task {}: {}", taskId, error.getMessage());
        try {
            Task task = taskService.read(ULongUtil.valueOf(taskId));
            if (task != null) {
                updateTaskForError(task, error);
            }
        } catch (Exception e) {
            logger.error("Could not update task {} with error status: {}", taskId, e.getMessage());
        }
    }

    private void updateTaskForError(Task task, Throwable error) {
        logger.error("Error executing task {}: {}", task.getId(), error.getMessage());
        task.setLastFireResult(error.getMessage());
        taskService.update(task);
    }

    private Task processTask(Task task) {
        FunctionExecutionSpec spec = FunctionExecutionSpec.fromJobData(task.getJobData());
        if (spec == null || !spec.hasFunctionSpec()) {
            task.setLastFireResult("No function specified for execution");
            return task;
        }

        try {
            String clientCode = task.getClientId() != null ? task.getClientId().toString() : null;
            String appCode = task.getAppId() != null ? task.getAppId().toString() : null;

            Map<String, JsonElement> params = spec.getParams() != null ? spec.getParams() : new HashMap<>();
            try {
                var output = coreFunctionService
                        .execute(spec.getNamespace(), spec.getName(), appCode, clientCode, params, null)
                        .block();
                if (output != null) {
                    task.setLastFireResult("Function executed successfully: " + new Gson().toJson(output.allResults()));
                }
            } catch (Exception error) {
                task.setLastFireResult("Error executing function: " + error.getMessage());
                logger.error("Error executing function for task {}: {}", task.getId(), error.getMessage());
            }
        } catch (Exception e) {
            task.setLastFireResult("Error parsing function parameters: " + e.getMessage());
            logger.error("Error parsing parameters for task {}: {}", task.getId(), e.getMessage());
        }
        return task;
    }
}
