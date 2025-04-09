package com.fincity.saas.commons.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

class TupleSerializationModuleTest {

    @Test
    void test() throws IOException {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new TupleSerializationModule());

        Tuple2<Integer, String> tup2 = Tuples.of(23, "Kiran");

        String json = om.writeValueAsString(tup2);

        assertEquals(tup2, om.readValue(json.getBytes(), new TypeReference<Tuple2<Integer, String>>() {}));

        TreeMap<String, String> lmp = new TreeMap<>(Map.of("own", "own", "oth", "oth"));

        Tuple4<Integer, String, Boolean, Map<String, String>> tup4 = Tuples.of(23, "Kiran", true, lmp);

        json = om.writeValueAsString(tup4);

        assertEquals(tup4, om.readValue(json.getBytes(), new TypeReference<Tuple2<Integer, String>>() {}));
    }
}
