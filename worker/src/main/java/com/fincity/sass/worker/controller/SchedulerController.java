package com.fincity.sass.worker.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import com.fincity.sass.worker.service.SchedulerService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedulers")
public class SchedulerController
        extends AbstractJOOQUpdatableDataController<
                WorkerSchedulerRecord, ULong, WorkerScheduler, SchedulerDAO, SchedulerService> {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }


}