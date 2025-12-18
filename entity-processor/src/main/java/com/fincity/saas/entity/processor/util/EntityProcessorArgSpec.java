package com.fincity.saas.entity.processor.util;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;

public final class EntityProcessorArgSpec {

    private EntityProcessorArgSpec() {}

    public static final String IDENTITY_SCHEMA_REF = "EntityProcessor.Model.Common.Identity";

    public static ClassSchema.ArgSpec<Identity> identity() {
        return identity("identity");
    }

    public static ClassSchema.ArgSpec<Identity> identity(String name) {
        return ClassSchema.ArgSpec.ofRef(name, IDENTITY_SCHEMA_REF, Identity.class);
    }

    public static ClassSchema.ArgSpec<ULong> uLong(String name) {
        return ClassSchema.ArgSpec.custom(
                name,
                Schema.ofInteger("ULong"),
                (g, j) -> j == null || j.isJsonNull() ? null : ULong.valueOf(j.getAsString()));
    }

    public static ClassSchema.ArgSpec<List<ULong>> uLongList(String name) {
        return ClassSchema.ArgSpec.custom(name, Schema.ofArray(name, Schema.ofInteger("ULong")), (g, j) -> {
            if (j == null || j.isJsonNull()) return List.of();
            if (!j.isJsonArray()) return List.of();
            List<ULong> out = new ArrayList<>();
            j.getAsJsonArray().forEach(e -> out.add(ULong.valueOf(e.getAsString())));
            return out;
        });
    }
}
