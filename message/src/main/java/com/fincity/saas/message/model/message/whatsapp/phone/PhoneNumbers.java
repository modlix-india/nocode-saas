package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.response.Paging;
import java.util.List;

public record PhoneNumbers(@JsonProperty("data") List<PhoneNumber> data, @JsonProperty("paging") Paging paging) {}
