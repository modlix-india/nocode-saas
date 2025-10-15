package com.fincity.saas.commons.jooq.gson;

import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jooq.types.UNumber;
import org.springframework.http.HttpStatus;

public class UNumberAdapter<R extends UNumber> extends TypeAdapter<R> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5949106876495442794L;

    private final Class<R> clazz;
    private final transient Method method;

    public UNumberAdapter(Class<R> clazz) {
        this.clazz = clazz;
        try {
            this.method = this.clazz.getDeclaredMethod("valueOf", String.class);
        } catch (Exception e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not find the valueOf method");
        }
    }

    @Override
    public void write(JsonWriter out, R value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public R read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String value = in.nextString();
        try {
            return (R) this.method.invoke(null, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to convert " + value + " to " + this.clazz.getSimpleName(),
                    e);
        }
    }
}
