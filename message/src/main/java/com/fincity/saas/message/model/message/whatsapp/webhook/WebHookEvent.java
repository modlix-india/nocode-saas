package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WebHookEvent(@JsonProperty("entry") List<Entry> entry, @JsonProperty("object") String object) {}
