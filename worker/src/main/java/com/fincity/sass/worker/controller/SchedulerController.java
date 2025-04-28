package com.fincity.sass.worker.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import com.fincity.sass.worker.service.SchedulerService;
import org.jooq.types.ULong;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/worker/schedulers")
public class SchedulerController
        extends AbstractJOOQDataController<
                        WorkerSchedulerRecord, ULong, WorkerScheduler, SchedulerDAO, SchedulerService> {

    @RequestMapping("/start")
    public Mono<ResponseEntity<WorkerScheduler>> startScheduler(@RequestParam String name) {
        return this.service.start(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/pause")
    public Mono<ResponseEntity<WorkerScheduler>> pauseScheduler(@RequestParam String name) {
        return this.service.pause(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/shutdown")
    public Mono<ResponseEntity<WorkerScheduler>> shutdownScheduler(@RequestParam String name) {
        return this.service.shutdown(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/restore")
    public Mono<ResponseEntity<WorkerScheduler>> restartScheduler(@RequestParam String name) {
        return this.service.restore(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/test-quartz-repo")
    public Mono<ResponseEntity<List<Scheduler>>> testQuartzRepo() {
        return this.service.testQuartzRepo().map(ResponseEntity::ok);
    }

}
