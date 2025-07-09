package com.fincity.saas.commons.jooq.gson;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public abstract class AbstractListAdapter<T> extends TypeAdapter<List<T>> implements Serializable {

    private final Function<String, T> deserializer;

    protected AbstractListAdapter(Function<String, T> deserializer) {
        this.deserializer = deserializer;
    }

    protected abstract String serializeItem(T item);

    @Override
    public void write(JsonWriter out, List<T> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginArray();
        for (T item : value) {
            if (item == null) {
                out.nullValue();
            } else {
                out.value(serializeItem(item));
            }
        }
        out.endArray();
    }

    @Override
    public List<T> read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        List<T> result = new ArrayList<>();

        if (token == JsonToken.NULL) {
            in.nextNull();
            return result;
        }

        if (token == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            while (in.hasNext()) {
                token = in.peek();

                if (token == JsonToken.NULL) {
                    in.nextNull();
                    continue;
                }

                String value = in.nextString();
                parseAndAddValues(result, value);
            }
            in.endArray();
        } else {
            String value = in.nextString();
            parseAndAddValues(result, value);
        }

        return result;
    }

    private void parseAndAddValues(List<T> result, String value) {
        if (value.contains(",")) {
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(deserializer)
                    .forEach(result::add);
        } else if (!value.isEmpty()) {
            result.add(deserializer.apply(value));
        }
    }
}
