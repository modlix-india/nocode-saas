package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.NameStatusType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.PlatformType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.QualityRatingType;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.Throughput;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappPhoneNumber extends BaseUpdatableDto<WhatsappPhoneNumber> {

    private String whatsappBusinessAccountId;
    private String displayPhoneNumber;
    private QualityRatingType qualityRating;
    private String verifiedName;
    private String phoneNumberId;
    private String codeVerificationStatus;
    private NameStatusType nameStatus;
    private PlatformType platformType;
    private Throughput throughput;
    private Boolean isDefault = Boolean.FALSE;

    public static WhatsappPhoneNumber of(String whatsappBusinessAccountId, PhoneNumber phoneNumber) {
        return new WhatsappPhoneNumber()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setDisplayPhoneNumber(phoneNumber.getDisplayPhoneNumber())
                .setQualityRating(phoneNumber.getQualityRating())
                .setVerifiedName(phoneNumber.getVerifiedName())
                .setPhoneNumberId(phoneNumber.getId())
                .setCodeVerificationStatus(phoneNumber.getCodeVerificationStatus())
                .setNameStatus(phoneNumber.getNameStatus())
                .setPlatformType(phoneNumber.getPlatformType())
                .setThroughput(phoneNumber.getThroughput());
    }

    public WhatsappPhoneNumber update(PhoneNumber phoneNumber) {
        this.qualityRating = phoneNumber.getQualityRating();
        this.verifiedName = phoneNumber.getVerifiedName();
        this.codeVerificationStatus = phoneNumber.getCodeVerificationStatus();
        this.nameStatus = phoneNumber.getNameStatus();
        this.platformType = phoneNumber.getPlatformType();
        this.throughput = phoneNumber.getThroughput();
        return this;
    }
}
