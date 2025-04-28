package com.fincity.sass.worker.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.sass.worker.jooq.Tables;
import com.fincity.sass.worker.jooq.enums.WorkerSchedulerStatus;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class SchedulerDAO extends AbstractUpdatableDAO<WorkerSchedulerRecord, ULong, WorkerScheduler> {

    protected SchedulerDAO() {
        super(WorkerScheduler.class, Tables.WORKER_SCHEDULER, Tables.WORKER_SCHEDULER.ID);
    }

    public Mono<List<WorkerScheduler>> findAll() {

        return Flux.from(this.dslContext
                        .selectFrom(Tables.WORKER_SCHEDULER)
                        .where(Tables.WORKER_SCHEDULER.STATUS.notEqual(WorkerSchedulerStatus.SHUTDOWN)))
                .map(e -> e.into(WorkerScheduler.class))
                .collectList();
    }

    public Mono<WorkerScheduler> findByName(String name) {

        return Mono.from(this.dslContext
                        .selectFrom(Tables.WORKER_SCHEDULER)
                        .where(Tables.WORKER_SCHEDULER
                                .NAME
                                .eq(name)
                                .and(Tables.WORKER_SCHEDULER.STATUS.notEqual(WorkerSchedulerStatus.SHUTDOWN)))
                        .limit(1))
                .map(e -> e.into(WorkerScheduler.class));
    }
}
