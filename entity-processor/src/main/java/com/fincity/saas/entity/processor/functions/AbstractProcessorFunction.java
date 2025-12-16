package com.fincity.saas.entity.processor.functions;

import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;

public abstract class AbstractProcessorFunction extends AbstractReactiveFunction {

    public static final String NAMESPACE_PREFIX = "EntityProcessor";

    private FunctionSignature functionSignature;

    public AbstractProcessorFunction(String namespaceSuffix, String functionName, Map<String, Parameter> parameters,
            String resultEventName, Schema resultSchema) {

        Event resultEvent = new Event().setName(Event.OUTPUT);

        if (!StringUtil.isNullOrBlank(resultEventName)) {
            resultEvent.setParameters(Map.of(resultEventName, resultSchema));
        }
        this.functionSignature = new FunctionSignature()
                .setNamespace(NAMESPACE_PREFIX + "." + namespaceSuffix)
                .setName(functionName)
                .setParameters(parameters)
                .setEvents(Map.of(Event.OUTPUT, resultEvent));
    }

    @Override
    public FunctionSignature getSignature() {
        return this.functionSignature;
    }
}
