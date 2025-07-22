package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.RestrictionType;

public record RestrictionInfo(
        @JsonProperty("restriction_type") RestrictionType restrictionType,
        @JsonProperty("expiration") String expiration) {}
