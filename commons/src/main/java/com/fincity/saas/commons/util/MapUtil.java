package com.fincity.saas.commons.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapUtil {

    @SuppressWarnings("unchecked")
    public static void setValueInMap(Map<String, Object> map, String key, Object value) { // NOSONAR
        // This method is not complex, it is just a lot of code
        // It is not possible to reduce the complexity without making the code
        // unreadable

        if (StringUtil.safeIsBlank(key) || value == null)
            return;

        String[] parts = key.split("\\.");

        Map<String, Object> curMap = map;

        int i;
        for (i = 0; i < parts.length - 1; i++) {

            String[] arrParts = parts[i].split("\\[");

            if (!curMap.containsKey(arrParts[0])) {
                curMap.put(arrParts[0],
                        arrParts.length == 1 ? new HashMap<String, Object>() : new ArrayList<Object>());
            }

            if (arrParts.length == 1) {
                curMap = (Map<String, Object>) curMap.get(arrParts[0]);
                continue;
            }

            List<Object> list = (List<Object>) curMap.get(arrParts[0]);
            for (int j = 1; j < arrParts.length; j++) {
                String arrPart = arrParts[j].replace(']', ' ').trim();
                Integer index = Integer.parseInt(arrPart);
                if (index >= list.size()) {
                    int size = index - list.size();
                    for (int k = 0; k <= size; k++)
                        list.add(j + 1 == arrParts.length && i + 1 == parts.length - 1 ? null
                                : new ArrayList<Object>());
                }
                if (j + 1 == arrParts.length) {
                    list.set(index, new HashMap<String, Object>());
                    curMap = (Map<String, Object>) list.get(index);
                } else
                    list = (List<Object>) list.get(index);
            }
        }

        String[] arrParts = parts[i].split("\\[");

        if (!curMap.containsKey(arrParts[0])) {
            curMap.put(arrParts[0],
                    arrParts.length == 1 ? value : new ArrayList<Object>());
        }

        if (arrParts.length == 1) {
            return;
        }

        List<Object> list = (List<Object>) curMap.get(arrParts[0]);
        for (int j = 1; j < arrParts.length; j++) {
            String arrPart = arrParts[j].replace(']', ' ').trim();
            Integer index = Integer.parseInt(arrPart);

            if (index >= list.size()) {
                int size = index - list.size();
                for (int k = 0; k <= size; k++)
                    list.add(new ArrayList<Object>());
            }

            if (j + 1 == arrParts.length) {
                list.set(index, value);
                return;
            }
            list = (List<Object>) list.get(index);
        }
    }

    private MapUtil() {
    }
}
