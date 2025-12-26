package com.fincity.saas.commons.core.mq.services;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EventCallFunctionService implements IEventActionService {

    private final CoreFunctionService functionService;
    private final CoreMessageResourceService msgService;
    private final Gson gson;
    private final IFeignSecurityService securityService;

    public EventCallFunctionService(CoreFunctionService functionService, CoreMessageResourceService msgService,
            IFeignSecurityService securityService, Gson gson) {
        this.functionService = functionService;
        this.msgService = msgService;
        this.gson = gson;
        this.securityService = securityService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

        Map<String, Object> data = CommonsUtil.nonNullValue(queObject.getData(), Map.of());
        JsonObject job = gson.toJsonTree(data).getAsJsonObject();

        Map<String, Object> taskParameter = CommonsUtil.nonNullValue(task.getParameters(), Map.of());

        String namespace = StringUtil.safeValueOf(taskParameter.get("namespace"));
        String name = StringUtil.safeValueOf(taskParameter.get("name"));
        String functionParameterName = StringUtil.safeValueOf(taskParameter.get("functionParameterName"));

        if (StringUtil.safeIsBlank(namespace) || StringUtil.safeIsBlank(name))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.UNABLE_TO_EXECUTE, name, namespace, job);

        Map<String, JsonElement> params = new HashMap<>();

        if (!StringUtil.safeIsBlank(functionParameterName)) {
            params.put(functionParameterName, job);
        }

        return this.execute(namespace, name, queObject.getAppCode(), queObject.getClientCode(),
                queObject.getAuthentication(), params);
    }

    private Mono<Boolean> execute(
            String namespace,
            String name,
            String appCode,
            String clientCode,
            ContextAuthentication authentication,
            Map<String, JsonElement> job) {

        return this.securityService.getClientByCode(clientCode)
                .map(e -> authentication != null ? authentication
                        : this.makeAnonymousContextAuth(appCode, clientCode, e))
                .flatMap(ca -> this.functionService.execute(namespace, name, appCode, clientCode, job, null)
                        .contextWrite(ReactiveSecurityContextHolder
                                .withSecurityContext(Mono.just(new SecurityContextImpl(ca)))))
                .map(e -> true)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        "Function",
                        namespace + "." + name))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventCallFunctionService.execute"));
    }

    private ContextAuthentication makeAnonymousContextAuth(String appCode, String clientCode, Client client) {

        return new ContextAuthentication(
                new ContextUser()
                        .setId(BigInteger.ZERO)
                        .setCreatedBy(BigInteger.ZERO)
                        .setUpdatedBy(BigInteger.ZERO)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now())
                        .setClientId(client.getId())
                        .setUserName("_Anonymous")
                        .setEmailId("nothing@nothing")
                        .setPhoneNumber("+910000000000")
                        .setFirstName("Anonymous")
                        .setLastName("")
                        .setLocaleCode("en")
                        .setPassword("")
                        .setPasswordHashed(false)
                        .setAccountNonExpired(true)
                        .setAccountNonLocked(true)
                        .setCredentialsNonExpired(true)
                        .setNoFailedAttempt((short) 0)
                        .setStringAuthorities(List.of("Authorities._Anonymous")),
                false,
                client.getId(),
                client.getCode(),
                client.getTypeCode(),
                client.getLevelType(),
                client.getCode(),
                "",
                LocalDateTime.MAX,
                appCode,
                clientCode,
                null);
    }
}
