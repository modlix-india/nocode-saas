package com.fincity.saas.commons.jooq.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jooq.types.UNumber;
import org.springframework.http.HttpStatus;

public class UNumberDeserializer<R extends UNumber> extends StdDeserializer<R> {

    private static final long serialVersionUID = -2888640386444756529L;

    private final Class<R> classs;

    private transient Method method;

    private transient AbstractMessageService msgResource;

    public UNumberDeserializer(Class<R> classs, AbstractMessageService msgResource) {
        super((Class<?>) null);
        this.classs = classs;
        try {
            this.method = this.classs.getDeclaredMethod("valueOf", String.class);
        } catch (Exception e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    msgResource.getDefaultLocaleMessage(AbstractMessageService.VALUEOF_METHOD_NOT_FOUND));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public R deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        String str = p.getValueAsString();

        try {
            return (R) this.method.invoke(null, str);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    msgResource.getDefaultLocaleMessage(
                            AbstractMessageService.UNABLE_TO_CONVERT, str, this.classs.getSimpleName()));
        }
    }
}
