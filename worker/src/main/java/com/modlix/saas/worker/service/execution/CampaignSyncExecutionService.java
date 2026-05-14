package com.modlix.saas.worker.service.execution;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.enums.TaskJobType;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CampaignSyncExecutionService extends AbstractExecutionService {

    private final IFeignEntityProcessorService feignEntityProcessorService;

    public CampaignSyncExecutionService(IFeignEntityProcessorService feignEntityProcessorService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
    }

    @Override
    public String execute(Task task) {
        TaskJobType type = task.getTaskJobType();
        logger.info("Executing campaign sync job type={}", type);

        Map<String, Object> result = runWithTimeout(() -> switch (type) {
            case CAMPAIGN_METRICS_SYNC -> feignEntityProcessorService.triggerCampaignMetricsSync();
            case CAMPAIGN_DISCOVERY_SYNC -> feignEntityProcessorService.triggerCampaignDiscoverySync();
            default -> throw new IllegalStateException("Unexpected job type: " + type);
        });

        int campaignsTouched = result != null && result.get("campaignsTouched") instanceof Number n ? n.intValue() : 0;
        int errors = result != null && result.get("errors") instanceof Number n ? n.intValue() : 0;

        String message = "Campaign " + (type == TaskJobType.CAMPAIGN_METRICS_SYNC ? "metrics" : "discovery")
                + " sync complete: " + campaignsTouched + " campaigns touched, " + errors + " errors";
        logger.info(message);
        return truncateResult(message);
    }
}
