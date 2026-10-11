package com.fincity.saas.commons.core.functions.events;

import java.util.HashMap;
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
import com.fincity.saas.commons.core.scheduler.ScheduledEventService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CoreServices.Event.CancelScheduledEvent: cancels the pending one-off stored
 * under {@code key} by ScheduleEvent, in the caller's app and client.
 * {@code cancelled} is false when there was nothing pending under that key: it
 * never existed, it already ran, or it is running right now.
 *
 * <p>
 * {@code clientCode} defaults to the caller's own client; a partner passes the
 * managing client to cancel a one-off stored there. See {@link ScheduleTenant}.
 */
public class CancelScheduledEventFunction extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "CancelScheduledEvent";

    private static final String NAMESPACE = "CoreServices.Event";

    private static final String KEY = "key";

    private static final String CLIENT_CODE = "clientCode";

    private static final String OUTPUT_CANCELLED = "cancelled";

    private final ScheduledEventService scheduledEventService;

    private final IFeignSecurityService securityService;

    public CancelScheduledEventFunction(ScheduledEventService scheduledEventService,
            IFeignSecurityService securityService) {
        this.scheduledEventService = scheduledEventService;
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(OUTPUT_CANCELLED, Schema.ofBoolean(OUTPUT_CANCELLED)));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(new HashMap<>(Map.ofEntries(
                        Parameter.ofEntry(KEY, Schema.ofString(KEY)),
                        // Blank is the caller's own client; otherwise its direct managing client.
                        Parameter.ofEntry(CLIENT_CODE,
                                Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String key = context.getArguments().get(KEY).getAsString();
        JsonElement clientElement = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientElement == null || clientElement.isJsonNull() ? ""
                : clientElement.getAsString().trim();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, "")))
                return Mono.just(output(false));

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> ScheduleTenant.clientCode(this.securityService, ca, clientCode),

                            (ca, client) -> LogUtil.isDraft(),

                            (ca, client, draft) -> this.scheduledEventService.cancel(ca.getUrlAppCode(), client,
                                    Boolean.TRUE.equals(draft), key),

                            (ca, client, draft, cancelled) -> Mono.just(output(Boolean.TRUE.equals(cancelled))))
                    .switchIfEmpty(Mono.defer(() -> Mono.just(output(false))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "CancelScheduledEventFunction.internalExecute"));
        });
    }

    private static FunctionOutput output(boolean cancelled) {
        return new FunctionOutput(
                List.of(EventResult.outputOf(Map.of(OUTPUT_CANCELLED, new JsonPrimitive(cancelled)))));
    }
}
