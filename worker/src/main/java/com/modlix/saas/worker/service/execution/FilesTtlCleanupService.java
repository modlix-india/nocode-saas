package com.modlix.saas.worker.service.execution;

import com.modlix.saas.worker.dto.CoreEventRaiseRequest;
import com.modlix.saas.worker.dto.FilesRetentionResult;
import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import com.modlix.saas.worker.feign.IFeignFilesService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *
 * <h2>Retention policies, and telling the app</h2>
 *
 * <p>After the lifetimes it asks the files service to apply retention policies: an app registers a
 * prefix under its own folder ({@code <appCode>/whatsapp/incoming}) with a number of days, and a
 * client may override it. Again the files service decides what goes. What comes back is grouped by
 * client and app, and for each group this job raises the core event {@code FILES_EXPIRED} with the
 * removed paths, so the app's EventAction can mark exactly the records that pointed at them. A
 * failure to raise is logged and never stops deletion or the other groups.
 */
@Service
public class FilesTtlCleanupService extends AbstractExecutionService {

    private static final String JOB_DATA_LIMIT = "limit";
    private static final int DEFAULT_LIMIT = 500;

    /** Matches the lifetime the WhatsApp uploads stamp on their files. */
    private static final int WHATSAPP_RETENTION_DAYS = 30;

    static final String FILES_EXPIRED_EVENT = "FILES_EXPIRED";

    /** At most this many paths in one FILES_EXPIRED event; a larger group is raised in chunks. */
    static final int MAX_PATHS_PER_EVENT = 500;

    private final IFeignFilesService feignFilesService;
    private final IFeignEntityProcessorService feignEntityProcessorService;
    private final IFeignCoreService feignCoreService;

    public FilesTtlCleanupService(
            IFeignFilesService feignFilesService,
            IFeignEntityProcessorService feignEntityProcessorService,
            IFeignCoreService feignCoreService) {
        this.feignFilesService = feignFilesService;
        this.feignEntityProcessorService = feignEntityProcessorService;
        this.feignCoreService = feignCoreService;
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

        // Then the retention policies, each bucket on its own, and each app told what it lost.
        int retainedSecured = applyRetention("secured", limit, result);
        int retainedStatic = applyRetention("static", limit, result);

        // After the deletes, so a message is never marked expired before its file has actually
        // gone. The stamp uses a cutoff a day wider still, so the ordering here is belt and braces
        // rather than the thing that makes it correct.
        int stamped = stampWhatsappMedia(result);

        logger.info(
                "Expired-file cleanup complete — secured: {}, static: {}, retention secured: {}, retention static: {}, messages marked: {}",
                secured, statics, retainedSecured, retainedStatic, stamped);

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

    /**
     * Applies retention for one bucket and raises FILES_EXPIRED for every client and app group in
     * what was removed. Answers the number of files removed. Raising is best effort: each event
     * on its own, a failure logged and counted, never thrown.
     */
    private int applyRetention(String resourceType, int limit, StringBuilder result) {

        List<FilesRetentionResult> groups;
        try {
            groups = runWithTimeout(() -> feignFilesService.applyRetention(resourceType, limit));
        } catch (Exception e) {
            logger.error("Retention failed for {}: {}", resourceType, e.getMessage());
            result.append(resourceType).append(" retention error: ").append(e.getMessage()).append("; ");
            return 0;
        }

        int removed = 0;
        int raised = 0;
        int failed = 0;

        if (groups != null) {
            for (FilesRetentionResult group : groups) {
                if (group == null || group.getPaths() == null || group.getPaths().isEmpty()) continue;

                List<String> paths = group.getPaths();
                removed += paths.size();

                for (int from = 0; from < paths.size(); from += MAX_PATHS_PER_EVENT) {
                    List<String> chunk =
                            new ArrayList<>(paths.subList(from, Math.min(paths.size(), from + MAX_PATHS_PER_EVENT)));
                    if (raiseFilesExpired(group, resourceType, chunk)) raised++;
                    else failed++;
                }
            }
        }

        result.append(resourceType)
                .append(" retention removed: ")
                .append(removed)
                .append(", events raised: ")
                .append(raised);
        if (failed > 0) result.append(", events failed: ").append(failed);
        result.append("; ");

        return removed;
    }

    private boolean raiseFilesExpired(FilesRetentionResult group, String resourceType, List<String> paths) {

        String type = group.getResourceType() == null ? resourceType.toUpperCase() : group.getResourceType();

        CoreEventRaiseRequest request = new CoreEventRaiseRequest()
                .setEventName(FILES_EXPIRED_EVENT)
                .setAppCode(group.getAppCode())
                .setClientCode(group.getClientCode())
                .setData(Map.of("resourceType", type, "paths", paths));

        try {
            Boolean ok = runWithTimeout(() -> feignCoreService.raiseEvent(request));
            if (Boolean.TRUE.equals(ok)) return true;
            logger.error("Raising {} for app {} client {} was refused ({} paths)", FILES_EXPIRED_EVENT,
                    group.getAppCode(), group.getClientCode(), paths.size());
            return false;
        } catch (Exception e) {
            logger.error("Raising {} for app {} client {} failed ({} paths): {}", FILES_EXPIRED_EVENT,
                    group.getAppCode(), group.getClientCode(), paths.size(), e.getMessage());
            return false;
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

    /**
     * Tells entity-processor which conversations now point at files that are gone.
     *
     * <p>Without it a bubble keeps its reference to a deleted object and renders as a broken frame
     * rather than saying the attachment expired. Failures are logged and swallowed: this is what the
     * thread says, and it must never stop files being collected.
     */
    private int stampWhatsappMedia(StringBuilder result) {
        try {
            Integer stamped = runWithTimeout(
                    () -> feignEntityProcessorService.stampExpiredWhatsappMedia(WHATSAPP_RETENTION_DAYS, 1000));
            int count = stamped == null ? 0 : stamped;
            result.append("messages marked expired: ").append(count).append("; ");
            return count;
        } catch (Exception e) {
            logger.error("Marking expired WhatsApp attachments failed: {}", e.getMessage());
            result.append("message marking error: ").append(e.getMessage()).append("; ");
            return 0;
        }
    }
}
