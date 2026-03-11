package com.modlix.saas.worker.configuration;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WorkerShutdownLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(WorkerShutdownLifecycle.class);
    private final Scheduler quartzScheduler;
    private volatile boolean running = false;

    public WorkerShutdownLifecycle(Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }

    @Override
    public void start() {
        this.running = true;
        logger.info("Worker shutdown lifecycle started");
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        logger.info("Graceful shutdown initiated - setting scheduler to standby");
        try {
            if (!quartzScheduler.isShutdown()) {
                quartzScheduler.standby();
                logger.info("Scheduler set to standby - no new jobs will be picked up");
            }
        } catch (SchedulerException e) {
            logger.error("Error setting scheduler to standby during shutdown", e);
        }
        this.running = false;
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 10;
    }
}
