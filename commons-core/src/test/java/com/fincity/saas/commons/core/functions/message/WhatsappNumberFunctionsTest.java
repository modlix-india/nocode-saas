package com.fincity.saas.commons.core.functions.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.feign.IFeignMessageService;
import com.fincity.saas.commons.core.functions.message.WhatsappNumberFunctions.Base;
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
 * Linking a WhatsApp number from any app's server function: it is linked for the caller's own client
 * and owned by core, so its traffic comes back as that app's WHATSAPP_INBOUND event. Administration
 * (link, pairing code, unlink) is own-client only; listing follows SendWhatsapp's rule.
 */
class WhatsappNumberFunctionsTest {

    private static final String APP = "leadzumptwo";

    private final Gson gson = new Gson();
    private final List<String> calls = new ArrayList<>();
    private final Set<String> manages = new HashSet<>();

    private IFeignMessageService messageService;
    private IFeignSecurityService securityService;

    @BeforeEach
    void setUp() {
        Map<String, Client> clients = Map.of(
                "SYSTEM", new Client().setId(BigInteger.ONE).setCode("SYSTEM").setTypeCode("SYS"),
                "FIN", new Client().setId(BigInteger.valueOf(7)).setCode("FIN").setTypeCode("BUS"),
                "BP1", new Client().setId(BigInteger.valueOf(90)).setCode("BP1").setTypeCode("BUS"));
        Map<BigInteger, List<BigInteger>> chains = Map.of(
                BigInteger.ONE, List.of(BigInteger.ONE),
                BigInteger.valueOf(7), List.of(BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(90), List.of(BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE));

        this.securityService = (IFeignSecurityService) Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] {IFeignSecurityService.class},
                (p, m, a) -> switch (m.getName()) {
                    case "doesClientManageClientCode" -> Mono.just(this.manages.contains(a[0] + ":" + a[1]));
                    case "getClientByCode" -> Mono.justOrEmpty(clients.get((String) a[0]));
                    case "getClientHierarchy" -> Mono.justOrEmpty(chains.get((BigInteger) a[0]));
                    case "hasWriteAccess" -> Mono.just(APP.equals(a[0]) && "FIN".equals(a[1]));
                    case "hasReadAccess" -> Mono.just(APP.equals(a[0]) && List.of("FIN", "BP1").contains(a[1]));
                    default -> throw new UnsupportedOperationException(m.getName());
                });

        this.messageService = new IFeignMessageService() {
            @Override
            public Mono<Map<String, Object>> createWhatsappSession(
                    String appCode, String clientCode, Map<String, Object> request) {
                calls.add("create " + appCode + " " + clientCode + " " + new TreeMap<>(request));
                return Mono.just(Map.of("code", "SESS9", "sessionState", "PAIRING"));
            }

            @Override
            public Mono<List<Map<String, Object>>> listWhatsappSessions(String appCode, String clientCode) {
                calls.add("list " + appCode + " " + clientCode);
                return Mono.just(List.of(Map.of("code", "SESS9")));
            }

            @Override
            public Mono<Map<String, Object>> getWhatsappSessionQr(String appCode, String clientCode, String sessionId) {
                calls.add("qr " + appCode + " " + clientCode + " " + sessionId);
                return Mono.just(Map.of("qr", "2@abc"));
            }

            @Override
            public Mono<Void> unlinkWhatsappSession(String appCode, String clientCode, String sessionId) {
                calls.add("unlink " + appCode + " " + clientCode + " " + sessionId);
                return Mono.empty();
            }

            @Override
            public Mono<Boolean> markWhatsappSessionDefault(String appCode, String clientCode, String sessionId) {
                calls.add("default " + appCode + " " + clientCode + " " + sessionId);
                return Mono.just(Boolean.TRUE);
            }

            @Override
            public Mono<Map<String, Object>> resolveWhatsappSession(String a, String c, String s) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Map<String, Object>> sendWhatsappSessionMessage(
                    String a, String c, String s, Map<String, Object> r) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Map<String, Object>> makeCallInternal(String a, String c, String o, Map<String, Object> r) {
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

    private Mono<FunctionOutput> run(Base fn, Map<String, JsonElement> args, String caller, boolean definition) {
        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(null, null);
        params.setArguments(args);
        return fn.internalExecute(params)
                .contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, definition ? "true" : "false"))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(userOf(caller)))));
    }

    private Base fn(String name) {
        return (Base) WhatsappNumberFunctions.all(this.messageService, this.securityService, this.gson).stream()
                .filter(f -> f.getSignature().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static boolean forbidden(Throwable e) {
        return e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN;
    }

    @Test
    void aNumberIsLinkedForTheCallersClientAndOwnedByCore() {
        StepVerifier.create(this.run(fn("LinkWhatsappNumber"), args("phoneNumber", "+919876543210"), "FIN", true))
                .assertNext(fo -> assertEquals("SESS9",
                        fo.next().getResult().get("session").getAsJsonObject().get("code").getAsString()))
                .verifyComplete();

        assertEquals(List.of("create " + APP + " FIN {ownerService=core, phoneNumber=+919876543210}"), this.calls);
    }

    @Test
    void aProductIdIsPassedOn() {
        StepVerifier.create(this.run(fn("LinkWhatsappNumber"),
                        args("phoneNumber", "+919876543210", "productId", "12"), "FIN", true))
                .expectNextCount(1)
                .verifyComplete();

        assertTrue(this.calls.get(0).contains("productId=12"), this.calls.get(0));
    }

    @Test
    void administrationIsOwnClientOnlyEvenForAPartnersManagingClient() {
        this.manages.add("FIN:BP1");

        for (String name : List.of("LinkWhatsappNumber", "GetWhatsappPairingCode", "RemoveWhatsappNumber",
                "SetDefaultWhatsappNumber"))
            StepVerifier.create(this.run(fn(name),
                            args("phoneNumber", "+91987", "sessionCode", "SESS9", "clientCode", "FIN"), "BP1", true))
                    .expectErrorMatches(WhatsappNumberFunctionsTest::forbidden)
                    .verify();

        assertTrue(this.calls.isEmpty(), "nothing may reach the internal routes for a refused tenant");
    }

    @Test
    void aPartnerMayListItsManagingClientsNumbers() {
        this.manages.add("FIN:BP1");

        StepVerifier.create(this.run(fn("ListWhatsappNumbers"), args("clientCode", "FIN"), "BP1", true))
                .assertNext(fo -> assertEquals(1, fo.next().getResult().get("numbers").getAsJsonArray().size()))
                .verifyComplete();

        assertEquals(List.of("list " + APP + " FIN"), this.calls);
    }

    @Test
    void pairingCodeAndUnlinkActOnTheNamedSession() {
        StepVerifier.create(this.run(fn("GetWhatsappPairingCode"), args("sessionCode", "SESS9"), "FIN", true))
                .assertNext(fo -> assertEquals("2@abc",
                        fo.next().getResult().get("pairing").getAsJsonObject().get("qr").getAsString()))
                .verifyComplete();

        StepVerifier.create(this.run(fn("RemoveWhatsappNumber"), args("sessionCode", "SESS9"), "FIN", true))
                .assertNext(fo -> assertTrue(fo.next().getResult().get("removed").getAsBoolean()))
                .verifyComplete();

        assertEquals(List.of("qr " + APP + " FIN SESS9", "unlink " + APP + " FIN SESS9"), this.calls);
    }

    @Test
    void missingArgumentsAreA400() {
        StepVerifier.create(this.run(fn("LinkWhatsappNumber"), args(), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
        StepVerifier.create(this.run(fn("RemoveWhatsappNumber"), args("sessionCode", " "), "FIN", true))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void outsideAServerFunctionEveryOneIsRefused() {
        for (String name : List.of("LinkWhatsappNumber", "ListWhatsappNumbers", "GetWhatsappPairingCode",
                "RemoveWhatsappNumber"))
            StepVerifier.create(this.run(fn(name), args("phoneNumber", "+91987", "sessionCode", "S"), "FIN", false))
                    .expectErrorMatches(WhatsappNumberFunctionsTest::forbidden)
                    .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    void noNameIsAPrefixOrPartOfAnother() {
        List<String> names = new ArrayList<>(WhatsappNumberFunctions.all(this.messageService, this.securityService,
                this.gson).stream().map(f -> f.getSignature().getName()).toList());
        names.add("SendWhatsapp");
        names.add("MakeCall");

        for (String a : names)
            for (String b : names)
                assertTrue(a.equals(b) || !b.toLowerCase().contains(a.toLowerCase()), a + " is inside " + b);
    }

    @Test
    void aNumberIsMadeTheDefaultForTheCallersClient() {
        StepVerifier.create(this.run(fn("SetDefaultWhatsappNumber"), args("sessionCode", "SESS9"), "FIN", true))
                .assertNext(fo -> assertTrue(fo.next().getResult().get("marked").getAsBoolean()))
                .verifyComplete();

        assertEquals(List.of("default " + APP + " FIN SESS9"), this.calls);
    }
}
