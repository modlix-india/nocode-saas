package com.modlix.saas.worker.service.execution;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversionsDispatchExecutionService extends AbstractExecutionService {

    @Value("${worker.conversions.batch-size:50}")
    private int batchSize;

    private final IFeignEntityProcessorService feignEntityProcessorService;

    public ConversionsDispatchExecutionService(IFeignEntityProcessorService feignEntityProcessorService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
    }

    @Override
    public String execute(Task task) {
        logger.info("Executing conversions API dispatch (batchSize={})", batchSize);

        Map<String, Object> result =
                runWithTimeout(() -> feignEntityProcessorService.triggerConversionsApiDispatch(batchSize));

        int dispatched = result != null && result.get("dispatched") instanceof Number n ? n.intValue() : 0;
        int failed = result != null && result.get("failed") instanceof Number n ? n.intValue() : 0;
        int skipped = result != null && result.get("skipped") instanceof Number n ? n.intValue() : 0;

        String message = "Conversions API dispatch complete: " + dispatched + " dispatched, "
                + failed + " failed, " + skipped + " skipped";
        logger.info(message);
        return truncateResult(message);
    }
}
