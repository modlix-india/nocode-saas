package com.fincity.saas.commons.model.condition;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ConditionEvaluator {

    public boolean evaluate(AbstractCondition condition, JsonElement json) {
        if (condition == null || condition.isEmpty()) return true;
        if (json == null || !json.isJsonObject()) return false;

        return switch (condition) {
            case ComplexCondition cc -> evaluateComplex(cc, json);
            case FilterCondition fc -> evaluateFilter(fc, json);
            default -> true;
        };
    }

    private boolean evaluateComplex(ComplexCondition cc, JsonElement json) {
        List<AbstractCondition> conds = cc.getConditions();
        if (conds == null || conds.isEmpty()) return true;

        boolean isAnd = cc.getOperator() == ComplexConditionOperator.AND;
        for (AbstractCondition sub : conds) {
            boolean result = evaluate(sub, json);
            if (isAnd && !result) return false;
            if (!isAnd && result) return true;
        }
        return isAnd;
    }

    private boolean evaluateFilter(FilterCondition fc, JsonElement json) {
        JsonObject obj = json.getAsJsonObject();
        JsonElement target = getNestedField(obj, fc.getField());
        Object filterVal = fc.getValue();
        Object toVal = fc.getToValue();

        return switch (fc.getOperator()) {
            case EQUALS -> valueEquals(target, filterVal);
            case GREATER_THAN -> compare(target, filterVal) > 0;
            case GREATER_THAN_EQUAL -> compare(target, filterVal) >= 0;
            case LESS_THAN -> compare(target, filterVal) < 0;
            case LESS_THAN_EQUAL -> compare(target, filterVal) <= 0;
            case BETWEEN -> compare(target, filterVal) >= 0 && compare(target, toVal) <= 0;
            case IN ->
                fc.getMultiValue() != null && fc.getMultiValue().stream().anyMatch(val -> valueEquals(target, val));
            case LIKE ->
                target != null
                        && target.isJsonPrimitive()
                        && target.getAsString().contains(String.valueOf(filterVal));
            case STRING_LOOSE_EQUAL ->
                target != null
                        && target.isJsonPrimitive()
                        && target.getAsString()
                                .toLowerCase()
                                .contains(String.valueOf(filterVal).toLowerCase());
            case IS_NULL -> target == null || target.isJsonNull();
            case IS_TRUE -> target != null && target.getAsBoolean();
            case IS_FALSE -> target != null && !target.getAsBoolean();
            default -> false;
        };
    }

    private boolean valueEquals(JsonElement jsonVal, Object filterVal) {
        if (jsonVal == null || jsonVal.isJsonNull()) return filterVal == null;
        if (filterVal == null) return false;

        if (jsonVal.isJsonPrimitive()) {
            JsonPrimitive p = jsonVal.getAsJsonPrimitive();
            if (p.isNumber()) return compare(jsonVal, filterVal) == 0;
            if (p.isBoolean()) return p.getAsBoolean() == Boolean.parseBoolean(filterVal.toString());
            if (p.isString()) return p.getAsString().equals(filterVal.toString());
        }

        return jsonVal.toString().equals(filterVal.toString());
    }

    private int compare(JsonElement jsonVal, Object filterVal) {
        if (jsonVal == null || jsonVal.isJsonNull() || filterVal == null) return 0;
        if (!jsonVal.isJsonPrimitive()) return 0;

        if (jsonVal.getAsJsonPrimitive().isString()) return compareDate(jsonVal, filterVal);

        return compareNumber(jsonVal, filterVal);
    }

    private int compareDate(JsonElement jsonVal, Object filterVal) {
        try {
            LocalDateTime jsonDate = LocalDateTime.parse(jsonVal.getAsString());
            LocalDateTime compareDate = LocalDateTime.parse(filterVal.toString());
            return jsonDate.compareTo(compareDate);
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private int compareNumber(JsonElement jsonVal, Object filterVal) {
        try {
            double jsonNumber = jsonVal.getAsDouble();
            double compareTo = Double.parseDouble(filterVal.toString());
            return Double.compare(jsonNumber, compareTo);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private JsonElement getNestedField(JsonObject obj, String field) {
        if (obj == null || field == null) return JsonNull.INSTANCE;

        String[] parts = field.split("\\.");
        JsonElement current = obj;

        for (String part : parts) {
            if (!current.isJsonObject()) return JsonNull.INSTANCE;

            JsonObject currentObj = current.getAsJsonObject();
            if (!currentObj.has(part)) return JsonNull.INSTANCE;

            current = currentObj.get(part);
        }

        return current;
    }
}
