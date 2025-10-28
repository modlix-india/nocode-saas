package com.fincity.saas.commons.jooq.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Serial;

public class GsonSerializationModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 7869369711866671282L;

    private final Gson gson;

    public GsonSerializationModule(Gson gson) {
        super("GsonSerializationModule");
        this.gson = gson;
        this.addSerializer(JsonElement.class, new GsonJsonElementSerializer());
        this.addDeserializer(JsonElement.class, new GsonJsonElementDeserializer());
    }

    public static class GsonJsonElementSerializer extends StdSerializer<JsonElement> {

        @Serial
        private static final long serialVersionUID = 1831673844416475247L;

        protected GsonJsonElementSerializer() {
            super(JsonElement.class);
        }

        @Override
        public void serialize(
                JsonElement jsonElement, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException {

            String data = jsonElement.getAsString();

            jsonGenerator.writeString(data);
        }
    }

    public class GsonJsonElementDeserializer extends StdDeserializer<JsonElement> {

        @Serial
        private static final long serialVersionUID = 932551376013276255L;

        protected GsonJsonElementDeserializer() {
            super(JsonElement.class);
        }

        @Override
        public JsonElement deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JacksonException {

            String str = jsonParser.getValueAsString();

            return gson.fromJson(str, JsonElement.class);
        }
    }
}
