package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.CodeVerificationStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.MessagingLimitTier;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.NameStatusType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.PlatformType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.QualityRatingType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.Status;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookConfig;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@JsonInclude(value = Include.NON_NULL)
@FieldNameConstants
public final class PhoneNumber implements Serializable {

    @Serial
    private static final long serialVersionUID = -8866348344507366068L;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;

    @JsonProperty("quality_rating")
    private QualityRatingType qualityRating;

    @JsonProperty("quality_score")
    private QualityScore qualityScore;

    @JsonProperty("verified_name")
    private String verifiedName;

    @JsonProperty("id")
    private String id;

    @JsonProperty("code_verification_status")
    private CodeVerificationStatus codeVerificationStatus;

    @JsonProperty("name_status")
    private NameStatusType nameStatus;

    @JsonProperty("platform_type")
    private PlatformType platformType;

    @JsonProperty("throughput")
    private Throughput throughput;

    @JsonProperty("messaging_limit_tier")
    private MessagingLimitTier messagingLimitTier;

    @JsonProperty("webhook_configuration")
    private WebhookConfig webhookConfig;
}
