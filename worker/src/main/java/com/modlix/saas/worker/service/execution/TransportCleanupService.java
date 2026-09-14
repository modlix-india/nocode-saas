package com.modlix.saas.worker.service.execution;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;
import com.modlix.saas.worker.feign.IFeignUIService;

/**
 * Removes app-transport import receipts that are past their retention window.
 *
 * <h2>What is being deleted</h2>
 *
 * <p>Promoting an app between environments uploads a zip per chunk to
 * {@code /transports/createAndApply}. Each upload is stored as a transport document with the whole
 * zip base64'd into a field, read back once by id while the import runs, and never looked at again.
 * Nothing deleted them: an app's transports go when the app itself is deleted, and there was no
 * other path. Creating one also writes a second copy of the same bytes into the version collection,
 * because versioning is on for every overridable service.
 *
 * <h2>Why it is worth a job</h2>
 *
 * <p>Not for the disk. {@code AbstractTransportService.create} looks a transport up by
 * {@code uniqueTransportCode} before inserting, and that field carries no index, so every chunk of
 * every import scans the whole collection, dragging multi-megabyte documents through the cache and
 * evicting everything else on the way. Measured on stage 2026-09-14: {@code ui.transport} at 955
 * documents and 2.1 GB against a 2.3 GB WiredTiger cache, one such lookup at 265 seconds, and a
 * single Application object taking 113 seconds to import where a smaller environment took 2.
 *
 * <p>So this bounds the scan by keeping the collection small. It does not remove the scan: that
 * needs an index on {@code uniqueTransportCode}, which is a schema change and the real fix.
 *
 * <h2>One job type, one service per task row</h2>
 *
 * <p>Which service to sweep is job data, not a job type. ui and core own separate transport
 * collections with nothing in common but the shape, and they are wildly different sizes: on stage
 * ui holds 2.1 GB to core's 116 MB. Seeding a task row per service is what lets them be scheduled,
 * tuned, paused and reported on one at a time, and a service that starts keeping transports later
 * is then a row rather than an enum value, a migration and a switch arm.
 *
 * <h2>Why the worker and not a scheduler in ui/core</h2>
 *
 * <p>Production runs several instances of both. A {@code @Scheduled} method fires on all of them at
 * the same instant, so every replica would read the same stale documents and race the others to
 * delete them. The worker runs on a Quartz cluster with a shared job store, which is what makes
 * "once, somewhere" true.
 */
@Service
public class TransportCleanupService extends AbstractExecutionService {

    private static final String JOB_DATA_SERVICE = "service";
    private static final String JOB_DATA_RETENTION_DAYS = "retentionDays";
    private static final String JOB_DATA_LIMIT = "limit";

    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int DEFAULT_LIMIT = 200;

    private final IFeignUIService feignUIService;
    private final IFeignCoreService feignCoreService;

    public TransportCleanupService(IFeignUIService feignUIService, IFeignCoreService feignCoreService) {
        this.feignUIService = feignUIService;
        this.feignCoreService = feignCoreService;
    }

    @Override
    public String execute(Task task) {

        String service = getServiceJobData(task);
        int retentionDays = getIntJobData(task, JOB_DATA_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        int limit = getIntJobData(task, JOB_DATA_LIMIT, DEFAULT_LIMIT);

        logger.info("Executing {} transport cleanup (older than {} days, up to {})", service, retentionDays, limit);

        Supplier<Map<String, Integer>> call = switch (service) {
            case "ui" -> () -> feignUIService.cleanupTransports(retentionDays, limit);
            case "core" -> () -> feignCoreService.cleanupTransports(retentionDays, limit);
            default -> throw new IllegalStateException(
                    "Unknown service in transport cleanup job data: '" + service + "'. Expected 'ui' or 'core'.");
        };

        Map<String, Integer> removed = runWithTimeout(call);

        int transports = removed == null ? 0 : removed.getOrDefault("transportsRemoved", 0);
        int versions = removed == null ? 0 : removed.getOrDefault("versionsRemoved", 0);

        logger.info("Transport cleanup complete for {}. transports: {}, versions: {}", service, transports, versions);

        return truncateResult(service + " transports removed: " + transports + ", versions removed: " + versions);
    }

    /**
     * The service to sweep, with no default.
     *
     * <p>Guessing would be worse than failing. A row whose job data lost its service key is a
     * mistake, and picking one on its behalf means quietly sweeping a collection nobody asked about
     * while the one that was meant grows unattended. Throwing puts the reason in LAST_FIRE_RESULT
     * where the task list shows it.
     */
    private String getServiceJobData(Task task) {

        Object value = task.getJobData() == null ? null : task.getJobData().get(JOB_DATA_SERVICE);

        if (value == null || value.toString().isBlank())
            throw new IllegalStateException(
                    "Transport cleanup task '" + task.getName() + "' has no '" + JOB_DATA_SERVICE
                            + "' in its job data. Expected 'ui' or 'core'.");

        return value.toString().trim().toLowerCase();
    }

    private int getIntJobData(Task task, String key, int fallback) {

        if (task.getJobData() == null) return fallback;

        Object value = task.getJobData().get(key);
        if (value instanceof Number num) return num.intValue();

        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} in job data: {}", key, value);
            }
        }
        return fallback;
    }
}
