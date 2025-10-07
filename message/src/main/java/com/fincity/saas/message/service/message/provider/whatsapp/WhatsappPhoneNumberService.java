package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.data.FbPagingData;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class WhatsappPhoneNumberService
        extends AbstractMessageService<MessageWhatsappPhoneNumbersRecord, WhatsappPhoneNumber, WhatsappPhoneNumberDAO> {

    public static final String WHATSAPP_PHONE_NUMBER_PROVIDER_URI = "/whatsapp/phone";

    private static final String WHATSAPP_PHONE_NUMBER_CACHE = "whatsappPhoneNumber";

    private final WhatsappApiFactory whatsappApiFactory;
    private WhatsappBusinessAccountService businessAccountService;

    @Autowired
    public WhatsappPhoneNumberService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Autowired
    public void setBusinessAccountService(WhatsappBusinessAccountService businessAccountService) {
        this.businessAccountService = businessAccountService;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_PHONE_NUMBER_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.WHATSAPP_PHONE_NUMBER;
    }

    @Override
    protected Mono<Boolean> evictCache(WhatsappPhoneNumber entity) {
        return super.evictCache(entity).flatMap(evicted -> Mono.zip(
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getCacheKey(
                                        entity.getAppCode(), entity.getClientCode(), entity.getPhoneNumberId())),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getCacheKey(
                                        entity.getAppCode(),
                                        entity.getClientCode(),
                                        entity.getWhatsappBusinessAccountId())),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getCacheKey(
                                        entity.getAppCode(),
                                        entity.getClientCode(),
                                        entity.getWhatsappBusinessAccountId(),
                                        entity.getPhoneNumberId())))
                .map(sEvicted -> sEvicted.getT1() && sEvicted.getT2() && sEvicted.getT3()));
    }

    @Override
    protected Mono<WhatsappPhoneNumber> updatableEntity(WhatsappPhoneNumber entity) {
        return super.updatableEntity(entity).flatMap(uEntity -> {
            uEntity.setProductId(entity.getProductId());
            uEntity.setQualityRating(entity.getQualityRating());
            uEntity.setQualityScore(entity.getQualityScore());
            uEntity.setVerifiedName(entity.getVerifiedName());
            uEntity.setCodeVerificationStatus(entity.getCodeVerificationStatus());
            uEntity.setNameStatus(entity.getNameStatus());
            uEntity.setPlatformType(entity.getPlatformType());
            uEntity.setThroughput(entity.getThroughput());
            uEntity.setStatus(entity.getStatus());
            uEntity.setMessagingLimitTier(entity.getMessagingLimitTier());
            uEntity.setIsDefault(entity.getIsDefault());
            uEntity.setWebhookConfig(entity.getWebhookConfig());
            return Mono.just(uEntity);
        });
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    @Override
    public String getProviderUri() {
        return WHATSAPP_PHONE_NUMBER_PROVIDER_URI;
    }

    public Flux<WhatsappPhoneNumber> syncPhoneNumbers(String connectionName) {
        return FlatMapUtil.flatMapFlux(
                () -> super.hasAccess().flux(),
                access -> this.getPhoneNumbers(connectionName, access).flux(),
                (access, phoneNumbers) -> this.savePhoneNumbers(phoneNumbers.getT1(), phoneNumbers.getT2(), access));
    }

    public Mono<WhatsappPhoneNumber> syncPhoneNumber(String connectionName, Identity whatsappPhoneNumberId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                (access, whatsappPhoneNumber) ->
                        this.getPhoneNumber(connectionName, access, whatsappPhoneNumber.getPhoneNumberId()),
                (access, whatsappPhoneNumber, phoneNumber) ->
                        this.savePhoneNumber(phoneNumber.getT1(), phoneNumber.getT2(), access));
    }

    public Mono<WhatsappPhoneNumber> setDefault(Identity phoneNumber) {
        return FlatMapUtil.flatMapMonoWithNull(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, phoneNumber),
                (access, whatsappPhoneNumber) -> this.getDefaultWhatsAppPhoneNumber(access),
                (access, whatsappPhoneNumber, defaultPhoneNumber) -> {
                    if (defaultPhoneNumber == null) return this.updateDefault(whatsappPhoneNumber, Boolean.TRUE);

                    if (whatsappPhoneNumber.getId().equals(defaultPhoneNumber.getId()))
                        return Mono.just(defaultPhoneNumber);

                    return Mono.zip(
                                    this.updateDefault(whatsappPhoneNumber, Boolean.TRUE),
                                    this.updateDefault(defaultPhoneNumber, Boolean.FALSE))
                            .map(Tuple2::getT1);
                });
    }

    public Flux<WhatsappPhoneNumber> updatePhoneNumbersStatus(String connectionName) {
        return FlatMapUtil.flatMapFlux(
                () -> super.hasAccess().flux(),
                access -> this.getPhoneNumbers(
                                connectionName,
                                access,
                                PhoneNumber.Fields.status,
                                PhoneNumber.Fields.qualityScore,
                                PhoneNumber.Fields.messagingLimitTier,
                                PhoneNumber.Fields.nameStatus)
                        .flux(),
                (access, phoneNumbers) -> this.updatePhoneNumbersStatus(phoneNumbers.getT2(), access));
    }

    public Mono<WhatsappPhoneNumber> updatePhoneNumberStatus(String connectionName, Identity whatsappPhoneNumberId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                (access, whatsappPhoneNumber) -> this.getPhoneNumber(
                        connectionName,
                        access,
                        whatsappPhoneNumber.getPhoneNumberId(),
                        PhoneNumber.Fields.status,
                        PhoneNumber.Fields.qualityScore,
                        PhoneNumber.Fields.messagingLimitTier,
                        PhoneNumber.Fields.nameStatus),
                (access, whatsappPhoneNumber, phoneNumber) ->
                        this.updatePhoneNumberStatus(phoneNumber.getT2(), access));
    }

    private Mono<WhatsappPhoneNumber> updateDefault(WhatsappPhoneNumber whatsappPhoneNumber, Boolean isDefault) {
        return super.update(whatsappPhoneNumber.setIsDefault(isDefault))
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    public Mono<WhatsappPhoneNumber> getByPhoneNumberId(MessageAccess access, String phoneNumberId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByPhoneNumberId(access, phoneNumberId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), phoneNumberId));
    }

    public Mono<WhatsappPhoneNumber> getByAccountId(MessageAccess access, ULong whatsappBusinessAccountId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByAccountId(access, whatsappBusinessAccountId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), whatsappBusinessAccountId));
    }

    public Mono<WhatsappPhoneNumber> getByAccountAndPhoneNumberId(
            MessageAccess access, ULong whatsappBusinessAccountId, String phoneNumberId) {
        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getByAccountAndPhoneNumberId(access, whatsappBusinessAccountId, phoneNumberId),
                        super.getCacheKey(
                                access.getAppCode(), access.getClientCode(), whatsappBusinessAccountId, phoneNumberId))
                .switchIfEmpty(this.getByAccountId(access, whatsappBusinessAccountId));
    }

    private Mono<Tuple2<WhatsappBusinessAccount, FbPagingData<PhoneNumber>>> getPhoneNumbers(
            String connectionName, MessageAccess access, String... fields) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                connection -> getWhatsappBusinessAccount(access, connection),
                (connection, businessAccount) -> this.getBusinessManagementApi(connection),
                (connection, businessAccount, api) ->
                        api.retrievePhoneNumbers(businessAccount.getWhatsappBusinessAccountId(), fields),
                (connection, businessAccount, api, phoneNumbers) ->
                        Mono.just(Tuples.of(businessAccount, phoneNumbers)));
    }

    private Mono<Tuple2<WhatsappBusinessAccount, PhoneNumber>> getPhoneNumber(
            String connectionName, MessageAccess access, String phoneNumberId, String... fields) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                connection -> getWhatsappBusinessAccount(access, connection),
                (connection, businessAccount) -> this.getBusinessManagementApi(connection),
                (connection, businessAccount, api) -> api.retrievePhoneNumber(phoneNumberId, fields),
                (connection, businessAccount, api, phoneNumber) -> Mono.just(Tuples.of(businessAccount, phoneNumber)));
    }

    private Flux<WhatsappPhoneNumber> savePhoneNumbers(
            WhatsappBusinessAccount whatsappBusinessAccount,
            FbPagingData<PhoneNumber> phoneNumbers,
            MessageAccess access) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.savePhoneNumber(whatsappBusinessAccount, phoneNumber, access));
    }

    private Mono<WhatsappPhoneNumber> savePhoneNumber(
            WhatsappBusinessAccount whatsappBusinessAccount, PhoneNumber phoneNumber, MessageAccess access) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.getByPhoneNumberId(access, phoneNumber.getId()),
                        whatsappPhoneNumber -> super.update(whatsappPhoneNumber.update(phoneNumber)),
                        (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                                this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber))
                .switchIfEmpty(Mono.defer(() -> super.createInternal(
                        access, WhatsappPhoneNumber.of(whatsappBusinessAccount.getId(), phoneNumber))));
    }

    private Flux<WhatsappPhoneNumber> updatePhoneNumbersStatus(
            FbPagingData<PhoneNumber> phoneNumbers, MessageAccess access) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.updatePhoneNumberStatus(phoneNumber, access));
    }

    private Mono<WhatsappPhoneNumber> updatePhoneNumberStatus(PhoneNumber phoneNumber, MessageAccess access) {

        return FlatMapUtil.flatMapMono(
                () -> this.dao.getByPhoneNumberId(access, phoneNumber.getId()),
                whatsappPhoneNumber -> super.update(whatsappPhoneNumber.updateStatus(phoneNumber)),
                (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                        this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber));
    }

    private Mono<WhatsappBusinessAccount> getWhatsappBusinessAccount(MessageAccess access, Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return this.businessAccountService.getBusinessAccount(access, businessAccountId);
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory.newBusinessManagementApiFromConnection(connection);
    }

    private Mono<WhatsappPhoneNumber> getDefaultWhatsAppPhoneNumber(MessageAccess access) {
        return this.dao.getDefaultPhoneNumber(access);
    }
}
