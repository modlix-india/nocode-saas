package com.fincity.security.controller.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.billing.BillingProfile;
import com.fincity.security.service.billing.BillingProfileService;

import reactor.core.publisher.Mono;

/**
 * The authenticated buyer's own billing profile for the app in the appCode header.
 * Read to pre-fill the order-summary form; upserted when they save. Requires
 * ROLE_Owner, and scoping is from the security context (own client only), both
 * enforced in {@link BillingProfileService}.
 */
@RestController
@RequestMapping("api/security/billing-profile")
public class BillingProfileController {

    private final BillingProfileService service;

    public BillingProfileController(BillingProfileService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<BillingProfile>> get(ServerHttpRequest request) {
        // Always return a 200 with an object so the order-summary form has something
        // to bind to. A first-time buyer (no saved profile) gets an EMPTY BillingProfile
        // to fill in - never a 204, whose empty body the UI FetchData turns into "" and
        // then crashes on Page.billingProfile.legalName (string index access).
        return this.service.getMyProfile(request.getHeaders().getFirst("appCode"))
                .defaultIfEmpty(new BillingProfile())
                .map(ResponseEntity::ok);
    }

    @PutMapping
    public Mono<ResponseEntity<BillingProfile>> save(@RequestBody BillingProfile profile, ServerHttpRequest request) {
        return this.service.saveMyProfile(request.getHeaders().getFirst("appCode"), profile)
                .map(ResponseEntity::ok);
    }
}
