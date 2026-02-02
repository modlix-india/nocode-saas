package com.modlix.saas.commons2.jackson;

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

import com.modlix.saas.commons2.util.Tuples;

public class TupleSerializationModule extends SimpleModule {

    private static final long serialVersionUID = 380051999564048056L;

    @SuppressWarnings("rawtypes")
    public TupleSerializationModule() {

        super();

        this.addSerializer(Tuples.Tuple2.class, new JsonSerializer<Tuples.Tuple2>() {

            @Override
            public void serialize(Tuples.Tuple2 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple3.class, new JsonSerializer<Tuples.Tuple3>() {

            @Override
            public void serialize(Tuples.Tuple3 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple4.class, new JsonSerializer<Tuples.Tuple4>() {

            @Override
            public void serialize(Tuples.Tuple4 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple5.class, new JsonSerializer<Tuples.Tuple5>() {

            @Override
            public void serialize(Tuples.Tuple5 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple6.class, new JsonSerializer<Tuples.Tuple6>() {

            @Override
            public void serialize(Tuples.Tuple6 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple7.class, new JsonSerializer<Tuples.Tuple7>() {

            @Override
            public void serialize(Tuples.Tuple7 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addSerializer(Tuples.Tuple8.class, new JsonSerializer<Tuples.Tuple8>() {

            @Override
            public void serialize(Tuples.Tuple8 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                writeTuple(value, gen);
            }
        });

        this.addDeserializer(Tuples.Tuple2.class, new JsonDeserializer<Tuples.Tuple2>() {

            @Override
            public Tuples.Tuple2 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple2) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple3.class, new JsonDeserializer<Tuples.Tuple3>() {

            @Override
            public Tuples.Tuple3 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple3) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple4.class, new JsonDeserializer<Tuples.Tuple4>() {

            @Override
            public Tuples.Tuple4 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple4) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple5.class, new JsonDeserializer<Tuples.Tuple5>() {

            @Override
            public Tuples.Tuple5 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple5) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple6.class, new JsonDeserializer<Tuples.Tuple6>() {

            @Override
            public Tuples.Tuple6 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple6) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple7.class, new JsonDeserializer<Tuples.Tuple7>() {

            @Override
            public Tuples.Tuple7 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple7) deserializeTuple(jp, ctxt);
            }
        });

        this.addDeserializer(Tuples.Tuple8.class, new JsonDeserializer<Tuples.Tuple8>() {

            @Override
            public Tuples.Tuple8 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return (Tuples.Tuple8) deserializeTuple(jp, ctxt);
            }
        });
    }

    private static Tuples.ITuple deserializeTuple(JsonParser jp, DeserializationContext ctxt) throws IOException {
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

    private static void writeTuple(Tuples.ITuple value, JsonGenerator gen) throws IOException {
        gen.writeStartArray();
        for (Object e : value.toArray()) {
            gen.writeObject(e);
        }
        gen.writeEndArray();
    }
}
