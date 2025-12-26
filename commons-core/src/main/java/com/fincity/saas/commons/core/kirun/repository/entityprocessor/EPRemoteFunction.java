package com.fincity.saas.commons.core.kirun.repository.entityprocessor;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class EPRemoteFunction implements ReactiveFunction {

    public static final String FORWARDED_HOST = "forwardedHost";
    public static final String FORWARDED_PORT = "forwardedPort";

    private final FunctionSignature functionSignature;
    private final IFeignEntityProcessor feignEntityProcessor;
    private final String appCode;
    private final String clientCode;
    private final Gson gson;

    public EPRemoteFunction(FunctionSignature functionSignature, IFeignEntityProcessor feignEntityProcessor,
            String appCode, String clientCode, Gson gson) {
        this.functionSignature = functionSignature;
        this.appCode = appCode;
        this.clientCode = clientCode;
        this.feignEntityProcessor = feignEntityProcessor;
        this.gson = gson;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<FunctionOutput> execute(ReactiveFunctionExecutionParameters parameters) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.deferContextual(ctx -> {
                    String forwardedHost = ctx.get(FORWARDED_HOST);
                    String forwardedPort = ctx.get(FORWARDED_PORT);
                    return this.feignEntityProcessor
                            .executeFunction(ca.getAccessToken(), forwardedHost,
                                    forwardedPort, ca.getUrlClientCode(),
                                    ca.getUrlAppCode(),
                                    this.functionSignature.getNamespace(),
                                    this.functionSignature.getName(), this.appCode, this.clientCode,
                                    this.gson.toJson(parameters.getArguments()))
                            .map(str -> (List<EventResult>) this.gson.fromJson(str, new TypeToken<List<EventResult>>() {
                            }.getType()))
                            .map(FunctionOutput::new);
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EPRemoteFunction.execute"));

    }

    @Override
    public FunctionSignature getSignature() {
        return this.functionSignature;
    }

    @Override
    public Map<String, Event> getProbableEventSignature(Map<String, List<Schema>> probableParameters) {
        return this.functionSignature.getEvents();
    }
}
