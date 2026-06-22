package com.fincity.saas.core.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.core.metering.StorageRentExecutionService;

import reactor.core.publisher.Mono;

/**
 * Internal billing triggers for the worker. The worker fires storage-rent-drip
 * hourly; core counts each billed client's stored rows and asks security to drip
 * the rent (all billing math stays in security).
 */
@RestController
@RequestMapping("api/core/billing")
public class CoreBillingController {

    private final StorageRentExecutionService storageRentService;

    public CoreBillingController(StorageRentExecutionService storageRentService) {
        this.storageRentService = storageRentService;
    }

    @PostMapping("/internal/storage-rent-drip")
    public Mono<ResponseEntity<Long>> storageRentDrip() {
        return this.storageRentService.dripStorageRent().map(ResponseEntity::ok);
    }
}
