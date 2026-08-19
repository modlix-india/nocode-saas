package com.modlix.saas.worker.service.execution;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignFilesService;
import org.springframework.stereotype.Service;

/**
 * Removes files whose declared lifetime has passed.
 *
 * <h2>Why this is a worker job and not a scheduler in the files service</h2>
 *
 * <p>Production runs several instances of every service. A {@code @Scheduled} method fires on all of
 * them at the same instant, so a cleanup written that way would have every replica reading the same
 * expired rows and racing to delete the same objects: duplicated S3 calls, errors on whichever loses
 * each race, and no way to tell a genuine failure from a lost race in the logs. The worker runs on a
 * Quartz cluster with a shared job store, which means exactly one instance fires a given trigger.
 *
 * <h2>Why it does not decide what to delete</h2>
 *
 * <p>It asks the files service to act on lifetimes that were set when each file was created. That
 * matters more than it looks. The obvious design - a sweep that finds old files and removes them -
 * puts the decision in whatever runs the sweep, and it has to be right about which files it owns.
 * The first version of this feature very nearly deleted a product's shared brochures because they
 * had been sent to a lead a month earlier. Expressed as a per-file lifetime, a file with none can
 * never be removed by this job no matter how old it is.
 */
@Service
public class FilesTtlCleanupService extends AbstractExecutionService {

    private static final String JOB_DATA_LIMIT = "limit";
    private static final int DEFAULT_LIMIT = 500;

    private final IFeignFilesService feignFilesService;

    public FilesTtlCleanupService(IFeignFilesService feignFilesService) {
        this.feignFilesService = feignFilesService;
    }

    @Override
    public String execute(Task task) {

        int limit = getLimit(task);
        logger.info("Executing expired-file cleanup (limit {} per store)", limit);

        StringBuilder result = new StringBuilder();

        // Both stores, because both can hold temporary files. Independently, so a failure in one
        // does not leave the other untouched.
        int secured = cleanup("secured", limit, result);
        int statics = cleanup("static", limit, result);

        logger.info("Expired-file cleanup complete — secured: {}, static: {}", secured, statics);

        return truncateResult(result.toString());
    }

    private int cleanup(String resourceType, int limit, StringBuilder result) {
        try {
            Integer removed = runWithTimeout(() -> feignFilesService.cleanupExpired(resourceType, limit));
            int count = removed == null ? 0 : removed;
            result.append(resourceType).append(" removed: ").append(count).append("; ");
            return count;
        } catch (Exception e) {
            logger.error("Expired-file cleanup failed for {}: {}", resourceType, e.getMessage());
            result.append(resourceType).append(" error: ").append(e.getMessage()).append("; ");
            return 0;
        }
    }

    private int getLimit(Task task) {
        if (task.getJobData() == null) return DEFAULT_LIMIT;

        Object value = task.getJobData().get(JOB_DATA_LIMIT);
        if (value instanceof Number num) return num.intValue();

        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.warn("Invalid limit in job data: {}", value);
            }
        }
        return DEFAULT_LIMIT;
    }
}
