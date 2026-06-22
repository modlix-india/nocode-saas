package com.modlix.saas.worker.service.execution;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignSecurityService;

/**
 * Hourly seat/app/site rent trigger. Security counts these from its own user and
 * app tables and drips the rent, so this job is just the scheduled trigger. Runs
 * once across the blue-green pair via the Quartz cluster lock.
 */
@Service
public class SeatAppSiteRentExecutionService extends AbstractExecutionService {

    private final IFeignSecurityService feignSecurityService;

    public SeatAppSiteRentExecutionService(IFeignSecurityService feignSecurityService) {
        this.feignSecurityService = feignSecurityService;
    }

    @Override
    public String execute(Task task) {
        logger.info("Executing seat/app/site rent drip");

        Long units = runWithTimeout(this.feignSecurityService::internalRentDrip);

        String message = "Seat/app/site rent drip complete: " + (units == null ? 0 : units) + " units charged";
        logger.info(message);
        return truncateResult(message);
    }
}
