package com.fincity.saas.commons.jooq.flow.schema.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum IndexDirection {
    ASC,
    DESC;

    public static List<JsonElement> getIndexDirection() {
        return Stream.of(IndexDirection.values())
                .map(direction -> new JsonPrimitive(direction.name()))
                .collect(Collectors.toList());
    }
}
