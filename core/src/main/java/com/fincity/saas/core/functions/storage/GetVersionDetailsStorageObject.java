package com.fincity.saas.core.functions.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.*;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

public class GetVersionDetailsStorageObject extends AbstractReactiveFunction {

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "GetVersionDetails";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String STORAGE_NAME = "storageName";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String FILTER = "filter";

    private static final String PAGE = "page";

    private static final String SIZE = "size";

    private static final String COUNT = "count";

    private static final String OBJECTID = "objectId";

    private final AppDataService appDataService;
    private final ObjectMapper mapper;
    private final Gson gson;

    public GetVersionDetailsStorageObject(AppDataService appDataService, ObjectMapper mapper, Gson gson) {

        this.appDataService = appDataService;
        this.mapper = mapper;
        this.gson = gson;
    }

    public AppDataService getAppDataService() {
        return appDataService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        Event errorEvent = new Event().setName(Event.ERROR)
                .setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        return new FunctionSignature().setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(

                        STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME)),

                        FILTER, Parameter.of(FILTER, Schema.ofObject(FILTER)
                                .setDefaultValue(new JsonObject())),

                        PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE)
                                .setDefaultValue(new JsonPrimitive(0))),

                        SIZE, Parameter.of(SIZE, Schema.ofInteger(SIZE)
                                .setDefaultValue(new JsonPrimitive(20))),

                        COUNT, Parameter.of(COUNT, Schema.ofBoolean(COUNT)
                                .setDefaultValue(new JsonPrimitive(true))),

                        OBJECTID,Parameter.of(OBJECTID,Schema.ofString(OBJECTID)),

                        APP_CODE,
                        Parameter.of(APP_CODE,
                                Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

                        CLIENT_CODE,
                        Parameter.of(CLIENT_CODE,
                                Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))
                        ))

                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String storageName = context.getArguments()
                .get(STORAGE_NAME)
                .getAsString();

        JsonObject filter = context.getArguments()
                .get(FILTER)
                .getAsJsonObject();

        Integer page = context.getArguments()
                .get(PAGE)
                .getAsInt();

        Integer size = context.getArguments()
                .get(SIZE)
                .getAsInt();

        String objectId = context.getArguments()
                .get(OBJECTID)
                .getAsString();

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        AbstractCondition absc = null;

        if (filter.size() != 0) {
            absc = this.mapper.convertValue(gson.fromJson(filter, Map.class), AbstractCondition.class);
        }

        Query dsq = new Query().setExcludeFields(false)
                .setFields(List.of())
                .setCondition(absc)
                .setPage(page)
                .setSize(size)
                .setCount(true);

        return this.appDataService
                .readPageVersion(StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode, storageName,objectId, dsq)
                .map(receivedObject -> {

                    Map<String, Object> pg = Map.of("content", receivedObject.getContent(),
                            "page", Map.of(
                                    "first", receivedObject.isFirst(),

                                    "last", receivedObject.isLast(),

                                    "size", receivedObject.getSize(),

                                    "page", receivedObject.getNumber(),

                                    "totalElements", receivedObject.getTotalElements(),

                                    "totalPages", receivedObject.getTotalPages(),

                                    "sort", receivedObject.getSort(),

                                    "numberOfElements", receivedObject.getNumberOfElements()),
                            "total", receivedObject.getTotalElements());

                    return new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(pg)))));
                });
    }



}
