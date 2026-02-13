package com.fincity.sass.worker.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulersRecord;
import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.service.SchedulerService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/worker/schedulers")
public class SchedulerController
        extends AbstractJOOQDataController<
                WorkerSchedulersRecord, ULong, Scheduler, SchedulerDAO, SchedulerService> {

    @RequestMapping("/start")
    public Mono<ResponseEntity<Scheduler>> startScheduler(@RequestParam String name) {
        return this.service.start(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/pause")
    public Mono<ResponseEntity<Scheduler>> pauseScheduler(@RequestParam String name) {
        return this.service.pause(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/shutdown")
    public Mono<ResponseEntity<Scheduler>> shutdownScheduler(@RequestParam String name) {
        return this.service.shutdown(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/restore")
    public Mono<ResponseEntity<Scheduler>> restartScheduler(@RequestParam String name) {
        return this.service.restore(name).map(ResponseEntity::ok);
    }

    @RequestMapping("/test-quartz-repo")
    public Mono<ResponseEntity<List<org.quartz.Scheduler>>> testQuartzRepo() {
        return this.service.testQuartzRepo().map(ResponseEntity::ok);
    }
}
