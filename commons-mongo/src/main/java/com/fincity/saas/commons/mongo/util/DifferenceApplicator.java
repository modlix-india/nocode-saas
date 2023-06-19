package com.fincity.saas.commons.mongo.util;

import java.util.Map;
import java.util.function.Function;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.ParameterReference;
import com.fincity.nocode.kirun.engine.model.Position;
import com.fincity.nocode.kirun.engine.model.Statement;
import com.fincity.nocode.kirun.engine.model.StatementGroup;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class DifferenceApplicator {

	public static Mono<Map<String, ?>> apply(Map<String, ?> override, Map<String, ?> base) { // NOSONAR
		// Need to be generic as the maps maybe of different type.

		if (override == null || override.isEmpty())
			return Mono.justOrEmpty(base);

		if (base == null || base.isEmpty())
			return Mono.justOrEmpty(override);

		return Flux.concat(Flux.fromIterable(base.keySet()), Flux.fromIterable(override.keySet()))
		        .distinct()
		        .subscribeOn(Schedulers.boundedElastic())
		        .flatMap(e ->
				{
			        if (!override.containsKey(e))
				        return Mono.just(Tuples.of(e, base.get(e)));

			        return apply(override.get(e), base.get(e)).map(d -> Tuples.of(e, d));
		        })
		        .collectMap(Tuple2::getT1, Tuple2::getT2)
		        .flatMap(e -> e.isEmpty() ? Mono.justOrEmpty(Map.of()) : Mono.justOrEmpty(e));
	}

	public static Mono<Map<String, Boolean>> applyMapBoolean(Map<String, Boolean> override, Map<String, Boolean> base) {
		if (override == null || override.isEmpty())
			return Mono.justOrEmpty(base);

		if (base == null || base.isEmpty())
			return Mono.justOrEmpty(override);

		return Flux.concat(Flux.fromIterable(base.keySet()), Flux.fromIterable(override.keySet()))
		        .distinct()
		        .subscribeOn(Schedulers.boundedElastic())
		        .map(e -> Tuples.of(e, override.containsKey(e) ? override.get(e) : base.get(e)))
		        .collectMap(Tuple2::getT1, Tuple2::getT2)
		        .flatMap(e -> e.isEmpty() ? Mono.justOrEmpty(Map.of()) : Mono.justOrEmpty(e));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Mono<Object> apply(Object override, Object base) {

		if (override == null)
			return Mono.empty();

		if (override instanceof Map && base instanceof Map)
			return apply((Map<String, Object>) override, (Map<String, Object>) base).map(e -> e);

		if (override instanceof IDifferentiable inc)
			return inc.applyOverride((IDifferentiable) base);

		if (override instanceof FunctionDefinition ifd && base instanceof FunctionDefinition efd)
			return apply(ifd, efd);

		if (override instanceof Statement ist)
			return apply(ist, (Statement) base);

		if (override instanceof StatementGroup ist)
			return apply(ist, (StatementGroup) base);

		if (override instanceof ParameterReference ist && base instanceof ParameterReference est)
			return apply(ist, est);

		if (override instanceof JsonElement ist && base instanceof JsonElement est)
			return apply(ist, est).map(Function.identity());

		return Mono.justOrEmpty(override);
	}

	private static Mono<JsonElement> apply(JsonElement override, JsonElement base) {

		if (override == null || override.isJsonNull())
			return base == null || base.isJsonNull() ? Mono.empty() : Mono.justOrEmpty(base);

		if (base == null || base.isJsonNull())
			return override.isJsonNull() ? Mono.empty() : Mono.justOrEmpty(override);

		if (override.isJsonObject() && base.isJsonObject())
			return apply(override.getAsJsonObject(), base.getAsJsonObject());

		return Mono.justOrEmpty(override);
	}

	private static Mono<JsonElement> apply(JsonObject override, JsonObject base) {

		if (base == null || base.size() == 0) {
			if (override == null || override.size() == 0)
				return Mono.justOrEmpty(new JsonObject());
			else
				return Mono.justOrEmpty(override);
		}

		if (override == null || override.size() == 0) {

			JsonObject jo = new JsonObject();
			for (String key : base.keySet())
				jo.add(key, null);

			return Mono.justOrEmpty(jo);
		}

		return Flux.concat(Flux.fromIterable(base.keySet()), Flux.fromIterable(override.keySet()))
		        .distinct()
		        .subscribeOn(Schedulers.boundedElastic())
		        .flatMap(e -> apply(override.get(e), base.get(e)).map(d -> Tuples.of(e, d)))
		        .reduce(new JsonObject(), (jo, tup) ->
				{
			        jo.add(tup.getT1(), tup.getT2());
			        return jo;
		        })
		        .flatMap(e -> e.size() == 0 ? Mono.justOrEmpty(new JsonObject()) : Mono.justOrEmpty(e));
	}

	private static Mono<Object> apply(ParameterReference override, ParameterReference base) {

		ParameterReference pr = override;

		pr.setKey(base.getKey());

		if (override.getExpression() == null)
			override.setExpression(base.getExpression());
		if (override.getType() == null)
			override.setType(base.getType());
		if (override.getValue() == null)
			override.setValue(base.getValue());

		return Mono.justOrEmpty(pr);
	}

	private static Mono<Object> apply(StatementGroup override, StatementGroup base) {

		if (base == null)
			return override.isOverride() ? Mono.empty() : Mono.justOrEmpty(override);

		return FlatMapUtil.flatMapMono(

		        () -> apply(override.getStatements(), base.getStatements()),

		        statMap ->
				{

			        override.setStatementGroupName(base.getStatementGroupName());
			        override.setPosition(apply(override.getPosition(), base.getPosition()));

			        if (override.getComment() == null)
				        override.setComment(base.getComment());

			        if (override.getDescription() == null)
				        override.setDescription(base.getDescription());

			        override.setOverride(true);
			        return Mono.justOrEmpty((Object) override);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "DifferenceApplicator.apply"));
	}

	@SuppressWarnings("unchecked")
	private static Mono<Object> apply(Statement override, Statement base) {

		if (base == null)
			return override.isOverride() ? Mono.empty() : Mono.justOrEmpty(override);

		return FlatMapUtil.flatMapMono(

		        () -> applyMapBoolean(override.getDependentStatements(), base.getDependentStatements()),

		        depMap -> apply(override.getParameterMap(), base.getParameterMap()),

		        (depMap, paramMap) ->
				{

			        override.setStatementName(base.getStatementName());
			        override.setDependentStatements(depMap);
			        override.setParameterMap((Map<String, Map<String, ParameterReference>>) paramMap);
			        override.setPosition(apply(override.getPosition(), base.getPosition()));

			        if (override.getComment() == null)
				        override.setComment(base.getComment());

			        if (override.getDescription() == null)
				        override.setDescription(base.getDescription());

			        if (override.getName() == null)
				        override.setName(base.getName());

			        if (override.getNamespace() == null)
				        override.setNamespace(base.getNamespace());

			        override.setOverride(true);
			        return Mono.justOrEmpty((Object) override);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "DifferenceApplicator.apply"));
	}

	private static Position apply(Position override, Position base) {

		if (base == null)
			return override;

		if (override == null)
			return base;

		if (override.getLeft() == null)
			override.setLeft(base.getLeft());
		if (override.getTop() != null)
			override.setTop(base.getTop());

		return override;
	}

	@SuppressWarnings("unchecked")
	private static Mono<Object> apply(FunctionDefinition override, FunctionDefinition base) {

		return FlatMapUtil.flatMapMono(

		        () -> apply(override.getSteps(), base.getSteps()),

		        stepMap -> apply(override.getStepGroups(), base.getStepGroups()),

		        (stepMap, stepGroupMap) ->
				{

			        override.setEvents(base.getEvents());
			        override.setName(base.getName());
			        override.setNamespace(base.getNamespace());
			        override.setParameters(base.getParameters());

			        override.setSteps((Map<String, Statement>) stepMap);
			        override.setStepGroups((Map<String, StatementGroup>) stepGroupMap);

			        return Mono.justOrEmpty((Object) override);
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "DifferenceApplicator.apply"));
	}

	private DifferenceApplicator() {
	}
}