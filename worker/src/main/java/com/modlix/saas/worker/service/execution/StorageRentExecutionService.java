package com.modlix.saas.worker.service.execution;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;

/**
 * Hourly storage-rent trigger. Core owns the Mongo, so it does the counting and
 * calls security to drip the rent; this job is just the scheduled trigger. Runs
 * once across the blue-green pair via the Quartz cluster lock.
 */
@Service
public class StorageRentExecutionService extends AbstractExecutionService {

    private final IFeignCoreService feignCoreService;

    public StorageRentExecutionService(IFeignCoreService feignCoreService) {
        this.feignCoreService = feignCoreService;
    }

    @Override
    public String execute(Task task) {
        logger.info("Executing storage-rent drip");

        Long rows = runWithTimeout(this.feignCoreService::storageRentDrip);

        String message = "Storage rent drip complete: " + (rows == null ? 0 : rows) + " rows charged";
        logger.info(message);
        return truncateResult(message);
    }
}
