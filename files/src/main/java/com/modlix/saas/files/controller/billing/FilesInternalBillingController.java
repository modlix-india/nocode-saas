package com.modlix.saas.files.controller.billing;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.files.service.billing.FilesBillingMeteringService;

/**
 * Cluster-only token-metering endpoints triggered by the worker. Covered by the
 * existing {@code /api/files/internal/**} allowlist (nginx blocks public internal).
 */
@RestController
@RequestMapping("api/files/internal/billing")
public class FilesInternalBillingController {

    private final FilesBillingMeteringService meteringService;

    public FilesInternalBillingController(FilesBillingMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    @PostMapping("/meter")
    public Boolean meter() {
        return this.meteringService.meterCurrentWindow();
    }

    @PostMapping("/reconcile")
    public Boolean reconcile(@RequestParam(required = false) String date) {
        return this.meteringService.reconcile(date == null ? null : LocalDate.parse(date));
    }
}
