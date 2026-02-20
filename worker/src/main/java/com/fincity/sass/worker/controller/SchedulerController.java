package com.fincity.sass.worker.controller;

import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulersRecord;
import com.fincity.sass.worker.service.SchedulerService;
import com.modlix.saas.commons2.jooq.controller.AbstractJOOQDataController;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/worker/schedulers")
public class SchedulerController
        extends AbstractJOOQDataController<WorkerSchedulersRecord, ULong, Scheduler, SchedulerDAO, SchedulerService> {

    @RequestMapping("/start")
    public ResponseEntity<Scheduler> startScheduler(@RequestParam String name) {
        return ResponseEntity.ok(this.service.start(name));
    }

    @RequestMapping("/pause")
    public ResponseEntity<Scheduler> pauseScheduler(@RequestParam String name) {
        return ResponseEntity.ok(this.service.pause(name));
    }

    @RequestMapping("/shutdown")
    public ResponseEntity<Scheduler> shutdownScheduler(@RequestParam String name) {
        return ResponseEntity.ok(this.service.shutdown(name));
    }

    @RequestMapping("/restore")
    public ResponseEntity<Scheduler> restartScheduler(@RequestParam String name) {
        return ResponseEntity.ok(this.service.restore(name));
    }
}
