package com.fincity.security.controller.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.service.billing.WalletService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/balance")
    public Mono<ResponseEntity<BigDecimal>> balance(@RequestParam ULong clientId, @RequestParam ULong appId) {
        return this.walletService.getBalance(clientId, appId).map(ResponseEntity::ok);
    }

    @PostMapping("/{walletId}/adjust")
    public Mono<ResponseEntity<ChargeResult>> adjust(@PathVariable ULong walletId,
            @RequestParam BigDecimal tokens, @RequestParam String reason) {
        return this.walletService.adjust(walletId, tokens, reason).map(ResponseEntity::ok);
    }
}
