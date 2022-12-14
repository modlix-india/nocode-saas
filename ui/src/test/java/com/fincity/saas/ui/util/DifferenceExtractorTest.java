package com.fincity.saas.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.mongo.util.DifferenceExtractor;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DifferenceExtractorTest {

	@Test
	void testExtractMap1() {

		Map<String, Object> incoming = Map.of("check", "what1", "checkMap1", Map.of("x", 2, "y", Map.of("a", 3)));
		Map<String, Object> base = Map.of("check", "what1", "checkMap", Map.of("x", 2, "y", Map.of("a", 3)));

		Mono<Map<String, ?>> x = DifferenceExtractor.extract(incoming, base);

		HashMap<String, Object> expected = new HashMap<>();
		expected.put("checkMap", null);
		expected.put("checkMap1", (Object) Map.of("x", 2, "y", Map.of("a", 3)));

		StepVerifier.create(x)
		        .assertNext(y -> assertEquals(expected, y))
		        .verifyComplete();
	}

	@Test
	void testExtractMap2() {

		Map<String, Object> incoming = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));
		Map<String, Object> base = Map.of("check", "what", "checkMap", Map.of("x", 2, "y", Map.of("a", 3)));

		System.out.println(incoming);
		System.out.println(base);

		Mono<Map<String, ?>> x = DifferenceExtractor.extract(incoming, base);

		HashMap<String, Object> expected = new HashMap<>();
		expected.put("checkMap", (Object) Map.of("x", 12, "y", Map.of("a", 12)));
		System.out.println(expected);

		StepVerifier.create(x)
		        .assertNext(y -> assertEquals(expected, y))
		        .verifyComplete();
	}

}
