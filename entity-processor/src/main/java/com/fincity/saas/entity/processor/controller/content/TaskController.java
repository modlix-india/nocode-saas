package com.fincity.saas.entity.processor.controller.content;

import com.fincity.saas.entity.processor.controller.content.base.BaseContentController;
import com.fincity.saas.entity.processor.dao.content.TaskDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.service.content.TaskService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tasks")
public class TaskController extends BaseContentController<EntityProcessorTasksRecord, Task, TaskDAO, TaskService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Task>> createRequest(@RequestBody TaskRequest taskRequest) {
        return this.service.createRequest(taskRequest).map(ResponseEntity::ok);
    }

    @PutMapping(REQ_PATH_ID + "/reminder")
    public Mono<ResponseEntity<Task>> setReminder(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestParam(name = "reminderDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime reminderDate) {

        return this.service.setReminder(identity, reminderDate).map(ResponseEntity::ok);
    }

    @PutMapping(REQ_PATH_ID + "/completed")
    public Mono<ResponseEntity<Task>> setTaskCompleted(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestParam(name = "completed", defaultValue = "true") Boolean isCompleted,
            @RequestParam(name = "completedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime completedDate) {

        return this.service
                .setTaskCompleted(identity, isCompleted, completedDate)
                .map(ResponseEntity::ok);
    }

    @PutMapping(REQ_PATH_ID + "/cancelled")
    public Mono<ResponseEntity<Task>> setTaskCancelled(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestParam(name = "cancelled", defaultValue = "true") Boolean cancelled,
            @RequestParam(name = "cancelledDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime cancelledDate) {

        return this.service.setTaskCancelled(identity, cancelled, cancelledDate).map(ResponseEntity::ok);
    }
}
