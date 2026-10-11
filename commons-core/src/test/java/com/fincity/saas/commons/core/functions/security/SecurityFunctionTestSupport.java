package com.fincity.saas.commons.core.functions.security;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * A hand-rolled security feign (this module has no mocking library) and the
 * plumbing to run a function as a given user, inside or outside a definition.
 */
final class SecurityFunctionTestSupport {

    /** Answers only the methods a test names; anything else fails loudly. */
    static final class FeignStub {

        final Map<String, Function<Object[], Object>> answers = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        FeignStub on(String method, Function<Object[], Object> answer) {
            this.answers.put(method, answer);
            return this;
        }

        IFeignSecurityService service() {
            return (IFeignSecurityService) Proxy.newProxyInstance(
                    IFeignSecurityService.class.getClassLoader(),
                    new Class<?>[] { IFeignSecurityService.class },
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class)
                            return method.invoke(this, args);

                        this.calls.add(method.getName() + Arrays.toString(args));
                        Function<Object[], Object> answer = this.answers.get(method.getName());
                        if (answer == null)
                            throw new UnsupportedOperationException("unexpected call " + method.getName());
                        return answer.apply(args);
                    });
        }

        long count(String method) {
            return this.calls.stream().filter(c -> c.startsWith(method + "[")).count();
        }

        /**
         * A small client world for the upward (partner) rule: SYSTEM(1, SYS) manages FIN(7)
         * and OTHER(55); FIN manages BP1(90). FIN has write access to leadzumptwo; SYSTEM has
         * write access to every app, which must never matter.
         */
        FeignStub withHierarchy() {
            Map<BigInteger, Client> clients = Map.of(
                    BigInteger.ONE, new Client().setId(BigInteger.ONE).setCode("SYSTEM").setTypeCode("SYS"),
                    BigInteger.valueOf(7), new Client().setId(BigInteger.valueOf(7)).setCode("FIN").setTypeCode("BUS"),
                    BigInteger.valueOf(90), new Client().setId(BigInteger.valueOf(90)).setCode("BP1").setTypeCode("BUS"),
                    BigInteger.valueOf(55), new Client().setId(BigInteger.valueOf(55)).setCode("OTHER").setTypeCode("BUS"));
            Map<BigInteger, List<BigInteger>> chains = Map.of(
                    BigInteger.ONE, ids(1),
                    BigInteger.valueOf(7), ids(7, 1),
                    BigInteger.valueOf(90), ids(90, 7, 1),
                    BigInteger.valueOf(55), ids(55, 1));

            this.answers.putIfAbsent("getClientHierarchy", a -> Mono.justOrEmpty(chains.get((BigInteger) a[0])));
            this.answers.putIfAbsent("getClientById", a -> Mono.justOrEmpty(clients.get((BigInteger) a[0])));
            this.answers.putIfAbsent("getClientByCode", a -> Mono.justOrEmpty(clients.values().stream()
                    .filter(c -> c.getCode().equals(a[0])).findFirst()));
            this.answers.putIfAbsent("hasWriteAccess", a -> Mono.just("SYSTEM".equals(a[1])
                    || ("leadzumptwo".equals(a[0]) && "FIN".equals(a[1]))));
            return this;
        }
    }

    static ContextAuthentication user(long userId, long clientId, String clientCode, String... authorities) {
        ContextUser user = new ContextUser()
                .setId(BigInteger.valueOf(userId))
                .setClientId(BigInteger.valueOf(clientId))
                .setStringAuthorities(List.of(authorities));

        ContextAuthentication ca = new ContextAuthentication()
                .setUser(user)
                .setClientCode(clientCode)
                .setUrlAppCode("leadzumptwo")
                .setUrlClientCode("SYSTEM");
        ca.setAuthenticated(true);
        return ca;
    }

    static ContextAuthentication anonymous(long clientId, String clientCode) {
        ContextAuthentication ca = user(0, clientId, clientCode);
        ca.setAuthenticated(false);
        return ca;
    }

    static Mono<FunctionOutput> run(ReactiveFunction fn, Map<String, JsonElement> args, ContextAuthentication ca,
            boolean inDefinition) {

        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(
                new KIRunReactiveFunctionRepository(), new KIRunReactiveSchemaRepository())
                .setArguments(new HashMap<>(args));

        Mono<FunctionOutput> mono = fn.execute(params)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));

        return inDefinition ? mono.contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, "true")) : mono;
    }

    static Map<String, JsonElement> output(FunctionOutput fo) {
        EventResult er = fo.next();
        return er.getResult();
    }

    @SafeVarargs
    static <T> Mono<List<T>> listOf(T... values) {
        return Mono.just(List.of(values));
    }

    static List<BigInteger> ids(long... values) {
        return Arrays.stream(values).mapToObj(BigInteger::valueOf).toList();
    }

    private SecurityFunctionTestSupport() {
    }
}
