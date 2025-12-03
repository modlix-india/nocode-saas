package com.fincity.saas.entity.processor.gson;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigInteger;

public class IdentityTypeAdapter extends TypeAdapter<Identity> {

    @Override
    public void write(JsonWriter out, Identity value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (value.getId() != null && value.getCode() != null) {
            out.beginObject();
            out.name(Identity.Fields.id);
            out.value(value.getId().toString());
            out.name(Identity.Fields.code);
            out.value(value.getCode());
            out.endObject();
        } else if (value.getId() != null) {
            out.value(value.getId().toString());
        } else if (value.getCode() != null) {
            out.value(value.getCode());
        } else {
            out.nullValue();
        }
    }

    @Override
    public Identity read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return Identity.ofNull();
        }

        if (token == JsonToken.NUMBER) {
            // Read the number as a string to preserve precision for large numbers
            // Gson allows reading numbers as strings
            String numberStr = in.nextString();
            try {
                return Identity.of(new BigInteger(numberStr));
            } catch (NumberFormatException e) {
                // If it's not a valid BigInteger (e.g., has decimal point), treat as code
                return Identity.of(numberStr);
            }
        }

        if (token == JsonToken.STRING) {
            String str = in.nextString();
            try {
                return Identity.of(new BigInteger(str));
            } catch (NumberFormatException e) {
                return Identity.of(str);
            }
        }

        if (token == JsonToken.BEGIN_OBJECT) {
            in.beginObject();
            BigInteger id = null;
            String code = null;

            while (in.hasNext()) {
                String fieldName = in.nextName();
                if (Identity.Fields.id.equals(fieldName)) {
                    String idStr = in.nextString();
                    try {
                        id = new BigInteger(idStr);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else if (Identity.Fields.code.equals(fieldName)) {
                    code = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            return Identity.of(id, code);
        }

        in.skipValue();
        return Identity.ofNull();
    }
}

