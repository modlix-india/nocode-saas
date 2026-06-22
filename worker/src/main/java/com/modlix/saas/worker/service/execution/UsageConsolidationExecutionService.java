package com.modlix.saas.worker.service.execution;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignSecurityService;

/**
 * Triggers the security service's 15-minute token-usage consolidation pass.
 * Security owns all the billing logic (group, resolve wallet, price, debit,
 * suspend, purge); this job is just the scheduled trigger. Runs once across the
 * blue-green pair via the Quartz cluster lock.
 */
@Service
public class UsageConsolidationExecutionService extends AbstractExecutionService {

    private final IFeignSecurityService feignSecurityService;

    public UsageConsolidationExecutionService(IFeignSecurityService feignSecurityService) {
        this.feignSecurityService = feignSecurityService;
    }

    @Override
    public String execute(Task task) {
        logger.info("Executing token-usage consolidation");

        Integer consolidated = runWithTimeout(this.feignSecurityService::consolidateUsage);

        String message = "Usage consolidation complete: " + (consolidated == null ? 0 : consolidated)
                + " rows consolidated";
        logger.info(message);
        return truncateResult(message);
    }
}
