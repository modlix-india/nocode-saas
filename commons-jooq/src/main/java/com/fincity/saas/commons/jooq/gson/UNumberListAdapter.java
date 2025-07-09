package com.fincity.saas.commons.jooq.gson;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.jooq.types.UNumber;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

public class UNumberListAdapter<R extends UNumber> extends AbstractListAdapter<R> {

    @Serial
    private static final long serialVersionUID = 8515192379851188483L;

    protected UNumberListAdapter(Function<String, R> deserializer) {
        super(deserializer);
    }

    @SuppressWarnings("unchecked")
    public UNumberListAdapter(Class<R> clazz) {
        super(value -> {
            try {
                Method method = clazz.getDeclaredMethod("valueOf", String.class);
                return (R) method.invoke(null, value);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new GenericException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to convert " + value + " to " + clazz.getSimpleName(),
                        e);
            }
        });
    }

    @Override
    protected String serializeItem(R item) {
        return item.toString();
    }
}
