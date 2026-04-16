package com.modlix.saas.worker.service.execution;

import java.util.Map;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.enums.TaskJobType;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import org.springframework.stereotype.Service;

@Service
public class PartnerDenormExecutionService extends AbstractExecutionService {

    private final IFeignEntityProcessorService feignEntityProcessorService;

    public PartnerDenormExecutionService(IFeignEntityProcessorService feignEntityProcessorService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
    }

    @Override
    public String execute(Task task) {
        boolean delta = task.getTaskJobType() == TaskJobType.PARTNER_DENORM_DELTA;
        logger.info("Executing partner denorm sync (delta={})", delta);

        Map<String, Object> result = runWithTimeout(
                () -> feignEntityProcessorService.triggerPartnerDenormalization(delta));

        int updated = 0;
        if (result != null && result.get("partnersUpdated") instanceof Number num)
            updated = num.intValue();

        String message = "Partner denorm " + (delta ? "delta" : "full") + " complete: " + updated + " partners updated";
        logger.info(message);
        return truncateResult(message);
    }
}
