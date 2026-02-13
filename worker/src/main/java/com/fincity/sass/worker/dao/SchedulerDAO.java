package com.fincity.sass.worker.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.sass.worker.jooq.Tables;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulersRecord;
import com.fincity.sass.worker.dto.Scheduler;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SchedulerDAO extends AbstractUpdatableDAO<WorkerSchedulersRecord, ULong, Scheduler> {

    protected SchedulerDAO() {
        super(Scheduler.class, Tables.WORKER_SCHEDULERS, Tables.WORKER_SCHEDULERS.ID);
    }

    public Mono<List<Scheduler>> findAll() {

        return Flux.from(this.dslContext
                        .selectFrom(Tables.WORKER_SCHEDULERS)
                        .where(Tables.WORKER_SCHEDULERS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN)))
                .map(e -> e.into(Scheduler.class))
                .collectList();
    }

    public Mono<Scheduler> findByName(String name) {

        return Mono.from(this.dslContext
                        .selectFrom(Tables.WORKER_SCHEDULERS)
                        .where(Tables.WORKER_SCHEDULERS
                                .NAME
                                .eq(name)
                                .and(Tables.WORKER_SCHEDULERS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN)))
                        .limit(1))
                .map(e -> e.into(Scheduler.class));
    }
}
