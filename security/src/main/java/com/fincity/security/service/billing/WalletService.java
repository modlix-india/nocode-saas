package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.dao.billing.WalletTransactionDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.dto.billing.Wallet;
import com.fincity.security.dto.billing.WalletTransaction;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.jooq.enums.SecurityWalletTransactionType;
import com.fincity.security.jooq.tables.records.SecurityWalletRecord;
import com.fincity.security.model.billing.AiChargeRequest;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.HostingDecision;
import com.fincity.security.model.billing.WalletStatusView;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * The token wallet domain. One service owns ledgering, the status cache and the
 * low-balance / suspend events. All pricing (free quota + rate + proration)
 * happens here, so a pricing change is a single edit.
 */
@Service
public class WalletService
        extends AbstractJOOQUpdatableDataService<SecurityWalletRecord, ULong, Wallet, WalletDAO> {

    private static final String CACHE_NAME_WALLET_STATUS = "walletStatus";
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final WalletTransactionDAO txnDAO;
    private final AppBillingConfigService configService;
    private final AppService appService;
    private final ClientService clientService;
    private final ClientHierarchyService clientHierarchyService;
    private final CacheService cacheService;
    private final EventCreationService ecService;
    private final SecurityMessageResourceService messageResourceService;

    public WalletService(WalletTransactionDAO txnDAO, AppBillingConfigService configService, AppService appService,
            ClientService clientService, ClientHierarchyService clientHierarchyService, CacheService cacheService,
            EventCreationService ecService, SecurityMessageResourceService messageResourceService) {
        this.txnDAO = txnDAO;
        this.configService = configService;
        this.appService = appService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
        this.cacheService = cacheService;
        this.ecService = ecService;
        this.messageResourceService = messageResourceService;
    }

    // ---------------------------------------------------------------------
    // Wallet lifecycle
    // ---------------------------------------------------------------------

    /** Lazily create the wallet seeded with 1 token, ACTIVE, so the first action proceeds. */
    public Mono<Wallet> getOrCreateWallet(ULong clientId, ULong appId) {
        return this.dao.findByClientAndApp(clientId, appId)
                .switchIfEmpty(Mono.defer(() -> this.dao.createSeeded(clientId, appId, BigDecimal.ONE)));
    }

    public Mono<BigDecimal> getBalance(ULong clientId, ULong appId) {
        return this.dao.findByClientAndApp(clientId, appId)
                .map(Wallet::getBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /** Window indices already charged for (client, app, action) on a day (reconciliation). */
    public Mono<java.util.List<Short>> chargedWindows(ULong clientId, ULong appId, String actionKey, LocalDate date) {
        return this.getOrCreateWallet(clientId, appId)
                .flatMap(w -> this.txnDAO.chargedWindows(w.getId(), actionKey, date));
    }

    /** Cached wallet status by (client, app) for the action gates. */
    public Mono<SecurityWalletStatus> getWalletStatus(ULong clientId, ULong appId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_WALLET_STATUS,
                () -> this.dao.findByClientAndApp(clientId, appId)
                        .map(Wallet::getStatus)
                        .defaultIfEmpty(SecurityWalletStatus.ACTIVE),
                clientId, appId);
    }

    private Mono<Boolean> evictWalletStatus(ULong clientId, ULong appId) {
        return this.cacheService.evict(CACHE_NAME_WALLET_STATUS, clientId, appId);
    }

    /**
     * Serving status for (urlAppCode, urlClientCode, callerClientId): no config
     * -> ACTIVE; config present -> the caller's wallet status (created if absent).
     */
    public Mono<WalletStatusView> getServingStatus(String urlAppCode, String urlClientCode, ULong callerClientId) {
        return this.configService.readByAppAndClient(urlAppCode, urlClientCode)
                .flatMap(config -> this.getOrCreateWallet(callerClientId, config.getAppId())
                        .map(w -> new WalletStatusView(w.getStatus().name(), w.getBalance(),
                                w.getStatus() == SecurityWalletStatus.SUSPENDED)))
                .switchIfEmpty(Mono.just(new WalletStatusView(SecurityWalletStatus.ACTIVE.name(), null, false)));
    }

    // ---------------------------------------------------------------------
    // App/site hosting gate (builder wallet -> suspend app/client)
    // ---------------------------------------------------------------------

    private static final String APPBUILDER = "appbuilder";
    private static final String SITEZUMP = "sitezump";
    private static final String SYSTEM = "SYSTEM";

    /**
     * Decide whether to serve M's requested app/site or a configured suspend
     * app/client. The deciding wallet is the BUILDER wallet (appbuilder for apps;
     * sitezump, else appbuilder, for sites), not the running app's. SYSTEM client, a
     * missing app/client, no builder wallet, or no suspend config all pass through
     * unchanged.
     */
    public Mono<HostingDecision> resolveHosting(String urlAppCode, String urlClientCode) {
        HostingDecision passthrough = HostingDecision.serve(urlAppCode, urlClientCode);
        if (StringUtil.safeIsBlank(urlClientCode) || SYSTEM.equalsIgnoreCase(urlClientCode)
                || StringUtil.safeIsBlank(urlAppCode))
            return Mono.just(passthrough);

        return FlatMapUtil.flatMapMono(
                () -> this.clientService.getClientBy(urlClientCode),
                client -> this.appService.getAppByCode(urlAppCode),
                (client, app) -> this.hostingWallet(client.getId(), app.getAppType() == SecurityAppAppType.SITE),
                (client, app, builderAndWallet) -> {
                    if (builderAndWallet.getT2().getStatus() != SecurityWalletStatus.SUSPENDED)
                        return Mono.just(passthrough);
                    return this.suspendTarget(builderAndWallet.getT1(), client.getId(), passthrough);
                })
                .defaultIfEmpty(passthrough)
                .switchIfEmpty(Mono.just(passthrough))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.resolveHosting"));
    }

    /** The builder (appCode, wallet) that gates serving: appbuilder for apps; sitezump else appbuilder for sites. */
    private Mono<Tuple2<String, Wallet>> hostingWallet(ULong mClientId, boolean isSite) {
        if (!isSite)
            return this.builderWallet(mClientId, APPBUILDER);
        return this.builderWallet(mClientId, SITEZUMP)
                .switchIfEmpty(this.builderWallet(mClientId, APPBUILDER));
    }

    private Mono<Tuple2<String, Wallet>> builderWallet(ULong mClientId, String builderAppCode) {
        return this.appService.getAppByCode(builderAppCode)
                .flatMap(app -> this.dao.findByClientAndApp(mClientId, app.getId())
                        .map(w -> Tuples.of(builderAppCode, w)));
    }

    /** Resolve config(C, builderApp) for M's managing client and read its suspend app/client. */
    private Mono<HostingDecision> suspendTarget(String builderAppCode, ULong mClientId, HostingDecision passthrough) {
        return this.appService.getAppByCode(builderAppCode)
                .flatMap(builderApp -> this.resolveConfigForBilledClient(builderApp.getId(), mClientId))
                .flatMap(config -> StringUtil.safeIsBlank(config.getSuspendAppCode())
                        || StringUtil.safeIsBlank(config.getSuspendClientCode())
                                ? Mono.just(passthrough)
                                : Mono.just(new HostingDecision(true, config.getSuspendAppCode(),
                                        config.getSuspendClientCode())))
                .defaultIfEmpty(passthrough);
    }

    // ---------------------------------------------------------------------
    // Metered charges (15-minute rent). All pricing lives here.
    // ---------------------------------------------------------------------

    public Mono<ChargeResult> charge(ChargeRequest req) {
        return this.configService.readByAppAndClientId(req.appId(), req.configClientId())
                .flatMap(config -> {
                    BigDecimal rate = rateFor(config, req.actionKey());
                    BigDecimal free = freeFor(config, req.actionKey());
                    if (rate == null || rate.signum() <= 0)
                        return noCharge();

                    BigDecimal billable = req.quantity().subtract(free == null ? BigDecimal.ZERO : free)
                            .max(BigDecimal.ZERO);
                    int windowsInMonth = YearMonth.from(req.date()).lengthOfMonth() * 96;
                    BigDecimal tokens = billable.multiply(rate)
                            .divide(BigDecimal.valueOf(windowsInMonth), 0, RoundingMode.DOWN);
                    if (tokens.signum() <= 0)
                        return noCharge();

                    Short window = req.windowIndex() == null ? null : req.windowIndex().shortValue();
                    String idemKey = req.actionKey() + ":" + req.date() + ":" + req.windowIndex();
                    String reason = "Metered " + req.actionKey() + " window " + req.windowIndex();
                    return this.getOrCreateWallet(req.billedClientId(), req.appId())
                            .flatMap(wallet -> this.applyDebit(wallet, tokens, config, req.actionKey(),
                                    req.quantity(), req.date(), window, idemKey, reason, null));
                })
                .switchIfEmpty(noCharge())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.charge"));
    }

    public Mono<Void> chargeBulk(java.util.List<ChargeRequest> requests) {
        return Flux.fromIterable(requests)
                .concatMap(req -> this.charge(req).onErrorResume(e -> noCharge()))
                .then();
    }

    /**
     * AI start-of-turn gate: false only when M's builder wallet is SUSPENDED. No
     * wallet or any lookup miss is allowed (fail-open is the caller's job too).
     */
    public Mono<Boolean> isAiAllowed(String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByCode(appCode),
                app -> this.clientService.getClientBy(clientCode),
                (app, client) -> this.dao.findByClientAndApp(client.getId(), app.getId())
                        .map(w -> w.getStatus() != SecurityWalletStatus.SUSPENDED)
                        .defaultIfEmpty(Boolean.TRUE))
                .defaultIfEmpty(Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.isAiAllowed"));
    }

    /** Immediate AI charge: resolve config via M's managing client, price flat per 1M tokens. */
    public Mono<ChargeResult> chargeAi(AiChargeRequest req) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByCode(req.appCode()),
                app -> this.clientService.getClientBy(req.clientCode()),
                (app, client) -> this.resolveConfigForBilledClient(app.getId(), client.getId()),
                (app, client, config) -> {
                    BigDecimal rate = config.getAiTokensPerMillion();
                    if (rate == null || rate.signum() <= 0)
                        return noCharge();
                    BigDecimal tokens = req.weightedTokens().multiply(rate)
                            .divide(ONE_MILLION, 0, RoundingMode.DOWN);
                    if (tokens.signum() <= 0)
                        return noCharge();
                    String idemKey = "ai:" + req.requestId();
                    String reason = "AI usage " + req.model();
                    return this.getOrCreateWallet(client.getId(), app.getId())
                            .flatMap(wallet -> this.applyDebit(wallet, tokens, config, BillingActionKeys.AI_LLM_TOKENS,
                                    req.weightedTokens(), LocalDate.now(), null, idemKey, reason, null));
                })
                .switchIfEmpty(noCharge())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.chargeAi"));
    }

    /** Find a config(C, app) where C directly manages the billed client M. */
    private Mono<AppBillingConfig> resolveConfigForBilledClient(ULong appId, ULong billedClientId) {
        return this.clientHierarchyService.getManagingClientIds(billedClientId)
                .flatMapMany(Flux::fromIterable)
                .concatMap(managerId -> this.configService.readByAppAndClientId(appId, managerId))
                .next();
    }

    // ---------------------------------------------------------------------
    // Credits + adjustments
    // ---------------------------------------------------------------------

    /** Credit a wallet from a paid invoice; idempotent on the payment reference. */
    public Mono<ChargeResult> creditFromPayment(Invoice invoice) {
        String idemKey = "payment:" + invoice.getPaymentReference();
        return this.getOrCreateWallet(invoice.getClientId(), invoice.getAppId())
                .flatMap(wallet -> this.applyCredit(wallet, invoice.getTokensPurchased(),
                        SecurityWalletTransactionType.CREDIT, "INVOICE", invoice.getId(), idemKey,
                        "Token purchase " + invoice.getInvoiceNumber(), invoice.getCreatedBy()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.creditFromPayment"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<ChargeResult> adjust(ULong walletId, BigDecimal tokens, String reason) {
        return FlatMapUtil.flatMapMono(

                () -> this.dao.readById(walletId)
                        .switchIfEmpty(this.forbidden("the wallet")),

                wallet -> SecurityContextUtil.getUsersContextAuthentication(),

                (wallet, ca) -> this.clientService.isUserClientManageClient(ca, wallet.getClientId())
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("manage the wallet's client")),

                (wallet, ca, managed) -> {
                    ULong callerClient = ULong.valueOf(ca.getUser().getClientId());
                    if (callerClient.equals(wallet.getClientId()))
                        return this.forbidden("adjust your own wallet");
                    return this.appService.hasWriteAccess(wallet.getAppId(), callerClient)
                            .filter(BooleanUtil::safeValueOf)
                            .switchIfEmpty(this.forbidden("write access to the application"));
                },

                (wallet, ca, managed, access) -> this.applyCredit(wallet, tokens,
                        SecurityWalletTransactionType.ADJUST, "ADJUST", null,
                        "adjust:" + java.util.UUID.randomUUID(), reason,
                        ULong.valueOf(ca.getUser().getId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WalletService.adjust"));
    }

    // ---------------------------------------------------------------------
    // Core debit / credit primitives
    // ---------------------------------------------------------------------

    private Mono<ChargeResult> applyDebit(Wallet wallet, BigDecimal tokens, AppBillingConfig config, String actionKey,
            BigDecimal quantity, LocalDate chargeDate, Short window, String idemKey, String reason, ULong actingUser) {

        if (wallet.getStatus() == SecurityWalletStatus.SUSPENDED)
            return Mono.just(new ChargeResult(false, true, false, wallet.getBalance()));

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(tokens);

        WalletTransaction txn = new WalletTransaction()
                .setWalletId(wallet.getId())
                .setType(SecurityWalletTransactionType.DEBIT)
                .setTokens(tokens)
                .setBalanceAfter(balanceAfter)
                .setActionKey(actionKey)
                .setAppId(wallet.getAppId())
                .setQuantity(quantity)
                .setChargeDate(chargeDate)
                .setWindowIndex(window)
                .setIdempotencyKey(idemKey)
                .setReason(reason);
        txn.setCreatedBy(actingUser);

        return this.txnDAO.recordTxn(txn).flatMap(inserted -> {
            if (Boolean.FALSE.equals(inserted))
                return Mono.just(new ChargeResult(false, false, false, balanceBefore));

            return this.dao.debitActive(wallet.getId(), tokens).flatMap(rows -> {
                if (rows == null || rows == 0)
                    return Mono.just(new ChargeResult(false, true, false, balanceBefore));

                BigDecimal threshold = wallet.getAlertThreshold() != null ? wallet.getAlertThreshold()
                        : config != null ? config.getLowBalanceThreshold() : null;
                boolean lowCross = threshold != null
                        && balanceBefore.compareTo(threshold) >= 0
                        && balanceAfter.compareTo(threshold) < 0
                        && (wallet.getLowBalanceNotified() == null || wallet.getLowBalanceNotified() == 0);
                boolean suspend = balanceAfter.signum() < 0;

                Mono<Void> effects = Mono.empty();
                if (lowCross)
                    effects = this.dao.setLowBalanceNotified(wallet.getId(), true)
                            .then(this.raiseWalletEvent(EventNames.WALLET_LOW_BALANCE, wallet, balanceAfter));
                if (suspend)
                    effects = effects
                            .then(this.dao.setStatus(wallet.getId(), SecurityWalletStatus.SUSPENDED))
                            .then(this.evictWalletStatus(wallet.getClientId(), wallet.getAppId()))
                            .then(this.raiseWalletEvent(EventNames.WALLET_SUSPENDED, wallet, balanceAfter));

                return effects.thenReturn(new ChargeResult(true, suspend, lowCross, balanceAfter));
            });
        });
    }

    private Mono<ChargeResult> applyCredit(Wallet wallet, BigDecimal tokens, SecurityWalletTransactionType type,
            String refType, ULong refId, String idemKey, String reason, ULong actingUser) {

        BigDecimal balanceAfter = wallet.getBalance().add(tokens);
        boolean reactivate = wallet.getStatus() == SecurityWalletStatus.SUSPENDED && balanceAfter.signum() > 0;

        WalletTransaction txn = new WalletTransaction()
                .setWalletId(wallet.getId())
                .setType(type)
                .setTokens(tokens)
                .setBalanceAfter(balanceAfter)
                .setAppId(wallet.getAppId())
                .setIdempotencyKey(idemKey)
                .setReferenceType(refType)
                .setReferenceId(refId)
                .setReason(reason);
        txn.setCreatedBy(actingUser);

        return this.txnDAO.recordTxn(txn).flatMap(inserted -> {
            if (Boolean.FALSE.equals(inserted))
                return Mono.just(new ChargeResult(false, false, false, wallet.getBalance()));

            Mono<Integer> apply = this.dao.creditBalance(wallet.getId(), tokens);
            return apply.flatMap(r -> {
                Mono<Void> effects = this.dao.setLowBalanceNotified(wallet.getId(), false).then();
                if (reactivate)
                    effects = effects
                            .then(this.dao.setStatus(wallet.getId(), SecurityWalletStatus.ACTIVE))
                            .then();
                effects = effects.then(this.evictWalletStatus(wallet.getClientId(), wallet.getAppId())).then();
                return effects.thenReturn(new ChargeResult(true, false, false, balanceAfter));
            });
        });
    }

    private Mono<Void> raiseWalletEvent(String eventName, Wallet wallet, BigDecimal balanceAfter) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppById(wallet.getAppId()),
                app -> this.clientService.getClientInfoById(wallet.getClientId()),
                (app, client) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("walletId", wallet.getId());
                    data.put("appCode", app.getAppCode());
                    data.put("clientCode", client.getCode());
                    data.put("balance", balanceAfter);
                    EventQueObject evt = new EventQueObject()
                            .setAppCode(app.getAppCode())
                            .setClientCode(client.getCode())
                            .setEventName(eventName)
                            .setData(data);
                    return this.ecService.createEvent(evt);
                }).then();
    }

    private Mono<ChargeResult> noCharge() {
        return Mono.just(new ChargeResult(false, false, false, BigDecimal.ZERO));
    }

    private <T> Mono<T> forbidden(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, what);
    }

    private static BigDecimal rateFor(AppBillingConfig c, String actionKey) {
        return switch (actionKey) {
            case BillingActionKeys.APP_RENT -> c.getAppRentPerMonth();
            case BillingActionKeys.SITE_RENT -> c.getSiteRentPerMonth();
            case BillingActionKeys.USER -> c.getUserTokensPerMonth();
            case BillingActionKeys.STORAGE_ROWS -> c.getStorageRowTokensPerMonth();
            case BillingActionKeys.DEALS -> c.getDealTokensPerMonth();
            case BillingActionKeys.FILES_GB -> c.getFilesTokensPerMonth();
            case BillingActionKeys.AI_LLM_TOKENS -> c.getAiTokensPerMillion();
            default -> null;
        };
    }

    private static BigDecimal freeFor(AppBillingConfig c, String actionKey) {
        return switch (actionKey) {
            case BillingActionKeys.APP_RENT -> c.getFreeApps();
            case BillingActionKeys.SITE_RENT -> c.getFreeSites();
            case BillingActionKeys.USER -> c.getFreeUsers();
            case BillingActionKeys.STORAGE_ROWS -> c.getFreeStorageRows();
            case BillingActionKeys.DEALS -> c.getFreeDeals();
            case BillingActionKeys.FILES_GB -> c.getFreeFilesGb();
            case BillingActionKeys.AI_LLM_TOKENS -> c.getFreeAiTokensPerMonth();
            default -> BigDecimal.ZERO;
        };
    }

    @Override
    protected Mono<Wallet> updatableEntity(Wallet entity) {
        return this.dao.readById(entity.getId()).map(existing -> existing
                .setAlertThreshold(entity.getAlertThreshold())
                .setStatus(entity.getStatus()));
    }
}
