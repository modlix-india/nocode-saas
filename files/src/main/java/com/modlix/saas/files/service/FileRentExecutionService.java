package com.modlix.saas.files.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.dto.Client;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.model.wallet.RentTarget;
import com.modlix.saas.files.dao.FileSystemDao;

/**
 * Hourly file-rent drip. Files own the file storage, so this counts each billed
 * client's total stored bytes and asks security to drip the rent. Files are
 * client-scoped (no app dimension) and a client uses exactly one builder, so the
 * config's app is only the rate key. All billing math stays in security.
 */
@Service
public class FileRentExecutionService {

    private static final String ACTION_KEY = "file.gb";
    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024L * 1024L);

    private final IFeignSecurityService securityService;
    private final FileSystemDao fileSystemDao;

    public FileRentExecutionService(IFeignSecurityService securityService, FileSystemDao fileSystemDao) {
        this.securityService = securityService;
        this.fileSystemDao = fileSystemDao;
    }

    /** Drip file rent for every billed app; returns total bytes charged. */
    public long dripFileRent() {
        long totalBytes = 0L;

        for (RentTarget target : this.securityService.rentTargets(ACTION_KEY)) {
            Client owner = this.securityService.getClientByCode(target.getOwnerClientCode());
            if (owner == null)
                continue;

            for (BigInteger managedId : this.securityService.getClientIdsOfManager(owner.getId())) {
                Client managed = this.securityService.getClientById(managedId);
                if (managed == null)
                    continue;

                long bytes = this.fileSystemDao.sumSizeByClientCode(managed.getCode());
                if (bytes <= 0L)
                    continue;

                BigDecimal gb = BigDecimal.valueOf(bytes).divide(BYTES_PER_GB, 10, RoundingMode.HALF_UP);
                this.securityService.chargeRent(target.getAppCode(), managed.getCode(), ACTION_KEY, gb);
                totalBytes += bytes;
            }
        }

        return totalBytes;
    }
}
