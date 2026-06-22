package com.fincity.security.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.security.model.wallet.WalletChargeRequest;
import com.fincity.saas.commons.security.model.wallet.WalletChargeResult;
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dao.wallet.WalletDAO;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.dto.wallet.Wallet;
import com.fincity.security.dto.wallet.WalletTransaction;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceType;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityWalletTransactionReferenceType;
import com.fincity.security.jooq.tables.records.SecurityWalletRecord;
import com.fincity.security.dto.App;
import com.fincity.saas.commons.security.model.wallet.RentTarget;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.ReservationResult;
import com.fincity.security.model.billing.TopUpRequest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.billing.InvoiceService;
import com.fincity.security.service.plansnbilling.PaymentService;
import com.fincity.security.service.wallet.UsageConsolidationService;
import com.fincity.security.service.wallet.WalletService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/wallets")
public class WalletController
        extends AbstractJOOQUpdatableDataController<SecurityWalletRecord, ULong, Wallet, WalletDAO, WalletService> {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AppBillingConfigDAO appBillingConfigDAO;
    private final AppActionCostDAO appActionCostDAO;
    private final AppService appService;
    private final ClientService clientService;
    private final UsageConsolidationService consolidationService;

    public WalletController(WalletService service, InvoiceService invoiceService, PaymentService paymentService,
            AppBillingConfigDAO appBillingConfigDAO, AppActionCostDAO appActionCostDAO, AppService appService,
            ClientService clientService, UsageConsolidationService consolidationService) {
        this.service = service;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.appBillingConfigDAO = appBillingConfigDAO;
        this.appActionCostDAO = appActionCostDAO;
        this.appService = appService;
        this.clientService = clientService;
        this.consolidationService = consolidationService;
    }

    @GetMapping("/balance")
    public Mono<ResponseEntity<Wallet>> getBalance(@RequestParam ULong clientId) {
        return this.service.getBalance(clientId).map(ResponseEntity::ok);
    }

    @GetMapping("/transactions")
    public Mono<ResponseEntity<List<WalletTransaction>>> getTransactions(@RequestParam ULong clientId,
            @RequestParam(defaultValue = "50") int limit) {
        return this.service.getRecentTransactions(clientId, limit).map(ResponseEntity::ok);
    }

    @PostMapping("/{walletId}/adjust")
    public Mono<ResponseEntity<Wallet>> adjust(@PathVariable ULong walletId, @RequestParam BigDecimal credits,
            @RequestParam(defaultValue = "true") boolean increase, @RequestParam(required = false) String reason) {
        return this.service.adjust(walletId, credits, increase, reason).map(ResponseEntity::ok);
    }

    @PostMapping("/topup/initialize")
    public Mono<ResponseEntity<Payment>> initializeTopUp(@RequestBody TopUpRequest req) {
        ULong gatewayClientId = req.getUrlClientId() != null ? req.getUrlClientId() : req.getClientId();
        return this.resolveGateway(req, gatewayClientId)
                .flatMap(gateway -> this.service.getOrCreateWallet(req.getClientId())
                        .flatMap(wallet -> this.invoiceService.generateTopUpInvoice(req.getClientId(), wallet.getId(),
                                SecurityInvoiceInvoiceType.TOPUP, req.getAmount(), req.getCredits(), req.getCurrency(),
                                req.getTaxAmount()))
                        .flatMap(invoice -> this.paymentService.initializePayment(invoice.getId(), gatewayClientId,
                                gateway, req.getMetadata())))
                .map(ResponseEntity::ok);
    }

    /** Use the requested gateway, else the (app, exposing-client) config default. */
    private Mono<SecurityPaymentGatewayPaymentGateway> resolveGateway(TopUpRequest req, ULong gatewayClientId) {
        if (req.getGateway() != null)
            return Mono.just(req.getGateway());
        return this.appBillingConfigDAO.findByAppIdAndClientId(req.getAppId(), gatewayClientId)
                .map(cfg -> SecurityPaymentGatewayPaymentGateway.valueOf(cfg.getDefaultPaymentGateway().name()))
                .switchIfEmpty(Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                        "No payment gateway specified and no billing config default for the app and client")));
    }

    // ---- internal (service-to-service / nocode-ai) metering endpoints ----

    @PostMapping("/internal/charge")
    public Mono<ResponseEntity<ChargeResult>> charge(@RequestBody ChargeRequest req) {
        return this.service.charge(req).map(this::toChargeResponse);
    }

    @PostMapping("/internal/reserve")
    public Mono<ResponseEntity<ReservationResult>> reserve(@RequestBody ChargeRequest req) {
        return this.service.reserve(req).map(this::toReserveResponse);
    }

    /** Code-based charge for context-driven callers (resolves codes -> ids server-side). */
    @PostMapping("/internal/charge-by-code")
    public Mono<ResponseEntity<WalletChargeResult>> chargeByCode(@RequestBody WalletChargeRequest req) {
        return this.resolveByCode(req).flatMap(this.service::charge).map(this::toWireResponse);
    }

    @PostMapping("/internal/reserve-by-code")
    public Mono<ResponseEntity<ReservationResult>> reserveByCode(@RequestBody WalletChargeRequest req) {
        return this.resolveByCode(req).flatMap(this.service::reserve).map(this::toReserveResponse);
    }

    /**
     * Resolve a code-based request to ids: clientCode -> consumer (wallet),
     * urlClientCode -> exposing client (config/rates), appCode -> app. A missing
     * urlClientCode leaves urlClientId null, which charge treats as not-enforced.
     */
    private Mono<ChargeRequest> resolveByCode(WalletChargeRequest r) {
        Mono<ULong> appIdM = this.appService.getAppByCode(r.getAppCode()).map(App::getId);
        Mono<ULong> clientIdM = this.clientService.getClientId(r.getClientCode());
        Mono<Optional<ULong>> urlClientIdM = (r.getUrlClientCode() == null || r.getUrlClientCode().isBlank())
                ? Mono.just(Optional.empty())
                : this.clientService.getClientId(r.getUrlClientCode()).map(Optional::of).defaultIfEmpty(Optional.empty());

        return Mono.zip(appIdM, clientIdM, urlClientIdM).map(t -> new ChargeRequest()
                .setAppId(t.getT1())
                .setClientId(t.getT2())
                .setUrlClientId(t.getT3().orElse(null))
                .setActionKey(r.getActionKey())
                .setQuantity(r.getQuantity())
                .setIdempotencyKey(r.getIdempotencyKey())
                .setReferenceType(r.getReferenceType() == null ? null
                        : SecurityWalletTransactionReferenceType.valueOf(r.getReferenceType()))
                .setReferenceId(r.getReferenceId())
                .setShadow(r.isShadow()));
    }

    @PostMapping("/internal/settle")
    public Mono<ResponseEntity<ChargeResult>> settle(@RequestParam ULong reservationId,
            @RequestParam BigDecimal actualCredits, @RequestParam(required = false) String idempotencyKey) {
        return this.service.settle(reservationId, actualCredits, idempotencyKey).map(this::toChargeResponse);
    }

    @PostMapping("/internal/release")
    public Mono<ResponseEntity<ChargeResult>> release(@RequestParam ULong reservationId,
            @RequestParam(required = false) String idempotencyKey) {
        return this.service.release(reservationId, idempotencyKey).map(this::toChargeResponse);
    }

    @GetMapping("/internal/balance")
    public Mono<ResponseEntity<Wallet>> internalBalance(@RequestParam ULong clientId) {
        return this.service.getBalance(clientId).map(ResponseEntity::ok);
    }

    /** Triggered by the worker on a 15-minute schedule; returns rows consolidated. */
    @PostMapping("/internal/consolidate-usage")
    public Mono<ResponseEntity<Integer>> consolidateUsage() {
        return this.consolidationService.consolidate().map(ResponseEntity::ok);
    }

    /**
     * Enforced billing configs that carry a cost for {@code actionKey} (e.g.
     * core.storage.row), with app code + owner. Core enumerates each owner's
     * direct managed clients and counts their usage before calling charge-rent.
     */
    @GetMapping("/internal/billing/rent-targets")
    public Mono<ResponseEntity<List<RentTarget>>> rentTargets(@RequestParam String actionKey) {
        return this.appActionCostDAO.findConfigsWithActionCost(actionKey).map(ResponseEntity::ok);
    }

    /**
     * Drip one hour of rent for {@code count} units of {@code actionKey} onto the
     * consumer's wallet at the owner's configured monthly rate
     * ({@code monthlyRate / hoursInMonth * count}). Reuses the consolidation
     * debit (unconditional, suspend-at-floor, idempotent per (wallet, hour)).
     */
    @PostMapping("/internal/billing/charge-rent")
    public Mono<ResponseEntity<WalletChargeResult>> chargeRent(@RequestParam String appCode,
            @RequestParam String clientCode, @RequestParam String actionKey, @RequestParam BigDecimal count) {

        long hourBucket = Instant.now().getEpochSecond() / 3600;
        long hoursInMonth = (long) YearMonth.now().lengthOfMonth() * 24;
        BigDecimal quantity = count.divide(BigDecimal.valueOf(hoursInMonth), 10, RoundingMode.HALF_UP);
        String idem = "rent:" + actionKey + ":" + appCode + ":" + clientCode + ":" + hourBucket;

        return Mono.zip(this.appService.getAppByCode(appCode), this.clientService.getClientId(clientCode))
                .flatMap(t -> this.service.consolidatedDebit(t.getT2(), t.getT1().getClientId(),
                        t.getT1().getId(), actionKey, quantity, idem))
                .map(this::toWireResponse);
    }

    /**
     * Creation gate (read-only): may the app OWNER create right now? Resolves
     * codes -> ids and checks the owner's wallet is not suspended for an enforced
     * app. A missing urlClientCode leaves the config not-enforced (allowed).
     */
    @GetMapping("/internal/creation-allowed")
    public Mono<ResponseEntity<Boolean>> creationAllowed(@RequestParam String ownerClientCode,
            @RequestParam String appCode, @RequestParam(required = false) String urlClientCode) {
        Mono<ULong> appIdM = this.appService.getAppByCode(appCode).map(App::getId);
        Mono<ULong> ownerM = this.clientService.getClientId(ownerClientCode);
        Mono<Optional<ULong>> urlM = (urlClientCode == null || urlClientCode.isBlank())
                ? Mono.just(Optional.empty())
                : this.clientService.getClientId(urlClientCode).map(Optional::of).defaultIfEmpty(Optional.empty());
        return Mono.zip(appIdM, ownerM, urlM)
                .flatMap(t -> this.service.isCreationAllowed(t.getT2(), t.getT1(), t.getT3().orElse(null)))
                .map(ResponseEntity::ok);
    }

    private ResponseEntity<ChargeResult> toChargeResponse(ChargeResult r) {
        return r.allowed()
                ? ResponseEntity.ok(r)
                : ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(r);
    }

    private ResponseEntity<ReservationResult> toReserveResponse(ReservationResult r) {
        return r.allowed()
                ? ResponseEntity.ok(r)
                : ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(r);
    }

    private ResponseEntity<WalletChargeResult> toWireResponse(ChargeResult r) {
        WalletChargeResult wire = new WalletChargeResult()
                .setAllowed(r.allowed())
                .setOutcome(r.outcome().name())
                .setCreditsCharged(r.creditsCharged())
                .setBalanceAfter(r.balanceAfter());
        return r.allowed()
                ? ResponseEntity.ok(wire)
                : ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(wire);
    }
}
