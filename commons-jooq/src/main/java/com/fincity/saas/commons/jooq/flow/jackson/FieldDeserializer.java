package com.fincity.saas.commons.jooq.flow.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fincity.saas.commons.util.Case;

public class FieldDeserializer extends StdDeserializer<Map<String, Object>> {

    private static final UnaryOperator<String> dbFieldConverter = Case.SCREAMING_SNAKE_CASE.getConverter();

    protected FieldDeserializer(StdDeserializer<Map<String, Object>> src) {
        super(src);
    }

    @Override
    public Map<String, Object> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {

        ObjectCodec codec = jsonParser.getCodec();
        JsonNode node = codec.readTree(jsonParser);
        return readFieldMap(node);
    }

    private Map<String, Object> readFieldMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull() || !jsonNode.isObject()) return new LinkedHashMap<>();

        Map<String, Object> result = LinkedHashMap.newLinkedHashMap(jsonNode.size());

        jsonNode.fields().forEachRemaining(field -> {
            String key = dbFieldConverter.apply(field.getKey());
            result.put(key, extractValue(field.getValue()));
        });

        return result;
    }

    private List<Object> readFieldList(JsonNode node) {
        List<Object> list = new ArrayList<>(node.size());

        node.forEach(item -> list.add(extractValue(item)));

        return list;
    }

    private Object extractValue(JsonNode valueNode) {
        if (valueNode.isObject()) return this.readFieldMap(valueNode);
        else if (valueNode.isArray()) return this.readFieldList(valueNode);
        else if (valueNode.isNumber()) return valueNode.numberValue();
        else if (valueNode.isBoolean()) return valueNode.booleanValue();
        else if (valueNode.isTextual()) return valueNode.textValue();
        else if (valueNode.isNull()) return null;
        else return valueNode.toString();
    }
}
