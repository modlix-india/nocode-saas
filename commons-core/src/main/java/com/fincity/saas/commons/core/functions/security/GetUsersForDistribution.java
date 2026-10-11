package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The people a distribution (an assignment rule's pool) resolves to: every ACTIVE
 * user of the app and client who matches ANY of the given users, roles,
 * profiles, designations or departments. A port of entity-processor's
 * {@code BaseUserDistributionService.getUsersByRuleId}, over the same security
 * call ({@code /users/internal/processor}).
 *
 * The client defaults to the caller's. A clientCode may name another one only when
 * the caller's client manages it, or it is a direct managing client (never SYSTEM) with write access
 * to the request's app (see ClientRelationUtil), so a partner's function can draw
 * from its managing client's people and nobody can list an unrelated tenant's or
 * SYSTEM's.
 *
 * As in entity-processor, a user's role is the single role id security reports for
 * them, and the result is sorted ascending so a round robin over it is stable.
 */
public class GetUsersForDistribution extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetUsersForDistribution";

    static final String USER_IDS = "userIds";
    static final String ROLE_IDS = "roleIds";
    static final String PROFILE_IDS = "profileIds";
    static final String DESIGNATION_IDS = "designationIds";
    static final String DEPARTMENT_IDS = "departmentIds";
    static final String CLIENT_CODE = "clientCode";

    static final String EVENT_DATA_USER_IDS = "userIds";

    private final IFeignSecurityService securityService;

    public GetUsersForDistribution(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    record Distribution(Set<Long> userIds, Set<Long> roleIds, Set<Long> profileIds, Set<Long> designationIds,
            Set<Long> departmentIds) {

        boolean isEmpty() {
            return userIds.isEmpty() && roleIds.isEmpty() && profileIds.isEmpty() && designationIds.isEmpty()
                    && departmentIds.isEmpty();
        }

        boolean matches(EntityProcessorUser user) {
            if (user == null || user.getId() == null)
                return false;

            if (userIds.contains(user.getId()))
                return true;
            if (user.getRoleId() != null && roleIds.contains(user.getRoleId()))
                return true;
            if (user.getDesignationId() != null && designationIds.contains(user.getDesignationId()))
                return true;
            if (user.getDepartmentId() != null && departmentIds.contains(user.getDepartmentId()))
                return true;

            return user.getProfileIds() != null && user.getProfileIds().stream().anyMatch(profileIds::contains);
        }
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
                .setParameters(Map.ofEntries(
                        Parameter.ofEntry(USER_IDS, SecurityFunctionSupport.idArraySchema(USER_IDS)),
                        Parameter.ofEntry(ROLE_IDS, SecurityFunctionSupport.idArraySchema(ROLE_IDS)),
                        Parameter.ofEntry(PROFILE_IDS, SecurityFunctionSupport.idArraySchema(PROFILE_IDS)),
                        Parameter.ofEntry(DESIGNATION_IDS, SecurityFunctionSupport.idArraySchema(DESIGNATION_IDS)),
                        Parameter.ofEntry(DEPARTMENT_IDS, SecurityFunctionSupport.idArraySchema(DEPARTMENT_IDS)),
                        Parameter.ofEntry(CLIENT_CODE,
                                Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))))
                .setEvents(Map.of(event.getName(), event));
    }

    static Distribution distributionOf(Map<String, JsonElement> args) {
        return new Distribution(
                longs(USER_IDS, args.get(USER_IDS)),
                longs(ROLE_IDS, args.get(ROLE_IDS)),
                longs(PROFILE_IDS, args.get(PROFILE_IDS)),
                longs(DESIGNATION_IDS, args.get(DESIGNATION_IDS)),
                longs(DEPARTMENT_IDS, args.get(DEPARTMENT_IDS)));
    }

    private static Set<Long> longs(String name, JsonElement element) {
        Set<Long> set = new HashSet<>();
        for (BigInteger id : SecurityFunctionSupport.idsOf(name, element))
            set.add(id.longValueExact());
        return set;
    }

    static List<BigInteger> pick(Distribution distribution, List<EntityProcessorUser> users) {

        if (distribution.isEmpty() || users == null || users.isEmpty())
            return List.of();

        Set<Long> picked = new TreeSet<>();
        for (EntityProcessorUser user : users)
            if (distribution.matches(user))
                picked.add(user.getId());

        return picked.stream().map(BigInteger::valueOf).toList();
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        Distribution distribution;
        try {
            distribution = distributionOf(context.getArguments());
        } catch (RuntimeException ex) {
            return Mono.error(ex);
        }

        JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
        String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

        return FlatMapUtil.flatMapMono(
                () -> SecurityFunctionSupport.definitionContextAuthentication(FUNCTION_NAME),
                ca -> this.targetClientCode(ca, clientCode),
                (ca, cc) -> distribution.isEmpty()
                        ? Mono.just(List.<EntityProcessorUser>of())
                        : this.securityService.getUsersForEntityProcessor(new UsersListRequest()
                                .setClientCode(cc)
                                .setAppCode(ca.getUrlAppCode()))
                                .defaultIfEmpty(List.of()),
                (ca, cc, users) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(
                        EVENT_DATA_USER_IDS, SecurityFunctionSupport.toJsonArray(pick(distribution, users))))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetUsersForDistribution.internalExecute"));
    }

    private Mono<String> targetClientCode(ContextAuthentication ca, String clientCode) {

        if (StringUtil.safeIsBlank(clientCode) || clientCode.equals(ca.getClientCode()))
            return Mono.just(ca.getClientCode());

        return SecurityFunctionSupport.isRelatedClientCode(this.securityService, ca.getClientCode(), clientCode,
                        ca.getUrlAppCode())
                .flatMap(related -> Boolean.TRUE.equals(related)
                        ? Mono.just(clientCode)
                        : Mono.error(SecurityFunctionSupport.forbidden(
                                "Client " + clientCode + " is not related to the current user's client")));
    }
}
