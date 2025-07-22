package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.phone.type.NameStatusType;
import com.fincity.saas.message.model.message.whatsapp.phone.type.PlatformType;
import com.fincity.saas.message.model.message.whatsapp.phone.type.QualityRatingType;

@JsonInclude(value = Include.NON_NULL)
public record PhoneNumber(
        @JsonProperty("display_phone_number") String displayPhoneNumber,
        @JsonProperty("quality_rating") QualityRatingType qualityRating,
        @JsonProperty("verified_name") String verifiedName,
        @JsonProperty("id") String id,
        @JsonProperty("code_verification_status") String codeVerificationStatus,
        @JsonProperty("name_status") NameStatusType nameStatus,
        @JsonProperty("platform_type") PlatformType platformType,
        @JsonProperty("throughput") Throughput throughput) {}
