package com.fincity.sass.worker.controller;

import com.fincity.sass.worker.dao.ClientScheduleControlDAO;
import com.fincity.sass.worker.dto.ClientScheduleControl;
import com.fincity.sass.worker.jooq.tables.records.WorkerClientScheduleControlsRecord;
import com.fincity.sass.worker.service.ClientScheduleControlService;
import com.modlix.saas.commons2.jooq.controller.AbstractJOOQDataController;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/worker/client-schedule-controls")
public class ClientScheduleControlController
        extends AbstractJOOQDataController<
                WorkerClientScheduleControlsRecord,
                ULong,
                ClientScheduleControl,
                ClientScheduleControlDAO,
                ClientScheduleControlService> {

    @RequestMapping("/start")
    public ResponseEntity<ClientScheduleControl> start(
            @RequestParam(required = false) String appCode, @RequestParam String clientCode) {
        return ResponseEntity.ok(this.service.start(appCode, clientCode));
    }

    @RequestMapping("/pause")
    public ResponseEntity<ClientScheduleControl> pause(
            @RequestParam(required = false) String appCode, @RequestParam String clientCode) {
        return ResponseEntity.ok(this.service.pause(appCode, clientCode));
    }

    @RequestMapping("/shutdown")
    public ResponseEntity<ClientScheduleControl> shutdown(
            @RequestParam(required = false) String appCode, @RequestParam String clientCode) {
        return ResponseEntity.ok(this.service.shutdown(appCode, clientCode));
    }

    @RequestMapping("/restore")
    public ResponseEntity<ClientScheduleControl> restore(
            @RequestParam(required = false) String appCode, @RequestParam String clientCode) {
        return ResponseEntity.ok(this.service.restore(appCode, clientCode));
    }
}
