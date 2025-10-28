package com.fincity.saas.entity.processor.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EnumSchemaUtil {

    public static <E extends Enum<E>> List<JsonElement> getSchemaEnums(Class<E> enumClass, E... enums) {
        if (enumClass == null) throw new IllegalArgumentException("enumClass cannot be null");

        if (enums == null || enums.length == 0) enums = enumClass.getEnumConstants();

        return Arrays.stream(enums).map(e -> new JsonPrimitive(e.name())).collect(Collectors.toList());
    }
}
