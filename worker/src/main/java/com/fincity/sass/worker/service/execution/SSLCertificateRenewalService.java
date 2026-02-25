package com.fincity.sass.worker.service.execution;

import com.fincity.sass.worker.dto.SSLCertificateRenewalResult;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.feign.IFeignSecuritySSLService;
import org.springframework.stereotype.Service;

@Service
public class SSLCertificateRenewalService extends AbstractExecutionService {
    private static final String JOB_DATA_DAYS_BEFORE_EXPIRY = "daysBeforeExpiry";
    private static final int DEFAULT_DAYS_BEFORE_EXPIRY = 30;

    private final IFeignSecuritySSLService feignSecuritySSLService;

    public SSLCertificateRenewalService(IFeignSecuritySSLService feignSecuritySSLService) {
        this.feignSecuritySSLService = feignSecuritySSLService;
    }

    @Override
    public String execute(Task task) {
        int daysBeforeExpiry = getDaysBeforeExpiry(task);
        logger.info("Executing SSL certificate renewal for certificates expiring within {} days", daysBeforeExpiry);

        try {
            SSLCertificateRenewalResult result =
                    runWithTimeout(() -> feignSecuritySSLService.renewExpiringCertificates(daysBeforeExpiry));
            return formatResult(result);
        } catch (Exception e) {
            logger.error("SSL certificate renewal failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private int getDaysBeforeExpiry(Task task) {
        if (task.getJobData() == null) {
            return DEFAULT_DAYS_BEFORE_EXPIRY;
        }
        Object value = task.getJobData().get(JOB_DATA_DAYS_BEFORE_EXPIRY);
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                logger.warn("Invalid daysBeforeExpiry in job data: {}", value);
            }
        }
        return DEFAULT_DAYS_BEFORE_EXPIRY;
    }

    private String formatResult(SSLCertificateRenewalResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Renewed: ").append(result.getRenewedCount());
        sb.append(", Failed: ").append(result.getFailedCount());
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            sb.append(". Errors: ").append(String.join("; ", result.getErrors()));
        }
        return sb.toString();
    }
}
