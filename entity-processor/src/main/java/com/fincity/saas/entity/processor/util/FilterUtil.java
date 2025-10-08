package com.fincity.saas.entity.processor.util;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterUtil {

    public static <T> List<T> intersectLists(List<?> current, List<T> mandatory) {

        if (mandatory == null || mandatory.isEmpty()) return List.of();

        if (current == null || current.isEmpty()) return mandatory;

        return mandatory.stream().filter(current::contains).toList();
    }
}
