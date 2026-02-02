package com.fincity.saas.commons.gson;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class LocalDateAdapter extends TypeAdapter<LocalDate> {

    @Override
    public void write(JsonWriter out, LocalDate value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        long epochSecondsAtStartOfDayUtc = value.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        out.value(epochSecondsAtStartOfDayUtc);
    }

    @Override
    public LocalDate read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String secondsStr = in.nextString();
        long epochSeconds = Long.parseLong(secondsStr);
        return LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC).toLocalDate();
    }
}
