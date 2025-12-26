package com.fincity.saas.entity.processor.gson;

import com.fincity.saas.entity.processor.model.common.Email;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class EmailTypeAdapter extends TypeAdapter<Email> {

    @Override
    public void write(JsonWriter out, Email value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        if (value.getAddress() != null) {
            out.value(value.getAddress());
        } else {
            out.nullValue();
        }
    }

    @Override
    public Email read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return Email.of(null);
        }

        if (token == JsonToken.STRING) {
            return Email.of(in.nextString());
        }

        if (token == JsonToken.BEGIN_OBJECT) {
            in.beginObject();
            String address = null;

            while (in.hasNext()) {
                String fieldName = in.nextName();
                if (Email.Fields.address.equals(fieldName)) {
                    address = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            return Email.of(address);
        }

        in.skipValue();
        return Email.of(null);
    }
}
