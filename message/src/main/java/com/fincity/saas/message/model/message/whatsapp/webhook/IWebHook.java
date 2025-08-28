package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
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
        } catch (InvalidDefinitionException ex) {
            System.err.println("InvalidDefinitionException: " + ex.getMessage());
            throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid webhook payload", ex);
        } catch (Exception e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Failed to parse webhook payload", e);
        }

    }
}
