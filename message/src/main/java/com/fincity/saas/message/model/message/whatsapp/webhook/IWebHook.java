package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public final class IWebHook {

    private static final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private IWebHook() {}

    public static Mono<IWebHookEvent> constructEvent(String payload) {
        try {
            return Mono.just(mapper.readValue(payload, IWebHookEvent.class));
        } catch (Exception e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Failed to parse webhook payload", e);
        }
    }
}
