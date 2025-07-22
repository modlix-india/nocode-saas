package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Contact(
        @JsonProperty("profile") Profile profile,
        @JsonProperty("name") Name name,
        @JsonProperty("phones") List<Phone> phones,
        @JsonProperty("wa_id") String waId) {}
