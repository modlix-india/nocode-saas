package com.fincity.saas.commons.jooq.flow.schema.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.stream.Collectors;

public enum KeyType {
    PRIMARY,
    FOREIGN,
    UNIQUE,
    INDEX;

    public static List<JsonElement> getKeyType(KeyType... keyTypes) {

        List<KeyType> selected =
                (keyTypes == null || keyTypes.length == 0) ? List.of(KeyType.values()) : List.of(keyTypes);

        return selected.stream()
                .map(keyType -> new JsonPrimitive(keyType.name()))
                .collect(Collectors.toList());
    }
}
