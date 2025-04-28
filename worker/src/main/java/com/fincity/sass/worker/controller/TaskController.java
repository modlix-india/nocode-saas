package com.fincity.sass.worker.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerTaskRecord;
import com.fincity.sass.worker.model.Task;
import com.fincity.sass.worker.service.TaskService;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/worker/tasks")
public class TaskController
        extends AbstractJOOQDataController<WorkerTaskRecord, ULong, Task, TaskDAO, TaskService> {


    @GetMapping("/test/{taskId}")
    public Mono<ResponseEntity<Task>> test(@PathVariable String taskId) {
        return this.service.test(ULong.valueOf(taskId)).map(ResponseEntity::ok);
    }

    @GetMapping("/test1/{taskId}")
    public Mono<ResponseEntity<String>> test1(@PathVariable String taskId) {
        return this.service.test1(ULong.valueOf(taskId)).map(ResponseEntity::ok);
    }
}