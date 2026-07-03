package com.modlix.saas.adzump.util;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * RFC 7386 (JSON Merge Patch) applied to config bodies (campaign overrides,
 * plan body patches). Jackson-only, no Spring dependencies.
 *
 * <p>Rules: a non-object patch replaces the target wholesale (arrays included);
 * an object patch is merged key by key recursively, where an explicit
 * {@code null} member removes that key from the target. The target is never
 * mutated; a merged copy is returned. A Java {@code null} patch is a no-op.
 */
public final class JsonMergePatchUtil {

    private JsonMergePatchUtil() {
    }

    /**
     * Applies {@code patch} over {@code target} per RFC 7386 and returns the
     * merged tree (a new node; inputs are not mutated).
     */
    public static JsonNode merge(JsonNode target, JsonNode patch) {

        if (patch == null)
            return target;

        // Scalars and arrays replace the target wholesale.
        if (!patch.isObject())
            return patch.deepCopy();

        ObjectNode merged = target != null && target.isObject()
                ? ((ObjectNode) target).deepCopy()
                : JsonNodeFactory.instance.objectNode();

        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();

            if (value == null || value.isNull())
                merged.remove(entry.getKey());
            else
                merged.set(entry.getKey(), merge(merged.get(entry.getKey()), value));
        }

        return merged;
    }
}
