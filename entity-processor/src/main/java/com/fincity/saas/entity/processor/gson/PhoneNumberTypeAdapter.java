package com.fincity.saas.entity.processor.gson;

import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class PhoneNumberTypeAdapter extends TypeAdapter<PhoneNumber> {

    @Override
    public void write(JsonWriter out, PhoneNumber value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (value.getCountryCode() != null && value.getNumber() != null) {
            out.beginObject();
            out.name(PhoneNumber.Fields.countryCode);
            out.value(value.getCountryCode());
            out.name(PhoneNumber.Fields.number);
            out.value(value.getNumber());
            out.endObject();
        } else if (value.getNumber() != null) {
            out.value(value.getNumber());
        } else {
            out.nullValue();
        }
    }

    @Override
    public PhoneNumber read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return PhoneNumber.of(null);
        }

        if (token == JsonToken.STRING) {
            return PhoneNumber.of(in.nextString());
        }

        if (token == JsonToken.BEGIN_OBJECT) {
            in.beginObject();
            Integer countryCode = null;
            String number = null;

            while (in.hasNext()) {
                String fieldName = in.nextName();
                if (PhoneNumber.Fields.countryCode.equals(fieldName)) {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                    } else {
                        countryCode = in.nextInt();
                    }
                } else if (PhoneNumber.Fields.number.equals(fieldName)) {
                    number = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            return PhoneNumber.of(countryCode, number);
        }

        in.skipValue();
        return PhoneNumber.of(null);
    }
}

