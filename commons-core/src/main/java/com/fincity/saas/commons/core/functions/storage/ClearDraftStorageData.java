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
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Empty an app's DRAFT rows, every storage, for the caller's tenant. Live rows are
 * never touched. Needs write access to the app.
 *
 * Output {@code result}: rows deleted by storage name, only the storages that had any.
 */
public class ClearDraftStorageData extends AbstractReactiveFunction {

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "ClearDraft";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private final AppDataService appDataService;
    private final Gson gson;

    public ClearDraftStorageData(AppDataService appDataService, Gson gson) {
        this.appDataService = appDataService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event =
                new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofObject(EVENT_RESULT)));

        Event errorEvent =
                new Event().setName(Event.ERROR).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(
                        APP_CODE,
                        Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                        CLIENT_CODE,
                        Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))))
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        return this.appDataService
                .clearDraftData(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode)
                .map(deleted -> new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(deleted))))));
    }
}
