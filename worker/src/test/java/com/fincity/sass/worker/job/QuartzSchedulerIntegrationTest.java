package com.fincity.sass.worker.job;


import java.time.LocalDateTime;
import java.util.Date;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.sass.worker.jooq.enums.WorkerTaskStatus;
import com.fincity.sass.worker.model.Task;
import com.fincity.sass.worker.service.TaskService;
import reactor.core.publisher.Mono;

@SpringBootTest
class QuartzSchedulerIntegrationTest {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private TaskService taskService;

    @Test
    void testSchedulerWithJDBCStore() {

        FlatMapUtil.flatMapMono(
                // Create task
                () -> {
                    Task task = new Task();
                    task.setJobName("Test Job");
                    task.setCronExpression("0/1 * * * * ?");
                    task.setNextExecutionTime(LocalDateTime.now());
                    task.setStatus(WorkerTaskStatus.UPCOMING);
                    task.setCreatedAt(LocalDateTime.now());
                    task.setCreatedBy(ULongUtil.valueOf(1));
                    return taskService.create(task);
                },

                // Create job detail and store it first
                task -> {
                    String taskId = task.getId().toString();
                    JobDetail jobDetail = JobBuilder.newJob(TaskExecutorJob.class)
                            .withIdentity("jdbcTest", "testGroup")
                            .usingJobData("taskId", taskId)
                            .usingJobData("taskData", "{\"test\":\"jdbc\"}")
                            .storeDurably()
                            .build();

                    try {
                        // Store the job first
                        scheduler.addJob(jobDetail, true);
                        
                        // Verify job was stored
                        JobDetail storedJob = scheduler.getJobDetail(jobDetail.getKey());
                        System.out.println("Job stored: " + (storedJob != null));
                        if (storedJob != null) {
                            System.out.println("Job class: " + storedJob.getJobClass().getName());
                        }
                        
                        return Mono.just(jobDetail);
                    } catch (SchedulerException e) {
                        System.err.println("Error storing job: " + e.getMessage());
                        return Mono.error(e);
                    }
                },

                // Create trigger
                // Create trigger with longer duration
                (task, jd) -> {
                    SimpleTrigger trigger = TriggerBuilder.newTrigger()
                            .withIdentity("jdbcTrigger", "testGroup")
                            .forJob(jd.getKey())
                            .startAt(new Date(System.currentTimeMillis() + 10000)) // Start 10 seconds in future
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                    .withIntervalInSeconds(30)  // Longer interval
                                    .withRepeatCount(2))        // More repeats
                            .build();

                    return Mono.just(trigger);
                },

                // Schedule the trigger and add verification
                (task, jd, tg) -> {
                    try {
                        Date scheduledDate = scheduler.scheduleJob(tg);
                        System.out.println("Trigger scheduled for: " + scheduledDate);
                        
                        // Verify trigger was stored
                        Trigger storedTrigger = scheduler.getTrigger(tg.getKey());
                        System.out.println("Trigger stored: " + (storedTrigger != null));
                        if (storedTrigger != null) {
                            System.out.println("Trigger type: " + storedTrigger.getClass().getName());
                            System.out.println("Trigger state: " + scheduler.getTriggerState(tg.getKey()));
                            System.out.println("Next fire time: " + storedTrigger.getNextFireTime());
                            System.out.println("Start time: " + storedTrigger.getStartTime());
                            System.out.println("End time: " + storedTrigger.getEndTime());
                        }
                        
                        // Add a delay to keep the test running while checking DB
                        Thread.sleep(2000);
                        
                        return Mono.just(scheduledDate);
                    } catch (SchedulerException | InterruptedException e) {
                        System.err.println("Error scheduling trigger: " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                },
                (task, jd, tg, scheduledDate) -> Mono.empty()).block();
    }
}