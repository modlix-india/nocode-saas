package com.fincity.security.controller.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.billing.Wallet;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.WalletCreditRequest;
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
     * Derived status (ACTIVE / LOW / SUSPENDED) of the AUTHENTICATED caller's own
     * wallet. Only the appCode header is used, purely to pick which app; appCode is
     * globally unique so no client identifier is needed. The wallet's owner client
     * is always taken from the security context, never a header, so a caller can
     * only ever read their own status. No wallet -> ACTIVE.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<WalletStatusResponse>> status(ServerHttpRequest request) {
        String urlAppCode = request.getHeaders().getFirst("appCode");
        return this.walletService.getDisplayStatus(urlAppCode).map(ResponseEntity::ok);
    }

    @PostMapping("/{walletId}/adjust")
    public Mono<ResponseEntity<ChargeResult>> adjust(@PathVariable ULong walletId,
            @RequestParam BigDecimal tokens, @RequestParam String reason) {
        return this.walletService.adjust(walletId, tokens, reason).map(ResponseEntity::ok);
    }

    /**
     * Admin grant: add tokens to the (clientCode, appCode) wallet, creating it if
     * absent, under the urlClientCode tenant context. Owner / payment manager only;
     * the caller must be able to see the tenant + target client and write the app.
     */
    @PostMapping("/credit")
    public Mono<ResponseEntity<ChargeResult>> credit(@RequestBody WalletCreditRequest request) {
        return this.walletService.creditTokens(request.appCode(), request.urlClientCode(),
                request.clientCode(), request.tokens(), request.reason()).map(ResponseEntity::ok);
    }

    /**
     * Balance preview for the (clientCode, appCode) wallet under the urlClientCode
     * context, before adding tokens. 204 when that wallet does not exist yet. Same
     * authz as {@link #credit}.
     */
    @GetMapping("/lookup")
    public Mono<ResponseEntity<Wallet>> lookup(@RequestParam String appCode,
            @RequestParam String urlClientCode, @RequestParam String clientCode) {
        return this.walletService.lookupWallet(appCode, urlClientCode, clientCode)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }
}
