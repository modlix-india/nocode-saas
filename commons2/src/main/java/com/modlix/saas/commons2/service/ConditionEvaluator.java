package com.modlix.saas.commons2.service;

import static com.modlix.saas.commons2.model.condition.ComplexConditionOperator.AND;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.ObjectValueSetterExtractor;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ConditionEvaluator {

    private static final Gson GSON = new Gson();
    private final String prefix;

    public ConditionEvaluator(String prefix) {
        this.prefix = prefix;
    }

    public Boolean evaluate(AbstractCondition condition, JsonElement json) {
        if (condition == null || condition.isEmpty())
            return Boolean.FALSE;
        if (json == null)
            return Boolean.FALSE;

        return switch (condition) {
            case ComplexCondition cc -> evaluateComplex(cc, json);
            case FilterCondition fc -> evaluateFilter(fc, json);
            default -> Boolean.FALSE;
        };
    }

    private Boolean evaluateComplex(ComplexCondition cc, JsonElement json) {
        List<AbstractCondition> conds = cc.getConditions();
        if (conds == null || conds.isEmpty())
            return Boolean.FALSE;

        boolean isAnd = cc.getOperator() == AND;

        if (isAnd) {
            return conds.stream()
                    .allMatch(sub -> evaluate(sub, json));
        } else {
            return conds.stream()
                    .anyMatch(sub -> evaluate(sub, json));
        }
    }

    private JsonElement extractFieldValue(JsonObject obj, String field) {
        if (obj == null || field == null)
            return JsonNull.INSTANCE;

        if (!field.startsWith(prefix))
            return JsonNull.INSTANCE;

        ObjectValueSetterExtractor extractor = new ObjectValueSetterExtractor(obj, prefix);

		JsonElement value = extractor.getValue(field);

        return value != null ? value : JsonNull.INSTANCE;
    }

    private Boolean evaluateFilter(FilterCondition fc, JsonElement json) {
        if (json == null || json.isJsonNull())
            return Boolean.FALSE;

        JsonObject obj = json.getAsJsonObject();

        JsonElement target = this.extractFieldValue(obj, fc.getField());
        JsonElement filterValElement = fc.isValueField()
                ? this.extractFieldValue(obj, Objects.toString(fc.getValue(), ""))
                : convertToJsonElement(fc.getValue());
        JsonElement toValElement = fc.isToValueField()
                ? extractFieldValue(obj, Objects.toString(fc.getToValue(), ""))
                : convertToJsonElement(fc.getToValue());

        return switch (fc.getOperator()) {
            case EQUALS -> valueEquals(target, filterValElement);
            case GREATER_THAN -> compare(target, filterValElement) > 0;
            case GREATER_THAN_EQUAL -> compare(target, filterValElement) >= 0;
            case LESS_THAN -> compare(target, filterValElement) < 0;
            case LESS_THAN_EQUAL -> compare(target, filterValElement) <= 0;
            case BETWEEN -> {
                boolean first = compare(target, filterValElement) >= 0;
                boolean second = compare(target, toValElement) <= 0;
                yield first && second;
            }
            case IN -> {
                List<?> multiValue = fc.getMultiValue();
                if (multiValue == null || multiValue.isEmpty())
                    yield Boolean.FALSE;

                yield multiValue.stream()
                        .map(this::convertToJsonElement)
                        .anyMatch(val -> valueEquals(target, val));
            }
            case LIKE -> target != null
                    && target.isJsonPrimitive()
                    && target.getAsString().contains(Objects.toString(fc.getValue(), ""));
            case STRING_LOOSE_EQUAL -> target != null
                    && target.isJsonPrimitive()
                    && target.getAsString()
                    .toLowerCase()
                    .contains(Objects.toString(fc.getValue(), "")
                            .toLowerCase());
            case IS_NULL -> target == null || target.isJsonNull();
            case IS_TRUE -> target != null
                    && target.isJsonPrimitive()
                    && target.getAsJsonPrimitive().getAsBoolean();
            case IS_FALSE -> target != null
                    && target.isJsonPrimitive()
                    && !target.getAsJsonPrimitive().getAsBoolean();
            default -> Boolean.FALSE;
        };
    }

    private JsonElement convertToJsonElement(Object value) {
        if (value == null)
            return JsonNull.INSTANCE;
        if (value instanceof JsonElement je)
            return je;
        return GSON.toJsonTree(value);
    }

    private Boolean valueEquals(JsonElement jsonVal, JsonElement filterVal) { // NOSONAR
        if (jsonVal == null || jsonVal.isJsonNull())
            return filterVal == null || filterVal.isJsonNull();
        if (filterVal == null || filterVal.isJsonNull())
            return Boolean.FALSE;

        if (jsonVal.isJsonPrimitive() && filterVal.isJsonPrimitive()) {
            JsonPrimitive p1 = jsonVal.getAsJsonPrimitive();
            JsonPrimitive p2 = filterVal.getAsJsonPrimitive();

            if (p1.isNumber() && p2.isNumber())
                return compare(jsonVal, filterVal) == 0;
            if (p1.isBoolean() && p2.isBoolean())
                return p1.getAsBoolean() == p2.getAsBoolean();
            if (p1.isString() && p2.isString())
                return p1.getAsString().equals(p2.getAsString());
        }

        return jsonVal.toString().equals(filterVal.toString());
    }

    private Integer compare(JsonElement jsonVal, JsonElement filterVal) {
        if (jsonVal == null || jsonVal.isJsonNull() || filterVal == null || filterVal.isJsonNull())
            return 0;
        if (!jsonVal.isJsonPrimitive() || !filterVal.isJsonPrimitive())
            return 0;

        if (jsonVal.getAsJsonPrimitive().isString()
                && filterVal.getAsJsonPrimitive().isString())
            return this.compareDate(jsonVal, filterVal);

        return this.compareNumber(jsonVal, filterVal);
    }

    private Integer compareDate(JsonElement jsonVal, JsonElement filterVal) {
        try {
            LocalDateTime jsonDate = LocalDateTime.parse(jsonVal.getAsString());
            LocalDateTime compareDate = LocalDateTime.parse(filterVal.getAsString());
            return jsonDate.compareTo(compareDate);
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private Integer compareNumber(JsonElement jsonVal, JsonElement filterVal) {
        try {
            double jsonNumber = jsonVal.getAsDouble();
            double compareTo = filterVal.getAsDouble();
            return Double.compare(jsonNumber, compareTo);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
