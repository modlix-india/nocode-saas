package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * A user plus everyone who reports to them, transitively: the ids entity-processor
 * calls the user's sub-org (security's /users/internal/{userId}/sub-org). An Owner
 * of the app gets every user of their client, as security decides.
 *
 * Without a userId it answers for the current user. With one, that user must
 * belong to the caller's client, a client it manages, or (the partner case) a
 * direct managing client (never SYSTEM) with write access to the request's app, so one tenant cannot
 * read another's org chart and nobody reads SYSTEM's. See ClientRelationUtil.
 */
public class GetUserSubOrg extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetUserSubOrg";

    static final String USER_ID = "userId";

    static final String EVENT_DATA_USER_IDS = "userIds";

    private final IFeignSecurityService securityService;

    public GetUserSubOrg(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_USER_IDS,
                        Schema.ofArray(EVENT_DATA_USER_IDS, Schema.ofLong("userId"))));

        return new FunctionSignature()
                .setNamespace(SecurityFunctionSupport.NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(USER_ID, Parameter.of(USER_ID, SecurityFunctionSupport.idSchema(USER_ID))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        BigInteger userId;
        try {
            userId = SecurityFunctionSupport.idOf(USER_ID, context.getArguments().get(USER_ID));
        } catch (RuntimeException ex) {
            return Mono.error(ex);
        }

        return FlatMapUtil.flatMapMono(
                () -> SecurityFunctionSupport.definitionContextAuthentication(FUNCTION_NAME),
                ca -> this.subOrg(ca, userId),
                (ca, ids) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(
                        Map.of(EVENT_DATA_USER_IDS, SecurityFunctionSupport.toJsonArray(ids)))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetUserSubOrg.internalExecute"));
    }

    private Mono<List<BigInteger>> subOrg(ContextAuthentication ca, BigInteger userId) {

        // An anonymous caller has no place in any reporting tree.
        if (!ca.isAuthenticated() || ca.getUser() == null)
            return Mono.just(List.of());

        BigInteger self = ca.getUser().getId();
        BigInteger selfClientId = ca.getUser().getClientId();

        if (userId == null || userId.equals(self))
            return this.securityService.getUserSubOrgInternal(self, ca.getUrlAppCode(), selfClientId)
                    .defaultIfEmpty(List.of());

        return FlatMapUtil.flatMapMono(
                () -> this.securityService.getUserInternal(userId, new LinkedMultiValueMap<>())
                        .switchIfEmpty(Mono.error(() -> SecurityFunctionSupport.badRequest(
                                "No user found with id : " + userId))),
                user -> SecurityFunctionSupport.isRelatedClient(this.securityService, selfClientId,
                        user.getClientId(), ca.getUrlAppCode()),
                (user, related) -> Boolean.TRUE.equals(related)
                        ? this.securityService.getUserSubOrgInternal(userId, ca.getUrlAppCode(), user.getClientId())
                                .defaultIfEmpty(List.of())
                        : Mono.error(SecurityFunctionSupport.forbidden(
                                "User " + userId + " is not in a client related to the current user's")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetUserSubOrg.subOrg"));
    }
}
