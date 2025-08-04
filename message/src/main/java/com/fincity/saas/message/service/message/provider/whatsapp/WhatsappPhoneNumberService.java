package com.fincity.saas.message.service.message.provider.whatsapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    public Mono<Void> syncPhoneNumbers(String connectionName) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                messageAccess -> super.messageConnectionService.getConnection(
                        messageAccess.getAppCode(), messageAccess.getClientCode(), connectionName),
                (messageAccess, connection) -> this.getWhatsappBusinessAccountId(connection),
                (messageAccess, connection, businessAccountId) -> this.getBusinessManagementApi(connection),
                (messageAccess, connection, businessAccountId, api) -> api.retrievePhoneNumbers(businessAccountId),
                (messageAccess, connection, businessAccountId, api, phoneNumbers) -> 
                        this.savePhoneNumbers(phoneNumbers.getData(), messageAccess).then()
        );
    }

     private Flux<WhatsappPhoneNumber> savePhoneNumbers(java.util.List<PhoneNumber> phoneNumbers, MessageAccess messageAccess) {
         return Flux.fromIterable(phoneNumbers).flatMap(phoneNumber -> this.syncPhoneNumber(phoneNumber, messageAccess));
     }

     private Mono<WhatsappPhoneNumber> syncPhoneNumber(PhoneNumber phoneNumber, MessageAccess messageAccess) {
         return this.dao
                .findByUniqueField(phoneNumber.getId())
                .flatMap(existing -> super.update(existing.update(phoneNumber)))
                .switchIfEmpty(
                        Mono.defer(() -> super.createInternal(messageAccess, WhatsappPhoneNumber.of(phoneNumber))));
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId =
                (String) connection.getConnectionDetails().getOrDefault("whatsappBusinessAccountId", null);

        if (businessAccountId == null) return super.throwMissingParam("whatsappBusinessAccountId");

        return Mono.just(businessAccountId);
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory.newBusinessManagementApiFromConnection(connection);
    }
}
