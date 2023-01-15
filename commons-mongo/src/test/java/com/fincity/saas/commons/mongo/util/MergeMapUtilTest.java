package com.fincity.saas.commons.mongo.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MergeMapUtilTest {

	@Test
	void testMerge() {
	
		Map<String, String> map1 = Map.of("var1", "val1");
		Map<String, String> map2 = Map.of("var2", "val2");
		
		Map<String, String> out = MergeMapUtil.merge(map1, map2);
		
		assertEquals(Map.of("var1", "val1", "var2", "val2"), out);
		
		map1 = Map.of("var1", "val1");
		map2 = Map.of("var1", "val2");
		
		out = MergeMapUtil.merge(map1, map2);
		
		assertEquals(Map.of("var1", "val2"), out);
		
		Map<String, Map<String, String>> dmap1 = Map.of("map1", Map.of("var1", "val1"));
		Map<String, Map<String, String>> dmap2 = Map.of("map2", Map.of("var1", "val1"));
		
		Map<String, Map<String, String>> dout = MergeMapUtil.merge(dmap1, dmap2);
		
		assertEquals(Map.of("map1", Map.of("var1", "val1"), "map2", Map.of("var1", "val1")), dout);
		
		
		dmap1 = Map.of("map1", Map.of("var1", "val1"));
		dmap2 = Map.of("map1", Map.of("var1", "val2", "var2", "vil2"));
		
		dout = MergeMapUtil.merge(dmap1, dmap2);
		
		assertEquals(Map.of("map1", Map.of("var1", "val2", "var2", "vil2")), dout);
	}
}
