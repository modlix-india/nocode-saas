package com.modlix.saas.adzump.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/**
 * Plain JUnit 5 tests for the RFC 7386 merge-patch semantics (no Spring
 * context). Covers nested merge, null-removes-key, wholesale array replacement,
 * and deep additive merge, plus a couple of edge cases.
 */
class JsonMergePatchUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Bad test JSON: " + text, e);
        }
    }

    @Test
    void mergesNestedObjectsKeyByKey() {

        JsonNode target = json("{\"a\":{\"x\":1,\"y\":2},\"b\":5}");
        JsonNode patch = json("{\"a\":{\"y\":9,\"z\":3}}");

        JsonNode result = JsonMergePatchUtil.merge(target, patch);

        assertEquals(json("{\"a\":{\"x\":1,\"y\":9,\"z\":3},\"b\":5}"), result);
    }

    @Test
    void nullMemberRemovesTheKey() {

        JsonNode target = json("{\"a\":1,\"b\":2,\"c\":{\"d\":4}}");
        JsonNode patch = json("{\"b\":null,\"c\":null}");

        JsonNode result = JsonMergePatchUtil.merge(target, patch);

        assertEquals(json("{\"a\":1}"), result);
    }

    @Test
    void arraysAreReplacedWholesaleNotMerged() {

        JsonNode target = json("{\"list\":[1,2,3],\"keep\":true}");
        JsonNode patch = json("{\"list\":[9]}");

        JsonNode result = JsonMergePatchUtil.merge(target, patch);

        assertEquals(json("{\"list\":[9],\"keep\":true}"), result);
    }

    @Test
    void deeplyAddsNewLeavesWithoutDroppingSiblings() {

        JsonNode target = json("{\"a\":{\"b\":{\"c\":1}}}");
        JsonNode patch = json("{\"a\":{\"b\":{\"d\":2},\"e\":3}}");

        JsonNode result = JsonMergePatchUtil.merge(target, patch);

        assertEquals(json("{\"a\":{\"b\":{\"c\":1,\"d\":2},\"e\":3}}"), result);
    }

    @Test
    void nonObjectPatchReplacesTargetWholesale() {

        JsonNode target = json("{\"a\":1}");

        assertEquals(json("42"), JsonMergePatchUtil.merge(target, json("42")));
        assertEquals(json("[1,2]"), JsonMergePatchUtil.merge(target, json("[1,2]")));
    }

    @Test
    void nullPatchIsANoOpReturningTheSameTarget() {

        JsonNode target = json("{\"a\":1}");

        assertSame(target, JsonMergePatchUtil.merge(target, null));
    }

    @Test
    void doesNotMutateTheInputTarget() {

        JsonNode target = json("{\"a\":{\"x\":1}}");
        JsonNode patch = json("{\"a\":{\"y\":2},\"a2\":9}");

        JsonMergePatchUtil.merge(target, patch);

        // Original target is untouched: no new key, nested object unchanged.
        assertNull(target.get("a2"));
        assertEquals(json("{\"a\":{\"x\":1}}"), target);
    }
}
