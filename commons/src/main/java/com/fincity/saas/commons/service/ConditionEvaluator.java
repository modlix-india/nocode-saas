package com.fincity.saas.commons.service;

import static com.fincity.saas.commons.model.condition.ComplexConditionOperator.AND;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.fincity.nocode.kirun.engine.runtime.expression.ExpressionEvaluator;
import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.ObjectValueSetterExtractor;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConditionEvaluator {

    private static final Gson GSON = new Gson();
    private final String prefix;

    private final ConcurrentHashMap<String, ExpressionEvaluator> evaluatorCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<JsonObject, ObjectValueSetterExtractor> extractorCache = new ConcurrentHashMap<>();

    public ConditionEvaluator(String prefix) {
        this.prefix = prefix;
    }

    public Mono<Boolean> evaluate(AbstractCondition condition, JsonElement json) {
        if (condition == null || condition.isEmpty()) return Mono.just(Boolean.FALSE);
        if (json == null) return Mono.just(Boolean.FALSE);

        return switch (condition) {
            case ComplexCondition cc -> evaluateComplex(cc, json);
            case FilterCondition fc -> evaluateFilter(fc, json);
            default -> Mono.just(Boolean.FALSE);
        };
    }

    private Mono<Boolean> evaluateComplex(ComplexCondition cc, JsonElement json) {
        List<AbstractCondition> conds = cc.getConditions();
        if (conds == null || conds.isEmpty()) return Mono.just(Boolean.FALSE);

        boolean isAnd = cc.getOperator() == AND;

        if (isAnd) {
            return Flux.fromIterable(conds)
                    .flatMap(sub -> evaluate(sub, json))
                    .takeUntil(result -> !result)
                    .all(result -> result)
                    .defaultIfEmpty(true);
        } else {
            return Flux.fromIterable(conds)
                    .flatMap(sub -> evaluate(sub, json))
                    .takeUntil(result -> result)
                    .any(result -> result)
                    .defaultIfEmpty(false);
        }
    }

    private Mono<JsonElement> extractFieldValue(JsonObject obj, String field) {
        if (obj == null || field == null) return Mono.just(JsonNull.INSTANCE);

        if (!field.startsWith(prefix)) return Mono.just(JsonNull.INSTANCE);

        ObjectValueSetterExtractor extractor =
                extractorCache.computeIfAbsent(obj, k -> new ObjectValueSetterExtractor(obj, prefix));

        ExpressionEvaluator evaluator = evaluatorCache.computeIfAbsent(field, k -> new ExpressionEvaluator(field));

        return Mono.just(evaluator.evaluate(Map.of(extractor.getPrefix(), extractor)));
    }

    private Mono<Boolean> evaluateFilter(FilterCondition fc, JsonElement json) {
        if (json == null || json.isJsonNull()) return Mono.just(Boolean.FALSE);

        JsonObject obj = json.getAsJsonObject();

        return FlatMapUtil.flatMapMono(
                        () -> this.extractFieldValue(obj, fc.getField()),
                        target -> fc.isValueField()
                                ? this.extractFieldValue(obj, Objects.toString(fc.getValue(), ""))
                                : convertToJsonElement(fc.getValue()),
                        (target, filterValElement) -> fc.isToValueField()
                                ? extractFieldValue(obj, Objects.toString(fc.getToValue(), ""))
                                : convertToJsonElement(fc.getToValue()),
                        (target, filterValElement, toValElement) -> switch (fc.getOperator()) {
                            case EQUALS -> valueEquals(target, filterValElement);
                            case GREATER_THAN ->
                                compare(target, filterValElement).map(result -> result > 0);
                            case GREATER_THAN_EQUAL ->
                                compare(target, filterValElement).map(result -> result >= 0);
                            case LESS_THAN -> compare(target, filterValElement).map(result -> result < 0);
                            case LESS_THAN_EQUAL ->
                                compare(target, filterValElement).map(result -> result <= 0);
                            case BETWEEN ->
                                Mono.zip(
                                        compare(target, filterValElement).map(result -> result >= 0),
                                        compare(target, toValElement).map(result -> result <= 0),
                                        (first, second) -> first && second);
                            case IN -> {
                                List<?> multiValue = fc.getMultiValue();
                                if (multiValue == null || multiValue.isEmpty()) yield Mono.just(Boolean.FALSE);

                                yield Flux.fromIterable(multiValue)
                                        .flatMap(this::convertToJsonElement)
                                        .flatMap(val -> valueEquals(target, val))
                                        .any(result -> result)
                                        .defaultIfEmpty(Boolean.FALSE);
                            }
                            case LIKE ->
                                Mono.just(target != null
                                        && target.isJsonPrimitive()
                                        && target.getAsString().contains(Objects.toString(fc.getValue(), "")));
                            case STRING_LOOSE_EQUAL ->
                                Mono.just(target != null
                                        && target.isJsonPrimitive()
                                        && target.getAsString()
                                                .toLowerCase()
                                                .contains(Objects.toString(fc.getValue(), "")
                                                        .toLowerCase()));
                            case IS_NULL -> Mono.just(target == null || target.isJsonNull());
                            case IS_TRUE ->
                                Mono.just(target != null
                                        && target.isJsonPrimitive()
                                        && target.getAsJsonPrimitive().getAsBoolean());
                            case IS_FALSE ->
                                Mono.just(target != null
                                        && target.isJsonPrimitive()
                                        && !target.getAsJsonPrimitive().getAsBoolean());
                            default -> Mono.just(Boolean.FALSE);
                        })
                .defaultIfEmpty(Boolean.FALSE);
    }

    private Mono<JsonElement> convertToJsonElement(Object value) {
        if (value == null) return Mono.just(JsonNull.INSTANCE);
        if (value instanceof JsonElement je) return Mono.just(je);
        return Mono.just(GSON.toJsonTree(value));
    }

    private Mono<Boolean> valueEquals(JsonElement jsonVal, JsonElement filterVal) { // NOSONAR
        if (jsonVal == null || jsonVal.isJsonNull()) return Mono.just(filterVal == null || filterVal.isJsonNull());
        if (filterVal == null || filterVal.isJsonNull()) return Mono.just(Boolean.FALSE);

        if (jsonVal.isJsonPrimitive() && filterVal.isJsonPrimitive()) {
            JsonPrimitive p1 = jsonVal.getAsJsonPrimitive();
            JsonPrimitive p2 = filterVal.getAsJsonPrimitive();

            if (p1.isNumber() && p2.isNumber())
                return compare(jsonVal, filterVal).map(result -> result == 0);
            if (p1.isBoolean() && p2.isBoolean()) return Mono.just(p1.getAsBoolean() == p2.getAsBoolean());
            if (p1.isString() && p2.isString())
                return Mono.just(p1.getAsString().equals(p2.getAsString()));
        }

        return Mono.just(jsonVal.toString().equals(filterVal.toString()));
    }

    private Mono<Integer> compare(JsonElement jsonVal, JsonElement filterVal) {
        if (jsonVal == null || jsonVal.isJsonNull() || filterVal == null || filterVal.isJsonNull()) return Mono.just(0);
        if (!jsonVal.isJsonPrimitive() || !filterVal.isJsonPrimitive()) return Mono.just(0);

        if (jsonVal.getAsJsonPrimitive().isString()
                && filterVal.getAsJsonPrimitive().isString()) {
            return this.compareDate(jsonVal, filterVal);
        }

        return this.compareNumber(jsonVal, filterVal);
    }

    private Mono<Integer> compareDate(JsonElement jsonVal, JsonElement filterVal) {
        return Mono.fromCallable(() -> {
            try {
                LocalDateTime jsonDate = LocalDateTime.parse(jsonVal.getAsString());
                LocalDateTime compareDate = LocalDateTime.parse(filterVal.getAsString());
                return jsonDate.compareTo(compareDate);
            } catch (DateTimeParseException e) {
                return 0;
            }
        });
    }

    private Mono<Integer> compareNumber(JsonElement jsonVal, JsonElement filterVal) {
        return Mono.fromCallable(() -> {
            try {
                double jsonNumber = jsonVal.getAsDouble();
                double compareTo = filterVal.getAsDouble();
                return Double.compare(jsonNumber, compareTo);
            } catch (NumberFormatException e) {
                return 0;
            }
        });
    }
}
