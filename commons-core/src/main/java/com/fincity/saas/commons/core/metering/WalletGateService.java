package com.fincity.saas.commons.core.metering;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.wallet.WalletChargeRequest;
import com.fincity.saas.commons.security.model.wallet.WalletChargeResult;

import reactor.core.publisher.Mono;

/**
 * Synchronous metering gate. Calls the security service's code-based charge
 * endpoint; the server resolves the consumer wallet and the exposing client's
 * config/rates and decides the outcome.
 *
 * <p>Fail-open: on transport error or timeout the action is allowed, so a wallet
 * or security outage never breaks platform actions. Only an explicit BLOCKED
 * result (insufficient balance for a METERED action) stops the action.
 */
@Service
public class WalletGateService {

    private static final Logger logger = LoggerFactory.getLogger(WalletGateService.class);

    private final IFeignSecurityService securityService;

    public WalletGateService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    /** Returns true if the action may proceed (charged, grace, shadow, not-enforced, or fail-open). */
    public Mono<Boolean> charge(WalletChargeRequest request) {
        return this.securityService.walletChargeByCode(request)
                .map(WalletChargeResult::isAllowed)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    logger.warn("Wallet charge gate failed open for action {} (app {}): {}",
                            request.getActionKey(), request.getAppCode(), e.toString());
                    return Mono.just(Boolean.TRUE);
                });
    }
}
