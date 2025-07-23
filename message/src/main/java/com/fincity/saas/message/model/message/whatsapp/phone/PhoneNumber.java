package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.phone.type.NameStatusType;
import com.fincity.saas.message.model.message.whatsapp.phone.type.PlatformType;
import com.fincity.saas.message.model.message.whatsapp.phone.type.QualityRatingType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(value = Include.NON_NULL)
public final class PhoneNumber implements Serializable {

    @Serial
    private static final long serialVersionUID = -8866348344507366068L;

    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;

    @JsonProperty("quality_rating")
    private QualityRatingType qualityRating;

    @JsonProperty("verified_name")
    private String verifiedName;

    @JsonProperty("id")
    private String id;

    @JsonProperty("code_verification_status")
    private String codeVerificationStatus;

    @JsonProperty("name_status")
    private NameStatusType nameStatus;

    @JsonProperty("platform_type")
    private PlatformType platformType;

    @JsonProperty("throughput")
    private Throughput throughput;
}
