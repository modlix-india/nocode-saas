package com.fincity.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.service.billing.RentDripService;

import reactor.core.publisher.Mono;

/**
 * Internal billing trigger for the worker. Seat/app/site rent is counted from
 * security's own tables, so it runs entirely here. Path is under
 * /api/security/wallets/internal/** which is already permitted.
 */
@RestController
@RequestMapping("api/security/wallets/internal/billing")
public class BillingRentController {

    private final RentDripService rentDripService;

    public BillingRentController(RentDripService rentDripService) {
        this.rentDripService = rentDripService;
    }

    @PostMapping("/internal-rent-drip")
    public Mono<ResponseEntity<Long>> internalRentDrip() {
        return this.rentDripService.dripInternalRent().map(ResponseEntity::ok);
    }
}
