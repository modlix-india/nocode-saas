package com.modlix.saas.files.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.files.service.FileRentExecutionService;

/**
 * Internal billing trigger for the worker. Under api/files/internal/** which is
 * already permitted (network-isolated). The worker fires file-rent-drip hourly.
 */
@RestController
@RequestMapping("api/files/internal/billing")
public class FilesBillingController {

    private final FileRentExecutionService fileRentService;

    public FilesBillingController(FileRentExecutionService fileRentService) {
        this.fileRentService = fileRentService;
    }

    @PostMapping("/file-rent-drip")
    public ResponseEntity<Long> fileRentDrip() {
        return ResponseEntity.ok(this.fileRentService.dripFileRent());
    }
}
