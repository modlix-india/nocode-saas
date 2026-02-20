package com.fincity.sass.worker.controller;

import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.jooq.tables.records.WorkerTasksRecord;
import com.fincity.sass.worker.service.TaskService;
import com.modlix.saas.commons2.jooq.controller.AbstractJOOQDataController;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/worker/tasks")
public class TaskController extends AbstractJOOQDataController<WorkerTasksRecord, ULong, Task, TaskDAO, TaskService> {

    @PostMapping("cancel/{taskId}")
    public ResponseEntity<Task> cancel(@PathVariable final ULong taskId) {
        return ResponseEntity.ok(this.service.cancelTask(taskId));
    }

    @PostMapping("pause/{taskId}")
    public ResponseEntity<Task> pause(@PathVariable final ULong taskId) {
        return ResponseEntity.ok(this.service.pauseTask(taskId));
    }

    @PostMapping("resume/{taskId}")
    public ResponseEntity<Task> resume(@PathVariable final ULong taskId) {
        return ResponseEntity.ok(this.service.resumeTask(taskId));
    }
}
