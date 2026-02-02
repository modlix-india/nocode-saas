package com.modlix.saas.commons2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.commons2.util.DifferenceExtractor;
import com.modlix.saas.commons2.util.HashMapUtil;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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

		Map<String, ?> result = DifferenceExtractor.extract(inc, base);

		assertNotNull(result);
		assertEquals(expected, result);
	}

	@Test
	void testExtractMap1() {

		Map<String, Object> incoming = Map.of("check", "what1", "checkMap1", Map.of("x", 2, "y", Map.of("a", 3)));
		Map<String, Object> base = Map.of("check", "what1", "checkMap", Map.of("x", 2, "y", Map.of("a", 3)));

		Map<String, ?> result = DifferenceExtractor.extract(incoming, base);

		HashMap<String, Object> expected = new HashMap<>();
		expected.put("checkMap", null);
		expected.put("checkMap1", (Object) Map.of("x", 2, "y", Map.of("a", 3)));

		assertNotNull(result);
		assertEquals(expected, result);
	}

	@Test
	void testExtractMap2() {

		Map<String, Object> incoming = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));
		Map<String, Object> base = Map.of("check", "what", "checkMap", Map.of("x", 2, "y", Map.of("a", 3)));

		Map<String, ?> result = DifferenceExtractor.extract(incoming, base);

		HashMap<String, Object> expected = new HashMap<>();
		expected.put("checkMap", (Object) Map.of("x", 12, "y", Map.of("a", 12)));

		assertNotNull(result);
		assertEquals(expected, result);
	}

	@Test
	void testExtractSame() {

		Map<String, Object> incoming = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));
		Map<String, Object> base = Map.of("check", "what", "checkMap", Map.of("x", 12, "y", Map.of("a", 12)));

		Map<String, ?> result = DifferenceExtractor.extract(incoming, base);

		Map<String, Object> expected = Map.of();

		assertNotNull(result);
		assertEquals(expected, result);
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

		Map<String, Boolean> result = DifferenceExtractor.extractMapBoolean(incMap, existingMap);

		assertNotNull(result);
		assertEquals(resultMap, result);
	}

}
