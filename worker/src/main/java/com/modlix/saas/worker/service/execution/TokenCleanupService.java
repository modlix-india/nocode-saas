package com.modlix.saas.worker.service.execution;

import java.util.Map;

import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignSecurityService;
import org.springframework.stereotype.Service;

@Service
public class TokenCleanupService extends AbstractExecutionService {

    private static final String JOB_DATA_UNUSED_DAYS = "unusedDays";
    private static final int DEFAULT_UNUSED_DAYS = 90;

    private final IFeignSecurityService feignSecurityService;

    public TokenCleanupService(IFeignSecurityService feignSecurityService) {
        this.feignSecurityService = feignSecurityService;
    }

    @Override
    public String execute(Task task) {
        int unusedDays = getUnusedDays(task);
        logger.info("Executing token cleanup (expired + unused for {} days)", unusedDays);

        StringBuilder result = new StringBuilder();

        int securityExpired = 0;
        int securityUnused = 0;

        try {
            Map<String, Integer> securityResult = runWithTimeout(
                    () -> feignSecurityService.cleanupTokens(unusedDays));
            if (securityResult != null) {
                securityExpired = securityResult.getOrDefault("expiredTokensRemoved", 0);
                securityUnused = securityResult.getOrDefault("unusedTokensRemoved", 0);
                result.append("Security expired removed: ").append(securityExpired);
                result.append(", unused removed: ").append(securityUnused);
            } else {
                result.append("Security tokens cleaned: 0");
            }
        } catch (Exception e) {
            logger.error("Security token cleanup failed: {}", e.getMessage());
            result.append("Security token cleanup error: ").append(e.getMessage());
        }

        logger.info("Token cleanup complete — security expired: {}, security unused: {}",
                securityExpired, securityUnused);

        return truncateResult(result.toString());
    }

    private int getUnusedDays(Task task) {
        if (task.getJobData() == null) return DEFAULT_UNUSED_DAYS;

        Object value = task.getJobData().get(JOB_DATA_UNUSED_DAYS);
        if (value instanceof Number num) return num.intValue();

        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.warn("Invalid unusedDays in job data: {}", value);
            }
        }
        return DEFAULT_UNUSED_DAYS;
    }
}
