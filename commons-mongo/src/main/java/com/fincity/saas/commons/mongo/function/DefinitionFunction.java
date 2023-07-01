package com.fincity.saas.commons.mongo.function;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveKIRuntime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = false)
public class DefinitionFunction extends AbstractReactiveFunction {

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
		return new ReactiveKIRuntime(definition).execute(context);
	}

	@JsonIgnore
	public DefinitionFunction getOnlySignatureFromDefinitionAsFunction() {
		FunctionDefinition fd = new FunctionDefinition(this.definition);
		fd.setStepGroups(Map.of());
		fd.setSteps(Map.of());

		return new DefinitionFunction(fd, null);
	}

}
