package com.fincity.sass.worker.dao;

import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.fincity.sass.worker.jooq.Tables;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulersRecord;
import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

@Service
public class SchedulerDAO extends AbstractUpdatableDAO<WorkerSchedulersRecord, ULong, Scheduler> {

    protected SchedulerDAO() {
        super(Scheduler.class, Tables.WORKER_SCHEDULERS, Tables.WORKER_SCHEDULERS.ID);
    }

    public List<Scheduler> findAll() {
        return this.dslContext
                .selectFrom(Tables.WORKER_SCHEDULERS)
                .where(Tables.WORKER_SCHEDULERS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN))
                .fetch()
                .map(e -> e.into(Scheduler.class));
    }

    public Scheduler findByName(String name) {
        var record = this.dslContext
                .selectFrom(Tables.WORKER_SCHEDULERS)
                .where(Tables.WORKER_SCHEDULERS.NAME.eq(name))
                .and(Tables.WORKER_SCHEDULERS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN))
                .limit(1)
                .fetchOne();
        return record != null ? record.into(Scheduler.class) : null;
    }
}
