package com.fincity.sass.worker.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.sass.worker.dao.TaskDAO;
import com.fincity.sass.worker.jooq.tables.records.WorkerTaskRecord;
import com.fincity.sass.worker.model.Task;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class TaskService extends AbstractJOOQUpdatableDataService<WorkerTaskRecord, ULong, Task, TaskDAO> {
    
    private static final String JOB_NAME = "jobName";
    private static final String CRON_EXPRESSION = "cronExpression";
    private static final String NEXT_EXECUTION_TIME = "nextExecutionTime";
    private static final String STATUS = "status";
    
    @Override
    protected Mono<Task> updatableEntity(Task entity) {
        return this.read(entity.getId())
                .map(existing -> {
                    existing.setJobName(entity.getJobName());
                    existing.setCronExpression(entity.getCronExpression());
                    existing.setNextExecutionTime(entity.getNextExecutionTime());
                    existing.setStatus(entity.getStatus());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return existing;
                });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        Map<String, Object> newFields = new HashMap<>();

        if (fields.containsKey(JOB_NAME))
            newFields.put(JOB_NAME, fields.get(JOB_NAME));
        if (fields.containsKey(CRON_EXPRESSION))
            newFields.put(CRON_EXPRESSION, fields.get(CRON_EXPRESSION));
        if (fields.containsKey(NEXT_EXECUTION_TIME))
            newFields.put(NEXT_EXECUTION_TIME, fields.get(NEXT_EXECUTION_TIME));
        if (fields.containsKey(STATUS))
            newFields.put(STATUS, fields.get(STATUS));
        
        if (!newFields.isEmpty())
            newFields.put("updatedAt", LocalDateTime.now());

        return Mono.just(newFields);
    }
}
