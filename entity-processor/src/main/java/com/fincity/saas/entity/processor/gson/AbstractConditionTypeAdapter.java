package com.fincity.saas.entity.processor.gson;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.GroupCondition;
import com.fincity.saas.commons.model.condition.HavingCondition;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class AbstractConditionTypeAdapter extends TypeAdapter<AbstractCondition> {

    private final Gson gson;

    public AbstractConditionTypeAdapter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public void write(JsonWriter out, AbstractCondition value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        switch (value) {
            case ComplexCondition cc ->
                this.gson.getAdapter(ComplexCondition.class).write(out, cc);
            case GroupCondition gc ->
                this.gson.getAdapter(GroupCondition.class).write(out, gc);
            case FilterCondition fc ->
                this.gson.getAdapter(FilterCondition.class).write(out, fc);
            case HavingCondition hc ->
                this.gson.getAdapter(HavingCondition.class).write(out, hc);
            default -> this.gson.getAdapter(AbstractCondition.class).write(out, value);
        }
    }

    @Override
    public AbstractCondition read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        return switch (token) {
            case NULL -> {
                in.nextNull();
                yield null;
            }
            case BEGIN_OBJECT -> this.readCondition(in);
            default -> {
                in.skipValue();
                yield null;
            }
        };
    }

    private AbstractCondition readCondition(JsonReader in) {
        JsonObject jsonObject = JsonParser.parseReader(in).getAsJsonObject();

        if (jsonObject == null || jsonObject.isEmpty()) return null;

        if (this.isGroupCondition(jsonObject)) return this.gson.fromJson(jsonObject, GroupCondition.class);

        if (this.isComplexCondition(jsonObject)) return this.gson.fromJson(jsonObject, ComplexCondition.class);

        if (this.isHavingCondition(jsonObject)) return this.gson.fromJson(jsonObject, HavingCondition.class);

        return this.gson.fromJson(jsonObject, FilterCondition.class);
    }

    private boolean isGroupCondition(JsonObject json) {
        return json.has("havingConditions") && json.get("havingConditions").isJsonArray();
    }

    private boolean isComplexCondition(JsonObject json) {
        if (json.has("conditions") && !json.has("havingConditions")) return true;

        if (json.has("operator")) {
            String op = json.get("operator").getAsString();
            return "AND".equalsIgnoreCase(op) || "OR".equalsIgnoreCase(op);
        }

        return false;
    }

    private boolean isHavingCondition(JsonObject json) {
        return json.has("aggregateFunction");
    }

    public static class Factory implements TypeAdapterFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (AbstractCondition.class.isAssignableFrom(type.getRawType()))
                return (TypeAdapter<T>) new AbstractConditionTypeAdapter(gson);
            return null;
        }
    }
}
