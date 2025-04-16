package com.fincity.sass.worker.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.sass.worker.configuration.QuartzConfiguration;
import com.fincity.sass.worker.dao.SchedulerDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.SchedulerInfo;
import com.fincity.sass.worker.model.WorkerScheduler;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.quartz.Scheduler;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SchedulerService
        extends AbstractJOOQUpdatableDataService<WorkerSchedulerRecord, ULong, WorkerScheduler, SchedulerDAO> {

    private final QuartzConfiguration quartzConfiguration;
    private final ApplicationContext applicationContext;
    private final Map<String, Scheduler> schedulerRegistry = new ConcurrentHashMap<>();

    public SchedulerService(QuartzConfiguration quartzConfiguration, ApplicationContext applicationContext) {
        this.quartzConfiguration = quartzConfiguration;
        this.applicationContext = applicationContext;
    }

    public Flux<SchedulerInfo> getAllSchedulers() {
        return Flux.fromIterable(schedulerRegistry.values())
                .flatMap(scheduler -> Mono.fromCallable(() -> SchedulerInfo.fromScheduler(scheduler))
                        .onErrorResume(e -> {
                            log.error("Error getting scheduler info", e);
                            return Mono.empty();
                        }));
    }

//    public Mono<SchedulerInfo> createScheduler(SchedulerConfiguration config) {
//        return Mono.fromCallable(() -> {
//                    if (schedulerRegistry.containsKey(config.getSchedulerId())) {
//                        throw new IllegalStateException("Scheduler already exists with ID: " + config.getSchedulerId());
//                    }
//
//                    SchedulerFactoryBean factory = quartzConfiguration.createSchedulerFactory(
//                            applicationContext,
//                            config.getName(),
//                            config.getThreadCount(),
//                            config.isClustered(),
//                            config.getMisfireThreshold());
//
//                    factory.afterPropertiesSet();
//                    Scheduler scheduler = factory.getScheduler();
//                    scheduler.start();
//
//                    schedulerRegistry.put(config.getSchedulerId(), scheduler);
//                    return SchedulerInfo.fromScheduler(scheduler);
//                })
//                .subscribeOn(Schedulers.boundedElastic());
//    }
//
//    public Mono<SchedulerInfo> getSchedulerInfo(String schedulerId) {
//        return Mono.justOrEmpty(schedulerRegistry.get(schedulerId))
//                .flatMap(scheduler -> Mono.fromCallable(() -> SchedulerInfo.fromScheduler(scheduler)));
//    }
//
//    public Mono<Void> pauseScheduler(String schedulerId) {
//        return Mono.justOrEmpty(schedulerRegistry.get(schedulerId))
//                .flatMap(scheduler -> Mono.fromRunnable(() -> {
//                    try {
//                        scheduler.standby();
//                    } catch (SchedulerException e) {
//                        throw new RuntimeException("Failed to pause scheduler", e);
//                    }
//                }));
//    }
//
//    public Mono<Void> resumeScheduler(String schedulerId) {
//        return Mono.justOrEmpty(schedulerRegistry.get(schedulerId))
//                .flatMap(scheduler -> Mono.fromRunnable(() -> {
//                    try {
//                        scheduler.start();
//                    } catch (SchedulerException e) {
//                        throw new RuntimeException("Failed to resume scheduler", e);
//                    }
//                }));
//    }

//    public Mono<Void> deleteScheduler(String schedulerId) {
//        return Mono.justOrEmpty(schedulerRegistry.get(schedulerId))
//                .flatMap(scheduler -> Mono.fromRunnable(() -> {
//                    try {
//                        scheduler.shutdown(true);
//                        schedulerRegistry.remove(schedulerId);
//                    } catch (SchedulerException e) {
//                        throw new RuntimeException("Failed to delete scheduler", e);
//                    }
//                }));
//    }
//
//    public Mono<SchedulerInfo> getSchedulerStatus(String schedulerId) {
//        return getSchedulerInfo(schedulerId);
//    }

    @Override
    protected Mono<WorkerScheduler> updatableEntity(WorkerScheduler entity) {
        return null;
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        return null;
    }
}