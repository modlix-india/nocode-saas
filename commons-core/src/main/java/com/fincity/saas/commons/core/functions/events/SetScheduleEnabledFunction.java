package com.fincity.saas.commons.core.functions.events;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CoreServices.Event.SetScheduleEnabled: turn the caller's own client's runs of a
 * scheduled event on or off.
 *
 * <p>
 * Made for an APP_ACCESS_CHANGED EventAction: the event runs in the affected
 * client's context, so the action can start that client's schedules when it gets
 * edit access. Only {@code schedule.enabled} is written into the client's own copy
 * of the definition; everything else is inherited from the base, so a later change
 * to the cron still reaches the client.
 *
 * <p>
 * The app and the client are both taken from the caller's context, never from
 * arguments, so a function can only switch schedules of its own app for the
 * client it is running for. Like the other definition-bound functions it does
 * nothing outside a server function, and on the draft surface it leaves the live
 * definition alone.
 *
 * <p>
 * Output {@code changed}: true when the client's effective schedule changed,
 * false when it already was so, when the app has no such scheduled event, or
 * when the call was refused.
 */
public class SetScheduleEnabledFunction extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "SetScheduleEnabled";

    private static final String NAMESPACE = "CoreServices.Event";

    private static final String EVENT_NAME = "eventName";

    private static final String ENABLED = "enabled";

    private static final String OUTPUT_CHANGED = "changed";

    private final EventDefinitionService edService;

    public SetScheduleEnabledFunction(EventDefinitionService edService) {
        this.edService = edService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(OUTPUT_CHANGED, Schema.ofBoolean(OUTPUT_CHANGED)));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.ofEntries(
                        Parameter.ofEntry(EVENT_NAME, Schema.ofString(EVENT_NAME)),
                        Parameter.ofEntry(ENABLED,
                                Schema.ofBoolean(ENABLED).setDefaultValue(new JsonPrimitive(true)))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        JsonElement nameElement = context.getArguments().get(EVENT_NAME);
        String eventName = nameElement == null || nameElement.isJsonNull() ? "" : nameElement.getAsString().trim();
        JsonElement enabledElement = context.getArguments().get(ENABLED);
        boolean enabled = enabledElement == null || enabledElement.isJsonNull() || enabledElement.getAsBoolean();

        if (eventName.isEmpty())
            return Mono.just(output(false));

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, "")))
                return Mono.just(output(false));

            return FlatMapUtil.flatMapMono(

                            LogUtil::isDraft,

                            draft -> Boolean.TRUE.equals(draft) ? Mono.empty()
                                    : SecurityContextUtil.getUsersContextAuthentication(),

                            (draft, ca) -> this.edService.setOwnScheduleEnabled(eventName, ca.getUrlAppCode(),
                                    ca.getClientCode(), enabled),

                            (draft, ca, changed) -> Mono.just(output(Boolean.TRUE.equals(changed))))
                    .switchIfEmpty(Mono.defer(() -> Mono.just(output(false))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "SetScheduleEnabledFunction.internalExecute"));
        });
    }

    private static FunctionOutput output(boolean changed) {
        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(OUTPUT_CHANGED, new JsonPrimitive(changed)))));
    }
}
