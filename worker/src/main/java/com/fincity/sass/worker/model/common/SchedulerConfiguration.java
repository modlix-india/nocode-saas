package com.fincity.sass.worker.model.common;

import com.fincity.sass.worker.dto.Scheduler;
import com.fincity.sass.worker.enums.SchedulerStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SchedulerConfiguration {

    private String schedulerId;
    private String name;
    private SchedulerStatus schedulerStatus = SchedulerStatus.STARTED;

    public static SchedulerConfiguration defaultConfiguration() {
        SchedulerConfiguration config = new SchedulerConfiguration();
        config.setSchedulerId("default");
        config.setName("Default Scheduler");
        config.setSchedulerStatus(SchedulerStatus.STARTED);
        return config;
    }

    public Scheduler toDto() {
        Scheduler scheduler = new Scheduler();
        scheduler.setName(this.name);
        scheduler.setSchedulerStatus(this.schedulerStatus);
        return scheduler;
    }
}
