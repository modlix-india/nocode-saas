package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.model.Task;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import com.google.gson.reflect.TypeToken;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
     * @return A Mono that completes with true when the task execution is complete
     */
    public Mono<Boolean> executeTask(String taskId, String taskData) {

        return FlatMapUtil.flatMapMono(

                        () -> taskService.read(ULongUtil.valueOf(taskId)).flatMap(
                                task -> {
                                    task.setLastExecutionTime(LocalDateTime.now());
                                    return taskService.update(task);
                                }
                        ),

                        this::processTask,

                        (task,processedTask) -> {
                            processedTask.setLastExecutionResult("Task completed successfully");
                            return taskService.update(processedTask);
                        })
                .onErrorResume(error ->  handleTaskError(taskId, error))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskExecutionService.executeTask"))
                .then(Mono.just(true));
    }

    private Mono<Task> handleTaskError(String taskId, Throwable error) {
        logger.error("Error executing task {}: {}", taskId, error.getMessage());

        return taskService.read(ULongUtil.valueOf(taskId)).flatMap(task -> updateTaskForError(task, error));
    }

    private Mono<Task> updateTaskForError(Task task, Throwable error) {
        logger.error("Error executing task {}: {}", task.getId(), error.getMessage());

        task.setLastExecutionResult(error.getMessage());
        return taskService.update(task);
    }

    private Mono<Task> processTask(Task task) {
        // Check if the task has function information
        if (task.getFunctionName() == null || task.getFunctionNamespace() == null) {
            return Mono.just(task.setLastExecutionResult("No function specified for execution"));
        }

        try {
            // Parse function parameters from JSON string if provided
            Map<String, JsonElement> params = new HashMap<>();
            if (task.getFunctionParams() != null && !task.getFunctionParams().isBlank()) {
                params = new Gson()
                        .fromJson(task.getFunctionParams(), new TypeToken<Map<String, JsonElement>>() {}.getType());
            }

            // Get client code and app code from IDs
            String clientCode = task.getClientId() != null ? task.getClientId().toString() : null;
            String appCode = task.getAppId() != null ? task.getAppId().toString() : null;

            // Execute the function using CoreFunctionService
            return coreFunctionService
                    .execute(task.getFunctionNamespace(), task.getFunctionName(), appCode, clientCode, params, null)
                    .map(output -> {
                        // Update task with function execution result
                        task.setLastExecutionResult(
                                STR."Function executed successfully: \{new Gson().toJson(output.allResults())}");
                        return task;
                    })
                    .onErrorResume(error -> {
                        // Handle function execution errors
                        task.setLastExecutionResult("Error executing function: " + error.getMessage());
                        logger.error("Error executing function for task {}: {}", task.getId(), error.getMessage());
                        return Mono.just(task);
                    });
        } catch (Exception e) {
            // Handle parameter parsing errors
            task.setLastExecutionResult("Error parsing function parameters: " + e.getMessage());
            logger.error("Error parsing parameters for task {}: {}", task.getId(), e.getMessage());
            return Mono.just(task);
        }
    }
}
