package com.fincity.saas.commons.mongo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Set;

import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What happens to a number on its way into Mongo.
 *
 * Every value written through the app data API passes here, so a rounding decision
 * taken in this one method is a rounding decision taken for the whole platform.
 */
@DisplayName("Numbers crossing into BSON")
class BJsonUtilNumberTest {

    private static Document from(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return BJsonUtil.from(Set.of(), object);
    }

    @Test
    @DisplayName("a long beyond a double's exact range survives unchanged")
    void largeLongIsExact() {
        // 2^53 + 1. Routed through Double on the way to deciding the BSON type, it
        // comes back 2^53, one less than it went in, and nothing reports it.
        Document doc = from("{\"n\": 9007199254740993}");

        assertInstanceOf(BsonInt64.class, doc.get("n"));
        assertEquals(9007199254740993L, ((BsonInt64) doc.get("n")).getValue());
    }

    @Test
    @DisplayName("an ordinary integer is still an int")
    void smallIntStaysInt() {
        assertInstanceOf(BsonInt32.class, from("{\"n\": 42}").get("n"));
    }

    @Test
    @DisplayName("a value past the int range becomes a long")
    void beyondIntBecomesLong() {
        Document doc = from("{\"n\": 3000000000}");

        assertInstanceOf(BsonInt64.class, doc.get("n"));
        assertEquals(3000000000L, ((BsonInt64) doc.get("n")).getValue());
    }

    @Test
    @DisplayName("a fractional value keeps its full double precision")
    void fractionIsNotNarrowedToFloat() {
        // The old shape asked whether the double equalled its own float value and,
        // when it did, stored the float widened back out. Harmless for the values
        // where it was true, and a strange thing to leave in the path every write
        // takes.
        Document doc = from("{\"n\": 0.1}");

        assertInstanceOf(BsonDouble.class, doc.get("n"));
        assertEquals(0.1d, ((BsonDouble) doc.get("n")).getValue());
    }

    @Test
    @DisplayName("a whole-numbered decimal still collapses to an integer, which is left alone on purpose")
    void wholeDoubleCollapses() {
        // Long-standing behaviour for every storage on the platform. Arguably
        // wrong - a DOUBLE field reads back as 3 rather than 3.0 - but changing it
        // changes what reads back for 219 live storages, and that is not something
        // a precision fix should carry with it.
        assertInstanceOf(BsonInt32.class, from("{\"n\": 3.0}").get("n"));
    }
}
