package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Data
@NoArgsConstructor
@FieldNameConstants
@JsonSerialize(using = IdAndValue.IdAndValueSerializer.class)
@JsonDeserialize(using = IdAndValue.IdAndValueDeserializer.class)
public class IdAndValue<I extends Serializable, U extends Serializable> implements Serializable {

    public static final String ID_CACHE_KEY = "idAndValue";
    public static final String VALUE_CACHE_KEY = "valueAndId";

    @Serial
    private static final long serialVersionUID = 4741758940431882981L;

    private I id;
    private U value;

    public IdAndValue(I id, U value) {
        this.id = id;
        this.value = value;
    }

    public static <I extends Serializable, U extends Serializable> IdAndValue<I, U> of(I id, U value) {
        return new IdAndValue<>(id, value);
    }

    public static <I extends Serializable, U extends Serializable> Map<I, U> toMap(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().collect(Collectors.toMap(IdAndValue::getId, IdAndValue::getValue, (a, b) -> b));
    }

    public static <I extends Serializable, U extends Serializable> Map<U, I> toValueMap(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().collect(Collectors.toMap(IdAndValue::getValue, IdAndValue::getId, (a, b) -> b));
    }

    public static <I extends Serializable, U extends Serializable> List<I> toIdList(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().map(IdAndValue::getId).toList();
    }

    public static <I extends Serializable, U extends Serializable> List<U> toValueList(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().map(IdAndValue::getValue).toList();
    }

    public Tuple2<I, U> toTuple() {
        return Tuples.of(id, value);
    }

    public static class IdAndValueSerializer extends JsonSerializer<IdAndValue<?, ?>> {

        @Override
        public void serialize(IdAndValue<?, ?> value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();

            if (value.getId() != null) gen.writeObjectField(Fields.id, value.getId());

            if (value.getValue() != null) gen.writeObjectField(Fields.value, value.getValue());

            gen.writeEndObject();
        }
    }

    public static class IdAndValueDeserializer extends JsonDeserializer<IdAndValue<?, ?>> {

        @Override
        public IdAndValue<?, ?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            JsonNode node = p.getCodec().readTree(p);

            JsonNode idNode = node.get(Fields.id);
            JsonNode valueNode = node.get(Fields.value);

            Serializable sId = deserializeToSerializable(idNode, ctxt);
            Serializable sValue = deserializeToSerializable(valueNode, ctxt);

            return new IdAndValue<>(sId, sValue);
        }

        private Serializable deserializeToSerializable(JsonNode node, DeserializationContext ctxt) throws IOException {

            if (node == null || node.isNull()) return null;

            if (node.isTextual()) return node.asText();

            if (node.isIntegralNumber()) {
                if (node.canConvertToLong()) {
                    long longValue = node.asLong();
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) return (int) longValue;
                    return longValue;
                }
                return node.asInt();
            }
            if (node.isFloatingPointNumber()) return node.asDouble();

            if (node.isBoolean()) return node.asBoolean();

            if (node.isObject() || node.isArray()) return (Serializable) ctxt.readTreeAsValue(node, Object.class);

            return node.asText();
        }
    }
}
