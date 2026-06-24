package com.fincity.security.controller.billing;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.billing.AiChargeRequest;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.WalletStatusView;
import com.fincity.security.service.billing.WalletService;

import reactor.core.publisher.Mono;

/**
 * Cluster-only billing endpoints (nginx blocks public {@code /internal/**}).
 * Called by the worker-triggered metering services and by nocode-ai.
 */
@RestController
@RequestMapping("api/security/internal/billing")
public class InternalBillingController {

    private final WalletService walletService;

    public InternalBillingController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** Bulk 15-minute metered charges. The service prices each from its config. */
    @PostMapping("/charge")
    public Mono<ResponseEntity<Boolean>> charge(@RequestBody List<ChargeRequest> requests) {
        return this.walletService.chargeBulk(requests).thenReturn(ResponseEntity.ok(Boolean.TRUE));
    }

    /** Immediate AI charge from nocode-ai. */
    @PostMapping("/charge-ai")
    public Mono<ResponseEntity<ChargeResult>> chargeAi(@RequestBody AiChargeRequest request) {
        return this.walletService.chargeAi(request).map(ResponseEntity::ok);
    }

    /** Serving status used by the action gates to decide ACTIVE/SUSPENDED. */
    @GetMapping("/serving-status")
    public Mono<ResponseEntity<WalletStatusView>> servingStatus(@RequestParam String urlAppCode,
            @RequestParam String urlClientCode, @RequestParam ULong clientId) {
        return this.walletService.getServingStatus(urlAppCode, urlClientCode, clientId).map(ResponseEntity::ok);
    }
}
