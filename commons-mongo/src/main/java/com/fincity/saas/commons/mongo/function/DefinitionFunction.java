package com.fincity.saas.commons.mongo.function;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.FunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.runtime.KIRuntime;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DefinitionFunction extends com.fincity.nocode.kirun.engine.function.AbstractFunction {

	private final FunctionDefinition definition;
	private final String executionAuthorization;

	@Override
	public FunctionSignature getSignature() {

		return definition;
	}

	@Override
	protected FunctionOutput internalExecute(FunctionExecutionParameters context) {

		KIRuntime runtime = new KIRuntime(definition);
		return runtime.execute(context);
	}
}
