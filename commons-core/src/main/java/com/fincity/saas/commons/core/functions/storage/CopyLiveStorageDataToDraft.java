package com.fincity.saas.commons.core.functions.storage;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.fincity.saas.commons.core.model.DraftCopyFilter;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Replace an app's DRAFT rows with its LIVE rows, every storage, for the caller's
 * tenant. Needs write access to the app.
 *
 * {@code filters} is keyed by storage name; a storage without an entry is copied
 * whole. An entry is a {@link DraftCopyFilter}:
 * {@code {condition: <a filter, as ReadPage takes>, follow: [{field, storage, refField}]}}.
 * To seed a draft with recent deals and what hangs off them:
 *
 * <pre>
 * deal:     {condition: {field: createdAt, operator: GREATER_THAN_EQUAL, value: "2026-04-15 00:00:00"}}
 * lead:     {follow: [{field: _id, storage: deal, refField: ownerId}]}
 * activity: {follow: [{field: ticketId, storage: deal}, {field: ownerId, storage: lead}]}
 * </pre>
 *
 * Output {@code result}: rows copied by storage name.
 */
public class CopyLiveStorageDataToDraft extends AbstractReactiveFunction {

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "CopyToDraft";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String FILTERS = "filters";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final TypeReference<Map<String, DraftCopyFilter>> FILTERS_TYPE = new TypeReference<>() {};

    private final AppDataService appDataService;
    private final ObjectMapper mapper;
    private final Gson gson;

    public CopyLiveStorageDataToDraft(AppDataService appDataService, ObjectMapper mapper, Gson gson) {
        this.appDataService = appDataService;
        this.mapper = mapper;
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
                        FILTERS,
                        Parameter.of(FILTERS, Schema.ofObject(FILTERS).setDefaultValue(new JsonObject())),
                        APP_CODE,
                        Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                        CLIENT_CODE,
                        Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))))
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        JsonObject filters = context.getArguments().get(FILTERS).getAsJsonObject();

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        Map<String, DraftCopyFilter> byStorage =
                filters.isEmpty() ? Map.of() : this.mapper.convertValue(StorageJson.toMap(filters), FILTERS_TYPE);

        return this.appDataService
                .copyAppToDraft(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode,
                        byStorage)
                .map(copied -> new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(copied))))));
    }
}
