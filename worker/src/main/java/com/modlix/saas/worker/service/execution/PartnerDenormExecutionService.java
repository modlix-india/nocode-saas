package com.modlix.saas.worker.service.execution;

import java.util.HashMap;
import java.util.Map;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.enums.TaskJobType;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import org.springframework.stereotype.Service;

@Service
public class PartnerDenormExecutionService extends AbstractExecutionService {

    private static final String JOB_DATA_LAST_SYNC_SINCE = "lastSyncSince";

    private final IFeignEntityProcessorService feignEntityProcessorService;

    public PartnerDenormExecutionService(IFeignEntityProcessorService feignEntityProcessorService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
    }

    @Override
    public String execute(Task task) {
        boolean delta = task.getTaskJobType() == TaskJobType.PARTNER_DENORM_DELTA;
        String since = delta ? readSince(task) : null;
        logger.info("Executing partner denorm sync (delta={}, since={})", delta, since);

        Map<String, Object> result = runWithTimeout(
                () -> feignEntityProcessorService.triggerPartnerDenormalization(delta, since));

        int updated = 0;
        if (result != null && result.get("partnersUpdated") instanceof Number num)
            updated = num.intValue();

        if (delta && result != null && result.get("nextSince") instanceof String next && !next.isBlank())
            writeSince(task, next);

        String message = "Partner denorm " + (delta ? "delta" : "full") + " complete: " + updated + " partners updated";
        logger.info(message);
        return truncateResult(message);
    }

    private String readSince(Task task) {
        if (task.getJobData() == null) return null;
        Object value = task.getJobData().get(JOB_DATA_LAST_SYNC_SINCE);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private void writeSince(Task task, String nextSince) {
        Map<String, Object> jobData = task.getJobData();
        if (jobData == null) {
            jobData = new HashMap<>();
            task.setJobData(jobData);
        }
        jobData.put(JOB_DATA_LAST_SYNC_SINCE, nextSince);
    }
}
