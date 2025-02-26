package com.fincity.saas.core.functions.notification;

import java.math.BigInteger;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;

import reactor.core.publisher.Mono;

public class SendNotification extends AbstractReactiveFunction {

	private static final String FUNCTION_NAME = "SendNotification";

	private static final String NAME_SPACE = "CoreServices.Notification";

	public static final String EVENT_DATA = "result";

	public static final String appCode = "appCode";

	public static final String clientCode = "clientCode";

	public static final String userId = "userId";




	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		return null;
	}

	@Override
	public FunctionSignature getSignature() {
		return null;
	}
}
