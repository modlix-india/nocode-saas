package com.modlix.saas.commons2.jooq.jackson;

import java.io.IOException;
import java.io.Serial;

import org.jooq.JSON;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class JSONSerializationModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 6825752053176184501L;

    public JSONSerializationModule() {
        super();

        this.addSerializer(JSON.class, new JSONSerializer());
        this.addDeserializer(JSON.class, new JSONDeserialize());
    }

    public static class JSONSerializer extends StdSerializer<JSON> {

        protected JSONSerializer() {
            super(JSON.class);
        }

        @Override
        public void serialize(JSON value, JsonGenerator gen, SerializerProvider provider) throws IOException {

            String data = value.data();

            gen.writeRaw(data);
        }
    }

    public static class JSONDeserialize extends StdDeserializer<JSON> {

        protected JSONDeserialize() {
            super(JSON.class);
        }

        @Override
        public JSON deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            String str = p.getValueAsString();

            return JSON.jsonOrNull(str);
        }
    }
}

