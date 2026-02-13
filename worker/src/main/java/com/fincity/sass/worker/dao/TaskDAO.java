package com.fincity.sass.worker.dao;

import static com.fincity.sass.worker.jooq.tables.WorkerTasks.WORKER_TASKS;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.jooq.tables.records.WorkerTasksRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TaskDAO extends AbstractUpdatableDAO<WorkerTasksRecord, ULong, Task> {

    protected TaskDAO() {
        super(Task.class, WORKER_TASKS, WORKER_TASKS.ID);
    }

    public Mono<List<Task>> findTasksDueForExecution(LocalDateTime currentTime) {

        return Flux.from(this.dslContext
                        .selectFrom(WORKER_TASKS)
                        .where(WORKER_TASKS.NEXT_FIRE_TIME.lessOrEqual(currentTime))
                        .orderBy(WORKER_TASKS.NEXT_FIRE_TIME.asc()))
                .map(e -> e.into(this.pojoClass))
                .collectList();
    }

    public Mono<Task> findByNameNGroup(String name, String groupName) {

        return Mono.from(this.dslContext
                        .selectFrom(WORKER_TASKS)
                        .where(WORKER_TASKS.NAME.eq(name))
                        .and(WORKER_TASKS.GROUP_NAME.eq(groupName)))
                .map(e -> e.into(this.pojoClass));
    }
}
