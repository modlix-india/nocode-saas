package com.fincity.saas.commons.util;

import static com.fincity.saas.commons.util.CommonsUtil.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.ParameterReference;
import com.fincity.nocode.kirun.engine.model.Position;
import com.fincity.nocode.kirun.engine.model.Statement;
import com.fincity.nocode.kirun.engine.model.StatementGroup;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class DifferenceExtractor {

	private static final String DIFFERENCE_EXTRACTOR_EXTRACT = "DifferenceExtractor.extract";

	public static Mono<Map<String, ?>> extract(Map<String, ?> incoming, Map<String, ?> existing) { // NOSONAR

		// This has to be as generic as possible to handle multiple cases.

		if (existing == null || existing.isEmpty()) {
			if (incoming == null || incoming.isEmpty())
				return Mono.just(Map.of());
			else
				return Mono.just(incoming);
		}

		if (incoming == null || incoming.isEmpty()) {

			HashMap<String, Object> m = new HashMap<>();
			for (String lang : existing.keySet())
				m.put(lang, null);

			return Mono.just(m);
		}

		return Flux.concat(Flux.fromIterable(existing.keySet()), Flux.fromIterable(incoming.keySet()))
				.distinct()
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(e -> extract(incoming.get(e), existing.get(e)).map(d -> Tuples.of(e, d)))
				.collectMap(Tuple2::getT1, tup -> tup.getT2() == JsonNull.INSTANCE ? null : tup.getT2(), HashMap::new)
				.flatMap(e -> e.isEmpty() ? Mono.just(Map.of()) : Mono.just(e));
	}

	public static Mono<Map<String, Boolean>> extractMapBoolean(Map<String, Boolean> incoming,
			Map<String, Boolean> existing) {

		if (existing == null || existing.isEmpty()) {
			if (incoming == null || incoming.isEmpty())
				return Mono.just(Map.of());
			else
				return Mono.just(incoming);
		}

		if (incoming == null || incoming.isEmpty()) {

			HashMap<String, Boolean> m = new HashMap<>();
			for (String lang : existing.keySet())
				m.put(lang, null);

			return Mono.just(m);
		}

		return Flux.concat(Flux.fromIterable(existing.keySet()), Flux.fromIterable(incoming.keySet()))
				.distinct()
				.subscribeOn(Schedulers.boundedElastic())
				.filter(e -> !safeEquals(incoming.get(e), existing.get(e)))
				.map(e -> Tuples.of(e, Optional.ofNullable(incoming.get(e))))
				.collectMap(Tuple2::getT1, e -> e.getT2().orElse(null), HashMap::new)
				.flatMap(e -> Mono.just(e.isEmpty() ? Map.of() : e));
	}

	@SuppressWarnings({ "unchecked" })
	public static Mono<Object> extract(Object incoming, Object existing) { // NOSONAR

		// Splitting the below logic makes no sense.

		if (existing == null) {
			if (incoming == null)
				return Mono.empty();
			else
				return Mono.just(incoming);
		}

		if (incoming == null)
			return Mono.just(JsonNull.INSTANCE);

		if (existing.equals(incoming))
			return Mono.empty();

		if (existing instanceof Map && incoming instanceof Map)
			return extract((Map<String, Object>) incoming, (Map<String, Object>) existing).map(e -> e);

		if (existing instanceof IDifferentiable exc && incoming instanceof IDifferentiable inc) // NOSONAR
			return exc.extractDifference(inc);

		if (existing instanceof FunctionDefinition efd && incoming instanceof FunctionDefinition ifd)
			return extract(ifd, efd);

		if (existing instanceof Statement est && incoming instanceof Statement ist)
			return extract(ist, est);

		if (existing instanceof StatementGroup est && incoming instanceof StatementGroup ist)
			return extract(ist, est);

		if (existing instanceof ParameterReference est && incoming instanceof ParameterReference ist)
			return extract(ist, est);

		if (existing instanceof JsonElement est && incoming instanceof JsonElement ist)
			return extract(ist, est).map(Function.identity());

		return Mono.just(incoming);
	}

	private static Mono<JsonElement> extract(JsonElement incoming, JsonElement existing) {

		if (incoming.equals(existing))
			return Mono.empty();

		if (existing.isJsonNull())
			return Mono.just(incoming);

		if (existing.isJsonPrimitive() || existing.isJsonArray())
			return Mono.just(incoming);

		if (existing.isJsonObject() && incoming.isJsonObject())
			return extract(incoming.getAsJsonObject(), existing.getAsJsonObject());

		return Mono.just(incoming);
	}

	private static Mono<JsonElement> extract(JsonObject incoming, JsonObject existing) {

		if (existing == null || existing.size() == 0) {
			if (incoming == null || incoming.size() == 0)
				return Mono.just(new JsonObject());
			else
				return Mono.just(incoming);
		}

		if (incoming == null || incoming.size() == 0) {

			JsonObject jo = new JsonObject();
			for (String key : existing.keySet())
				jo.add(key, null);

			return Mono.just(jo);
		}

		return Flux.concat(Flux.fromIterable(existing.keySet()), Flux.fromIterable(incoming.keySet()))
				.distinct()
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(e -> extract(incoming.get(e), existing.get(e)).map(d -> Tuples.of(e, d)))
				.reduce(new JsonObject(), (jo, tup) -> {
					jo.add(tup.getT1(), tup.getT2());
					return jo;
				})
				.flatMap(e -> e.size() == 0 ? Mono.just(new JsonObject()) : Mono.just(e));
	}

	private static Mono<Object> extract(ParameterReference incoming, ParameterReference existing) {

		boolean changed = false;

		ParameterReference pr = new ParameterReference();

		if (!safeEquals(incoming.getExpression(), existing.getExpression())) {
			changed = true;
			pr.setExpression(incoming.getExpression());
		}

		if (!safeEquals(incoming.getType(), existing.getType())) {
			changed = true;
			pr.setType(incoming.getType());
		}

		if (!safeEquals(incoming.getValue(), existing.getValue())) {
			return extract(incoming.getValue(), existing.getValue()).map(pr::setValue);
		}

		return changed ? Mono.just(pr) : Mono.empty();
	}

	private static Mono<Object> extract(StatementGroup incoming, StatementGroup existing) {

		return FlatMapUtil.flatMapMono(

				() -> extract(incoming.getStatements(), existing.getStatements()),

				statDiff -> {

					boolean changed = !statDiff.isEmpty();

					StatementGroup st = new StatementGroup();

					if (!safeEquals(incoming.getComment(), existing.getComment())) {
						st.setComment(incoming.getComment());
						changed = true;
					}

					if (!safeEquals(incoming.getDescription(), existing.getDescription())) {
						st.setDescription(incoming.getDescription());
						changed = true;
					}

					st.setOverride(true);
					return changed ? Mono.just((Object) st) : Mono.empty();
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, DIFFERENCE_EXTRACTOR_EXTRACT));
	}

	private static Mono<Object> extract(Statement incoming, Statement existing) {

		return FlatMapUtil.flatMapMono(() -> extract(incoming.getParameterMap(), existing.getParameterMap()),

				paramMapDiff -> extract(incoming.getDependentStatements(), existing.getDependentStatements()),

				(paramMapDiff, depDiff) -> {

					Position p = extract(incoming.getPosition(), existing.getPosition());

					boolean changed = !paramMapDiff.isEmpty() || !depDiff.isEmpty() || p != null;

					Statement st = new Statement();

					if (!safeEquals(incoming.getComment(), existing.getComment())) {
						st.setComment(incoming.getComment());
						changed = true;
					}

					if (!safeEquals(incoming.getDescription(), existing.getDescription())) {
						st.setDescription(incoming.getDescription());
						changed = true;
					}

					if (!safeEquals(incoming.getNamespace(), existing.getNamespace())) {
						st.setNamespace(incoming.getNamespace());
						changed = true;
					}

					if (!safeEquals(incoming.getName(), existing.getName())) {
						st.setName(incoming.getName());
						changed = true;
					}

					st.setOverride(true);

					return changed ? Mono.just((Object) st) : Mono.empty();
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, DIFFERENCE_EXTRACTOR_EXTRACT));
	}

	private static Position extract(Position incoming, Position existing) {

		if (existing == null) {
			return incoming;
		}

		if (incoming == null)
			return null;

		if (existing.equals(incoming))
			return null;

		Position p = new Position();
		if (!safeEquals(incoming.getLeft(), existing.getLeft()))
			p.setLeft(incoming.getLeft());
		if (!safeEquals(incoming.getTop(), existing.getTop()))
			p.setTop(incoming.getTop());

		return p;
	}

	@SuppressWarnings({ "unchecked" })
	private static Mono<Object> extract(FunctionDefinition incoming, FunctionDefinition existing) {

		return FlatMapUtil.flatMapMono(

				() -> extract(incoming.getSteps(), existing.getSteps()),

				stepDiff -> extract(incoming.getStepGroups(), existing.getStepGroups()),

				(stepDiff, stepGroupDiff) -> {

					if (stepDiff.isEmpty() && stepGroupDiff.isEmpty())
						return Mono.empty();

					FunctionDefinition fd = new FunctionDefinition();

					fd.setSteps((Map<String, Statement>) stepDiff);
					fd.setStepGroups((Map<String, StatementGroup>) stepGroupDiff);

					return Mono.just((Object) fd);
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, DIFFERENCE_EXTRACTOR_EXTRACT));
	}

	private DifferenceExtractor() {
	}

}