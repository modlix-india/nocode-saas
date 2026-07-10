package com.fincity.security.controller.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.billing.Wallet;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.WalletStatusResponse;
import com.fincity.security.service.billing.WalletService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * The whole wallet for the app + client in context (appCode/clientCode
     * headers). 204 when that (client, app) has no wallet yet. Owner / payment
     * manager only.
     */
    @GetMapping
    public Mono<ResponseEntity<Wallet>> wallet(ServerHttpRequest request) {
        String appCode = request.getHeaders().getFirst("appCode");
        String clientCode = request.getHeaders().getFirst("clientCode");
        return this.walletService.getWalletByCodes(appCode, clientCode)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    /**
     * Derived status (ACTIVE / LOW / SUSPENDED) for the app + client in context
     * (appCode/clientCode headers). No wallet -> ACTIVE.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<WalletStatusResponse>> status(ServerHttpRequest request) {
        String appCode = request.getHeaders().getFirst("appCode");
        String clientCode = request.getHeaders().getFirst("clientCode");
        return this.walletService.getDisplayStatus(appCode, clientCode).map(ResponseEntity::ok);
    }

    @PostMapping("/{walletId}/adjust")
    public Mono<ResponseEntity<ChargeResult>> adjust(@PathVariable ULong walletId,
            @RequestParam BigDecimal tokens, @RequestParam String reason) {
        return this.walletService.adjust(walletId, tokens, reason).map(ResponseEntity::ok);
    }
}
