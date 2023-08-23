package com.fincity.saas.commons.mongo.jackson;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fincity.nocode.kirun.engine.json.schema.type.MultipleType;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.SingleType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;

public class KIRuntimeSerializationModule extends SimpleModule {

	private static final long serialVersionUID = -4161587607031755887L;

	public KIRuntimeSerializationModule() {

		this.addDeserializer(Type.class, new JsonDeserializer<Type>() {

			@Override
			public Type deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {

				ObjectMapper mapper = (ObjectMapper) jp.getCodec();
				JsonNode node = mapper.readTree(jp);

				HashSet<SchemaType> result = new HashSet<>();
				if (node instanceof ArrayNode arrayNode) {

					for (var elementNode : arrayNode) {
						result.add(mapper.readValue(elementNode.traverse(mapper), SchemaType.class));
					}

					return new MultipleType().setType(result);
				} else {
					return Type.of(mapper.readValue(node.traverse(mapper), SchemaType.class));
				}
			}
		});

		this.addSerializer(Type.class, new JsonSerializer<Type>() {

			@Override
			public void serialize(Type value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

				if (value instanceof MultipleType mt) {
					gen.writeStartArray();
					for (SchemaType e : mt.getAllowedSchemaTypes()) {
						gen.writeObject(e);
					}
					gen.writeEndArray();
				} else {
					gen.writeObject(((SingleType) value).getType());
				}
			}

		});
	}
}
