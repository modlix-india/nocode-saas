package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Clients by id, keyed by id: the client twin of {@link GetUsersByIds}.
 *
 * What a page shows next to a row's {@code clientId}: the partner's code and name and,
 * when asked, its owners. The same visibility rule as GetUsersByIds: only the caller's
 * own client, clients it manages, and its direct managing client (for a partner, when
 * that client has write access to the app) come back; any other id is silently left
 * out, so a row naming an unrelated client resolves to nothing rather than leaking it.
 */
public class GetClientsByIds extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetClientsByIds";

    static final String IDS = "ids";

    static final String FETCH_OWNERS = "fetchOwners";

    static final String FETCH_MANAGING_CLIENT = "fetchManagingClient";

    static final String FETCH_CLIENT_MANAGERS = "fetchClientManagers";

    static final String WITH_COUNTS = "withCounts";

    static final String EVENT_DATA_CLIENTS = "clients";

    private final IFeignSecurityService securityService;

    public GetClientsByIds(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_CLIENTS, Schema.ofObject(EVENT_DATA_CLIENTS)));

        return new FunctionSignature()
                .setNamespace(SecurityFunctionSupport.NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(
                        IDS, Parameter.of(IDS, SecurityFunctionSupport.idArraySchema(IDS)),
                        FETCH_OWNERS,
                        Parameter.of(FETCH_OWNERS,
                                Schema.ofBoolean(FETCH_OWNERS).setDefaultValue(new JsonPrimitive(false))),
                        FETCH_MANAGING_CLIENT,
                        Parameter.of(FETCH_MANAGING_CLIENT,
                                Schema.ofBoolean(FETCH_MANAGING_CLIENT).setDefaultValue(new JsonPrimitive(false))),
                        FETCH_CLIENT_MANAGERS,
                        Parameter.of(FETCH_CLIENT_MANAGERS,
                                Schema.ofBoolean(FETCH_CLIENT_MANAGERS).setDefaultValue(new JsonPrimitive(false))),
                        WITH_COUNTS,
                        Parameter.of(WITH_COUNTS,
                                Schema.ofBoolean(WITH_COUNTS).setDefaultValue(new JsonPrimitive(false)))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        List<BigInteger> ids;
        try {
            ids = SecurityFunctionSupport.idsOf(IDS, context.getArguments().get(IDS));
        } catch (RuntimeException ex) {
            return Mono.error(ex);
        }

        boolean fetchOwners = flag(context.getArguments().get(FETCH_OWNERS));
        boolean fetchManaging = flag(context.getArguments().get(FETCH_MANAGING_CLIENT));
        boolean fetchManagers = flag(context.getArguments().get(FETCH_CLIENT_MANAGERS));
        boolean withCounts = flag(context.getArguments().get(WITH_COUNTS));

        return FlatMapUtil.flatMapMono(
                () -> SecurityFunctionSupport.definitionContextAuthentication(FUNCTION_NAME),
                ca -> this.visibleIds(ca, ids),
                (ca, visible) -> visible.isEmpty()
                        ? Mono.just(List.<Client>of())
                        : this.securityService.getClientInternalBatch(visible,
                                params(fetchOwners, fetchManaging, fetchManagers))
                                .defaultIfEmpty(List.of()),
                (ca, visible, clients) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(
                        Map.of(EVENT_DATA_CLIENTS,
                                toJson(clients, visible, fetchOwners, fetchManaging, fetchManagers, withCounts)))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetClientsByIds.internalExecute"));
    }

    private static boolean flag(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static MultiValueMap<String, String> params(boolean fetchOwners, boolean fetchManaging,
            boolean fetchManagers) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (fetchOwners)
            params.add(FETCH_OWNERS, "true");
        if (fetchManaging)
            params.add(FETCH_MANAGING_CLIENT, "true");
        if (fetchManagers)
            params.add(FETCH_CLIENT_MANAGERS, "true");
        return params;
    }

    /** Checked before the read, so an unrelated client is never even fetched. */
    private Mono<List<BigInteger>> visibleIds(ContextAuthentication ca, List<BigInteger> ids) {

        if (ids.isEmpty() || ca.getUser() == null)
            return Mono.just(List.of());

        BigInteger callerClientId = ca.getUser().getClientId();

        return Flux.fromIterable(ids)
                .concatMap(cid -> SecurityFunctionSupport.isRelatedClient(this.securityService, callerClientId, cid,
                        ca.getUrlAppCode())
                        .map(related -> Map.entry(cid, related)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, HashMap::new)
                .map(related -> ids.stream().filter(id -> Boolean.TRUE.equals(related.get(id))).toList());
    }

    static JsonObject toJson(List<Client> clients, List<BigInteger> visible, boolean fetchOwners,
            boolean fetchManaging) {
        return toJson(clients, visible, fetchOwners, fetchManaging, false, false);
    }

    static JsonObject toJson(List<Client> clients, List<BigInteger> visible, boolean fetchOwners,
            boolean fetchManaging, boolean fetchManagers, boolean withCounts) {

        Set<BigInteger> allowed = new LinkedHashSet<>(visible);
        JsonObject map = new JsonObject();

        for (Client c : clients) {
            if (c == null || c.getId() == null || !allowed.contains(c.getId()))
                continue;

            JsonObject o = clientJson(c);

            if (fetchOwners) {
                JsonArray owners = new JsonArray();
                if (c.getOwners() != null)
                    for (User u : c.getOwners())
                        if (u != null && u.getId() != null)
                            owners.add(GetUsersByIds.userJson(u));
                o.add("owners", owners);
            }

            // The users who manage this client (security_client_manager), as entity-processor's
            // partner pages show them. Each is the same plain user shape as an owner.
            if (fetchManagers) {
                JsonArray managers = new JsonArray();
                if (c.getClientManagers() != null)
                    for (User u : c.getClientManagers())
                        if (u != null && u.getId() != null)
                            managers.add(GetUsersByIds.userJson(u));
                o.add("clientManagers", managers);
            }

            // The user-count fields of security's Client exactly as security fills them (0 unless
            // security counted them), for callers that must reproduce entity-processor's client JSON.
            if (withCounts) {
                o.add("activeUsers", new JsonPrimitive(c.getActiveUsers()));
                o.add("inactiveUsers", new JsonPrimitive(c.getInactiveUsers()));
                o.add("deletedUsers", new JsonPrimitive(c.getDeletedUsers()));
                o.add("lockedUsers", new JsonPrimitive(c.getLockedUsers()));
                o.add("passwordExpiredUsers", new JsonPrimitive(c.getPasswordExpiredUsers()));
                o.add("totalUsers", new JsonPrimitive(c.getActiveUsers() + c.getInactiveUsers()
                        + c.getDeletedUsers() + c.getLockedUsers() + c.getPasswordExpiredUsers()));
            }

            // The direct (level 0) managing client: for a partner, the client whose data it works on.
            // Only its plain fields, never its owners.
            if (fetchManaging && c.getManagagingClient() != null && c.getManagagingClient().getId() != null)
                o.add("managingClient", clientJson(c.getManagagingClient()));

            map.add(c.getId().toString(), o);
        }

        return map;
    }

    private static JsonObject clientJson(Client c) {

        JsonObject o = new JsonObject();
        o.add("id", new JsonPrimitive(c.getId()));
        add(o, "code", c.getCode());
        add(o, "name", c.getName());
        add(o, "typeCode", c.getTypeCode());
        add(o, "levelType", c.getLevelType());
        add(o, "statusCode", c.getStatusCode());
        add(o, "businessType", c.getBusinessType());
        add(o, "businessSize", c.getBusinessSize());
        add(o, "industry", c.getIndustry());
        add(o, "localeCode", c.getLocaleCode());
        add(o, "timeZone", c.getTimeZone());
        o.add("tokenValidityMinutes", new JsonPrimitive(c.getTokenValidityMinutes()));
        if (c.getCreatedBy() != null)
            o.add("createdBy", new JsonPrimitive(c.getCreatedBy()));
        if (c.getUpdatedBy() != null)
            o.add("updatedBy", new JsonPrimitive(c.getUpdatedBy()));

        return o;
    }

    private static void add(JsonObject o, String key, String value) {
        if (value != null)
            o.add(key, new JsonPrimitive(value));
    }
}
