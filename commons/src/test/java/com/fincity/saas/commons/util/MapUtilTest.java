package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MapUtilTest {

    @Test
    @SuppressWarnings("unchecked")
    void testSetValueInMap() {
        Map<String, Object> myMap = new HashMap<>();
        MapUtil.setValueInMap(myMap, "a", "kiran");
        assertEquals("kiran", myMap.get("a"));

        MapUtil.setValueInMap(myMap, "b.c", "kiran");
        assertEquals("kiran", ((Map<String, Object>) myMap.get("b")).get("c"));

        MapUtil.setValueInMap(myMap, "c[0]", "kiran");
        assertEquals("kiran", ((List<Object>) myMap.get("c")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedValue() {
        Map<String, Object> myMap = new HashMap<>();
        MapUtil.setValueInMap(myMap, "a.b.c", "kiran");
        assertEquals("kiran", ((Map<String, Object>) ((Map<String, Object>) myMap.get("a")).get("b")).get("c"));

        myMap = new HashMap<>();
        MapUtil.setValueInMap(myMap, "a.b[0].c", "kiran");
        assertEquals("kiran", ((Map<String, Object>) ((List<Object>) ((Map<String, Object>) myMap.get("a")).get("b"))
                .get(0)).get("c"));

        myMap = new HashMap<>();
        MapUtil.setValueInMap(myMap, "a.b[0].c[0]", "kiran");
        assertEquals("kiran", ((List<Object>) ((Map<String, Object>) ((List<Object>) ((Map<String, Object>) myMap
                .get("a")).get("b")).get(0)).get("c")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedArray() {
        Map<String, Object> mymap = new HashMap<>();
        MapUtil.setValueInMap(mymap, "a.b[0][2].c[1]", "kiran");
        assertEquals("kiran",
                ((List<Object>) ((Map<String, Object>) ((List<Object>) ((List<Object>) ((Map<String, Object>) mymap
                        .get("a")).get("b")).get(0)).get(2)).get("c")).get(1));
    }
}
