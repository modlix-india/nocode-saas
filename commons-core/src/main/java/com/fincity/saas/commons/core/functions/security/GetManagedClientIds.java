package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The client ids a user may see business-partner data for, as entity-processor's
 * {@code IProcessorAccessService.getUserInheritanceInfo} works them out:
 * <ul>
 * <li>{@code Authorities.ROLE_Owner}: every client the user's client manages, its
 * own included (security's managingClientIds).</li>
 * <li>{@code Authorities.ROLE_Partner_Manager}: the clients the user, or anyone in
 * their sub-org, is a client manager of (clientIdsOfManagers).</li>
 * <li>Anyone else: none.</li>
 * </ul>
 *
 * Only the current user. Deciding the roles of somebody else needs their
 * authorities, and security has no internal endpoint that returns those, so a
 * userId other than the caller's is refused (403) rather than guessed at.
 */
public class GetManagedClientIds extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetManagedClientIds";

    static final String USER_ID = "userId";

    static final String EVENT_DATA_CLIENT_IDS = "clientIds";

    static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    static final String PARTNER_MANAGER_ROLE = "Authorities.ROLE_Partner_Manager";

    private final IFeignSecurityService securityService;

    public GetManagedClientIds(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_CLIENT_IDS,
                        Schema.ofArray(EVENT_DATA_CLIENT_IDS, Schema.ofLong("clientId"))));

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
                ca -> this.managedClientIds(ca, userId),
                (ca, ids) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(
                        Map.of(EVENT_DATA_CLIENT_IDS, SecurityFunctionSupport.toJsonArray(ids)))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetManagedClientIds.internalExecute"));
    }

    private Mono<List<BigInteger>> managedClientIds(ContextAuthentication ca, BigInteger userId) {

        if (!ca.isAuthenticated() || ca.getUser() == null)
            return Mono.just(List.of());

        BigInteger self = ca.getUser().getId();

        // 403, like every other refusal in these functions (GetUserSubOrg for an unrelated user, a
        // direct call outside a server function): the id is well formed, the caller just may not
        // ask about anyone but themselves. It used to be a 400, which read as a malformed request.
        if (userId != null && !userId.equals(self))
            return Mono.error(SecurityFunctionSupport.forbidden(
                    FUNCTION_NAME + " answers only for the current user; security exposes no other user's roles"));

        BigInteger clientId = ca.getUser().getClientId();

        if (SecurityContextUtil.hasAuthority(OWNER_ROLE, ca.getUser().getAuthorities()))
            return this.securityService.getManagingClientIds(clientId).defaultIfEmpty(List.of());

        if (!SecurityContextUtil.hasAuthority(PARTNER_MANAGER_ROLE, ca.getUser().getAuthorities()))
            return Mono.just(List.of());

        return this.securityService.getUserSubOrgInternal(self, ca.getUrlAppCode(), clientId)
                .defaultIfEmpty(List.of())
                .flatMap(subOrg -> {
                    Set<BigInteger> managers = new LinkedHashSet<>(subOrg);
                    managers.add(self);
                    return this.securityService.getClientIdsOfManagers(new ArrayList<>(managers));
                })
                .defaultIfEmpty(List.of())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetManagedClientIds.managedClientIds"));
    }
}
