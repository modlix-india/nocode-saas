package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.data.FbPagingData;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class WhatsappPhoneNumberService
        extends AbstractMessageService<MessageWhatsappPhoneNumberRecord, WhatsappPhoneNumber, WhatsappPhoneNumberDAO> {

    public static final String WHATSAPP_PHONE_NUMBER_PROVIDER_URI = "/whatsapp/phone";

    private static final String WHATSAPP_PHONE_NUMBER_CACHE = "whatsappPhoneNumber";

    private final WhatsappApiFactory whatsappApiFactory;

    @Autowired
    public WhatsappPhoneNumberService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
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
            uEntity.setQualityRating(entity.getQualityRating());
            uEntity.setVerifiedName(entity.getVerifiedName());
            uEntity.setCodeVerificationStatus(entity.getCodeVerificationStatus());
            uEntity.setNameStatus(entity.getNameStatus());
            uEntity.setPlatformType(entity.getPlatformType());
            uEntity.setThroughput(entity.getThroughput());
            uEntity.setIsDefault(entity.getIsDefault());

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
                messageAccess ->
                        this.getPhoneNumbers(connectionName, messageAccess).flux(),
                (messageAccess, phoneNumbers) ->
                        this.savePhoneNumbers(phoneNumbers.getT1(), phoneNumbers.getT2(), messageAccess));
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

    private Mono<WhatsappPhoneNumber> updateDefault(WhatsappPhoneNumber whatsappPhoneNumber, Boolean isDefault) {
        return super.update(whatsappPhoneNumber.setIsDefault(isDefault))
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    public Mono<WhatsappPhoneNumber> getByPhoneNumberId(MessageAccess messageAccess, String phoneNumberId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByPhoneNumberId(messageAccess, phoneNumberId),
                super.getCacheKey(messageAccess.getAppCode(), messageAccess.getClientCode(), phoneNumberId));
    }

    public Mono<WhatsappPhoneNumber> getByAccountId(MessageAccess messageAccess, String whatsappBusinessAccountId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByAccountId(messageAccess, whatsappBusinessAccountId),
                super.getCacheKey(
                        messageAccess.getAppCode(), messageAccess.getClientCode(), whatsappBusinessAccountId));
    }

    public Mono<WhatsappPhoneNumber> getByAccountAndPhoneNumberId(
            MessageAccess messageAccess, String whatsappBusinessAccountId, String phoneNumberId) {
        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getByAccountAndPhoneNumberId(
                                messageAccess, whatsappBusinessAccountId, phoneNumberId),
                        super.getCacheKey(
                                messageAccess.getAppCode(),
                                messageAccess.getClientCode(),
                                whatsappBusinessAccountId,
                                phoneNumberId))
                .switchIfEmpty(this.getByAccountId(messageAccess, whatsappBusinessAccountId));
    }

    private Mono<Tuple2<String, FbPagingData<PhoneNumber>>> getPhoneNumbers(
            String connectionName, MessageAccess messageAccess) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        messageAccess.getAppCode(), messageAccess.getClientCode(), connectionName),
                this::getWhatsappBusinessAccountId,
                (connection, businessAccountId) -> this.getBusinessManagementApi(connection),
                (connection, businessAccountId, api) -> api.retrievePhoneNumbers(businessAccountId),
                (connection, businessAccountId, api, phoneNumbers) ->
                        Mono.just(Tuples.of(businessAccountId, phoneNumbers)));
    }

    private Mono<Tuple2<String, PhoneNumber>> getPhoneNumber(
            String connectionName, MessageAccess messageAccess, String phoneNumberId) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        messageAccess.getAppCode(), messageAccess.getClientCode(), connectionName),
                this::getWhatsappBusinessAccountId,
                (connection, businessAccountId) -> this.getBusinessManagementApi(connection),
                (connection, businessAccountId, api) -> api.retrievePhoneNumber(phoneNumberId),
                (connection, businessAccountId, api, phoneNumber) ->
                        Mono.just(Tuples.of(businessAccountId, phoneNumber)));
    }

    private Flux<WhatsappPhoneNumber> savePhoneNumbers(
            String whatsappBusinessAccountId, FbPagingData<PhoneNumber> phoneNumbers, MessageAccess messageAccess) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.savePhoneNumber(whatsappBusinessAccountId, phoneNumber, messageAccess));
    }

    private Mono<WhatsappPhoneNumber> savePhoneNumber(
            String whatsappBusinessAccountId, PhoneNumber phoneNumber, MessageAccess messageAccess) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.getByPhoneNumberId(messageAccess, phoneNumber.getId()),
                        whatsappPhoneNumber -> super.update(whatsappPhoneNumber.update(phoneNumber)),
                        (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                                this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber))
                .switchIfEmpty(Mono.defer(() -> super.createInternal(
                        messageAccess, WhatsappPhoneNumber.of(whatsappBusinessAccountId, phoneNumber))));
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return Mono.just(businessAccountId);
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory.newBusinessManagementApiFromConnection(connection);
    }

    private Mono<WhatsappPhoneNumber> getDefaultWhatsAppPhoneNumber(MessageAccess messageAccess) {
        return this.dao.getDefaultPhoneNumber(messageAccess);
    }
}
