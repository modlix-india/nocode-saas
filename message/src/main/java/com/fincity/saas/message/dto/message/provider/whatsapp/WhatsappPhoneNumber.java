package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.eager.relations.resolvers.field.ProductFieldResolver;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.CodeVerificationStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.MessagingLimitTier;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.NameStatusType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.PlatformType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.QualityRatingType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.Status;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookConfig;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.QualityScore;
import com.fincity.saas.message.model.message.whatsapp.phone.Throughput;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappPhoneNumber extends BaseUpdatableDto<WhatsappPhoneNumber> {

    @Serial
    private static final long serialVersionUID = 9214491312043215338L;

    private ULong productId;
    private ULong whatsappBusinessAccountId;
    private String displayPhoneNumber;
    private QualityRatingType qualityRating;
    private QualityScore qualityScore;
    private String verifiedName;
    private String phoneNumberId;
    private CodeVerificationStatus codeVerificationStatus;
    private NameStatusType nameStatus;
    private PlatformType platformType;
    private Throughput throughput;
    private Status status;
    private MessagingLimitTier messagingLimitTier;
    private Boolean isDefault = Boolean.FALSE;
    private WebhookConfig webhookConfig;

    public WhatsappPhoneNumber() {
        super();
        this.relationsResolverMap.put(ProductFieldResolver.class, Fields.productId);
    }

    public WhatsappPhoneNumber(WhatsappPhoneNumber whatsappPhoneNumber) {
        super(whatsappPhoneNumber);
        this.productId = whatsappPhoneNumber.productId;
        this.whatsappBusinessAccountId = whatsappPhoneNumber.whatsappBusinessAccountId;
        this.displayPhoneNumber = whatsappPhoneNumber.displayPhoneNumber;
        this.qualityRating = whatsappPhoneNumber.qualityRating;
        this.qualityScore = whatsappPhoneNumber.qualityScore;
        this.verifiedName = whatsappPhoneNumber.verifiedName;
        this.phoneNumberId = whatsappPhoneNumber.phoneNumberId;
        this.codeVerificationStatus = whatsappPhoneNumber.codeVerificationStatus;
        this.nameStatus = whatsappPhoneNumber.nameStatus;
        this.platformType = whatsappPhoneNumber.platformType;
        this.throughput = whatsappPhoneNumber.throughput;
        this.status = whatsappPhoneNumber.status;
        this.messagingLimitTier = whatsappPhoneNumber.messagingLimitTier;
        this.isDefault = whatsappPhoneNumber.isDefault;
        this.webhookConfig = whatsappPhoneNumber.webhookConfig;
    }

    public static WhatsappPhoneNumber of(ULong whatsappBusinessAccountId, PhoneNumber phoneNumber) {
        return new WhatsappPhoneNumber()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setDisplayPhoneNumber(phoneNumber.getDisplayPhoneNumber())
                .setQualityRating(phoneNumber.getQualityRating())
                .setVerifiedName(phoneNumber.getVerifiedName())
                .setPhoneNumberId(phoneNumber.getId())
                .setCodeVerificationStatus(phoneNumber.getCodeVerificationStatus())
                .setNameStatus(phoneNumber.getNameStatus())
                .setPlatformType(phoneNumber.getPlatformType())
                .setThroughput(phoneNumber.getThroughput())
                .setWebhookConfig(phoneNumber.getWebhookConfig());
    }

    public WhatsappPhoneNumber update(PhoneNumber phoneNumber) {
        this.qualityRating = phoneNumber.getQualityRating();
        this.verifiedName = phoneNumber.getVerifiedName();
        this.codeVerificationStatus = phoneNumber.getCodeVerificationStatus();
        this.nameStatus = phoneNumber.getNameStatus();
        this.platformType = phoneNumber.getPlatformType();
        this.throughput = phoneNumber.getThroughput();
        this.webhookConfig = phoneNumber.getWebhookConfig();
        return this;
    }

    public WhatsappPhoneNumber updateStatus(PhoneNumber phoneNumber) {
        this.qualityScore = phoneNumber.getQualityScore();
        this.status = phoneNumber.getStatus();
        this.nameStatus = phoneNumber.getNameStatus();
        this.messagingLimitTier = phoneNumber.getMessagingLimitTier();
        return this;
    }
}
