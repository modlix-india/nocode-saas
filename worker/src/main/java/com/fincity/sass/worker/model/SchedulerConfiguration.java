package com.fincity.sass.worker.model;

import com.fincity.sass.worker.jooq.enums.WorkerSchedulerStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SchedulerConfiguration {

    private String schedulerId;
    private String name;
    private WorkerSchedulerStatus status = WorkerSchedulerStatus.STARTED;

    public static SchedulerConfiguration defaultConfiguration() {
        SchedulerConfiguration config = new SchedulerConfiguration();
        config.setSchedulerId("default");
        config.setName("Default Scheduler");
        config.setStatus(WorkerSchedulerStatus.STARTED);
        return config;
    }

    public WorkerScheduler toModel() {
        WorkerScheduler scheduler = new WorkerScheduler();
        scheduler.setName(this.name);
        scheduler.setStatus(this.status);
        return scheduler;
    }
}
