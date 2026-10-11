package com.fincity.saas.commons.core.functions.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.feign.IFeignMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * {@code CoreServices.Message.SendWhatsapp} and {@code MakeCall}: who they act for, and what they
 * send to the message service.
 *
 * <p>The internal routes trust the app and client they are given, so the tenant check here is the
 * only one. Fakes are hand-written proxies: inline mocking cannot instrument classes on newer JDKs.
 */
class MessageFunctionsTest {

    private static final String APP = "leadzumptwo";

    private final Gson gson = new Gson();

    /** "managing:managed" pairs the fake security service answers true for. */
    private Set<String> manages;

    /** Every message-service call, as "method appCode clientCode extra body". */
    private List<String> calls;

    private Map<String, Object> resolved;

    private IFeignMessageService messageService;
    private IFeignSecurityService securityService;

    @BeforeEach
    void setUp() {
        this.manages = new HashSet<>();
        this.calls = new ArrayList<>();
        this.resolved = Map.of("code", "SESS1");

        // A small client world for the upward (partner) rule: SYSTEM(1, SYS) above FIN(7) and
        // OTHER(55); FIN above BP1(90). FIN has write access to the app; SYSTEM has write access to
        // every app, which must never lend it out. FIN can also read "leadzump".
        Map<String, Client> clients = Map.of(
                "SYSTEM", new Client().setId(BigInteger.ONE).setCode("SYSTEM").setTypeCode("SYS"),
                "FIN", new Client().setId(BigInteger.valueOf(7)).setCode("FIN").setTypeCode("BUS"),
                "BP1", new Client().setId(BigInteger.valueOf(90)).setCode("BP1").setTypeCode("BUS"),
                "OTHER", new Client().setId(BigInteger.valueOf(55)).setCode("OTHER").setTypeCode("BUS"),
                "SUB", new Client().setId(BigInteger.valueOf(91)).setCode("SUB").setTypeCode("BUS"));
        Map<BigInteger, List<BigInteger>> chains = Map.of(
                BigInteger.ONE, List.of(BigInteger.ONE),
                BigInteger.valueOf(7), List.of(BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(90), List.of(BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(55), List.of(BigInteger.valueOf(55), BigInteger.ONE),
                BigInteger.valueOf(91),
                List.of(BigInteger.valueOf(91), BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE));

        this.securityService = (IFeignSecurityService) Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] {IFeignSecurityService.class},
                (p, m, a) -> switch (m.getName()) {
                    case "doesClientManageClientCode" -> Mono.just(this.manages.contains(a[0] + ":" + a[1]));
                    case "getClientByCode" -> Mono.justOrEmpty(clients.get((String) a[0]));
                    case "getClientHierarchy" -> Mono.justOrEmpty(chains.get((BigInteger) a[0]));
                    case "hasWriteAccess" -> Mono.just("SYSTEM".equals(a[1]) || (APP.equals(a[0]) && "FIN".equals(a[1])));
                    case "hasReadAccess" -> Mono.just(("leadzump".equals(a[0]) && "FIN".equals(a[1]))
                            || ("ownerapp".equals(a[0]) && "FIN".equals(a[1]))
                            || (APP.equals(a[0]) && List.of("FIN", "BP1", "SUB", "SYSTEM").contains(a[1])));
                    default -> throw new UnsupportedOperationException(m.getName());
                });

        this.messageService = new IFeignMessageService() {
            @Override
            public Mono<Boolean> markWhatsappSessionDefault(String appCode, String clientCode, String sessionId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Map<String, Object>> resolveWhatsappSession(String appCode, String clientCode, String sessionCode) {
                calls.add("resolve " + appCode + " " + clientCode + " " + sessionCode);
                return Mono.justOrEmpty(resolved);
            }

            @Override
            public Mono<Map<String, Object>> sendWhatsappSessionMessage(
                    String appCode, String clientCode, String sessionId, Map<String, Object> request) {
                calls.add("send " + appCode + " " + clientCode + " " + sessionId + " " + new java.util.TreeMap<>(request));
                return Mono.just(Map.of("messageId", "wamid.9"));
            }

            @Override
            public Mono<Map<String, Object>> makeCallInternal(
                    String appCode, String clientCode, String ownerService, Map<String, Object> request) {
                calls.add("call " + appCode + " " + clientCode + " " + ownerService + " " + gson.toJson(request));
                return Mono.just(Map.of("sid", "sid-1"));
            }

            // Number administration, covered by WhatsappNumberFunctionsTest.
            @Override
            public Mono<Map<String, Object>> createWhatsappSession(
                    String appCode, String clientCode, Map<String, Object> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<List<Map<String, Object>>> listWhatsappSessions(String appCode, String clientCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Map<String, Object>> getWhatsappSessionQr(String appCode, String clientCode, String sessionId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Void> unlinkWhatsappSession(String appCode, String clientCode, String sessionId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ContextAuthentication userOf(String clientCode) {
        ContextUser user = new ContextUser().setId(BigInteger.valueOf(110)).setClientId(BigInteger.ONE);
        ContextAuthentication ca = new ContextAuthentication().setUser(user);
        ca.setAuthenticated(true);
        return ca.setClientCode(clientCode)
                .setLoggedInFromClientCode(clientCode)
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP)
                .setAccessTokenExpiryAt(LocalDateTime.MAX);
    }

    private static Map<String, JsonElement> args(String... kv) {
        Map<String, JsonElement> args = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) args.put(kv[i], new JsonPrimitive(kv[i + 1]));
        return args;
    }

    private Mono<FunctionOutput> run(
            com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction fn,
            Map<String, JsonElement> args,
            String callerClient,
            boolean definitionContext) {

        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(null, null);
        params.setArguments(args);

        Mono<FunctionOutput> out = fn instanceof SendWhatsapp s ? s.internalExecute(params)
                : ((MakeCall) fn).internalExecute(params);

        return out.contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, definitionContext ? "true" : "false"))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(userOf(callerClient)))));
    }

    private static EventResult first(FunctionOutput fo) {
        return fo.next();
    }

    private SendWhatsapp sendWhatsapp() {
        return new SendWhatsapp(this.messageService, this.securityService, this.gson);
    }

    private MakeCall makeCall() {
        return new MakeCall(this.messageService, this.securityService, this.gson);
    }

    @Test
    @DisplayName("SendWhatsapp with no number named sends from the tenant default, for the caller's own client")
    void sendsFromDefaultNumber() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "919876543210", "text", "hi"), "FIN", true))
                .assertNext(fo -> {
                    EventResult r = first(fo);
                    assertEquals("SESS1", r.getResult().get("sessionCode").getAsString());
                    assertEquals("wamid.9", r.getResult().get("result").getAsJsonObject().get("messageId").getAsString());
                })
                .verifyComplete();

        assertEquals(List.of("resolve " + APP + " FIN null", "send " + APP + " FIN SESS1 {text=hi, to=919876543210}"), this.calls);
    }

    @Test
    @DisplayName("a named number is asked for by its code")
    void namedNumber() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "sessionCode", "SESS7"), "FIN", true))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals("resolve " + APP + " FIN SESS7", this.calls.get(0));
    }

    @Test
    @DisplayName("another client's number is refused before the message service is called")
    void otherTenantRefused() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "clientCode", "OTHER"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty(), "nothing may reach the internal routes for a refused tenant");
    }

    @Test
    @DisplayName("a partner sends from the managing client's number")
    void partnerSendsForManagingClient() {
        this.manages.add("FIN:BP1");

        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "clientCode", "FIN"), "BP1", true))
                .expectNextCount(1)
                .verifyComplete();

        assertTrue(this.calls.get(1).startsWith("send " + APP + " FIN SESS1"));
    }

    @Test
    @DisplayName("nobody sends from SYSTEM's numbers, although SYSTEM manages every client")
    void systemNumbersRefused() {
        this.manages.add("SYSTEM:FIN");
        this.manages.add("SYSTEM:BP1");
        this.manages.add("FIN:BP1");

        for (String caller : List.of("FIN", "BP1", "OTHER")) {
            StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "clientCode", "SYSTEM"), caller, true))
                    .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                    .verify();
        }

        assertTrue(this.calls.isEmpty(), "nothing may reach the internal routes for SYSTEM");
    }

    @Test
    @DisplayName("no call is placed on SYSTEM's account")
    void systemCallAccountRefused() {
        this.manages.add("SYSTEM:FIN");

        StepVerifier.create(this.run(this.makeCall(), args("toNumber", "9876543210", "clientCode", "SYSTEM"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("a partner may not send from the managing client's numbers of an app the managing client cannot edit")
    void partnerRefusedForAnotherApp() {
        this.manages.add("FIN:BP1");

        StepVerifier.create(this.run(this.sendWhatsapp(),
                        args("to", "91987", "text", "hi", "clientCode", "FIN", "appCode", "leadzump"), "BP1", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("an explicit appCode the caller's client cannot read is refused")
    void unreadableAppRefused() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "appCode", "someoneElsesApp"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("no linked number is a clear refusal, not an empty step")
    void noLinkedNumber() {
        this.resolved = null;

        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    @DisplayName("neither text nor attachment is refused")
    void nothingToSend() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("outside a server function nothing is sent")
    void onlyInDefinitionFunctions() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi"), "FIN", false))
                .verifyComplete();
        StepVerifier.create(this.run(this.makeCall(), args("toNumber", "9876543210"), "FIN", false))
                .verifyComplete();
        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("an attachment's fields pass through and blanks are left out")
    void mediaRequestShape() {
        Map<String, JsonElement> a = args("to", "91987", "text", "", "filePath", "x/y.pdf", "kind", "document", "fileName", "y.pdf");
        a.put("voiceNote", new JsonPrimitive(false));

        Map<String, Object> request = SendWhatsapp.request(a);

        assertEquals(Map.of("to", "91987", "filePath", "x/y.pdf", "kind", "document", "fileName", "y.pdf"), request);
        assertFalse(request.containsKey("text"));
    }

    @Test
    @DisplayName("MakeCall places the call owned by core, with numbers in the shape the message service reads")
    void makeCallOwnedByCore() {
        Map<String, JsonElement> a = args("toNumber", "9876543210", "callerId", "08012345678", "connectionName", "exotel");
        a.put("toCountryCode", new JsonPrimitive(91));
        a.put("callerIdCountryCode", new JsonPrimitive(0));

        StepVerifier.create(this.run(this.makeCall(), a, "FIN", true))
                .assertNext(fo -> assertEquals("sid-1", first(fo).getResult().get("call").getAsJsonObject().get("sid").getAsString()))
                .verifyComplete();

        assertEquals(1, this.calls.size());
        String call = this.calls.get(0);
        assertTrue(call.startsWith("call " + APP + " FIN core "), call);

        Map<?, ?> body = this.gson.fromJson(call.substring(call.indexOf('{')), Map.class);
        assertEquals(Map.of("number", "9876543210", "countryCode", 91.0), body.get("toNumber"));
        assertEquals(Map.of("number", "08012345678"), body.get("callerId"), "country code 0 is left to the default");
        assertEquals("exotel", body.get("connectionName"));
    }

    @Test
    @DisplayName("an explicit appCode wins; otherwise the app that owns the running function")
    void appCodeResolution() {
        StepVerifier.create(MessageTenant.resolve(this.securityService, "leadzump", "")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl(userOf("FIN"))))))
                .assertNext(t -> {
                    assertEquals("leadzump", t.getT1());
                    assertEquals("FIN", t.getT2());
                })
                .verifyComplete();

        StepVerifier.create(MessageTenant.resolve(this.securityService, "", null)
                        .contextWrite(Context.of(DefinitionFunction.OWNER_APP_CODE, "ownerapp"))
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl(userOf("FIN"))))))
                .assertNext(t -> assertEquals("ownerapp", t.getT1()))
                .verifyComplete();
    }

    @Test
    @DisplayName("the request's own app is still checked: a caller whose client cannot read it is refused")
    void requestAppWithoutAccessRefused() {
        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi"), "OTHER", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("managing a client does not let the caller send from its numbers")
    void managerCannotSpeakAsManagedClient() {
        this.manages.add("FIN:BP1");

        StepVerifier.create(this.run(this.sendWhatsapp(), args("to", "91987", "text", "hi", "clientCode", "BP1"), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("only the direct managing client: a grandparent with write access is refused")
    void grandparentRefused() {
        this.manages.add("FIN:BP1");
        this.manages.add("FIN:SUB");
        this.manages.add("BP1:SUB");

        StepVerifier.create(this.run(this.makeCall(), args("toNumber", "9876543210", "clientCode", "FIN"), "SUB", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        assertTrue(this.calls.isEmpty());
    }
}
