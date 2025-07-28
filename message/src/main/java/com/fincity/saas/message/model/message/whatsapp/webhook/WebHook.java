package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class WebHook {

    private static final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private WebHook() {}

    public static WebHookEvent constructEvent(String payload) throws JsonProcessingException {
        return mapper.readValue(payload, WebHookEvent.class);
    }
}
