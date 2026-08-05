package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappBusinessAccountDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappBusinessAccountsRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.business.BusinessAccount;
import com.fincity.saas.message.model.message.whatsapp.business.SubscribedApp;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookOverride;
import com.fincity.saas.message.model.message.whatsapp.data.FbData;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WhatsappBusinessAccountService
        extends AbstractMessageService<
                MessageWhatsappBusinessAccountsRecord, WhatsappBusinessAccount, WhatsappBusinessAccountDAO> {

    private static final String KEY_META_APP_ID = "metaAppId";
    private static final String WHATSAPP_BUSINESS_ACCOUNT_PROVIDER_URI = "/whatsapp/account/business";

    private static final String WHATSAPP_BUSINESS_ACCOUNT_CACHE = "whatsappBusinessAccount";

    private final WhatsappApiFactory whatsappApiFactory;

    @Autowired
    public WhatsappBusinessAccountService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_BUSINESS_ACCOUNT_CACHE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    @Override
    public String getProviderUri() {
        return WHATSAPP_BUSINESS_ACCOUNT_PROVIDER_URI;
    }

    @Override
    protected Mono<Boolean> evictCache(WhatsappBusinessAccount entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(
                                entity.getAppCode(), entity.getClientCode(), entity.getWhatsappBusinessAccountId())),
                (baseEvicted, acCcEvicted) -> baseEvicted && acCcEvicted);
    }

    @Override
    protected Mono<WhatsappBusinessAccount> updatableEntity(WhatsappBusinessAccount entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setName(entity.getName());
            existing.setCurrency(entity.getCurrency());
            existing.setTimezoneId(entity.getTimezoneId());
            existing.setMessageTemplateNamespace(entity.getMessageTemplateNamespace());
            existing.setSubscribedApp(entity.getSubscribedApp());
            // Only overwrite when the caller actually supplied one, so a generic update cannot
            // blank the pointer the webhook signature check depends on.
            if (entity.getConnectionName() != null && !entity.getConnectionName().isBlank())
                existing.setConnectionName(entity.getConnectionName());

            return Mono.just(existing);
        });
    }

    @Override
    protected Mono<Connection> isValidConnection(Connection connection) {

        String facebookAppId = (String) connection.getConnectionDetails().getOrDefault(KEY_META_APP_ID, null);

        if (facebookAppId == null || facebookAppId.isEmpty()) return this.throwMissingParam(KEY_META_APP_ID);

        return super.isValidConnection(connection);
    }

    public Mono<WhatsappBusinessAccount> getBusinessAccount(String id) {
        return super.hasAccess().flatMap(access -> this.getBusinessAccount(access, id));
    }

    protected Mono<WhatsappBusinessAccount> getBusinessAccount(MessageAccess access, String id) {
        return super.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> super.findByUniqueField(access, id),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), id));
    }

    public Mono<WhatsappBusinessAccount> syncBusinessAccount(String connectionName) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.messageConnectionService
                        .getCoreDocument(access.getAppCode(), access.getClientCode(), connectionName)
                        .flatMap(this::isValidConnection),
                (access, connection) -> this.getBusinessManagementApi(connection),
                (access, connection, api) -> this.getWhatsappBusinessAccountId(connection),
                (access, connection, api, businessAccountId) -> api.getBusinessAccount(businessAccountId),
                (access, connection, api, businessAccountId, businessAccount) -> this.saveBusinessAccount(
                                access, businessAccount, businessAccountId, connectionName)
                        .flatMap(saved -> this.reconcileWebhook(api, connection, saved)));
    }

    /**
     * Brings the stored subscription in line with Meta and, on a first connect, claims the account.
     *
     * <p>Replaces asking the user to press a button they had no way to evaluate. An account is
     * claimed automatically the first time we see it, because there is exactly one right answer
     * then. After that the state is simply reported: {@code webhookConnected} compares Meta's live
     * override against the URL this environment would register, and the UI offers the action only
     * when they differ, which is the one case a human has to decide - the account is currently
     * delivering somewhere else, and taking it is a choice with consequences elsewhere.
     *
     * <p>"First connect" means no subscription has ever been recorded here, not strictly a new row.
     * An account carrying one already, from another environment or an earlier setup, is left alone.
     *
     * <p>Reads the subscription from Meta rather than trusting the stored copy. The stored copy is
     * what made a claimed account look connected forever.
     *
     * <p>Never fails the sync. This runs on every settings page load, and a Graph hiccup must show
     * the page with the connect action offered, not an error.
     */
    private Mono<WhatsappBusinessAccount> reconcileWebhook(
            WhatsappBusinessManagementApi api, Connection connection, WhatsappBusinessAccount waba) {

        boolean claimable = waba.getSubscribedApp() == null && !super.isLocalEnvironment();

        return this.readSubscribedApp(api, connection, waba)
                .flatMap(app -> claimable && !this.isThisEnvironment(app)
                        ? api.overrideBusinessWebhook(waba.getWhatsappBusinessAccountId(), this.webhookOverride())
                                .then(this.readSubscribedApp(api, connection, waba))
                        : Mono.just(app))
                .flatMap(app -> super.updateInternal(waba.setSubscribedApp(app)))
                .defaultIfEmpty(waba)
                .onErrorResume(e -> {
                    logger.error(
                            "Could not reconcile the webhook subscription for business account {}. Reporting it as"
                                    + " not connected so the action stays available.",
                            waba.getWhatsappBusinessAccountId(),
                            e);
                    return Mono.just(waba);
                })
                .map(saved -> saved.setWebhookConnected(this.isThisEnvironment(saved.getSubscribedApp())));
    }

    /**
     * Our app's subscription on this account, as Meta currently has it.
     *
     * <p>Empty when our app is not among the subscribers, which is a real state rather than an
     * error: it means nobody has connected this account yet.
     */
    private Mono<SubscribedApp> readSubscribedApp(
            WhatsappBusinessManagementApi api, Connection connection, WhatsappBusinessAccount waba) {

        String facebookAppId = (String) connection.getConnectionDetails().get(KEY_META_APP_ID);

        return api.getSubscribedApp(waba.getWhatsappBusinessAccountId()).flatMap(subscribedApps -> {
            if (subscribedApps.getData() == null) return Mono.empty();

            for (SubscribedApp app : subscribedApps.getData())
                if (app.getBusinessApiData() != null
                        && app.getBusinessApiData().getId().equalsIgnoreCase(facebookAppId)) return Mono.just(app);

            return Mono.empty();
        });
    }

    /** Whether the account's callback is the one this environment registers. */
    private boolean isThisEnvironment(SubscribedApp app) {
        return app != null && super.getWebhookUrl().equals(app.getOverrideCallBackUrl());
    }

    /**
     * Subscribes our Meta app to this business account and claims it for this environment.
     *
     * <p>The override is <b>per environment</b>, not per tenant. One Meta app holds one app-level
     * callback URL, so without an override only whichever environment that URL points at can ever
     * receive; the override is what lets dev, stage and prod each own the business accounts they
     * use while sharing an app. What was wrong before was not that an override existed but that its
     * URL was built per tenant, which made every tenant's URL distinct and let two tenants sharing
     * an account overwrite each other's, last click winning, invisibly from both sides.
     *
     * <p>{@link #getWebhookUrl()} now returns one value for the whole environment, so every tenant
     * on an account produces the same URL and clicking this from either is idempotent. The tenant
     * is resolved on arrival from the phone number in the payload.
     *
     * <p>Two environments pointed at the <b>same</b> business account still contend, and no amount
     * of URL construction fixes that: an account can deliver to exactly one place. Give each
     * environment its own account.
     */
    public Mono<WhatsappBusinessAccount> overrideWebhook(String connectionName, Identity whatsappBusinessAccountId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappBusinessAccountId),
                (access, waba) -> super.messageConnectionService
                        .getCoreDocument(access.getAppCode(), access.getClientCode(), connectionName)
                        .flatMap(this::isValidConnection),
                (access, waba, connection) -> this.getBusinessManagementApi(connection),
                (access, waba, connection, api) -> api.overrideBusinessWebhook(
                                waba.getWhatsappBusinessAccountId(), this.webhookOverride())
                        .then(api.getSubscribedApp(waba.getWhatsappBusinessAccountId())),
                (MessageAccess access,
                        WhatsappBusinessAccount waba,
                        Connection connection,
                        WhatsappBusinessManagementApi api,
                        FbData<SubscribedApp> subscribedApps) -> {
                    String facebookAppId =
                            (String) connection.getConnectionDetails().get(KEY_META_APP_ID);

                    for (SubscribedApp app : subscribedApps.getData()) {
                        if (app.getBusinessApiData().getId().equalsIgnoreCase(facebookAppId))
                            return super.updateInternal(waba.setSubscribedApp(app));
                    }

                    return super.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.META_APP_NOT_CONNECTED,
                            waba.getId(),
                            waba.getName());
                });
    }

    /**
     * The environment's callback plus the verify token Meta echoes back to it.
     *
     * <p>The token has to travel with the override: an override URL is a fresh subscription as far
     * as Meta is concerned, and it runs the GET handshake against it before delivering anything. It
     * must equal {@code meta.webhook.verify-token}, which is what {@code verifyMetaWebhook} checks.
     */
    private WebhookOverride webhookOverride() {
        return new WebhookOverride()
                .setOverrideCallbackUri(super.getWebhookUrl())
                .setVerifyToken(super.verifyToken);
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappBusinessAccount.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappBusinessAccount.Fields.whatsappBusinessAccountId);

        return Mono.just(businessAccountId);
    }

    /**
     * @param connectionName recorded on the row so an inbound webhook can walk back to the Meta app
     *     secret it must be verified against. Re-set on every sync, so an account moved to a
     *     different connection corrects itself rather than keeping a stale pointer.
     */
    private Mono<WhatsappBusinessAccount> saveBusinessAccount(
            MessageAccess access,
            BusinessAccount businessAccount,
            String businessAccountId,
            String connectionName) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.findByUniqueField(access, businessAccountId),
                        whatsappBusinessAccount -> super.update(
                                whatsappBusinessAccount.update(businessAccount).setConnectionName(connectionName)),
                        (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                                this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber))
                .switchIfEmpty(Mono.defer(() -> super.createInternal(
                        access,
                        WhatsappBusinessAccount.of(businessAccountId, businessAccount)
                                .setConnectionName(connectionName))));
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory
                .newBusinessManagementApiFromConnection(connection)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new com.fincity.saas.commons.exeception.GenericException(
                                HttpStatus.INTERNAL_SERVER_ERROR, msg),
                        "failed_to_create_whatsapp_api"));
    }
}
