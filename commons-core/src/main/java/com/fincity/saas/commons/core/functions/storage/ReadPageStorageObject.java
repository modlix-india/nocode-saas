package com.fincity.saas.commons.core.functions.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import reactor.core.publisher.Mono;

public class ReadPageStorageObject extends AbstractReactiveFunction {

    private static final String NULL_HANDLING = "nullHandling";

    private static final String IGNORE_CASE = "ignoreCase";

    private static final String PROPERTY = "property";

    private static final String DIRECTION = "direction";

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "ReadPage";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String STORAGE_NAME = "storageName";

    private static final String FILTER = "filter";

    private static final String SORT = "sort";

    private static final String PAGE = "page";

    private static final String SIZE = "size";

    private static final String COUNT = "count";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String EAGER = "eager";

    private static final String EAGER_FIELDS = "eagerFields";

    private static final String IGNORE_FIELDS = "ignoreFields";

    private final AppDataService appDataService;
    private final ObjectMapper mapper;
    private final Gson gson;

    public ReadPageStorageObject(AppDataService appDataService, ObjectMapper mapper, Gson gson) {
        this.appDataService = appDataService;
        this.mapper = mapper;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {
        Schema objectSchema = new Schema()
                .setName("SortOrder")
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        DIRECTION,
                        Schema.ofString(DIRECTION)
                                .setEnums(List.of(new JsonPrimitive("ASC"), new JsonPrimitive("DESC")))
                                .setDefaultValue(new JsonPrimitive("ASC")),
                        PROPERTY,
                        Schema.ofString(PROPERTY),
                        IGNORE_CASE,
                        Schema.ofBoolean(IGNORE_CASE).setDefaultValue(new JsonPrimitive(true)),
                        NULL_HANDLING,
                        Schema.ofString(NULL_HANDLING)
                                .setEnums(List.of(
                                        new JsonPrimitive("NATIVE"),
                                        new JsonPrimitive("NULLS_FIRST"),
                                        new JsonPrimitive("NULLS_LAST")))
                                .setDefaultValue(new JsonPrimitive("NATIVE"))));

        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        Event errorEvent =
                new Event().setName(Event.ERROR).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.ofEntries(
                        Map.entry(STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME))),
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
                                SORT,
                                Parameter.of(SORT, Schema.ofArray(SORT, objectSchema).setDefaultValue(new JsonArray()))),
                        Map.entry(
                                APP_CODE,
                                Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive("")))),
                        Map.entry(
                                CLIENT_CODE,
                                Parameter.of(
                                        CLIENT_CODE,
                                        Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))),
                        Map.entry(
                                EAGER,
                                Parameter.of(EAGER, Schema.ofBoolean(EAGER).setDefaultValue(new JsonPrimitive(false)))),
                        Map.entry(EAGER_FIELDS, Parameter.of(EAGER_FIELDS, Schema.ofString(EAGER_FIELDS), true)),
                        Map.entry(IGNORE_FIELDS, Parameter.of(IGNORE_FIELDS, Schema.ofString(IGNORE_FIELDS), true))))
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String storageName = context.getArguments().get(STORAGE_NAME).getAsString();

        JsonObject filter = context.getArguments().get(FILTER).getAsJsonObject();

        JsonArray sort = context.getArguments().get(SORT).getAsJsonArray();

        Integer page = context.getArguments().get(PAGE).getAsInt();

        Integer size = context.getArguments().get(SIZE).getAsInt();

        boolean eager = context.getArguments().get(EAGER).getAsBoolean();

        List<String> eagerFields = this.stringList(context, EAGER_FIELDS);

        // Fields to drop from every returned row. Mongo takes this as a projection
        // exclusion, so a read can leave a large column (a document body, a blob of
        // JSON) in the database instead of shipping it to the caller.
        List<String> ignoreFields = this.stringList(context, IGNORE_FIELDS);

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        AbstractCondition absc = null;

        if (filter.size() != 0) {
            absc = this.mapper.convertValue(gson.fromJson(filter, Map.class), AbstractCondition.class);
        }

        Query dsq = new Query()
                .setExcludeFields(!ignoreFields.isEmpty())
                .setFields(ignoreFields)
                .setCondition(absc)
                .setPage(page)
                .setSize(size)
                .setCount(true)
                .setEager(eager)
                .setSort(getSortObj(sort))
                .setEagerFields(eagerFields);

        return this.appDataService
                .readPage(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode,
                        storageName,
                        dsq)
                .map(receivedObject -> {
                    Map<String, Object> pg = Map.of(
                            "content",
                            receivedObject.getContent(),
                            "page",
                            Map.of(
                                    "first",
                                    receivedObject.isFirst(),
                                    "last",
                                    receivedObject.isLast(),
                                    "size",
                                    receivedObject.getSize(),
                                    "page",
                                    receivedObject.getNumber(),
                                    "totalElements",
                                    receivedObject.getTotalElements(),
                                    "totalPages",
                                    receivedObject.getTotalPages(),
                                    "sort",
                                    receivedObject.getSort(),
                                    "numberOfElements",
                                    receivedObject.getNumberOfElements()),
                            "total",
                            receivedObject.getTotalElements());

                    return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(pg)))));
                });
    }

    private List<String> stringList(ReactiveFunctionExecutionParameters context, String parameterName) {
        JsonElement arg = context.getArguments().get(parameterName);
        if (arg == null || arg.isJsonNull()) return List.of();

        return StreamSupport.stream(arg.getAsJsonArray().spliterator(), false)
                .filter(e -> e != null && !e.isJsonNull())
                .map(JsonElement::getAsString)
                .filter(e -> !StringUtil.isNullOrBlank(e))
                .toList();
    }

    private Sort getSortObj(JsonArray sortJson) {
        List<Order> orders = new ArrayList<>();
        for (JsonElement jsonElement : sortJson) {
            JsonObject sortObj = jsonElement.getAsJsonObject();

            Order order = "desc".equalsIgnoreCase(sortObj.get(DIRECTION).getAsString())
                    ? Order.desc(sortObj.get(PROPERTY).getAsString())
                    : Order.asc(sortObj.get(PROPERTY).getAsString());

            if (sortObj.has(IGNORE_CASE) && sortObj.get(IGNORE_CASE).getAsBoolean()) {
                order = order.ignoreCase();
            }

            if (sortObj.has(NULL_HANDLING)) {
                order = getNullsOrder(order, sortObj);
            }
            orders.add(order);
        }
        return Sort.by(orders);
    }

    private Order getNullsOrder(Order order, JsonObject sortObj) {
        return switch (sortObj.get(NULL_HANDLING).getAsString()) {
            case "NULLS_FIRST" -> order.nullsFirst();
            case "NULLS_LAST" -> order.nullsLast();
            default -> order.nullsNative();
        };
    }
}
