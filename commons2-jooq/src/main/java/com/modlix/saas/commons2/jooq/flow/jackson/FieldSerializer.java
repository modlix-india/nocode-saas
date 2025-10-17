package com.modlix.saas.commons2.jooq.flow.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.modlix.saas.commons2.util.Case;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class FieldSerializer extends StdSerializer<Map<String, Object>> {

    private static final UnaryOperator<String> dbFieldConverter = Case.CAMEL.getConverter();

    protected FieldSerializer(StdSerializer<Map<String, Object>> src) {
        super(src);
    }

    @Override
    public void serialize(
            Map<String, Object> stringObjectMap, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {

        jsonGenerator.writeStartObject();
        this.writeMap(stringObjectMap, jsonGenerator, serializerProvider);
        jsonGenerator.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private void writeMap(Map<String, Object> map, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = dbFieldConverter.apply(entry.getKey());
            Object value = entry.getValue();

            switch (value) {
                case Map<?, ?> nestedMap -> {
                    jsonGenerator.writeFieldName(key);
                    jsonGenerator.writeStartObject();
                    this.writeMap((Map<String, Object>) nestedMap, jsonGenerator, serializerProvider);
                    jsonGenerator.writeEndObject();
                }
                case List<?> nestedList -> {
                    jsonGenerator.writeArrayFieldStart(key);
                    this.writeList((List<Object>) nestedList, jsonGenerator, serializerProvider);
                    jsonGenerator.writeEndArray();
                }
                default -> jsonGenerator.writeObjectField(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeList(List<Object> list, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {

        for (Object value : list) {
            switch (value) {
                case Map<?, ?> nestedMap -> {
                    jsonGenerator.writeStartObject();
                    this.writeMap((Map<String, Object>) nestedMap, jsonGenerator, serializerProvider);
                    jsonGenerator.writeEndObject();
                }
                case List<?> nestedList -> {
                    jsonGenerator.writeStartArray();
                    this.writeList((List<Object>) nestedList, jsonGenerator, serializerProvider);
                    jsonGenerator.writeEndArray();
                }
                default -> jsonGenerator.writeObject(value);
            }
        }
    }
}
