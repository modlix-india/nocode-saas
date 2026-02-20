package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulersRecord;
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
public class SchedulerService
        extends AbstractJOOQUpdatableDataService<WorkerSchedulersRecord, ULong, Scheduler, SchedulerDAO> {

    private final SchedulerDAO schedulerDAO;
    private final QuartzService quartzService;
    private final WorkerMessageResourceService messageResourceService;

    public SchedulerService(
            SchedulerDAO schedulerDAO,
            QuartzService quartzService,
            WorkerMessageResourceService messageResourceService) {
        this.schedulerDAO = schedulerDAO;
        this.quartzService = quartzService;
        this.messageResourceService = messageResourceService;
    }

    /**
     * Initializes all schedulers from the database when the application starts.
     * This method is automatically called after the service is fully initialized.
     */
    @PostConstruct
    public void initializeSchedulersFromDatabase() {
        log.info("Initializing schedulers from database...");
        List<Scheduler> schedulers = findAll();
        for (Scheduler scheduler : schedulers) {
            log.debug("Found scheduler in database: {}", scheduler.getName());
            try {
                this.quartzService.initializeSchedulerOnStartUp(scheduler);
            } catch (Exception e) {
                log.error("Error initializing scheduler: {}", scheduler.getName(), e);
                throw new GenericException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                        e);
            }
        }
    }

    @Override
    public Scheduler create(Scheduler workerScheduler) {
        try {
            this.quartzService.initializeSchedulerOnStartUp(workerScheduler);
        } catch (Exception e) {
            log.error("Error initializing scheduler: {}", workerScheduler.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        return super.create(workerScheduler);
    }

    @Override
    public Integer delete(ULong id) {
        throw new GenericException(
                HttpStatus.BAD_REQUEST,
                messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_DELETION_NOT_ALLOWED));
    }

    public List<Scheduler> findAll() {
        return this.dao.findAll();
    }

    public Scheduler findByName(String schedulerName) {
        return schedulerDAO.findByName(schedulerName);
    }

    public Scheduler start(String schedulerName) {
        Scheduler ws = this.findByName(schedulerName);
        if (ws == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.startScheduler(ws);
        } catch (Exception e) {
            log.error("Error starting scheduler: {}", schedulerName, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_START_SCHEDULER),
                    e);
        }
        return super.update(ws);
    }

    public Scheduler pause(String schedulerName) {
        Scheduler ws = this.findByName(schedulerName);
        if (ws == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.pauseScheduler(ws);
        } catch (Exception e) {
            log.error("Error pausing scheduler: {}", schedulerName, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_PAUSE_SCHEDULER),
                    e);
        }
        return super.update(ws);
    }

    public Scheduler shutdown(String schedulerName) {
        Scheduler ws = this.findByName(schedulerName);
        if (ws == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.shutdownScheduler(ws);
        } catch (Exception e) {
            log.error("Error shutting down scheduler: {}", schedulerName, e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_SHUTDOWN_SCHEDULER),
                    e);
        }
        return super.update(ws);
    }

    public Scheduler restore(String schedulerName) {
        Scheduler scheduler = findByName(schedulerName);
        if (scheduler == null) {
            throw new GenericException(
                    HttpStatus.NOT_FOUND,
                    messageResourceService.getMessage(WorkerMessageResourceService.SCHEDULER_NOT_FOUND));
        }
        try {
            this.quartzService.initializeSchedulerOnStartUp(scheduler);
        } catch (Exception e) {
            log.error("Error initializing scheduler: {}", scheduler.getName(), e);
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageResourceService.getMessage(WorkerMessageResourceService.FAILED_TO_INITIALIZE_SCHEDULER),
                    e);
        }
        return scheduler;
    }

    @Override
    protected Scheduler updatableEntity(Scheduler entity) {
        Scheduler existing = this.read(entity.getId());
        if (existing == null) {
            return null;
        }
        existing.setName(entity.getName());
        existing.setSchedulerStatus(entity.getSchedulerStatus());
        return existing;
    }
}
