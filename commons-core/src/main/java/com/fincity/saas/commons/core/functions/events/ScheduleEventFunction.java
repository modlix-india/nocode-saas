package com.fincity.saas.commons.core.functions.events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.scheduler.ScheduledEventService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CoreServices.Event.ScheduleEvent: raise {@code eventName} at {@code runAt}
 * instead of now. The scheduler raises it into the same queue CreateEvent uses,
 * so the same-named EventAction runs it.
 *
 * <p>
 * {@code key} is the idempotency handle. Scheduling the same key again replaces
 * the earlier one unless it is running right now, which answers
 * {@code scheduled: false}. A blank key gets a generated one, returned in
 * {@code key}. Delivery is at least once, so the action must tolerate a repeat.
 *
 * <p>
 * {@code clientCode} defaults to the caller's own client. A partner's server
 * function passes the managing client, as it does for Storage, so the one-off
 * lives with the data it is about and the managing client's users can replace
 * or cancel it under the same key. See {@link ScheduleTenant}.
 */
public class ScheduleEventFunction extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "ScheduleEvent";

    private static final String NAMESPACE = "CoreServices.Event";

    private static final String EVENT_NAME = "eventName";

    private static final String DATA = "data";

    private static final String RUN_AT = "runAt";

    private static final String KEY = "key";

    private static final String CLIENT_CODE = "clientCode";

    private static final String OUTPUT_SCHEDULED = "scheduled";

    private final ScheduledEventService scheduledEventService;

    private final EventDefinitionService edService;

    private final IFeignSecurityService securityService;

    public ScheduleEventFunction(ScheduledEventService scheduledEventService, EventDefinitionService edService,
            IFeignSecurityService securityService) {
        this.scheduledEventService = scheduledEventService;
        this.edService = edService;
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(OUTPUT_SCHEDULED, Schema.ofBoolean(OUTPUT_SCHEDULED), KEY,
                        Schema.ofString(KEY)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(EVENT_NAME, Schema.ofString(EVENT_NAME)),
                Parameter.ofEntry(DATA, Schema.ofObject(DATA).setDefaultValue(new JsonObject())),
                // An ISO timestamp (an offset, or UTC when it has none) or epoch milliseconds.
                Parameter.ofEntry(RUN_AT, Schema.ofAny(RUN_AT)),
                Parameter.ofEntry(KEY, Schema.ofString(KEY).setDefaultValue(new JsonPrimitive(""))),
                // Blank is the caller's own client; otherwise its direct managing client (the partner case).
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String eventName = context.getArguments().get(EVENT_NAME).getAsString();
        JsonElement dataElement = context.getArguments().get(DATA);
        Map<String, Object> data = dataElement == null || !dataElement.isJsonObject() ? Map.of()
                : JsonUtil.toMap(dataElement.getAsJsonObject());
        JsonElement keyElement = context.getArguments().get(KEY);
        String key = keyElement == null || keyElement.isJsonNull() ? "" : keyElement.getAsString();
        JsonElement clientElement = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientElement == null || clientElement.isJsonNull() ? ""
                : clientElement.getAsString().trim();

        Instant runAt;
        try {
            runAt = parseRunAt(context.getArguments().get(RUN_AT));
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, "")))
                return Mono.just(output(false, key));

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> ScheduleTenant.clientCode(this.securityService, ca, clientCode),

                            // Refuse an event the app does not define, as CreateEvent does.
                            (ca, client) -> this.edService.read(eventName, ca.getUrlAppCode(), client),

                            (ca, client, ed) -> LogUtil.isDraft(),

                            (ca, client, ed, draft) -> this.scheduledEventService.schedule(ca.getUrlAppCode(),
                                    client, Boolean.TRUE.equals(draft), eventName, data, runAt, key,
                                    ca.getUser() == null || ca.getUser().getId() == null ? null
                                            : ca.getUser().getId().toString()),

                            (ca, client, ed, draft, scheduled) -> Mono.just(output(scheduled.event() != null,
                                    scheduled.key())))
                    .switchIfEmpty(Mono.defer(() -> Mono.just(output(false, key))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ScheduleEventFunction.internalExecute"));
        });
    }

    private static FunctionOutput output(boolean scheduled, String key) {
        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(OUTPUT_SCHEDULED, new JsonPrimitive(scheduled),
                KEY, new JsonPrimitive(key == null ? "" : key)))));
    }

    public static Instant parseRunAt(JsonElement element) {

        if (element == null || element.isJsonNull())
            return Instant.now();

        if (!element.isJsonPrimitive())
            throw new IllegalArgumentException("runAt must be an ISO timestamp or epoch milliseconds");

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber())
            return Instant.ofEpochMilli(primitive.getAsLong());

        String text = primitive.getAsString().trim();
        if (text.isEmpty())
            return Instant.now();

        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException offsetMissing) {
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("runAt is not an ISO timestamp: " + text, e);
            }
        }
    }
}
