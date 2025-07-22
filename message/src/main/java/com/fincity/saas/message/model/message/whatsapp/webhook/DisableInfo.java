package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DisableInfo(@JsonProperty("disable_date") String disableDate) {}
