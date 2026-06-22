package com.modlix.saas.worker.service.execution;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignFilesService;

/**
 * Hourly file-rent trigger. The files service owns file storage, so it counts
 * each billed client's stored bytes and calls security to drip the rent; this
 * job is just the scheduled trigger. Runs once across the blue-green pair via
 * the Quartz cluster lock.
 */
@Service
public class FileRentExecutionService extends AbstractExecutionService {

    private final IFeignFilesService feignFilesService;

    public FileRentExecutionService(IFeignFilesService feignFilesService) {
        this.feignFilesService = feignFilesService;
    }

    @Override
    public String execute(Task task) {
        logger.info("Executing file-rent drip");

        Long bytes = runWithTimeout(this.feignFilesService::fileRentDrip);

        String message = "File rent drip complete: " + (bytes == null ? 0 : bytes) + " bytes charged";
        logger.info(message);
        return truncateResult(message);
    }
}
