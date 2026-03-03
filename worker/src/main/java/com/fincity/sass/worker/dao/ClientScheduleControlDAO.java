package com.fincity.sass.worker.dao;

import com.fincity.sass.worker.dto.ClientScheduleControl;
import com.fincity.sass.worker.enums.SchedulerStatus;
import com.fincity.sass.worker.jooq.Tables;
import com.fincity.sass.worker.jooq.tables.records.WorkerClientScheduleControlsRecord;
import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

@Service
public class ClientScheduleControlDAO
        extends AbstractUpdatableDAO<WorkerClientScheduleControlsRecord, ULong, ClientScheduleControl> {

    protected ClientScheduleControlDAO() {
        super(
                ClientScheduleControl.class,
                Tables.WORKER_CLIENT_SCHEDULE_CONTROLS,
                Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.ID);
    }

    public List<ClientScheduleControl> findAll() {
        return this.dslContext
                .selectFrom(Tables.WORKER_CLIENT_SCHEDULE_CONTROLS)
                .where(Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN))
                .fetch()
                .map(e -> e.into(ClientScheduleControl.class));
    }

    public ClientScheduleControl findByAppCodeAndClientCode(String appCode, String clientCode) {
        var condition = (appCode == null || appCode.isBlank())
                ? Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.APP_CODE.isNull()
                : Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.APP_CODE.eq(appCode);
        var record = this.dslContext
                .selectFrom(Tables.WORKER_CLIENT_SCHEDULE_CONTROLS)
                .where(condition)
                .and(Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.CLIENT_CODE.eq(clientCode))
                .and(Tables.WORKER_CLIENT_SCHEDULE_CONTROLS.SCHEDULER_STATUS.notEqual(SchedulerStatus.SHUTDOWN))
                .limit(1)
                .fetchOne();
        return record != null ? record.into(ClientScheduleControl.class) : null;
    }
}
