package com.fincity.saas.commons.jooq.gson;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jooq.types.UNumber;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class UNumberAdapter<R extends UNumber> extends TypeAdapter<R> {

    private final Class<R> clazz;
    private final Method method;

    public UNumberAdapter(Class<R> clazz) {
        this.clazz = clazz;
        try {
            this.method = this.clazz.getDeclaredMethod("valueOf", String.class);
        } catch (Exception e) {
            throw new RuntimeException("valueOf method not found in " + clazz.getName(), e);
        }
    }

    @Override
    public void write(JsonWriter out, R value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.toString());
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
            throw new RuntimeException("Unable to convert " + value + " to " + this.clazz.getSimpleName(), e);
        }
    }
}
