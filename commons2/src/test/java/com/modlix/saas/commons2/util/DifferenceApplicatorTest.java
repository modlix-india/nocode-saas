package com.modlix.saas.commons2.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.commons2.util.DifferenceApplicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DifferenceApplicatorTest {

    @Test
    void testApply() {
        Map<String, String> base = Map.of("a1", "b1", "a2", "b2", "a3", "b3");
        Map<String, String> ovr = new HashMap<>(Map.of("a1", "c1"));
        ovr.put("a2", null);

        Map<String, ?> result = DifferenceApplicator.apply(ovr, base);

        Map<String, ?> expected = new HashMap<>(Map.of("a1", "c1", "a3", "b3"));

        assertNotNull(result);
        assertEquals(expected, result);
    }
}
