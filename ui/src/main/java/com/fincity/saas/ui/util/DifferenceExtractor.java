package com.fincity.saas.ui.util;

import java.util.HashMap;
import java.util.Map;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.Statement;
import com.fincity.nocode.kirun.engine.model.StatementGroup;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.ui.model.ComponentDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class DifferenceExtractor {

	public static Mono<Map<String, ?>> jsonMap(Map<String, ?> incoming, Map<String, ?> existing) {

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
		        .flatMap(e -> json(incoming.get(e), existing.get(e)).map(d -> Tuples.of(e, d)))
		        .collectMap(Tuple2::getT1, Tuple2::getT2)
		        .flatMap(e -> e.isEmpty() ? Mono.just(Map.of()) : Mono.just(e));
	}

	public static Mono<Map<String, Boolean>> jsonMapBoolean(Map<String, Boolean> incoming,
	        Map<String, Boolean> existing) {

		if (existing == null || existing.isEmpty()) {
			if (incoming == null || incoming.isEmpty())
				return Mono.empty();
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
		        .filter(e -> !incoming.get(e)
		                .equals(existing.get(e)))
		        .map(e -> Tuples.of(e, incoming.get(e)))
		        .collectMap(Tuple2::getT1, Tuple2::getT2)
		        .flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e));
	}

	@SuppressWarnings({ "unchecked" })
	public static Mono<Object> json(Object incoming, Object existing) {

		if (existing == null) {
			if (incoming == null)
				return Mono.empty();
			else
				return Mono.just(incoming);
		}

		if (incoming == null)
			return Mono.empty();

		if (existing.equals(incoming))
			return Mono.empty();

		if (existing instanceof Map && incoming instanceof Map) {
			return jsonMap((Map<String, Object>) existing, (Map<String, Object>) incoming).map(e -> e);
		}

		if (existing instanceof ComponentDefinition exc && incoming instanceof ComponentDefinition inc) {

			return FlatMapUtil.flatMapMono(

			        () -> jsonMap(inc.getProperties(), exc.getProperties()).defaultIfEmpty(Map.of()),

			        propDiff -> jsonMapBoolean(inc.getChildren(), exc.getChildren()).defaultIfEmpty(Map.of()),

			        (propDiff, childDiff) ->
					{

				        ComponentDefinition cd = new ComponentDefinition();
				        cd.setKey(inc.getKey()
				                .equals(exc.getKey()) ? null : exc.getKey());
				        cd.setName(inc.getName()
				                .equals(exc.getName()) ? null : exc.getName());
				        cd.setOverride(true);
				        cd.setType(inc.getType()
				                .equals(exc.getType()) ? null : exc.getType());
				        cd.setProperties((Map<String, Object>) propDiff);
				        cd.setChildren(childDiff);

				        return Mono.just(cd);
			        });
		}

		if (existing instanceof FunctionDefinition efd && incoming instanceof FunctionDefinition ifd) {

			return FlatMapUtil.flatMapMono(

			        () -> jsonMap(ifd.getSteps(), efd.getSteps()),

			        stepDiff -> jsonMap(ifd.getStepGroups(), efd.getStepGroups()),

			        (stepDiff, stepGroupDiff) ->
					{

				        FunctionDefinition fd = new FunctionDefinition();

				        fd.setSteps((Map<String, Statement>) stepDiff);
				        fd.setStepGroups((Map<String, StatementGroup>) stepGroupDiff);

				        return Mono.just(fd);
			        }

			);
		}

		if (existing instanceof Statement est && incoming instanceof Statement ist) {

//			return FlatMapUtil.flatMapMono(
//					() -> 
//			);
		}

		return Mono.just(incoming);
	}

	private DifferenceExtractor() {
	}

}
