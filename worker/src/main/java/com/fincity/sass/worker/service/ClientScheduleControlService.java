package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dao.ClientScheduleControlDAO;
import com.fincity.sass.worker.dto.ClientScheduleControl;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.fincity.sass.worker.jooq.tables.records.WorkerClientScheduleControlsRecord;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ClientScheduleControlService
        extends AbstractJOOQUpdatableDataService<
                WorkerClientScheduleControlsRecord, ULong, ClientScheduleControl, ClientScheduleControlDAO> {

    private final ClientScheduleControlDAO clientScheduleControlDAO;
    private final QuartzService quartzService;
    private final WorkerMessageResourceService messageResourceService;

    public ClientScheduleControlService(
            ClientScheduleControlDAO clientScheduleControlDAO,
            QuartzService quartzService,
            WorkerMessageResourceService messageResourceService) {
        this.clientScheduleControlDAO = clientScheduleControlDAO;
        this.quartzService = quartzService;
        this.messageResourceService = messageResourceService;
    }

    @PostConstruct
    public void initializeSchedulersFromDatabase() {
        log.info("Initializing client schedule controls from database...");
        List<ClientScheduleControl> controls = findAll();
        for (ClientScheduleControl control : controls) {
            log.debug(
                    "Found control in database: appCode={}, clientCode={}",
                    control.getAppCode(),
                    control.getClientCode());
            try {
                SchedulerStatus status = control.getSchedulerStatus();
                if (status == SchedulerStatus.STARTED) {
                    quartzService.startClientScheduleControl(control);
                } else if (status == SchedulerStatus.STANDBY) {
                    quartzService.pauseClientScheduleControl(control);
                } else if (status == SchedulerStatus.SHUTDOWN) {
                    quartzService.shutdownClientScheduleControl(control);
                }
            } catch (Exception e) {
                log.error(
                        "Error applying schedule control state from database: appCode={}, clientCode={}",
                        control.getAppCode(),
                        control.getClientCode(),
                        e);
            }
        }
    }

    @Override
    public ClientScheduleControl create(ClientScheduleControl control) {
        return super.create(control);
    }

    @Override
    public Integer delete(ULong id) {
        throw new GenericException(
                HttpStatus.BAD_REQUEST,
                messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_DELETION_NOT_ALLOWED));
    }

    public List<ClientScheduleControl> findAll() {
        return this.dao.findAll();
    }

    public ClientScheduleControl findByAppCodeAndClientCode(String appCode, String clientCode) {
        return clientScheduleControlDAO.findByAppCodeAndClientCode(appCode, clientCode);
    }

    public ClientScheduleControl start(String appCode, String clientCode) {
        ClientScheduleControl control = findByAppCodeAndClientCode(appCode, clientCode);
        if (control == null)
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        try {
            quartzService.startClientScheduleControl(control);
        } catch (Exception e) {
            log.error("Error starting schedule control: appCode={}, clientCode={}", appCode, clientCode, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_START_SCHEDULER),
                    e);
        }
        return super.update(control);
    }

    public ClientScheduleControl pause(String appCode, String clientCode) {
        ClientScheduleControl control = findByAppCodeAndClientCode(appCode, clientCode);
        if (control == null)
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        try {
            quartzService.pauseClientScheduleControl(control);
        } catch (Exception e) {
            log.error("Error pausing schedule control: appCode={}, clientCode={}", appCode, clientCode, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_PAUSE_SCHEDULER),
                    e);
        }
        return super.update(control);
    }

    public ClientScheduleControl shutdown(String appCode, String clientCode) {
        ClientScheduleControl control = findByAppCodeAndClientCode(appCode, clientCode);
        if (control == null)
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        try {
            quartzService.shutdownClientScheduleControl(control);
        } catch (Exception e) {
            log.error("Error shutting down schedule control: appCode={}, clientCode={}", appCode, clientCode, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_SHUTDOWN_SCHEDULER),
                    e);
        }
        return super.update(control);
    }

    public ClientScheduleControl restore(String appCode, String clientCode) {
        ClientScheduleControl control = findByAppCodeAndClientCode(appCode, clientCode);
        if (control == null)
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        try {
            quartzService.startClientScheduleControl(control);
        } catch (Exception e) {
            log.error("Error restoring schedule control: appCode={}, clientCode={}", appCode, clientCode, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        return super.update(control);
    }

    @Override
    protected ClientScheduleControl updatableEntity(ClientScheduleControl entity) {
        ClientScheduleControl existing = this.read(entity.getId());
        if (existing == null) return null;
        existing.setName(entity.getName());
        existing.setSchedulerStatus(entity.getSchedulerStatus());
        return existing;
    }
}
