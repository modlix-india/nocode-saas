package com.fincity.saas.commons.util;

import java.util.HashMap;
import java.util.Map;

public class HashMapUtil {

    public static <K, E> Map<K, E> of(K k1, E e1) {

        HashMap<K, E> map = new HashMap<>();

        map.put(k1, e1);

        return map;
    }

    public static <K, E> Map<K, E> of(K k1, E e1, K k2, E e2) {

        HashMap<K, E> map = new HashMap<>();

        map.put(k1, e1);
        map.put(k2, e2);

        return map;
    }

    private HashMapUtil() {}
}
