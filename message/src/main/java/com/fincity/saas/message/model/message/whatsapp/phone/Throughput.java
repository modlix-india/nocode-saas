package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.phone.type.LevelType;

@JsonInclude(value = Include.NON_NULL)
public record Throughput(@JsonProperty("level") LevelType Level) {}
