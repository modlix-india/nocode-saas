package com.fincity.sass.worker.model;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class SchedulerConfiguration {

    private String name;
    private String status;
    private boolean isRunning = false;
    private boolean isStandbyMode = false;
    private boolean isShutdown = false;

    public static SchedulerConfiguration defaultConfiguration() {
        SchedulerConfiguration config = new SchedulerConfiguration();
        config.setName("Default Scheduler");
        config.setStatus("RUNNING");
        config.setRunning(true);
        config.setStandbyMode(false);
        config.setShutdown(false);
        return config;
    }

    public WorkerScheduler toModel() {
        WorkerScheduler scheduler = new WorkerScheduler();
        scheduler.setName(this.name);
        scheduler.setStatus(this.status);
        scheduler.setRunning(this.isRunning);
        scheduler.setStandbyMode(this.isStandbyMode);
        scheduler.setShutdown(this.isShutdown);
        return scheduler;
    }
}