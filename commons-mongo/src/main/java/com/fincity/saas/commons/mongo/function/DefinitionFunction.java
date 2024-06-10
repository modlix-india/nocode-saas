package com.fincity.saas.commons.mongo.function;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.function.reactive.IDefinitionBasedFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveKIRuntime;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = false)
public class DefinitionFunction extends AbstractReactiveFunction implements IDefinitionBasedFunction {

	public static final String CONTEXT_KEY = "KIRun Runtime";

	private final FunctionDefinition definition;
	private final String executionAuthorization;

	@Override
	public FunctionSignature getSignature() {
		return definition;
	}

	@Override
	public Map<String, Event> getProbableEventSignature(Map<String, List<Schema>> probableParameters) {
		return this.getSignature()
				.getEvents();
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		return Mono.deferContextual(ctx -> {

			boolean isDebug = ctx.hasKey(LogUtil.DEBUG_KEY);

			ReactiveKIRuntime runtime = new ReactiveKIRuntime(definition, isDebug);

			return runtime.execute(context).contextWrite(Context.of(CONTEXT_KEY, "true")).map(e -> {

				if (isDebug) {
					FlatMapUtil.logValue("Definition Function : Executing Function : " + definition.getFullName());
					FlatMapUtil.logValue(runtime.getDebugString());
				}

				return e;
			});
		});
	}

	@JsonIgnore
	public DefinitionFunction getOnlySignatureFromDefinitionAsFunction() {
		FunctionDefinition fd = new FunctionDefinition(this.definition);
		fd.setStepGroups(Map.of());
		fd.setSteps(Map.of());

		return new DefinitionFunction(fd, null);
	}

	@JsonIgnore
	public FunctionSignature getOnlySignatureFromDefinition() {
		FunctionDefinition fd = new FunctionDefinition(this.definition);
		fd.setStepGroups(Map.of());
		fd.setSteps(Map.of());

		return fd;
	}
}
