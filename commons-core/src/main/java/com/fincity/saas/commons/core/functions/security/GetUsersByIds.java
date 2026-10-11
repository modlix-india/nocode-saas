package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.LinkedMultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Many users in one call, keyed by id: what entity-processor's
 * {@code UserFieldResolver} fetches to put names on assignedUserId, createdBy and
 * the like. One POST to security's /users/internal/batch whatever the count.
 *
 * Each value carries the user's plain fields only (id, clientId, userName,
 * emailId, phoneNumber, firstName, middleName, lastName, localeCode, timeZone,
 * statusCode). Ids that do not exist are absent from the map.
 *
 * A user whose client is not the caller's, not one it manages, and not a
 * direct managing client (never SYSTEM) with write access to the request's app is left out, so a
 * function cannot read contact details of another tenant's (or SYSTEM's) people by
 * guessing ids. Partner users seen by the managing client, and the managing
 * client's users seen by a partner, stay in. See ClientRelationUtil.
 */
public class GetUsersByIds extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetUsersByIds";

    static final String IDS = "ids";

    static final String EVENT_DATA_USERS = "users";

    private final IFeignSecurityService securityService;

    public GetUsersByIds(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_USERS, Schema.ofObject(EVENT_DATA_USERS)));

        return new FunctionSignature()
                .setNamespace(SecurityFunctionSupport.NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(IDS, Parameter.of(IDS, SecurityFunctionSupport.idArraySchema(IDS))))
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

        return FlatMapUtil.flatMapMono(
                () -> SecurityFunctionSupport.definitionContextAuthentication(FUNCTION_NAME),
                ca -> ids.isEmpty()
                        ? Mono.just(List.<User>of())
                        : this.securityService.getUsersInternalBatch(ids, new LinkedMultiValueMap<>())
                                .defaultIfEmpty(List.of()),
                (ca, users) -> this.visible(ca, users),
                (ca, users, visible) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(
                        Map.of(EVENT_DATA_USERS, toJson(visible)))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetUsersByIds.internalExecute"));
    }

    private Mono<List<User>> visible(ContextAuthentication ca, List<User> users) {

        if (users.isEmpty())
            return Mono.just(users);

        BigInteger callerClientId = ca.getUser() == null ? null : ca.getUser().getClientId();

        Set<BigInteger> clientIds = new LinkedHashSet<>();
        users.forEach(u -> {
            if (u.getClientId() != null)
                clientIds.add(u.getClientId());
        });

        // One check per distinct client, not per user: a page of deals names a
        // handful of clients at most.
        return Flux.fromIterable(clientIds)
                .flatMap(cid -> SecurityFunctionSupport.isRelatedClient(this.securityService, callerClientId, cid,
                        ca.getUrlAppCode())
                        .map(related -> Map.entry(cid, related)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, HashMap::new)
                .map(related -> users.stream()
                        .filter(u -> u.getClientId() != null && Boolean.TRUE.equals(related.get(u.getClientId())))
                        .toList());
    }

    static JsonObject toJson(List<User> users) {

        JsonObject map = new JsonObject();

        for (User u : users) {
            if (u.getId() == null)
                continue;

            map.add(u.getId().toString(), userJson(u));
        }

        return map;
    }

    /** One user's plain fields, the shape every security function hands out for a person. */
    static JsonObject userJson(User u) {

        JsonObject o = new JsonObject();
        o.add("id", new JsonPrimitive(u.getId()));
        if (u.getClientId() != null)
            o.add("clientId", new JsonPrimitive(u.getClientId()));
        add(o, "userName", u.getUserName());
        add(o, "emailId", u.getEmailId());
        add(o, "phoneNumber", u.getPhoneNumber());
        add(o, "firstName", u.getFirstName());
        add(o, "middleName", u.getMiddleName());
        add(o, "lastName", u.getLastName());
        add(o, "localeCode", u.getLocaleCode());
        add(o, "timeZone", u.getTimeZone());
        add(o, "statusCode", u.getStatusCode());

        return o;
    }

    private static void add(JsonObject o, String key, String value) {
        if (value != null)
            o.add(key, new JsonPrimitive(value));
    }
}
