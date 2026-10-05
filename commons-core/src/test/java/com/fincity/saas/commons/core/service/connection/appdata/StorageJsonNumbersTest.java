package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fincity.saas.commons.core.functions.storage.StorageJson;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A row written through a KIRun storage function must keep the number it was
 * given.
 *
 * Found live: tportNumber.big, declared LONG, was handed 9007199254740993
 * (2^53+1) through CoreServices.Storage.Create and read back 9007199254740992.
 * The loss is not in the Mongo codec - BJsonUtil already takes the exact text -
 * it is one line earlier, in {@code gson.fromJson(obj, Map.class)}. Gson's
 * default object strategy maps EVERY untyped JSON number to Double, so the
 * value is a double before any storage code sees it.
 *
 * The REST data API does not share the defect: it decodes with Jackson, which
 * keeps an integral literal integral. Only the KIRun path was affected.
 */
class StorageJsonNumbersTest {

    private static final String BEYOND_DOUBLE = "9007199254740993"; // 2^53 + 1

    @Test
    @DisplayName("Gson's default strategy is what loses it - the premise of this fix")
    void defaultGsonLosesTheInteger() {

        Map<String, Object> viaDefault = new Gson()
                .fromJson(
                        "{\"big\":" + BEYOND_DOUBLE + "}", new TypeToken<Map<String, Object>>() {}.getType());

        assertInstanceOf(Double.class, viaDefault.get("big"));
        assertEquals("9.007199254740992E15", viaDefault.get("big").toString());
    }

    @Test
    @DisplayName("An integer past 2^53 survives the row conversion")
    void keepsIntegerBeyondDoublePrecision() {

        Map<String, Object> row = StorageJson.toMap(obj("{\"big\":" + BEYOND_DOUBLE + "}"));

        assertInstanceOf(Long.class, row.get("big"));
        assertEquals(9007199254740993L, row.get("big"));
    }

    @Test
    @DisplayName("A whole number small enough for an int is still a Long, not a Double")
    void keepsSmallWholeNumbers() {

        Map<String, Object> row = StorageJson.toMap(obj("{\"n\":42}"));

        assertInstanceOf(Long.class, row.get("n"));
        assertEquals(42L, row.get("n"));
    }

    /**
     * 3.0 reaching Mongo as an Int32 is long-standing behaviour for every
     * storage in the fleet, and BJsonUtil decides that from the value's scale.
     * It can only keep deciding it if a fractional literal still arrives as a
     * Double, so this pins the half of the old behaviour that must NOT change.
     */
    @Test
    @DisplayName("A fractional literal is still a Double, so BJsonUtil's scale rule is untouched")
    void leavesFractionalNumbersAsDouble() {

        Map<String, Object> row = StorageJson.toMap(obj("{\"a\":3.0,\"b\":0.125}"));

        assertInstanceOf(Double.class, row.get("a"));
        assertInstanceOf(Double.class, row.get("b"));
        assertEquals(3.0d, row.get("a"));
        assertEquals(0.125d, row.get("b"));
    }

    @Test
    @DisplayName("A number too large for a long falls back to Double rather than throwing")
    void fallsBackForOversizedNumbers() {

        Map<String, Object> row = StorageJson.toMap(obj("{\"huge\":1e30}"));

        assertInstanceOf(Double.class, row.get("huge"));
    }

    @Test
    @DisplayName("Nested objects and arrays are converted with the same rule")
    void appliesThroughNesting() {

        Map<String, Object> row =
                StorageJson.toMap(obj("{\"outer\":{\"big\":" + BEYOND_DOUBLE + "},\"list\":[" + BEYOND_DOUBLE + "]}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outer = (Map<String, Object>) row.get("outer");
        assertEquals(9007199254740993L, outer.get("big"));

        assertEquals(9007199254740993L, ((java.util.List<?>) row.get("list")).getFirst());
    }

    @Test
    @DisplayName("A filter value keeps its precision too, or the row cannot be matched back")
    void keepsPrecisionInFilterValues() {

        Map<String, Object> filter =
                StorageJson.toMap(obj("{\"field\":\"big\",\"operator\":\"EQUALS\",\"value\":" + BEYOND_DOUBLE + "}"));

        assertEquals(9007199254740993L, filter.get("value"));
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
