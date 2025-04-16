package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.fincity.sass.worker.jooq.enums.WorkerTaskStatus;
import reactor.util.context.Context;

import java.time.LocalDateTime;

@Service
public class TaskExecutionService {

    private TaskService taskService;

    private Logger logger = LoggerFactory.getLogger(TaskExecutionService.class);

    private TaskExecutionService(TaskService taskService) {
        this.taskService = taskService;
    }

    public Mono<Void> executeTask(String taskId, String taskData) {
        return FlatMapUtil.flatMapMono(
                // Step 1: Read task
                () -> taskService.read(ULongUtil.valueOf(taskId)),

                // Step 2: Update status to RUNNING
                task -> {
                    task.setStatus(WorkerTaskStatus.RUNNING);
                    task.setLastExecutionTime(LocalDateTime.now());
                    return taskService.update(task);
                },

                // Step 3: Process task
                (initialTask, runningTask) -> processTask(runningTask),

                // Step 4: Update status to FINISHED
                (initialTask, runningTask, processedTask) -> {
                    processedTask.setStatus(WorkerTaskStatus.FINISHED);
                    processedTask.setLastExecutionResult("Task completed successfully");
                    return taskService.update(processedTask);
                }
        )
        .onErrorResume(error -> {
            logger.error("Error executing task {}: {}", taskId, error.getMessage());
            return taskService.read(ULongUtil.valueOf(taskId))
                    .flatMap(task -> {
                        task.setStatus(WorkerTaskStatus.FAILED);
                        task.setLastExecutionResult(error.getMessage());
                        return taskService.update(task);
                    });
        })
        .then()
        .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskExecutionService.executeTask"));
    }

    private Mono<Task> processTask(Task task) {
        // Example task processing logic
        return Mono.defer(() -> {
            try {
                // Simulate task execution
                Thread.sleep(1000);
                task.setLastExecutionResult("Processed task: " + task.getJobName());
                return Mono.just(task);
            } catch (InterruptedException e) {
                return Mono.error(new RuntimeException("Task processing interrupted", e));
            }
        });
    }
}
 