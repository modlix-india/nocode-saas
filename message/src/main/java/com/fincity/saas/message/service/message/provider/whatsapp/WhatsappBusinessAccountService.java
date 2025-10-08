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
                (access, connection, api, businessAccountId, businessAccount) ->
                        this.saveBusinessAccount(access, businessAccount, businessAccountId));
    }

    public Mono<WhatsappBusinessAccount> overrideWebhook(String connectionName, Identity whatsappBusinessAccountId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappBusinessAccountId),
                (access, waba) -> super.messageConnectionService
                        .getCoreDocument(access.getAppCode(), access.getClientCode(), connectionName)
                        .flatMap(this::isValidConnection),
                (access, waba, connection) -> this.getBusinessManagementApi(connection),
                (access, waba, connection, api) -> this.createWebhookOverride(access)
                        .flatMap(wo -> api.overrideBusinessWebhook(waba.getWhatsappBusinessAccountId(), wo)
                                .then(api.getSubscribedApp(waba.getWhatsappBusinessAccountId()))),
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

    private Mono<WebhookOverride> createWebhookOverride(MessageAccess access) {
        return super.getWebhookUrl(access.getAppCode(), access.getClientCode())
                .map(url -> new WebhookOverride().setOverrideCallbackUri(url).setVerifyToken(super.verifyToken));
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappBusinessAccount.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappBusinessAccount.Fields.whatsappBusinessAccountId);

        return Mono.just(businessAccountId);
    }

    private Mono<WhatsappBusinessAccount> saveBusinessAccount(
            MessageAccess access, BusinessAccount businessAccount, String businessAccountId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.findByUniqueField(access, businessAccountId),
                        whatsappBusinessAccount -> super.update(whatsappBusinessAccount.update(businessAccount)),
                        (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                                this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber))
                .switchIfEmpty(Mono.defer(() ->
                        super.createInternal(access, WhatsappBusinessAccount.of(businessAccountId, businessAccount))));
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
