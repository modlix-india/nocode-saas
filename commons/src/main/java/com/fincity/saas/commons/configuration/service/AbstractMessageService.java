package com.fincity.saas.commons.configuration.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AbstractMessageService {

    protected static final String UKNOWN_ERROR = "unknown_error";

    public static final String VALUEOF_METHOD_NOT_FOUND = "valueof_method_not_found";
    public static final String UNABLE_TO_CONVERT = "unable_to_convert";
    public static final String UNKNOWN_ERROR_WITH_ID = "unknown_error_with_id";
    public static final String CANNOT_BE_UPDATED = "cannot_be_updated";

    public static final String OBJECT_NOT_FOUND = "object_not_found";
    public static final String FIELD_NOT_AVAILABLE = "Provided field(s)/value(s) are not applicable to the selected model"; 
    

    protected Map<Locale, ResourceBundle> bundleMap;

    protected AbstractMessageService(Map<Locale, ResourceBundle> bundle) {
        this.bundleMap = new ConcurrentHashMap<>(bundle);
    }

    public Mono<String> getMessage(final String messageId) {

        ResourceBundle defaultBundle = this.bundleMap.get(Locale.ENGLISH);
        return Mono.just(defaultBundle.getString(defaultBundle.containsKey(messageId) ? messageId : UKNOWN_ERROR));
    }

    public Mono<String> getMessage(String messageId, Object... params) {

        return this.getMessage(messageId)
                .map(e -> StringFormatter.format(e, params));
    }

    public GenericException nonReactiveMessage(Function<String, GenericException> genericExceptionFunction,
            String messageId, Object... params) {

        return genericExceptionFunction.apply(this.getDefaultLocaleMessage(messageId, params));
    }

    public <T> Mono<T> throwMessage(Function<String, GenericException> genericExceptionFunction, String messageId,
            Object... params) {

        return Mono.defer(

                () -> this.getMessage(messageId, params)
                        .map(genericExceptionFunction)
                        .flatMap(Mono::error)

        );
    }

    public <T> Flux<T> throwFluxMessage(Function<String, GenericException> genericExceptionFunction, String messageId,
            Object... params) {

        return Flux.defer(
                () -> Flux.from(this.getMessage(messageId, params))
                        .map(genericExceptionFunction)
                        .flatMap(Flux::error)

        );
    }

    public String getDefaultLocaleMessage(String messageId) {
        return this.getLocaleLocaleMessage(Locale.ENGLISH, messageId);
    }

    public String getDefaultLocaleMessage(String messageId, Object... params) {
        return this.getLocaleLocaleMessage(Locale.ENGLISH, messageId, params);
    }

    public String getLocaleLocaleMessage(Locale locale, String messageId) {
        return this.bundleMap.get(locale)
                .getString(messageId);
    }

    public String getLocaleLocaleMessage(Locale locale, String messageId, Object... params) {
        return StringFormatter.format(this.bundleMap.get(locale)
                .getString(messageId), params);
    }
}
