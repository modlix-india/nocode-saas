package com.modlix.saas.worker.service.execution;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;

/**
 * Keeps core's event run log (core_event_runs) to its retention window.
 *
 * <h2>What it does</h2>
 *
 * <p>Core records a row for every run of an event's EventAction, with each task's outcome, so an
 * app's users can see whether an event ran and why it failed. Each call to core:
 * <ol>
 * <li>closes runs still RUNNING after {@code staleAfterHours} as FAILED, so a run whose server
 * stopped mid-way does not read as running forever;</li>
 * <li>deletes successful runs started more than {@code successRetentionDays} ago;</li>
 * <li>deletes every run started more than {@code retentionDays} ago.</li>
 * </ol>
 *
 * <p>At most {@code limit} rows in all per call. Successes go sooner because they are nearly every
 * row and the least looked at; a failure, a skip or a missing action keeps the full retention.
 *
 * <h2>Why the worker and not a scheduler in core</h2>
 *
 * <p>Production runs several core instances, and a {@code @Scheduled} method fires on all of them at
 * once, each racing the others to delete the same rows. The worker's Quartz cluster runs it once,
 * somewhere, and the task row is where its schedule and retention are changed.
 *
 * <p>Core enforces the floors and the cap whatever the job data says (never younger than 30 days, or
 * a day for successes, never stale before an hour, at most 200,000 rows a call), because the endpoint is internal and
 * unauthenticated. A {@code runsRemoved} equal to the limit in LAST_FIRE_RESULT means there was more
 * to delete: raise the limit or run it more often.
 */
@Service
public class EventRunCleanupService extends AbstractExecutionService {

    private static final String JOB_DATA_RETENTION_DAYS = "retentionDays";
    private static final String JOB_DATA_SUCCESS_RETENTION_DAYS = "successRetentionDays";
    private static final String JOB_DATA_STALE_AFTER_HOURS = "staleAfterHours";
    private static final String JOB_DATA_LIMIT = "limit";

    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int DEFAULT_SUCCESS_RETENTION_DAYS = 7;
    private static final int DEFAULT_STALE_AFTER_HOURS = 6;
    private static final int DEFAULT_LIMIT = 50_000;

    private final IFeignCoreService feignCoreService;

    public EventRunCleanupService(IFeignCoreService feignCoreService) {
        this.feignCoreService = feignCoreService;
    }

    @Override
    public String execute(Task task) {

        int retentionDays = getIntJobData(task, JOB_DATA_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        int successRetentionDays = getIntJobData(task, JOB_DATA_SUCCESS_RETENTION_DAYS,
                DEFAULT_SUCCESS_RETENTION_DAYS);
        int staleAfterHours = getIntJobData(task, JOB_DATA_STALE_AFTER_HOURS, DEFAULT_STALE_AFTER_HOURS);
        int limit = getIntJobData(task, JOB_DATA_LIMIT, DEFAULT_LIMIT);

        logger.info("Executing event run log cleanup (successes older than {} days, all older than {} days, "
                + "stale after {} hours, up to {})", successRetentionDays, retentionDays, staleAfterHours, limit);

        Map<String, Integer> result = runWithTimeout(() -> feignCoreService.cleanupEventRuns(retentionDays,
                successRetentionDays, staleAfterHours, limit));

        int removed = result == null ? 0 : result.getOrDefault("runsRemoved", 0);
        int successes = result == null ? 0 : result.getOrDefault("successesRemoved", 0);
        int stale = result == null ? 0 : result.getOrDefault("staleClosed", 0);

        logger.info("Event run log cleanup complete. removed: {}, stale closed: {}", removed, stale);

        return truncateResult("event runs removed: " + removed + " (" + successes + " successful)"
                + (removed >= limit ? ", limit reached" : "") + ", stale runs closed: " + stale);
    }

    private int getIntJobData(Task task, String key, int fallback) {

        if (task.getJobData() == null) return fallback;

        Object value = task.getJobData().get(key);
        if (value instanceof Number num) return num.intValue();

        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} in job data: {}", key, value);
            }
        }
        return fallback;
    }
}
