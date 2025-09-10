package com.modlix.saas.commons2.mongo.util;

import java.util.Comparator;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MapWithOrderComparator implements Comparator<Map<String, Object>> {

    private Object orderKey = "order";

    @Override
    public int compare(Map<String, Object> o1, Map<String, Object> o2) {

        Integer i1 = o1 == null ? 0 : Integer.valueOf(o1.getOrDefault(orderKey, "0").toString());
        Integer i2 = o2 == null ? 0 : Integer.valueOf(o2.getOrDefault(orderKey, "0").toString());

        return i1.compareTo(i2);
    }
}

