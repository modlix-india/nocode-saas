package com.fincity.saas.commons.core.functions.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;

/**
 * JSON to Map/List for the KIRun storage functions, without flattening numbers.
 *
 * Gson's default strategy for an UNTYPED target maps every JSON number to
 * Double. For a row that is silent data loss: a LONG field handed
 * 9007199254740993 is stored as 9007199254740992, and nothing anywhere reports
 * it - the write returns 200 and the value reads back one short. It bites a
 * filter just as hard, where a mangled value simply matches no row.
 *
 * {@link ToNumberPolicy#LONG_OR_DOUBLE} is deliberately the whole change.
 * A literal with no fractional part becomes a Long; everything else stays a
 * Double, exactly as before. That second half matters: BJsonUtil decides
 * Int32/Int64/Double from the value's SCALE, so 3.0 must keep arriving as a
 * Double for it to keep storing 3.0 as an Int32 - which is what all 219
 * storages in the fleet already do and is not this fix's business to change.
 *
 * The REST data API never had the defect. It decodes with Jackson, which keeps
 * an integral literal integral, so only the KIRun path needed this.
 */
public final class StorageJson {

    private static final Gson NUMBER_SAFE =
            new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private static final java.lang.reflect.Type LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    private StorageJson() {}

    /** One row, or one filter, as a Map with its numbers intact. */
    public static Map<String, Object> toMap(JsonElement element) {
        return NUMBER_SAFE.fromJson(element, MAP_TYPE);
    }

    /** Many rows, as Maps with their numbers intact. */
    public static List<Map<String, Object>> toMapList(JsonElement element) {
        return NUMBER_SAFE.fromJson(element, LIST_TYPE);
    }
}
