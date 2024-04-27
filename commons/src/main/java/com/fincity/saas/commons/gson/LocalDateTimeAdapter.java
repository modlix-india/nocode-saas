package com.fincity.saas.commons.gson;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

    @Override
    public void write(JsonWriter out, LocalDateTime value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.toEpochSecond(ZoneOffset.UTC));
    }

    @Override
    public LocalDateTime read(JsonReader in) throws IOException {

        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String date = in.nextString();
        return Instant.ofEpochSecond(0, Long.parseLong(date)).atZone(ZoneOffset.UTC).toLocalDateTime();
    }
}