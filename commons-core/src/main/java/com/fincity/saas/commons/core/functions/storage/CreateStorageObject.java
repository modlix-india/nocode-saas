package com.fincity.saas.commons.core.functions.storage;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import reactor.core.publisher.Mono;

public class CreateStorageObject extends AbstractReactiveFunction {

    private static final String DATA_OBJECT = "dataObject";

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "Create";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String STORAGE_NAME = "storageName";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String EAGER = "eager";

    private static final String EAGER_FIELDS = "eagerFields";

    private final AppDataService appDataService;
    private final Gson gson;

    public CreateStorageObject(AppDataService appDataService, Gson gson) {
        this.appDataService = appDataService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        Event errorEvent =
                new Event().setName(Event.ERROR).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        return new FunctionSignature()
                .setName(FUNCTION_NAME)
                .setNamespace(NAME_SPACE)
                .setParameters(Map.of(
                        STORAGE_NAME,
                        new Parameter().setParameterName(STORAGE_NAME).setSchema(Schema.ofString(STORAGE_NAME)),
                        DATA_OBJECT,
                        new Parameter().setParameterName(DATA_OBJECT).setSchema(Schema.ofObject(DATA_OBJECT)),
                        APP_CODE,
                        Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                        CLIENT_CODE,
                        Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                        EAGER,
                        Parameter.of(EAGER, Schema.ofBoolean(EAGER).setDefaultValue(new JsonPrimitive(false))),
                        EAGER_FIELDS,
                        Parameter.of(EAGER_FIELDS, Schema.ofString(EAGER_FIELDS), true)))
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String storageName = context.getArguments().get(STORAGE_NAME).getAsString();

        JsonObject dataObject = context.getArguments().get(DATA_OBJECT).getAsJsonObject();

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        boolean eager = context.getArguments().get(EAGER).getAsBoolean();

        List<String> eagerFields = StreamSupport.stream(
                        context.getArguments()
                                .get(EAGER_FIELDS)
                                .getAsJsonArray()
                                .spliterator(),
                        false)
                .map(JsonElement::getAsString)
                .toList();

        if (storageName == null || dataObject == null)
            return Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonObject())))));

        Map<String, Object> dataObj = gson.fromJson(dataObject, new TypeToken<Map<String, Object>>() {}.getType());

        return appDataService
                .create(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode,
                        storageName,
                        new DataObject().setData(dataObj),
                        eager,
                        eagerFields)
                .map(obj ->
                        new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(obj))))));
    }
}
