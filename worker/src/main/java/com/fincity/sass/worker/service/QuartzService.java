package com.fincity.sass.worker.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.sass.worker.job.TaskExecutorJob;
import com.fincity.sass.worker.model.Task;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
@Slf4j
public class QuartzService {

    private final Scheduler scheduler;
    private final TaskService taskService;
    private final WorkerMessageResourceService workerMessageResourceService;

    private static final Logger logger = LoggerFactory.getLogger(QuartzService.class);

    public QuartzService(
            @Qualifier("defaultQuartzScheduler") Scheduler scheduler,
            TaskService taskService,
            WorkerMessageResourceService workerMessageResourceService) {
        this.scheduler = scheduler;
        this.taskService = taskService;
        this.workerMessageResourceService = workerMessageResourceService;
    }

    public Mono<Void> unscheduleTask(String jobName) {
        return Mono.fromCallable(() -> {
            scheduler.unscheduleJob(TriggerKey.triggerKey(jobName + "-trigger", "worker-triggers"));
            scheduler.deleteJob(JobKey.jobKey(jobName, "worker-jobs"));
            return null;
        });
    }
}