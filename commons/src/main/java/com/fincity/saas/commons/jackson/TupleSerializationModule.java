package com.fincity.saas.commons.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuple6;
import reactor.util.function.Tuple7;
import reactor.util.function.Tuple8;
import reactor.util.function.Tuples;

public class TupleSerializationModule extends SimpleModule {

	private static final long serialVersionUID = 380051999564048056L;

	@SuppressWarnings("rawtypes")
	public TupleSerializationModule() {

		super();

		this.addSerializer(Tuple2.class, new JsonSerializer<Tuple2>() {

			@Override
			public void serialize(Tuple2 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple3.class, new JsonSerializer<Tuple3>() {

			@Override
			public void serialize(Tuple3 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple4.class, new JsonSerializer<Tuple4>() {

			@Override
			public void serialize(Tuple4 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple5.class, new JsonSerializer<Tuple5>() {

			@Override
			public void serialize(Tuple5 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple6.class, new JsonSerializer<Tuple6>() {

			@Override
			public void serialize(Tuple6 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple7.class, new JsonSerializer<Tuple7>() {

			@Override
			public void serialize(Tuple7 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		this.addSerializer(Tuple8.class, new JsonSerializer<Tuple8>() {

			@Override
			public void serialize(Tuple8 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				writeTuple(value, gen);
			}
		});

		// One deserializer for every arity. `Tuples.fromArray` already returns the
		// right concrete type and the body below accepts 2..8 elements, so this was
		// only ever a registration gap -- but Jackson picks a deserializer by the
		// DECLARED type, and Tuple3 is not a subclass lookup away from Tuple2 as far
		// as the registry is concerned. Serializers were registered for all eight
		// from the start; deserializers stopped at Tuple2, so the first service to
		// return a Tuple3 over the wire got "cannot deserialize from Array value"
		// and every request through it failed.

		JsonDeserializer<Tuple2> tupleDeserializer = new JsonDeserializer<Tuple2>() {

			@Override
			public Tuple2 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {

				ObjectMapper mapper = (ObjectMapper) jp.getCodec();
				JsonNode node = mapper.readTree(jp);
				List<Object> result = new ArrayList<>();
				if (node != null) {
					if (node instanceof ArrayNode arrayNode) {
						for (var elementNode : arrayNode) {
							result.add(mapper.readValue(elementNode.traverse(mapper), Object.class));
						}
					} else {
						result.add(mapper.readValue(node.traverse(mapper), Object.class));
					}
				}

				if (result.size() < 2 || result.size() > 8)
					throw new IOException(
					        "Tuple can have min 2 and max 8 elements but found : " + result.size() + " elements");

				return Tuples.fromArray(result.toArray());
			}
		};

		this.addDeserializer(Tuple2.class, tupleDeserializer);
		this.addDeserializer(Tuple3.class, (JsonDeserializer) tupleDeserializer);
		this.addDeserializer(Tuple4.class, (JsonDeserializer) tupleDeserializer);
		this.addDeserializer(Tuple5.class, (JsonDeserializer) tupleDeserializer);
		this.addDeserializer(Tuple6.class, (JsonDeserializer) tupleDeserializer);
		this.addDeserializer(Tuple7.class, (JsonDeserializer) tupleDeserializer);
		this.addDeserializer(Tuple8.class, (JsonDeserializer) tupleDeserializer);
	}

	@SuppressWarnings("rawtypes")
	private static void writeTuple(Tuple2 value, JsonGenerator gen) throws IOException {
		gen.writeStartArray();
		for (Object e : value.toArray()) {
			gen.writeObject(e);
		}
		gen.writeEndArray();
	}
}
