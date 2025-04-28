package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.LocalDateTime;

@Service
public class TaskExecutionService {

    private final TaskService taskService;

    private final Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    private TaskExecutionService(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Execute a task with the given ID.
     * 
     * @param taskId The ID of the task to execute
     * @param taskData Additional data for the task (currently unused, reserved for future use)
     * @return A Mono that completes with true when the task execution is complete
     */
    public Mono<Boolean> executeTask(String taskId, String taskData) {
        // Store the task for error handling
        Task[] taskHolder = new Task[1];

        return FlatMapUtil.flatMapMono(
                        // Step 1: Read task
                        () -> taskService.read(ULongUtil.valueOf(taskId))
                                .doOnNext(task -> taskHolder[0] = task),

                        // Step 2: Update status to RUNNING
                        task -> {
                            task.setLastExecutionTime(LocalDateTime.now());
                            return taskService.update(task);
                        },

                        // Step 3: Process task
                        (initialTask, runningTask) -> processTask(runningTask),

                        // Step 4: Update status to FINISHED
                        (initialTask, runningTask, processedTask) -> {
                            processedTask.setLastExecutionResult("Task completed successfully");
                            return taskService.update(processedTask);
                        })
                .onErrorResume(error -> {
                    // Use the stored task if available, otherwise fall back to taskId
                    if (taskHolder[0] != null) {
                        return updateTaskForError(taskHolder[0], error);
                    } else {
                        return handleTaskError(taskId, error);
                    }
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskExecutionService.executeTask"))
                .then(Mono.just(true));
    }

    private Mono<Task> handleTaskError(String taskId, Throwable error) {
        logger.error("Error executing task {}: {}", taskId, error.getMessage());

        return taskService.read(ULongUtil.valueOf(taskId))
                .flatMap(task -> updateTaskForError(task, error));
    }

    private Mono<Task> updateTaskForError(Task task, Throwable error) {
        logger.error("Error executing task {}: {}", task.getId(), error.getMessage());

        task.setLastExecutionResult(error.getMessage());
        return taskService.update(task);
    }

    //TODO  update the task functionality processing logic
    private Mono<Task> processTask(Task task) {
        // Example task processing logic - using non-blocking delay instead of Thread.sleep
        return Mono.delay(java.time.Duration.ofSeconds(1))
                .map(ignored -> {
                    task.setLastExecutionResult("Processed task: " + task.getName());
                    return task;
                });
    }
}
