package com.fincity.sass.worker.service;

import com.fincity.sass.worker.dto.ExpireTicketsResult;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.feign.IFeignEntityProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TicketExpirationService {

    private static final Logger logger = LoggerFactory.getLogger(TicketExpirationService.class);

    private final IFeignEntityProcessor feignEntityProcessor;

    public TicketExpirationService(IFeignEntityProcessor feignEntityProcessor) {
        this.feignEntityProcessor = feignEntityProcessor;
    }

    public String runExpiration(Task task) {
        String appCode = task.getAppCode();
        String clientCode = task.getClientCode();
        if (appCode == null || appCode.isBlank() || clientCode == null || clientCode.isBlank()) {
            return "Error: task must have appCode and clientCode";
        }
        logger.info("Executing ticket expiration for appCode={}, clientCode={}", appCode, clientCode);
        try {
            ExpireTicketsResult result = feignEntityProcessor.runTicketExpiration(appCode, clientCode);
            return "Expired " + result.getExpiredCount() + " ticket(s)";
        } catch (Exception e) {
            logger.error(
                    "Ticket expiration failed for appCode={}, clientCode={}: {}", appCode, clientCode, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
