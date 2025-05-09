package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SchedulerService
        extends AbstractJOOQUpdatableDataService<WorkerSchedulerRecord, ULong, WorkerScheduler, SchedulerDAO> {

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String STATUS = "status";
    private static final String RUNNING = "is_running";
    private static final String STANDBY_MODE = "is_standby_mode";
    private static final String SHUTDOWN = "is_shutdown";
    private static final String UPDATED_BY = "updatedBy";
    private static final String UPDATED_AT = "updatedAt";

    private final SchedulerRepository schedulerRepository;
    private final SchedulerDAO schedulerDAO;
    private final QuartzService quartzService;

    public SchedulerService(
            SchedulerRepository schedulerRepository,
            SchedulerDAO schedulerDAO,
            QuartzService quartzService) {
        this.schedulerRepository = schedulerRepository;
        this.schedulerDAO = schedulerDAO;
        this.quartzService = quartzService;
    }

    /**
     * Initializes all schedulers from the database when the application starts.
     * This method is automatically called after the service is fully initialized.
     */
    @PostConstruct
    public Mono<Void> initializeSchedulersFromDatabase() {

        log.info("Initializing schedulers from database...");

        return findAll()
                .flatMapMany(Flux::fromIterable)
                .doOnNext(scheduler -> log.debug("Found scheduler in database: {}", scheduler.getName()))
                .flatMap(ws -> Mono.fromCallable(() -> this.quartzService.initializeSchedulerOnStartUp(ws))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("Error initializing scheduler: {}", ws.getName(), e);
                            return Mono.error(new RuntimeException("Failed to initialize scheduler", e));
                        }))
                .collectList()
                .flatMap(list -> Mono.empty());
    }

    @Override
    public Mono<WorkerScheduler> create(WorkerScheduler workerScheduler) {
        return Mono.fromCallable(() -> this.quartzService.initializeSchedulerOnStartUp(workerScheduler))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(super::create)
                .onErrorResume(e -> {
                    log.error("Error initializing scheduler: {}", workerScheduler.getName(), e);
                    return Mono.error(new RuntimeException("Failed to initialize scheduler", e));
                });
    }

    // delete we should prevent delete
    @Override
    public Mono<Integer> delete(ULong id) {

        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Scheduler deletion is not allowed"));
    }

    public Mono<List<WorkerScheduler>> findAll() {
        return this.dao.findAll();
    }

    public Mono<WorkerScheduler> findById(ULong id) {
        return this.dao.readById(id);
    }

    // TODO : add caching
    public Mono<WorkerScheduler> findByName(String schedulerName) {
        return schedulerDAO.findByName(schedulerName);
    }

    // what if scheduler is already running?
    public Mono<WorkerScheduler> start(String schedulerName) {

        return FlatMapUtil.flatMapMono(() -> this.findByName(schedulerName), ws -> Mono.fromCallable(
                                () -> this.quartzService.startScheduler(ws))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(this::update))
                .onErrorResume(e -> {
                    log.error("Error starting scheduler: {}", schedulerName, e);
                    return Mono.error(new RuntimeException("Failed to start scheduler", e));
                });
    }

    // what if scheduler is already paused ?
    public Mono<WorkerScheduler> pause(String schedulerName) {

        return FlatMapUtil.flatMapMono(() -> this.findByName(schedulerName), ws -> Mono.fromCallable(
                                () -> this.quartzService.pauseScheduler(ws))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(this::update))
                .onErrorResume(e -> {
                    log.error("Error pausing scheduler: {}", schedulerName, e);
                    return Mono.error(new RuntimeException("Failed to pause scheduler", e));
                });
    }

    public Mono<WorkerScheduler> shutdown(String schedulerName) {

        return FlatMapUtil.flatMapMono(() -> this.findByName(schedulerName), ws -> Mono.fromCallable(
                                () -> this.quartzService.shutdownScheduler(ws))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(this::update))
                .onErrorResume(e -> {
                    log.error("Error shutting down scheduler: {}", schedulerName, e);
                    return Mono.error(new RuntimeException("Failed to shoutdown scheduler", e));
                });
    }

    public Mono<WorkerScheduler> restore(String schedulerName) {
        return FlatMapUtil.flatMapMono(() -> findByName(schedulerName), scheduler -> Mono.fromCallable(
                        () -> this.quartzService.initializeSchedulerOnStartUp(scheduler))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Error initializing scheduler: {}", scheduler.getName(), e);
                    return Mono.error(new RuntimeException("Failed to initialize scheduler", e));
                }));
    }

    // TODO remove this funtion only created for testing purpose
    public Mono<List<Scheduler>> testQuartzRepo() {
        return Mono.just(schedulerRepository.lookupAll().stream().toList());
    }

    @Override
    protected Mono<WorkerScheduler> updatableEntity(WorkerScheduler entity) {

        return this.read(entity.getId()).flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {
                    existing.setName(entity.getName());
                    existing.setStatus(entity.getStatus());
                    return existing;
                }));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        Map<String, Object> newFields = new HashMap<>();

        if (fields.containsKey(NAME)) newFields.put(NAME, fields.get(NAME));
        if (fields.containsKey(DESCRIPTION)) newFields.put(DESCRIPTION, fields.get(DESCRIPTION));
        if (fields.containsKey(STATUS)) newFields.put(STATUS, fields.get(STATUS));
        if (fields.containsKey(UPDATED_BY)) newFields.put(UPDATED_BY, fields.get(UPDATED_BY));
        if (fields.containsKey(UPDATED_AT)) newFields.put(UPDATED_AT, LocalDateTime.now());

        return Mono.just(newFields);
    }
}
