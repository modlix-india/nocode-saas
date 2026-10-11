package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.fincity.saas.commons.security.model.ClientDenormData;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Security's denormalised summary of clients, keyed by client id: what entity-processor's
 * {@code PartnerDenormalizationService} copies onto each partner row (client name, status code,
 * active user count, the users' names and phones, and the {@code ,id,id,} list of client
 * managers). One POST to security's /clients/internal/clients/denorm/full.
 *
 * Same visibility as {@link GetClientsByIds}: only related clients are asked for and come back;
 * any other id is silently left out.
 */
public class GetClientsDenorm extends AbstractReactiveFunction {

    static final String FUNCTION_NAME = "GetClientsDenorm";

    static final String IDS = "ids";

    static final String EVENT_DATA = "denorm";

    private final IFeignSecurityService securityService;

    public GetClientsDenorm(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA, Schema.ofObject(EVENT_DATA)));

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
                ca -> this.visibleIds(ca, ids),
                (ca, visible) -> visible.isEmpty()
                        ? Mono.just(Map.<BigInteger, ClientDenormData>of())
                        : this.securityService.getClientsDenormFull(visible)
                                .defaultIfEmpty(Map.of()),
                (ca, visible, data) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(
                        Map.of(EVENT_DATA, toJson(data, visible)))))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "GetClientsDenorm.internalExecute"));
    }

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

    static JsonObject toJson(Map<BigInteger, ClientDenormData> data, List<BigInteger> visible) {

        JsonObject map = new JsonObject();
        if (data == null)
            return map;

        for (BigInteger id : visible) {
            ClientDenormData d = data.get(id);
            if (d == null)
                continue;
            JsonObject o = new JsonObject();
            o.add("clientId", new JsonPrimitive(id));
            if (d.getClientName() != null)
                o.add("clientName", new JsonPrimitive(d.getClientName()));
            if (d.getStatusCode() != null)
                o.add("statusCode", new JsonPrimitive(d.getStatusCode()));
            o.add("activeUsers", new JsonPrimitive(d.getActiveUsers()));
            if (d.getUserNames() != null)
                o.add("userNames", new JsonPrimitive(d.getUserNames()));
            if (d.getUserPhones() != null)
                o.add("userPhones", new JsonPrimitive(d.getUserPhones()));
            if (d.getClientManagerIds() != null)
                o.add("clientManagerIds", new JsonPrimitive(d.getClientManagerIds()));
            map.add(id.toString(), o);
        }
        return map;
    }
}
