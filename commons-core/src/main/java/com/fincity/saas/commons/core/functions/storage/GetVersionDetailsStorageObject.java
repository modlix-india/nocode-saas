package com.fincity.saas.commons.core.functions.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * CoreServices.Storage.GetVersionDetails
 *
 * The version and audit history of ONE row. Version history is reachable over
 * REST but was not reachable from a page at all, so an app could not render its
 * own audit trail.
 *
 * Scoped to a single objectId, which is what the underlying read supports: there
 * is no cross-row version query on the platform today.
 */
public class GetVersionDetailsStorageObject extends AbstractReactiveFunction {

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "GetVersionDetails";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String STORAGE_NAME = "storageName";

    private static final String OBJECT_ID = "objectId";

    private static final String INCLUDE_OBJECT = "includeObject";

    private static final String FILTER = "filter";

    private static final String PAGE = "page";

    private static final String SIZE = "size";

    private static final String COUNT = "count";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private final AppDataService appDataService;
    private final ObjectMapper mapper;
    private final Gson gson;

    public GetVersionDetailsStorageObject(AppDataService appDataService, ObjectMapper mapper, Gson gson) {
        this.appDataService = appDataService;
        this.mapper = mapper;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        Event errorEvent =
                new Event().setName(Event.ERROR).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.ofEntries(
                        Map.entry(STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME))),
                        Map.entry(OBJECT_ID, Parameter.of(OBJECT_ID, Schema.ofString(OBJECT_ID))),
                        Map.entry(
                                INCLUDE_OBJECT,
                                Parameter.of(
                                        INCLUDE_OBJECT,
                                        Schema.ofBoolean(INCLUDE_OBJECT).setDefaultValue(new JsonPrimitive(true)))),
                        Map.entry(
                                FILTER,
                                Parameter.of(FILTER, Schema.ofObject(FILTER).setDefaultValue(new JsonObject()))),
                        Map.entry(
                                PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE).setDefaultValue(new JsonPrimitive(0)))),
                        Map.entry(
                                SIZE,
                                Parameter.of(SIZE, Schema.ofInteger(SIZE).setDefaultValue(new JsonPrimitive(20)))),
                        Map.entry(
                                COUNT,
                                Parameter.of(COUNT, Schema.ofBoolean(COUNT).setDefaultValue(new JsonPrimitive(true)))),
                        Map.entry(
                                APP_CODE,
                                Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive("")))),
                        Map.entry(
                                CLIENT_CODE,
                                Parameter.of(
                                        CLIENT_CODE,
                                        Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))))))
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String storageName = context.getArguments().get(STORAGE_NAME).getAsString();

        JsonElement objectIdJSON = context.getArguments().get(OBJECT_ID);
        String objectId = objectIdJSON == null || objectIdJSON.isJsonNull() ? null : objectIdJSON.getAsString();

        if (StringUtil.isNullOrBlank(objectId))
            return Mono.just(new FunctionOutput(List.of(EventResult.of(
                    Event.ERROR,
                    Map.of(Event.ERROR, new JsonPrimitive("objectId is required to read a row's version history."))))));

        boolean includeObject = context.getArguments().get(INCLUDE_OBJECT).getAsBoolean();

        JsonObject filter = context.getArguments().get(FILTER).getAsJsonObject();

        Integer page = context.getArguments().get(PAGE).getAsInt();
        Integer size = context.getArguments().get(SIZE).getAsInt();
        boolean count = context.getArguments().get(COUNT).getAsBoolean();

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        AbstractCondition condition = filter.size() == 0
                ? null
                : this.mapper.convertValue(gson.fromJson(filter, Map.class), AbstractCondition.class);

        Query dsq = new Query().setCondition(condition).setPage(page).setSize(size).setCount(count);

        return this.appDataService
                .readPageVersion(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode,
                        storageName,
                        objectId,
                        dsq,
                        includeObject)
                .map(received -> {
                    Map<String, Object> pg = Map.of(
                            "content",
                            received.getContent(),
                            "page",
                            Map.of(
                                    "first", received.isFirst(),
                                    "last", received.isLast(),
                                    "size", received.getSize(),
                                    "page", received.getNumber(),
                                    "totalElements", received.getTotalElements(),
                                    "totalPages", received.getTotalPages(),
                                    "numberOfElements", received.getNumberOfElements()),
                            "total",
                            received.getTotalElements());

                    return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(pg)))));
                });
    }
}
