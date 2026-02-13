package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.model.common.FunctionExecutionSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

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
                        () -> taskService.read(ULongUtil.valueOf(taskId)).flatMap(task -> {
                            task.setLastFireTime(LocalDateTime.now());
                            return taskService.update(task);
                        }),
                        this::processTask,
                        (task, processedTask) -> {
                            processedTask.setLastFireResult("Task completed successfully");
                            return taskService.update(processedTask);
                        })
                .onErrorResume(error -> handleTaskError(taskId, error))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskExecutionService.executeTask"))
                .then(Mono.just(true));
    }

    private Mono<Task> handleTaskError(String taskId, Throwable error) {
        logger.error("Error executing task {}: {}", taskId, error.getMessage());

        return taskService.read(ULongUtil.valueOf(taskId)).flatMap(task -> updateTaskForError(task, error));
    }

    private Mono<Task> updateTaskForError(Task task, Throwable error) {
        logger.error("Error executing task {}: {}", task.getId(), error.getMessage());

        task.setLastFireResult(error.getMessage());
        return taskService.update(task);
    }

    private Mono<Task> processTask(Task task) {
        FunctionExecutionSpec spec = FunctionExecutionSpec.fromJobData(task.getJobData());
        if (spec == null || !spec.hasFunctionSpec()) {
            return Mono.just(task.setLastFireResult("No function specified for execution"));
        }

        try {
            String clientCode = task.getClientId() != null ? task.getClientId().toString() : null;
            String appCode = task.getAppId() != null ? task.getAppId().toString() : null;

            Map<String, JsonElement> params = spec.getParams() != null ? spec.getParams() : new HashMap<>();
            return coreFunctionService
                    .execute(spec.getNamespace(), spec.getName(), appCode, clientCode, params, null)
                    .map(output -> {
                        // Update task with function execution result
                        task.setLastFireResult(
                                "Function executed successfully: " + new Gson().toJson(output.allResults()));
                        return task;
                    })
                    .onErrorResume(error -> {
                        // Handle function execution errors
                        task.setLastFireResult("Error executing function: " + error.getMessage());
                        logger.error("Error executing function for task {}: {}", task.getId(), error.getMessage());
                        return Mono.just(task);
                    });
        } catch (Exception e) {
            // Handle parameter parsing errors
            task.setLastFireResult("Error parsing function parameters: " + e.getMessage());
            logger.error("Error parsing parameters for task {}: {}", task.getId(), e.getMessage());
            return Mono.just(task);
        }
    }
}
