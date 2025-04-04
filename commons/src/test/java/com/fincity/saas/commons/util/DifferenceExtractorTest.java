package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.HashMapUtil;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DifferenceExtractorTest {

	@Test
	void testExtractObjectObject() {

		Map<String, Object> base = Map.of("a", 20, "b", 30, "c", Map.of("k", 1, "i", 2), "d", Map.of("k", 1, "i", 2),
				"e", Map.of("k", 1, "i", 2));

		Map<String, Object> inc = Map.of("a", 20, "c", Map.of("k", 1, "i", 2), "d", Map.of("k", 2), "f",
				Map.of("k", 2, "i", 3));

		Map<String, Object> expected = new HashMap<>();
		expected.put("b", null);
		expected.put("d", HashMapUtil.of("i", null, "k", 2));
		expected.put("e", null);
		expected.put("f", Map.of("k", 2, "i", 3));

		Mono<Map<String, ?>> x = DifferenceExtractor.extract(inc, base);

		StepVerifier.create(x)
				.assertNext(y -> assertEquals(expected, y))
				.verifyComplete();
	}

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

		Mono<Map<String, ?>> x = DifferenceExtractor.extract(incoming, base);

		HashMap<String, Object> expected = new HashMap<>();
		expected.put("checkMap", (Object) Map.of("x", 12, "y", Map.of("a", 12)));

		StepVerifier.create(x)
				.assertNext(y -> assertEquals(expected, y))
				.verifyComplete();
	}

	@Test
	void testExtractSame() {

		Map<String, Object> incoming = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));
		Map<String, Object> base = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));

		Mono<Map<String, ?>> x = DifferenceExtractor.extract(incoming, base);

		Map<String, Object> expected = Map.of();

		StepVerifier.create(x)
				.assertNext(y -> assertEquals(expected, y))
				.verifyComplete();
	}

	@Test
	void testMapBoolean() {

		HashMap<String, Boolean> incMap = new HashMap<>(Map.of("a", true, "b", false));
		HashMap<String, Boolean> existingMap = new HashMap<>(Map.of("a", true, "b", true, "c", true));

		incMap.put("c", null);
		incMap.put("d", null);

		existingMap.put("d", null);

		HashMap<String, Boolean> resultMap = new HashMap<>(Map.of("b", false));
		resultMap.put("c", null);

		StepVerifier.create(DifferenceExtractor.extractMapBoolean(incMap, existingMap))
				.assertNext(y -> assertEquals(resultMap, y))
				.verifyComplete();
	}

}
