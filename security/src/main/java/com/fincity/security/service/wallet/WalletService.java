package com.fincity.security.service.wallet;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dao.wallet.WalletDAO;
import com.fincity.security.dao.wallet.WalletTransactionDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.wallet.Wallet;
import com.fincity.security.dto.wallet.WalletTransaction;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.jooq.enums.SecurityWalletTransactionReferenceType;
import com.fincity.security.jooq.enums.SecurityWalletTransactionTransactionType;
import com.fincity.security.jooq.tables.records.SecurityWalletRecord;
import com.fincity.security.model.billing.ChargeOutcome;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.ActionClass;
import com.fincity.security.model.billing.ReservationResult;
import com.fincity.security.model.billing.ResolvedCost;
import com.fincity.security.service.AbstractSecurityUpdatableDataService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Prepaid token wallet operations: provisioning, balance, and the
 * charge / reserve / settle / release / top-up / grant / adjust / seat-burn
 * primitives. Concurrency safety comes from {@link WalletDAO}'s conditional
 * atomic updates; idempotency from the ledger's unique key.
 */
@Service
public class WalletService
        extends AbstractSecurityUpdatableDataService<SecurityWalletRecord, ULong, Wallet, WalletDAO> {

    private final WalletTransactionDAO walletTransactionDAO;
    private final LedgerService ledgerService;
    private final PricingService pricingService;
    private final AppBillingConfigDAO appBillingConfigDAO;

    public WalletService(WalletDAO dao, WalletTransactionDAO walletTransactionDAO,
            LedgerService ledgerService, PricingService pricingService, AppBillingConfigDAO appBillingConfigDAO) {
        this.dao = dao;
        this.walletTransactionDAO = walletTransactionDAO;
        this.ledgerService = ledgerService;
        this.pricingService = pricingService;
        this.appBillingConfigDAO = appBillingConfigDAO;
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.WALLET;
    }

    @Override
    protected ULong resolveClientId(Wallet entity) {
        return entity.getClientId();
    }

    @Override
    protected String describeEntity(Wallet entity) {
        return entity == null || entity.getClientId() == null ? null : "client " + entity.getClientId();
    }

    /** The client's parent wallet, provisioning an empty one on first use. */
    public Mono<Wallet> getOrCreateWallet(ULong clientId) {
        return this.getOrCreateWallet(clientId, null);
    }

    /** Read-only resolved wallet status for (clientId, appId); ACTIVE if no wallet yet. */
    public Mono<SecurityWalletStatus> resolveStatus(ULong clientId, ULong appId) {
        return this.dao.resolveWallet(clientId, appId)
                .map(Wallet::getStatus)
                .defaultIfEmpty(SecurityWalletStatus.ACTIVE);
    }

    /**
     * Resolve the wallet that governs (clientId, appId): the app sub-wallet if
     * funded, else the parent. Only the parent is auto-provisioned; sub-wallets
     * are created deliberately when an app's tokens are ring-fenced.
     */
    public Mono<Wallet> getOrCreateWallet(ULong clientId, ULong appId) {
        return this.dao.resolveWallet(clientId, appId)
                .switchIfEmpty(Mono.defer(() -> this.dao.create(new Wallet()
                        .setClientId(clientId)
                        .setBalance(BigDecimal.ZERO)
                        .setReservedBalance(BigDecimal.ZERO)
                        .setCurrency("INR")
                        .setStatus(SecurityWalletStatus.ACTIVE)
                        .setGraceFloor(BigDecimal.ZERO)
                        .setVersion(ULong.valueOf(0L)))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.getOrCreateWallet"));
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_READ')")
    public Mono<Wallet> getBalance(ULong clientId) {
        return this.getOrCreateWallet(clientId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.getBalance"));
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_READ')")
    public Mono<List<WalletTransaction>> getRecentTransactions(ULong clientId, int limit) {
        return this.getOrCreateWallet(clientId)
                .flatMap(wallet -> this.walletTransactionDAO.findRecentByWallet(wallet.getId(), limit))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.getRecentTransactions"));
    }

    /**
     * Charge (debit) the consumer's wallet for a metered action. Billing is
     * resolved by the exposing client's (app, urlClient) config: no config means
     * no enforcement; a config that is not yet enforced runs in shadow.
     */
    public Mono<ChargeResult> charge(ChargeRequest req) {
        return this.appBillingConfigDAO.findByAppIdAndClientId(req.getAppId(), req.getUrlClientId())
                .flatMap(config -> this.chargeWithConfig(req, config))
                .switchIfEmpty(Mono.just(ChargeResult.notEnforced()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.charge"));
    }

    private Mono<ChargeResult> chargeWithConfig(ChargeRequest req, AppBillingConfig config) {
        boolean shadow = req.isShadow() || !config.isEnforced();
        return this.getOrCreateWallet(req.getClientId(), req.getAppId())
                .flatMap(wallet -> replayOr(wallet.getId(), req.getIdempotencyKey(),
                        () -> pricingService
                                .resolveCost(config.getId(), req.getActionKey(), req.getQuantity())
                                .flatMap(rc -> applyCharge(wallet, req, rc, shadow))));
    }

    private Mono<ChargeResult> applyCharge(Wallet wallet, ChargeRequest req, ResolvedCost rc, boolean shadow) {

        ULong walletId = wallet.getId();
        BigDecimal credits = rc.credits();

        if (shadow)
            return ledger(walletId, SecurityWalletTransactionTransactionType.DEBIT, credits, wallet,
                    req.getActionKey(), req.getAppId(), req.getQuantity(),
                    refType(req, SecurityWalletTransactionReferenceType.ACTION), req.getReferenceId(),
                    req.getIdempotencyKey(), true)
                    .map(t -> ChargeResult.shadow(credits, wallet.getBalance(), t.getId()));

        if (credits.signum() == 0)
            return Mono.just(ChargeResult.charged(BigDecimal.ZERO, wallet.getBalance(), null));

        Mono<Integer> debit = rc.actionClass() == ActionClass.ENGAGEMENT
                ? this.dao.atomicDebitAllowNegative(walletId, credits)
                : this.dao.atomicDebit(walletId, credits);

        return debit.flatMap(rows -> {
            if (rows == 0)
                return this.dao.readById(walletId)
                        .map(w -> ChargeResult.blocked(ChargeOutcome.BLOCKED_INSUFFICIENT, w.getBalance()));

            return this.dao.readById(walletId).flatMap(after -> ledger(walletId,
                    SecurityWalletTransactionTransactionType.DEBIT, credits, after,
                    req.getActionKey(), req.getAppId(), req.getQuantity(),
                    refType(req, SecurityWalletTransactionReferenceType.ACTION), req.getReferenceId(),
                    req.getIdempotencyKey(), false)
                    .map(t -> after.getBalance().signum() < 0
                            ? ChargeResult.grace(credits, after.getBalance(), t.getId())
                            : ChargeResult.charged(credits, after.getBalance(), t.getId())));
        });
    }

    /** Reserve (pre-authorize) credits for an expensive action. */
    public Mono<ReservationResult> reserve(ChargeRequest req) {
        return this.appBillingConfigDAO.findByAppIdAndClientId(req.getAppId(), req.getUrlClientId())
                .flatMap(config -> this.reserveWithConfig(req, config))
                // No config (or shadow): allow the action without holding credits.
                .switchIfEmpty(Mono.just(ReservationResult.reserved(null, BigDecimal.ZERO, null)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.reserve"));
    }

    private Mono<ReservationResult> reserveWithConfig(ChargeRequest req, AppBillingConfig config) {

        if (req.isShadow() || !config.isEnforced())
            return Mono.just(ReservationResult.reserved(null, BigDecimal.ZERO, null));

        return this.getOrCreateWallet(req.getClientId(), req.getAppId())
                .flatMap(wallet -> pricingService
                        .resolveCost(config.getId(), req.getActionKey(), req.getQuantity())
                        .flatMap(rc -> {
                            BigDecimal credits = rc.credits();
                            if (credits.signum() == 0)
                                return Mono.just(ReservationResult.reserved(null, BigDecimal.ZERO, wallet.getBalance()));

                            return this.dao.atomicReserve(wallet.getId(), credits).flatMap(rows -> {
                                if (rows == 0)
                                    return this.dao.readById(wallet.getId())
                                            .map(w -> ReservationResult.blocked(
                                                    ChargeOutcome.BLOCKED_INSUFFICIENT, w.getBalance()));

                                return this.dao.readById(wallet.getId()).flatMap(after -> ledger(wallet.getId(),
                                        SecurityWalletTransactionTransactionType.RESERVE, credits, after,
                                        req.getActionKey(), req.getAppId(), req.getQuantity(),
                                        SecurityWalletTransactionReferenceType.RESERVATION, req.getReferenceId(),
                                        req.getIdempotencyKey(), false)
                                        .map(t -> ReservationResult.reserved(t.getId(), credits, after.getBalance())));
                            });
                        }));
    }

    /** Settle a reservation, consuming the actual credits and refunding the remainder. */
    public Mono<ChargeResult> settle(ULong reservationId, BigDecimal actualCredits, String idempotencyKey) {
        return this.walletTransactionDAO.readById(reservationId)
                .flatMap(reservation -> {
                    ULong walletId = reservation.getWalletId();
                    BigDecimal reserved = reservation.getCredits();
                    return replayOr(walletId, idempotencyKey, () -> this.dao
                            .atomicSettle(walletId, reserved, actualCredits)
                            .then(this.dao.readById(walletId))
                            .flatMap(after -> ledger(walletId, SecurityWalletTransactionTransactionType.DEBIT,
                                    actualCredits, after, reservation.getActionKey(), reservation.getAppId(), null,
                                    SecurityWalletTransactionReferenceType.RESERVATION, reservationId.toString(),
                                    idempotencyKey, false)
                                    .map(t -> ChargeResult.charged(actualCredits, after.getBalance(), t.getId()))));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.settle"));
    }

    /** Release a reservation in full (e.g. the expensive action failed). */
    public Mono<ChargeResult> release(ULong reservationId, String idempotencyKey) {
        return this.walletTransactionDAO.readById(reservationId)
                .flatMap(reservation -> {
                    ULong walletId = reservation.getWalletId();
                    BigDecimal reserved = reservation.getCredits();
                    return replayOr(walletId, idempotencyKey, () -> this.dao.atomicRelease(walletId, reserved)
                            .then(this.dao.readById(walletId))
                            .flatMap(after -> ledger(walletId, SecurityWalletTransactionTransactionType.RELEASE,
                                    reserved, after, reservation.getActionKey(), reservation.getAppId(), null,
                                    SecurityWalletTransactionReferenceType.RESERVATION, reservationId.toString(),
                                    idempotencyKey, false)
                                    .map(t -> ChargeResult.charged(reserved, after.getBalance(), t.getId()))));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.release"));
    }

    /** Credit the wallet for a paid token purchase (called on payment success). */
    public Mono<Wallet> topUp(ULong walletId, BigDecimal credits, String idempotencyKey,
            SecurityWalletTransactionReferenceType refType, String refId) {
        return creditWith(walletId, credits, idempotencyKey,
                SecurityWalletTransactionTransactionType.TOPUP, refType, refId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.topUp"));
    }

    /** Grant free credits (monthly allotment / promo). */
    public Mono<Wallet> grant(ULong walletId, BigDecimal credits, String idempotencyKey) {
        return creditWith(walletId, credits, idempotencyKey,
                SecurityWalletTransactionTransactionType.GRANT, SecurityWalletTransactionReferenceType.GRANT, null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.grant"));
    }

    /** Hourly seat/platform token burn (token-denominated seat fee). */
    public Mono<ChargeResult> burnSeatFee(ULong clientId, ULong appId, BigDecimal credits, String idempotencyKey) {
        return this.getOrCreateWallet(clientId)
                .flatMap(wallet -> replayOr(wallet.getId(), idempotencyKey, () -> {
                    if (credits.signum() == 0)
                        return Mono.just(ChargeResult.charged(BigDecimal.ZERO, wallet.getBalance(), null));
                    return this.dao.atomicDebit(wallet.getId(), credits).flatMap(rows -> {
                        if (rows == 0)
                            return this.dao.readById(wallet.getId())
                                    .map(w -> ChargeResult.blocked(ChargeOutcome.BLOCKED_INSUFFICIENT, w.getBalance()));
                        return this.dao.readById(wallet.getId()).flatMap(after -> ledger(wallet.getId(),
                                SecurityWalletTransactionTransactionType.SEAT_BURN, credits, after,
                                "platform.seat", appId, null,
                                SecurityWalletTransactionReferenceType.SEAT_BURN, null, idempotencyKey, false)
                                .map(t -> ChargeResult.charged(credits, after.getBalance(), t.getId())));
                    });
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.burnSeatFee"));
    }

    /**
     * Read-only creation gate. May the app owner create right now? No config for
     * (app, urlClient) or a config not yet enforced means yes; otherwise the
     * owner's resolved wallet (sub -> parent) must not be SUSPENDED. A client
     * with no wallet yet is allowed (nothing has been billed). Keys on the OWNER,
     * never the acting/delegate user.
     */
    public Mono<Boolean> isCreationAllowed(ULong ownerClientId, ULong appId, ULong urlClientId) {
        return this.appBillingConfigDAO.findByAppIdAndClientId(appId, urlClientId)
                .flatMap(cfg -> !cfg.isEnforced()
                        ? Mono.just(Boolean.TRUE)
                        : this.dao.resolveWallet(ownerClientId, appId)
                                .map(w -> w.getStatus() != SecurityWalletStatus.SUSPENDED)
                                .defaultIfEmpty(Boolean.TRUE))
                .switchIfEmpty(Mono.just(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.isCreationAllowed"));
    }

    /**
     * Apply one aggregated debit from the 15-minute consolidation pass for a
     * group (clientId, appId, actionKey) over a closed window. Resolves the
     * wallet (sub -> parent), prices the summed quantity, debits
     * unconditionally (allow-negative; the prepaid model permits one
     * overshoot), records the ledger row idempotently per window, and flips the
     * wallet to SUSPENDED once the balance crosses the grace floor. No config
     * for (app, urlClient) means no enforcement; config present but not enforced
     * records a shadow row without moving the balance.
     */
    public Mono<ChargeResult> consolidatedDebit(ULong clientId, ULong urlClientId, ULong appId,
            String actionKey, BigDecimal quantity, String idempotencyKey) {
        return this.appBillingConfigDAO.findByAppIdAndClientId(appId, urlClientId)
                .flatMap(config -> this.getOrCreateWallet(clientId, appId)
                        .flatMap(wallet -> replayOr(wallet.getId(), idempotencyKey,
                                () -> pricingService.resolveCost(config.getId(), actionKey, quantity)
                                        .flatMap(rc -> applyConsolidatedDebit(wallet, appId, actionKey, quantity,
                                                rc, idempotencyKey, !config.isEnforced())))))
                .switchIfEmpty(Mono.just(ChargeResult.notEnforced()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.consolidatedDebit"));
    }

    private Mono<ChargeResult> applyConsolidatedDebit(Wallet wallet, ULong appId, String actionKey,
            BigDecimal quantity, ResolvedCost rc, String idempotencyKey, boolean shadow) {

        ULong walletId = wallet.getId();
        BigDecimal credits = rc.credits();

        if (shadow)
            return ledger(walletId, SecurityWalletTransactionTransactionType.DEBIT, credits, wallet,
                    actionKey, appId, quantity, SecurityWalletTransactionReferenceType.ACTION, null,
                    idempotencyKey, true)
                    .map(t -> ChargeResult.shadow(credits, wallet.getBalance(), t.getId()));

        if (credits.signum() == 0)
            return Mono.just(ChargeResult.charged(BigDecimal.ZERO, wallet.getBalance(), null));

        return this.dao.atomicDebitUnconditional(walletId, credits)
                .then(this.dao.readById(walletId))
                .flatMap(after -> ledger(walletId, SecurityWalletTransactionTransactionType.DEBIT, credits, after,
                        actionKey, appId, quantity, SecurityWalletTransactionReferenceType.ACTION, null,
                        idempotencyKey, false)
                        .flatMap(t -> maybeSuspend(after)
                                .thenReturn(ChargeResult.charged(credits, after.getBalance(), t.getId()))));
    }

    /** Flip an ACTIVE wallet to SUSPENDED once its balance has crossed the grace floor. */
    private Mono<Void> maybeSuspend(Wallet after) {
        if (after.getStatus() == SecurityWalletStatus.ACTIVE
                && after.getBalance().compareTo(after.getGraceFloor()) < 0)
            return this.dao.setStatus(after.getId(), SecurityWalletStatus.SUSPENDED).then();
        return Mono.empty();
    }

    /** Admin manual correction (increase or decrease). */
    @PreAuthorize("hasAuthority('Authorities.Payment_UPDATE')")
    public Mono<Wallet> adjust(ULong walletId, BigDecimal credits, boolean increase, String reason) {
        Mono<Integer> op = increase
                ? this.dao.atomicCredit(walletId, credits)
                : this.dao.atomicDebitAllowNegative(walletId, credits);
        return op.then(this.dao.readById(walletId))
                .flatMap(after -> ledger(walletId, SecurityWalletTransactionTransactionType.ADJUSTMENT,
                        credits, after, null, null, null,
                        SecurityWalletTransactionReferenceType.ADJUSTMENT, reason, null, false)
                        .thenReturn(after))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.adjust"));
    }

    // ---- helpers ----------------------------------------------------------

    private Mono<Wallet> creditWith(ULong walletId, BigDecimal credits, String idempotencyKey,
            SecurityWalletTransactionTransactionType type, SecurityWalletTransactionReferenceType refType,
            String refId) {

        Mono<Wallet> apply = this.dao.atomicCredit(walletId, credits)
                .then(this.dao.readById(walletId))
                .flatMap(after -> ledger(walletId, type, credits, after, null, null, null, refType, refId,
                        idempotencyKey, false).thenReturn(after));

        if (idempotencyKey == null)
            return apply;

        return this.walletTransactionDAO.findByIdempotencyKey(walletId, idempotencyKey)
                .flatMap(existing -> this.dao.readById(walletId))
                .switchIfEmpty(apply);
    }

    private Mono<ChargeResult> replayOr(ULong walletId, String key, Supplier<Mono<ChargeResult>> fn) {
        if (key == null)
            return fn.get();
        return this.walletTransactionDAO.findByIdempotencyKey(walletId, key)
                .map(existing -> ChargeResult.replay(existing.getCredits(), existing.getBalanceAfter(), existing.getId()))
                .switchIfEmpty(Mono.defer(fn));
    }

    private SecurityWalletTransactionReferenceType refType(ChargeRequest req,
            SecurityWalletTransactionReferenceType fallback) {
        return req.getReferenceType() != null ? req.getReferenceType() : fallback;
    }

    private Mono<WalletTransaction> ledger(ULong walletId, SecurityWalletTransactionTransactionType type,
            BigDecimal credits, Wallet after, String actionKey, ULong appId, BigDecimal quantity,
            SecurityWalletTransactionReferenceType refType, String refId, String idempotencyKey, boolean shadow) {

        return this.ledgerService.record(new WalletTransaction()
                .setWalletId(walletId)
                .setTransactionType(type)
                .setCredits(credits.abs())
                .setBalanceAfter(after.getBalance())
                .setReservedAfter(after.getReservedBalance())
                .setActionKey(actionKey)
                .setAppId(appId)
                .setQuantity(quantity)
                .setReferenceType(refType)
                .setReferenceId(refId)
                .setIdempotencyKey(idempotencyKey)
                .setShadow(shadow));
    }
}
