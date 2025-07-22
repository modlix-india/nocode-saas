package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TwoStepCode(@JsonProperty("pin") String pin) {}
