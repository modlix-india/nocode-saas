package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.functions.storage.StorageJson;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * One page of the users of ONE client, filtered and sorted by security: what entity-processor's
 * partner "teammates" routes list (security's /users/internal/query with {@code clientId = X}
 * ANDed onto the caller's condition).
 *
 * The client must be related to the caller's (own, managed, or the direct managing client with
 * write access to the app, see {@link SecurityFunctionSupport#isRelatedClient}); any other client
 * is a 403, so a function cannot page through another tenant's people. The clientId filter is
 * always added here, so a condition naming another client can only narrow the page to nothing.
 *
 * Output: {@code users} (the plain user shape of {@link GetUsersByIds}, in security's order),
 * {@code totalElements}, {@code totalPages}, {@code number}, {@code size}.
 */
public class GetClientUsersPage extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetClientUsersPage";

    static final String CLIENT_ID = "clientId";
    static final String CONDITION = "condition";
    static final String PAGE = "page";
    static final String SIZE = "size";
    static final String SORT = "sort";

    static final String EVENT_USERS = "users";
    static final String EVENT_TOTAL = "totalElements";
    static final String EVENT_TOTAL_PAGES = "totalPages";
    static final String EVENT_NUMBER = "number";
    static final String EVENT_SIZE = "size";

    private final IFeignSecurityService securityService;
    private final ObjectMapper mapper;

    public GetClientUsersPage(IFeignSecurityService securityService, ObjectMapper mapper) {
        this.securityService = securityService;
        this.mapper = mapper == null ? new ObjectMapper().registerModule(new CommonsSerializationModule()) : mapper;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(
                        EVENT_USERS, Schema.ofArray(EVENT_USERS, Schema.ofObject("user")),
                        EVENT_TOTAL, Schema.ofLong(EVENT_TOTAL),
                        EVENT_TOTAL_PAGES, Schema.ofInteger(EVENT_TOTAL_PAGES),
                        EVENT_NUMBER, Schema.ofInteger(EVENT_NUMBER),
                        EVENT_SIZE, Schema.ofInteger(EVENT_SIZE)));

        return new FunctionSignature()
                .setNamespace(SecurityFunctionSupport.NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(
                        CLIENT_ID, Parameter.of(CLIENT_ID, SecurityFunctionSupport.idSchema(CLIENT_ID)),
                        CONDITION, Parameter.of(CONDITION, Schema.ofObject(CONDITION).setDefaultValue(new JsonObject())),
                        PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE).setDefaultValue(new JsonPrimitive(0))),
                        SIZE, Parameter.of(SIZE, Schema.ofInteger(SIZE).setDefaultValue(new JsonPrimitive(10))),
                        SORT, Parameter.of(SORT, Schema.ofAny(SORT))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        BigInteger clientId;
        Query query;
        try {
            clientId = SecurityFunctionSupport.idOf(CLIENT_ID, context.getArguments().get(CLIENT_ID));
            if (clientId == null)
                throw SecurityFunctionSupport.badRequest(CLIENT_ID + " is required");
            query = this.query(clientId, context.getArguments());
        } catch (RuntimeException ex) {
            return Mono.error(ex);
        }

        return FlatMapUtil.flatMapMono(
                () -> SecurityFunctionSupport.definitionContextAuthentication(FUNCTION_NAME),
                ca -> SecurityFunctionSupport.isRelatedClient(this.securityService,
                        ca.getUser() == null ? null : ca.getUser().getClientId(), clientId, ca.getUrlAppCode()),
                (ca, related) -> Boolean.TRUE.equals(related)
                        ? this.securityService.readUserPageFilterInternal(query, new LinkedMultiValueMap<>())
                        : Mono.<Page<User>>error(SecurityFunctionSupport.forbidden(
                                "Not allowed to list the users of client " + clientId)),
                (ca, related, page) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(toJson(page,
                        query))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetClientUsersPage.internalExecute"));
    }

    Query query(BigInteger clientId, Map<String, JsonElement> args) {

        AbstractCondition clientCondition = FilterCondition.make(CLIENT_ID, clientId)
                .setOperator(FilterConditionOperator.EQUALS);

        JsonElement cond = args.get(CONDITION);
        AbstractCondition given = null;
        if (cond != null && cond.isJsonObject() && cond.getAsJsonObject().size() != 0)
            given = this.mapper.convertValue(StorageJson.toMap(cond.getAsJsonObject()), AbstractCondition.class);

        AbstractCondition condition = given == null || given.isEmpty() ? clientCondition
                : ComplexCondition.and(given, clientCondition);

        int page = intOf(args.get(PAGE), 0);
        int size = intOf(args.get(SIZE), 10);

        Query q = new Query().setCondition(condition).setPage(Math.max(page, 0)).setSize(size <= 0 ? 10 : size);
        Sort sort = sortOf(args.get(SORT));
        if (sort != null)
            q.setSort(sort);
        return q;
    }

    private static int intOf(JsonElement e, int def) {
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
            return def;
        try {
            return e.getAsNumber().intValue();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /** A sort array or a single sort object ({property, direction}); null when none is given. */
    static Sort sortOf(JsonElement e) {
        if (e == null || e.isJsonNull())
            return null;

        JsonArray arr;
        if (e.isJsonArray())
            arr = e.getAsJsonArray();
        else if (e.isJsonObject()) {
            arr = new JsonArray();
            arr.add(e);
        } else
            return null;

        List<Order> orders = new ArrayList<>();
        for (JsonElement each : arr) {
            if (!each.isJsonObject() || !each.getAsJsonObject().has("property"))
                continue;
            JsonObject o = each.getAsJsonObject();
            String prop = o.get("property").getAsString();
            boolean desc = o.has("direction") && !o.get("direction").isJsonNull()
                    && "desc".equalsIgnoreCase(o.get("direction").getAsString());
            orders.add(desc ? Order.desc(prop) : Order.asc(prop));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }

    static Map<String, JsonElement> toJson(Page<User> page, Query query) {

        JsonArray users = new JsonArray();
        if (page != null)
            for (User u : page.getContent())
                if (u != null && u.getId() != null)
                    users.add(GetUsersByIds.userJson(u));

        long total = page == null ? 0 : page.getTotalElements();
        int size = query.getSize();
        int totalPages = size <= 0 ? 1 : (int) Math.ceil((double) total / size);

        return Map.of(
                EVENT_USERS, users,
                EVENT_TOTAL, new JsonPrimitive(total),
                EVENT_TOTAL_PAGES, new JsonPrimitive(totalPages),
                EVENT_NUMBER, new JsonPrimitive(query.getPage()),
                EVENT_SIZE, new JsonPrimitive(size));
    }
}
