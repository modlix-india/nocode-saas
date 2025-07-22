package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyCode(@JsonProperty("code") String code) {}
