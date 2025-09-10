package com.modlix.saas.commons2.mongo;

import org.junit.jupiter.api.Test;
import com.modlix.saas.commons2.mongo.enums.TransportFileType;
import com.modlix.saas.commons2.mongo.util.MapWithOrderComparator;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Commons2MongoTest {

    @Test
    public void testTransportFileType() {
        assertEquals(TransportFileType.JSON, TransportFileType.valueOf("JSON"));
        assertEquals(TransportFileType.ZIP, TransportFileType.valueOf("ZIP"));
    }

    @Test
    public void testMapWithOrderComparator() {
        MapWithOrderComparator comparator = new MapWithOrderComparator();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("order", "1");
        map1.put("name", "first");

        Map<String, Object> map2 = new HashMap<>();
        map2.put("order", "2");
        map2.put("name", "second");

        assertTrue(comparator.compare(map1, map2) < 0);
        assertTrue(comparator.compare(map2, map1) > 0);
        assertEquals(0, comparator.compare(map1, map1));
    }

    @Test
    public void testMapWithOrderComparatorWithCustomOrderKey() {
        MapWithOrderComparator comparator = new MapWithOrderComparator();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("order", "10");
        map1.put("name", "high");

        Map<String, Object> map2 = new HashMap<>();
        map2.put("order", "5");
        map2.put("name", "low");

        assertTrue(comparator.compare(map1, map2) > 0);
        assertTrue(comparator.compare(map2, map1) < 0);
    }
}
