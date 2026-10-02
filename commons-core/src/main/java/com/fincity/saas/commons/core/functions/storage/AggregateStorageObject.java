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
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import reactor.core.publisher.Mono;

/**
 * CoreServices.Storage.Aggregate
 *
 * Returns a flat array: one object per group carrying the group keys and the
 * measures side by side, which is the shape a chart binds to directly.
 */
public class AggregateStorageObject extends AbstractReactiveFunction {

    private static final String EVENT_RESULT = "result";

    private static final String FUNCTION_NAME = "Aggregate";

    private static final String NAME_SPACE = "CoreServices.Storage";

    private static final String STORAGE_NAME = "storageName";

    private static final String FILTER = "filter";

    private static final String HAVING = "having";

    private static final String GROUP_BY = "groupBy";

    private static final String AGGREGATIONS = "aggregations";

    private static final String SORT = "sort";

    private static final String PAGE = "page";

    private static final String SIZE = "size";

    private static final String COUNT = "count";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String FIELD = "field";

    private static final String ALIAS = "alias";

    private static final String BUCKET = "bucket";

    private static final String ENCODING = "encoding";

    private static final String TIMEZONE = "timezone";

    private static final String FUNCTION = "function";

    private static final String PROPERTY = "property";

    private static final String DIRECTION = "direction";

    private final AppDataService appDataService;
    private final ObjectMapper mapper;
    private final Gson gson;

    public AggregateStorageObject(AppDataService appDataService, ObjectMapper mapper, Gson gson) {
        this.appDataService = appDataService;
        this.mapper = mapper;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Schema groupBySchema = new Schema()
                .setName("GroupByField")
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        FIELD,
                        Schema.ofString(FIELD),
                        ALIAS,
                        Schema.ofString(ALIAS),
                        BUCKET,
                        Schema.ofString(BUCKET).setEnums(enumsOf(DateBucketUnit.values())),
                        ENCODING,
                        Schema.ofString(ENCODING).setEnums(enumsOf(DateEncoding.values())),
                        TIMEZONE,
                        Schema.ofString(TIMEZONE).setDefaultValue(new JsonPrimitive("UTC"))));

        Schema aggregationSchema = new Schema()
                .setName("Aggregation")
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        FUNCTION,
                        Schema.ofString(FUNCTION).setEnums(enumsOf(AggregateFunction.values())),
                        FIELD,
                        Schema.ofString(FIELD),
                        ALIAS,
                        Schema.ofString(ALIAS)));

        Schema sortSchema = new Schema()
                .setName("SortOrder")
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        DIRECTION,
                        Schema.ofString(DIRECTION)
                                .setEnums(List.of(new JsonPrimitive("ASC"), new JsonPrimitive("DESC")))
                                .setDefaultValue(new JsonPrimitive("ASC")),
                        PROPERTY,
                        Schema.ofString(PROPERTY)));

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
                                HAVING,
                                Parameter.of(HAVING, Schema.ofObject(HAVING).setDefaultValue(new JsonObject()))),
                        Map.entry(
                                GROUP_BY,
                                Parameter.of(
                                        GROUP_BY,
                                        Schema.ofArray(GROUP_BY, groupBySchema).setDefaultValue(new JsonArray()))),
                        Map.entry(
                                AGGREGATIONS,
                                Parameter.of(
                                        AGGREGATIONS,
                                        Schema.ofArray(AGGREGATIONS, aggregationSchema)
                                                .setDefaultValue(new JsonArray()))),
                        Map.entry(
                                SORT,
                                Parameter.of(SORT, Schema.ofArray(SORT, sortSchema).setDefaultValue(new JsonArray()))),
                        Map.entry(
                                PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE).setDefaultValue(new JsonPrimitive(0)))),
                        Map.entry(
                                SIZE,
                                Parameter.of(SIZE, Schema.ofInteger(SIZE).setDefaultValue(new JsonPrimitive(100)))),
                        Map.entry(
                                COUNT,
                                Parameter.of(COUNT, Schema.ofBoolean(COUNT).setDefaultValue(new JsonPrimitive(false)))),
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

    private static List<JsonElement> enumsOf(Enum<?>[] values) {
        List<JsonElement> list = new ArrayList<>();
        for (Enum<?> v : values) list.add(new JsonPrimitive(v.name()));
        return list;
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String storageName = context.getArguments().get(STORAGE_NAME).getAsString();

        JsonObject filter = context.getArguments().get(FILTER).getAsJsonObject();
        JsonObject having = context.getArguments().get(HAVING).getAsJsonObject();

        Integer page = context.getArguments().get(PAGE).getAsInt();
        Integer size = context.getArguments().get(SIZE).getAsInt();
        boolean count = context.getArguments().get(COUNT).getAsBoolean();

        JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
        String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        AggregateQuery query = new AggregateQuery()
                .setCondition(this.condition(filter))
                .setHaving(this.condition(having))
                .setGroupBy(this.groupBy(context.getArguments().get(GROUP_BY)))
                .setAggregations(this.aggregations(context.getArguments().get(AGGREGATIONS)))
                .setSort(this.sort(context.getArguments().get(SORT)))
                .setPage(page)
                .setSize(size)
                .setCount(count);

        return this.appDataService
                .aggregate(
                        StringUtil.isNullOrBlank(appCode) ? null : appCode,
                        StringUtil.isNullOrBlank(clientCode) ? null : clientCode,
                        storageName,
                        query)
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

    private AbstractCondition condition(JsonObject obj) {
        if (obj == null || obj.isJsonNull() || obj.size() == 0) return null;
        return this.mapper.convertValue(gson.fromJson(obj, Map.class), AbstractCondition.class);
    }

    private List<GroupByField> groupBy(JsonElement arg) {
        List<GroupByField> list = new ArrayList<>();
        if (arg == null || arg.isJsonNull()) return list;

        for (JsonElement e : arg.getAsJsonArray()) {
            if (e == null || e.isJsonNull()) continue;
            JsonObject o = e.getAsJsonObject();

            GroupByField g = new GroupByField().setField(this.str(o, FIELD)).setAlias(this.str(o, ALIAS));

            String bucket = this.str(o, BUCKET);
            if (bucket != null) g.setBucket(DateBucketUnit.valueOf(bucket.toUpperCase()));

            String encoding = this.str(o, ENCODING);
            if (encoding != null) g.setEncoding(DateEncoding.valueOf(encoding.toUpperCase()));

            g.setTimezone(this.str(o, TIMEZONE));
            list.add(g);
        }
        return list;
    }

    private List<Aggregation> aggregations(JsonElement arg) {
        List<Aggregation> list = new ArrayList<>();
        if (arg == null || arg.isJsonNull()) return list;

        for (JsonElement e : arg.getAsJsonArray()) {
            if (e == null || e.isJsonNull()) continue;
            JsonObject o = e.getAsJsonObject();

            Aggregation a = new Aggregation().setField(this.str(o, FIELD)).setAlias(this.str(o, ALIAS));

            String fn = this.str(o, FUNCTION);
            if (fn != null) a.setFunction(AggregateFunction.valueOf(fn.toUpperCase()));

            list.add(a);
        }
        return list;
    }

    private Sort sort(JsonElement arg) {
        if (arg == null || arg.isJsonNull()) return null;

        List<Order> orders = new ArrayList<>();
        for (JsonElement e : arg.getAsJsonArray()) {
            if (e == null || e.isJsonNull()) continue;
            JsonObject o = e.getAsJsonObject();

            String property = this.str(o, PROPERTY);
            if (property == null) continue;

            orders.add("desc".equalsIgnoreCase(this.str(o, DIRECTION)) ? Order.desc(property) : Order.asc(property));
        }
        return orders.isEmpty() ? null : Sort.by(orders);
    }

    private String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s;
    }
}
