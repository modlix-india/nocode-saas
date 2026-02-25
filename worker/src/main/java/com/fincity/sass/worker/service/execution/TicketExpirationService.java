package com.fincity.sass.worker.service.execution;

import com.fincity.sass.worker.dto.ExpireTicketsResult;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.feign.IFeignEntityProcessor;
import org.springframework.stereotype.Service;

@Service
public class TicketExpirationService extends AbstractExecutionService {

    private final IFeignEntityProcessor feignEntityProcessor;

    public TicketExpirationService(IFeignEntityProcessor feignEntityProcessor) {
        this.feignEntityProcessor = feignEntityProcessor;
    }

    @Override
    public String execute(Task task) {
        String appCode = task.getAppCode();
        String clientCode = task.getClientCode();
        if (appCode == null || appCode.isBlank() || clientCode == null || clientCode.isBlank())
            return "Error: task must have appCode and clientCode";

        logger.info("Executing ticket expiration for appCode={}, clientCode={}", appCode, clientCode);
        try {
            ExpireTicketsResult result =
                    runWithTimeout(() -> feignEntityProcessor.runTicketExpiration(appCode, clientCode));
            return "Expired " + result.getExpiredCount() + " ticket(s)";
        } catch (Exception e) {
            logger.error(
                    "Ticket expiration failed for appCode={}, clientCode={}: {}", appCode, clientCode, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
