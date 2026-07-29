package com.modlix.saas.worker.service.execution;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import com.modlix.saas.worker.feign.IFeignFilesService;
import com.modlix.saas.worker.feign.IFeignSecurityService;

/**
 * Triggers the 15-minute token metering on each owning service, and the nightly
 * billing reconciliation across all of them.
 */
@Service
public class MeteringExecutionService extends AbstractExecutionService {

    private final IFeignSecurityService securityService;
    private final IFeignCoreService coreService;
    private final IFeignEntityProcessorService entityProcessorService;
    private final IFeignFilesService filesService;

    public MeteringExecutionService(IFeignSecurityService securityService, IFeignCoreService coreService,
            IFeignEntityProcessorService entityProcessorService, IFeignFilesService filesService) {
        this.securityService = securityService;
        this.coreService = coreService;
        this.entityProcessorService = entityProcessorService;
        this.filesService = filesService;
    }

    @Override
    public String execute(Task task) {
        StringBuilder result = new StringBuilder();
        switch (task.getTaskJobType()) {
            case SECURITY_METERING ->
                result.append("security metering: ").append(call(this.securityService::triggerSecurityMetering));
            case CORE_METERING ->
                result.append("core metering: ").append(call(this.coreService::triggerBillingMetering));
            case ENTITY_PROCESSOR_METERING ->
                result.append("entity-processor metering: ")
                        .append(call(this.entityProcessorService::triggerBillingMetering));
            case FILES_METERING ->
                result.append("files metering: ").append(call(this.filesService::triggerBillingMetering));
            case BILLING_RECONCILE -> result
                    .append("reconcile security:").append(call(this.securityService::reconcileBilling))
                    .append(" core:").append(call(this.coreService::reconcileBilling))
                    .append(" entity-processor:").append(call(this.entityProcessorService::reconcileBilling))
                    .append(" files:").append(call(this.filesService::reconcileBilling));
            default -> throw new IllegalStateException("Unexpected metering job type: " + task.getTaskJobType());
        }
        logger.info("Metering trigger complete: {}", result);
        return truncateResult(result.toString());
    }

    private String call(Supplier<Boolean> remote) {
        try {
            return String.valueOf(runWithTimeout(remote::get));
        } catch (Exception e) {
            logger.error("Metering trigger failed: {}", e.getMessage());
            return "error:" + e.getMessage();
        }
    }
}
