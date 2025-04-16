package com.fincity.sass.worker.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.sass.worker.jooq.Tables;
import com.fincity.sass.worker.jooq.tables.records.WorkerSchedulerRecord;
import com.fincity.sass.worker.model.WorkerScheduler;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;


@Service
public class SchedulerDAO extends AbstractUpdatableDAO<WorkerSchedulerRecord, ULong, WorkerScheduler> {

    protected SchedulerDAO() {
        super(WorkerScheduler.class, Tables.WORKER_SCHEDULER, Tables.WORKER_SCHEDULER.ID);
    }

}

