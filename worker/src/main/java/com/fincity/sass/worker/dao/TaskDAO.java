package com.fincity.sass.worker.dao;

import static com.fincity.sass.worker.jooq.tables.WorkerTasks.WORKER_TASKS;

import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.jooq.tables.records.WorkerTasksRecord;
import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

@Service
public class TaskDAO extends AbstractUpdatableDAO<WorkerTasksRecord, ULong, Task> {

    protected TaskDAO() {
        super(Task.class, WORKER_TASKS, WORKER_TASKS.ID);
    }

    public List<Task> findTasksDueForExecution(LocalDateTime currentTime) {
        return this.dslContext
                .selectFrom(WORKER_TASKS)
                .where(WORKER_TASKS.NEXT_FIRE_TIME.lessOrEqual(currentTime))
                .orderBy(WORKER_TASKS.NEXT_FIRE_TIME.asc())
                .fetch()
                .map(e -> e.into(this.pojoClass));
    }

    public Task findByNameNGroup(String name, String groupName) {
        var record = this.dslContext
                .selectFrom(WORKER_TASKS)
                .where(WORKER_TASKS.NAME.eq(name))
                .and(WORKER_TASKS.GROUP_NAME.eq(groupName))
                .fetchOne();
        return record != null ? record.into(this.pojoClass) : null;
    }
}
