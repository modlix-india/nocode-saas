package com.fincity.saas.commons.core.functions.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Ids reach these functions as numbers from GetAuthentication and as strings from
 * STRING storage columns; both must work, and "left at the default" must read as
 * not given.
 */
class SecurityFunctionSupportTest {

    @Test
    void unsetValuesAreNotGiven() {
        assertNull(SecurityFunctionSupport.idOf("userId", null));
        assertNull(SecurityFunctionSupport.idOf("userId", JsonNull.INSTANCE));
        assertNull(SecurityFunctionSupport.idOf("userId", new JsonPrimitive("")));
        assertNull(SecurityFunctionSupport.idOf("userId", new JsonPrimitive("  ")));
        assertNull(SecurityFunctionSupport.idOf("userId", new JsonPrimitive(0)));
        assertNull(SecurityFunctionSupport.idOf("userId", new JsonPrimitive("0")));
    }

    @Test
    void numbersAndNumericStringsAreIds() {
        assertEquals(BigInteger.valueOf(110), SecurityFunctionSupport.idOf("userId", new JsonPrimitive(110)));
        assertEquals(BigInteger.valueOf(110), SecurityFunctionSupport.idOf("userId", new JsonPrimitive(110L)));
        assertEquals(BigInteger.valueOf(110), SecurityFunctionSupport.idOf("userId", new JsonPrimitive(" 110 ")));
        assertEquals(BigInteger.valueOf(110), SecurityFunctionSupport.idOf("userId", new JsonPrimitive(110.0)));
    }

    @Test
    void anythingElseIsABadRequest() {
        for (var bad : List.of(new JsonPrimitive("abc"), new JsonPrimitive(-1), new JsonPrimitive(1.5),
                new JsonPrimitive(true), new JsonObject())) {
            GenericException ex = assertThrows(GenericException.class,
                    () -> SecurityFunctionSupport.idOf("userId", bad), bad.toString());
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Test
    void arraysKeepOrderAndDropRepeatsAndBlanks() {
        JsonArray a = new JsonArray();
        a.add(3);
        a.add("1");
        a.add(JsonNull.INSTANCE);
        a.add("");
        a.add(3);
        a.add(0);
        a.add(" 2 ");

        assertEquals(SecurityFunctionTestSupport.ids(3, 1, 2), SecurityFunctionSupport.idsOf("ids", a));
    }

    @Test
    void aSingleValueIsAOneItemList() {
        assertEquals(SecurityFunctionTestSupport.ids(7), SecurityFunctionSupport.idsOf("ids", new JsonPrimitive("7")));
        assertEquals(List.of(), SecurityFunctionSupport.idsOf("ids", null));
        assertEquals(List.of(), SecurityFunctionSupport.idsOf("ids", JsonNull.INSTANCE));
        assertEquals(List.of(), SecurityFunctionSupport.idsOf("ids", new JsonArray()));
    }

    @Test
    void aBadItemFailsTheWholeArray() {
        JsonArray a = new JsonArray();
        a.add(1);
        a.add("x");
        assertThrows(GenericException.class, () -> SecurityFunctionSupport.idsOf("ids", a));
    }
}
