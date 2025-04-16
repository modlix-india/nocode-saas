package com.fincity.sass.worker.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fincity.sass.worker.service.TaskExecutionService;

@Component
public class TaskExecutorJob implements Job {

    @Autowired
    private TaskExecutionService taskExecutionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String taskId = context.getJobDetail().getJobDataMap().getString("taskId");
        String taskData = context.getJobDetail().getJobDataMap().getString("taskData");

        if (taskId == null || taskData == null) {
            throw new JobExecutionException("Required job data missing");
        }

        try {
            taskExecutionService.executeTask(taskId, taskData)
                .subscribe();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}