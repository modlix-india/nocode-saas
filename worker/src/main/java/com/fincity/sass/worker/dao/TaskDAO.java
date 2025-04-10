package com.fincity.sass.worker.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;

import static com.fincity.sass.worker.jooq.tables.WorkerTask.WORKER_TASK;

import com.fincity.sass.worker.jooq.enums.WorkerTaskStatus;
import com.fincity.sass.worker.jooq.tables.records.WorkerTaskRecord;
import com.fincity.sass.worker.model.Task;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskDAO extends AbstractUpdatableDAO<WorkerTaskRecord, ULong, Task> {

    protected TaskDAO() {
        super(Task.class, WORKER_TASK, WORKER_TASK.ID);
    }

    public Mono<List<Task>> findTasksDueForExecution(LocalDateTime currentTime) {

        return Flux.from(this.dslContext
                        .selectFrom(WORKER_TASK)
                        .where(WORKER_TASK.NEXT_EXECUTION_TIME.lessOrEqual(currentTime))
                        .and(WORKER_TASK.STATUS.eq(WorkerTaskStatus.UPCOMING))
                        .orderBy(WORKER_TASK.NEXT_EXECUTION_TIME.asc()))
                .map(e -> e.into(this.pojoClass))
                .collectList();
    }
}
