package com.modlix.saas.files.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.feign.IFeignSecurityBillingService;
import com.modlix.saas.files.model.billing.ChargeRequest;
import com.modlix.saas.files.model.billing.MeteringInstruction;

/**
 * Files' slice of the 15-minute token metering loop: for every (C, app, M) that
 * carries a {@code files.gb} rate, sum M's stored bytes (static + secured),
 * convert to GB and post the raw amount to security, which does all pricing.
 *
 * <p>The file system is keyed by client code only (no app dimension), so this is a
 * per-client GB total billed to each app instruction. Sellers should set the files
 * rate on a single app per client (typically the builder app) to avoid charging the
 * same GB against multiple wallets. Reconciliation re-posts every window of a day
 * idempotently; security no-ops windows already charged.
 */
@Service
public class FilesBillingMeteringService {

    private static final Logger logger = LoggerFactory.getLogger(FilesBillingMeteringService.class);

    private static final String ACTION_FILES_GB = "files.gb";
    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024L * 1024L);

    private final IFeignSecurityBillingService securityBilling;
    private final FileSystemDao fileSystemDao;

    public FilesBillingMeteringService(IFeignSecurityBillingService securityBilling, FileSystemDao fileSystemDao) {
        this.securityBilling = securityBilling;
        this.fileSystemDao = fileSystemDao;
    }

    /** Worker trigger: charge the current 15-minute window. */
    public boolean meterCurrentWindow() {
        LocalDate date = LocalDate.now();
        int window = LocalTime.now().toSecondOfDay() / 900;
        return this.run(date, List.of(window));
    }

    /** Worker trigger: reconcile a day (default yesterday) by re-posting every window. */
    public boolean reconcile(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        return this.run(day, IntStream.range(0, 96).boxed().toList());
    }

    private boolean run(LocalDate date, List<Integer> windows) {
        List<MeteringInstruction> instructions = this.securityBilling.getInstructions(ACTION_FILES_GB);
        if (instructions == null)
            return true;

        for (MeteringInstruction instr : instructions) {
            try {
                long bytes = this.fileSystemDao.sumBytesByClient(instr.billedClientCode());
                if (bytes <= 0)
                    continue;
                BigDecimal gb = BigDecimal.valueOf(bytes).divide(BYTES_PER_GB, 6, RoundingMode.HALF_UP);
                if (gb.signum() <= 0)
                    continue;
                List<ChargeRequest> charges = windows.stream()
                        .map(w -> new ChargeRequest(instr.configClientId(), instr.billedClientId(), instr.appId(),
                                ACTION_FILES_GB, gb, date, w))
                        .toList();
                this.securityBilling.charge(charges);
            } catch (Exception e) {
                logger.error("Files metering failed for client {}: {}", instr.billedClientCode(), e.getMessage());
            }
        }
        return true;
    }
}
