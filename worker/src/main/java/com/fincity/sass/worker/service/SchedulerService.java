package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.sass.worker.configuration.QuartzConfiguration;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.enums.WorkerSchedulerStatus;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.SchedulerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
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

    private final QuartzConfiguration quartzConfiguration;
    private final ApplicationContext applicationContext;
    private final SchedulerRepository schedulerRepository;
    private final SchedulerDAO schedulerDAO;

    public SchedulerService(
            QuartzConfiguration quartzConfiguration,
            ApplicationContext applicationContext,
            SchedulerRepository schedulerRepository,
            SchedulerDAO schedulerDAO) {
        this.quartzConfiguration = quartzConfiguration;
        this.applicationContext = applicationContext;
        this.schedulerRepository = schedulerRepository;
        this.schedulerDAO = schedulerDAO;
    }

    /**
     * Initializes all schedulers from the database when the application starts.
     * This method is automatically called after the service is fully initialized.
     */
    @PostConstruct
    public void initializeSchedulersFromDatabase() {

        log.info("Initializing schedulers from database...");

        findAll()
                .flatMapMany(Flux::fromIterable)
                .doOnNext(scheduler -> log.debug("Found scheduler in database: {}", scheduler.getName()))
                .flatMap(this::initializeSchedulerOnStartUp)
                .doOnError(e -> log.error("Error initializing schedulers from database", e))
                .subscribe(
                        scheduler -> log.info("Successfully initialized scheduler: {}", scheduler.getName()),
                        error -> log.error("Failed to initialize schedulers", error),
                        () -> log.info("Completed initializing all schedulers from database"));
    }

    /**
     * Initializes a single scheduler from a WorkerScheduler entity.
     *
     * <p><strong>WARNING:</strong> This method is intended for internal use during service restart
     * to restore scheduler persistence. <u>Do not use this method reference (`this::initializeScheduler`)
     * in any other context</u>, especially in runtime job creation or updates, as it may lead to
     * duplicate scheduler instances or inconsistent Quartz state.</p>
     *
     * @param workerScheduler the WorkerScheduler entity from the database
     * @return a Mono that completes when the scheduler is initialized
     */
    private Mono<WorkerScheduler> initializeSchedulerOnStartUp(WorkerScheduler workerScheduler) {

        return Mono.fromCallable(() -> {
                    try {
                        log.info("Initializing scheduler: {}", workerScheduler.getName());

                        // Step 1: Create and initialize the scheduler factory
                        SchedulerFactoryBean factory = quartzConfiguration.createSchedulerFactory(
                                applicationContext, workerScheduler.getName());
                        factory.afterPropertiesSet();

                        // Step 2: Get the scheduler instance
                        Scheduler scheduler = factory.getScheduler();

                        // Step 3: Bind to repository and configure state
                        schedulerRepository.bind(scheduler);
                        log.debug("Bound scheduler to repository: {}", workerScheduler.getName());

                        if (workerScheduler.getStatus().equals(WorkerSchedulerStatus.STARTED)) {
                            scheduler.start();
                            log.debug("Started scheduler: {}", workerScheduler.getName());
                        } else if (workerScheduler.getStatus().equals(WorkerSchedulerStatus.STANDBY)) {
                            scheduler.standby();
                            log.debug("Set scheduler to standby mode: {}", workerScheduler.getName());
                        }

                        log.info(
                                "Scheduler is available in Quartz's SchedulerRepository: {}",
                                workerScheduler.getName());
                        workerScheduler.setInstanceId(scheduler.getSchedulerInstanceId());
                        return workerScheduler;
                    } catch (Exception e) {
                        log.error("Failed to initialize scheduler: {}", workerScheduler.getName(), e);
                        throw new RuntimeException("Failed to initialize scheduler: " + workerScheduler.getName(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<WorkerScheduler> create(WorkerScheduler entity) {

        return initializeSchedulerOnStartUp(entity).flatMap(super::create);
    }

    // delete we should prevent delete
    @Override
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(() -> this.read(id), ws -> {
            if (!ws.getStatus().equals(WorkerSchedulerStatus.SHUTDOWN)) {
                return Mono.error(new RuntimeException("Please shutdown scheduler before deleting it"));
            }

            //  should we make delete also do shut down?
            return Mono.empty();
        });
    }

    public Mono<List<WorkerScheduler>> findAll() {
        return this.dao.findAll();
    }

    public Mono<WorkerScheduler> findById(ULong id){
        return this.dao.readById(id);
    }

    // TODO : add caching
    public Mono<WorkerScheduler> findByName(String schedulerName) {
        return schedulerDAO.findByName(schedulerName);
    }

    // what if scheduler is already running ?
    public Mono<WorkerScheduler> start(String schedulerName) {

        return FlatMapUtil.flatMapMono(() -> findByName(schedulerName), workerScheduler -> {
            try {
                // Get the scheduler from the repository
                Scheduler quartzScheduler = schedulerRepository.lookup(schedulerName);

                if (quartzScheduler == null) {
                    return Mono.error(new RuntimeException("Quartz scheduler not found"));
                }

                // Pause the scheduler
                quartzScheduler.start();
                log.debug("Started scheduler: {}", schedulerName);

                // Update the WorkerScheduler object
                workerScheduler.setStatus(WorkerSchedulerStatus.STARTED);

                // Update the scheduler in the database
                return this.update(workerScheduler);
            } catch (SchedulerException e) {
                log.error("Failed to start scheduler: {}", schedulerName, e);
                return Mono.error(new RuntimeException("Failed to start scheduler", e));
            }
        });
    }

    // what if scheduler is already paused ?
    public Mono<WorkerScheduler> pause(String schedulerName) {

        return FlatMapUtil.flatMapMono(

                // Step 1: Find the WorkerScheduler by name
                () -> findByName(schedulerName),

                // Step 2: Get the Quartz scheduler and pause it
                workerScheduler -> {
                    try {
                        // Get the scheduler from the repository
                        Scheduler quartzScheduler = schedulerRepository.lookup(schedulerName);

                        if (quartzScheduler == null) {
                            return Mono.error(new RuntimeException("Quartz scheduler not found"));
                        }

                        // Pause the scheduler
                        quartzScheduler.standby();
                        log.debug("Paused scheduler: {}", schedulerName);

                        // Update the WorkerScheduler object
                        workerScheduler.setStatus(WorkerSchedulerStatus.STANDBY);

                        // Update the scheduler in the database
                        return this.update(workerScheduler);
                    } catch (SchedulerException e) {
                        log.error("Failed to pause scheduler: {}", schedulerName, e);
                        return Mono.error(new RuntimeException("Failed to pause scheduler", e));
                    }
                });
    }

    public Mono<WorkerScheduler> shutdown(String schedulerName) {

        return FlatMapUtil.flatMapMono(

                // Step 1: Find the WorkerScheduler by name
                () -> findByName(schedulerName),

                // Step 2: Get the Quartz scheduler and pause it
                workerScheduler -> {
                    try {
                        // Get the scheduler from the repository
                        Scheduler quartzScheduler = schedulerRepository.lookup(schedulerName);

                        if (quartzScheduler == null) {
                            return Mono.error(new RuntimeException("Quartz scheduler not found"));
                        }

                        // Pause the scheduler
                        quartzScheduler.shutdown();
                        log.debug("shutdown complete scheduler: {}", schedulerName);

                        // Update the WorkerScheduler object
                        workerScheduler.setStatus(WorkerSchedulerStatus.SHUTDOWN);

                        // Update the scheduler in the database
                        return this.update(workerScheduler);
                    } catch (SchedulerException e) {
                        log.error("Failed to shutdown scheduler: {}", schedulerName, e);
                        return Mono.error(new RuntimeException("Failed to shutdown scheduler", e));
                    }
                });
    }

    public Mono<WorkerScheduler> restore(String schedulerName) {
        return FlatMapUtil.flatMapMono(() -> findByName(schedulerName), this::initializeSchedulerOnStartUp);
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
