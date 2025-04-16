package com.fincity.sass.worker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;

import java.util.Date;

@Data
@NoArgsConstructor
public class SchedulerInfo {
    private String schedulerId;
    private String name;
    private String instanceId;
    private String status;
    private boolean running;
    private boolean standbyMode;
    private boolean shutdown;
    private Date startTime;
    private int threadPoolSize;
    private int activeJobs;
    private long totalJobsExecuted;
    private String version;
    private boolean clustered;
    private String jobStoreSupportsPersistence;
    private int numberOfJobsExecuted;

    public static SchedulerInfo fromScheduler(Scheduler scheduler) throws SchedulerException {
        SchedulerInfo info = new SchedulerInfo();
        SchedulerMetaData metaData = scheduler.getMetaData();
        
        info.setName(scheduler.getSchedulerName());
        info.setInstanceId(scheduler.getSchedulerInstanceId());
        info.setRunning(scheduler.isStarted());
        info.setStandbyMode(scheduler.isInStandbyMode());
        info.setShutdown(scheduler.isShutdown());
        info.setStartTime(new Date(metaData.getRunningSince().getTime()));
        info.setThreadPoolSize(metaData.getThreadPoolSize());
        info.setActiveJobs(scheduler.getCurrentlyExecutingJobs().size());
        info.setVersion(metaData.getVersion());
        info.setClustered(metaData.isJobStoreClustered());
        info.setJobStoreSupportsPersistence(String.valueOf(metaData.isJobStoreSupportsPersistence()));
        info.setNumberOfJobsExecuted(metaData.getNumberOfJobsExecuted());
        
        if (scheduler.isStarted()) {
            info.setStatus("RUNNING");
        } else if (scheduler.isInStandbyMode()) {
            info.setStatus("STANDBY");
        } else if (scheduler.isShutdown()) {
            info.setStatus("SHUTDOWN");
        } else {
            info.setStatus("UNKNOWN");
        }
        
        return info;
    }
}