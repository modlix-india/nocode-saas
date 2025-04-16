package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.*;
import static com.fincity.saas.commons.util.EqualsUtil.safeEquals;

import org.junit.jupiter.api.Test;

class EqualsUtilTest {

	@Test
	void test() {
		
		assertFalse(safeEquals(null, "kiran"));
		assertFalse(safeEquals("kiran", null));
		assertTrue(safeEquals(2, 2));
		assertFalse(safeEquals(null, 2));
		assertTrue(safeEquals("kiran", "kiran"));
	}

}
