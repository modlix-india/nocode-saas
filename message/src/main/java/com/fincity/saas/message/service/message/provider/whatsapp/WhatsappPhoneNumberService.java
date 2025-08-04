package com.fincity.saas.message.service.message.provider.whatsapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;

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
}
