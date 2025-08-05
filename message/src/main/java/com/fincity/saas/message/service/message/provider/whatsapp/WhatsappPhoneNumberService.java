package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumbers;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
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

    private Mono<Tuple2<String, PhoneNumbers>> getPhoneNumbers(String connectionName, MessageAccess messageAccess) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getConnection(
                        messageAccess.getAppCode(), messageAccess.getClientCode(), connectionName),
                this::getWhatsappBusinessAccountId,
                (connection, businessAccountId) -> this.getBusinessManagementApi(connection),
                (connection, businessAccountId, api) -> api.retrievePhoneNumbers(businessAccountId),
                (connection, businessAccountId, api, phoneNumbers) ->
                        Mono.just(Tuples.of(businessAccountId, phoneNumbers)));
    }

    private Flux<WhatsappPhoneNumber> savePhoneNumbers(
            String whatsappBusinessAccountId, PhoneNumbers phoneNumbers, MessageAccess messageAccess) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.syncPhoneNumber(whatsappBusinessAccountId, phoneNumber, messageAccess));
    }

    private Mono<WhatsappPhoneNumber> syncPhoneNumber(
            String whatsappBusinessAccountId, PhoneNumber phoneNumber, MessageAccess messageAccess) {
        return this.dao
                .findByUniqueField(phoneNumber.getId())
                .flatMap(existing -> super.update(existing.update(phoneNumber)))
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
}
