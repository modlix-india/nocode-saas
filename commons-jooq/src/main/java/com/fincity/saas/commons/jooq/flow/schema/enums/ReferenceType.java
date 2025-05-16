package com.fincity.saas.commons.jooq.flow.schema.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.stream.Collectors;

public enum ReferenceType {
    RESTRICT,
    CASCADE,
    SET_NULL,
    NO_ACTION,
    SET_DEFAULT;

    public static List<JsonElement> getReferenceTypes(ReferenceType... referenceTypes) {

        List<ReferenceType> selected = (referenceTypes == null || referenceTypes.length == 0)
                ? List.of(ReferenceType.values())
                : List.of(referenceTypes);

        return selected.stream().map(ref -> new JsonPrimitive(ref.name())).collect(Collectors.toList());
    }
}
