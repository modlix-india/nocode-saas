package com.fincity.saas.core.functions.crypto.mac;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;

import reactor.core.publisher.Mono;

public class MacValidator extends AbstractReactiveFunction {

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		return null;
	}

	@Override
	public FunctionSignature getSignature() {
		return null;
	}
}
