package com.fincity.saas.commons.core.functions.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.feign.IFeignSecurityAsCaller;
import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.commons.core.kirun.repository.CoreFunctionRepository.CoreFunctionRepositoryBuilder;
import com.fincity.saas.commons.core.kirun.repository.entityprocessor.EPRemoteFunction;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import feign.FeignException;
import feign.Request;
import feign.Response;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * The as-caller user and org functions: the caller gate (definition only, signed in, token and
 * request present), header forwarding, the tenant rule (own or managed client, never an ancestor
 * or an unrelated client), each function's route and argument handling, and security's errors.
 *
 * <p>World (from {@link FeignStub#withHierarchy}): SYSTEM(1) manages FIN(7) and OTHER(55); FIN
 * manages BP1(90). The caller is user 110 of FIN unless a test says otherwise. Users: 110 and 111
 * in FIN, 500 in BP1, 600 in OTHER, 700 in SYSTEM.
 */
class CallerSecurityFunctionsTest {

    private static final String TOKEN = "tok-110";
    private static final String HOST = "leadzumptwo.local.modlix.com";
    private static final String PORT = "443";

    private static final Map<Long, Long> USER_CLIENT = Map.of(110L, 7L, 111L, 7L, 500L, 90L, 600L, 55L, 700L, 1L);

    /** Records every as-caller call with its arguments and answers what the test named. */
    static final class CallerStub {

        final Map<String, Function<Object[], Object>> answers = new HashMap<>();
        final List<String> names = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();

        CallerStub on(String method, Function<Object[], Object> answer) {
            this.answers.put(method, answer);
            return this;
        }

        CallerStub answer(String method, String body) {
            return this.on(method, a -> body == null ? Mono.empty() : Mono.just(body));
        }

        IFeignSecurityAsCaller service() {
            return (IFeignSecurityAsCaller) Proxy.newProxyInstance(
                    IFeignSecurityAsCaller.class.getClassLoader(),
                    new Class<?>[] { IFeignSecurityAsCaller.class },
                    (proxy, method, a) -> {
                        if (method.getDeclaringClass() == Object.class)
                            return method.invoke(this, a);
                        this.names.add(method.getName());
                        this.args.add(a);
                        Function<Object[], Object> answer = this.answers.get(method.getName());
                        if (answer == null)
                            throw new UnsupportedOperationException("unexpected call " + method.getName());
                        return answer.apply(a);
                    });
        }

        Object[] last(String method) {
            for (int i = this.names.size() - 1; i >= 0; i--)
                if (this.names.get(i).equals(method))
                    return this.args.get(i);
            throw new AssertionError("no call to " + method + ", calls: " + this.names);
        }
    }

    private static FeignStub security() {
        return new FeignStub()
                .on("doesClientManageClient", a -> {
                    BigInteger manager = (BigInteger) a[0];
                    BigInteger client = (BigInteger) a[1];
                    boolean manages = (manager.longValue() == 1L && client.longValue() != 1L)
                            || (manager.longValue() == 7L && client.longValue() == 90L);
                    return Mono.just(manages);
                })
                .on("getUserInternal", a -> {
                    Long clientId = USER_CLIENT.get(((BigInteger) a[0]).longValue());
                    return clientId == null ? Mono.empty()
                            : Mono.just(new User().setId((BigInteger) a[0]).setClientId(BigInteger.valueOf(clientId)));
                })
                .withHierarchy();
    }

    private static ContextAuthentication caller(long userId, long clientId, String clientCode) {
        ContextAuthentication ca = SecurityFunctionTestSupport.user(userId, clientId, clientCode);
        ca.setAccessToken(TOKEN);
        return ca;
    }

    private static ContextAuthentication fin() {
        return caller(110, 7, "FIN");
    }

    private static ContextAuthentication finWith(String... authorities) {
        ContextAuthentication ca = SecurityFunctionTestSupport.user(110, 7, "FIN", authorities);
        ca.setAccessToken(TOKEN);
        return ca;
    }

    /** What security's error answer looks like to feign. */
    private static FeignException feign(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://security/api/security/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("x", Response.builder()
                .status(status)
                .reason("refused")
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build());
    }

    /** Security's GET users/{id}: the user with its client from the test world, else its 404. */
    private static CallerStub usersById() {
        return new CallerStub().on("getUser", a -> {
            Long clientId = USER_CLIENT.get(((BigInteger) a[5]).longValue());
            return clientId == null
                    ? Mono.error(feign(404, "{\"message\":\"User with id " + a[5] + " not found\"}"))
                    : Mono.just("{\"id\":" + a[5] + ",\"clientId\":" + clientId + "}");
        });
    }

    private static boolean message(Throwable e, HttpStatus status, String message) {
        return status(e, status) && message.equals(e.getMessage());
    }

    private static AbstractReactiveFunction fn(String name, CallerStub stub, FeignStub sec) {
        return CallerSecurityFunctions.all(stub.service(), sec.service(), new ObjectMapper(), new Gson())
                .stream()
                .filter(f -> f.getSignature().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Mono<FunctionOutput> run(AbstractReactiveFunction fn, Map<String, JsonElement> args,
            ContextAuthentication ca, boolean inDefinition, boolean withRequest) {

        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(
                new KIRunReactiveFunctionRepository(), new KIRunReactiveSchemaRepository())
                .setArguments(new HashMap<>(args));

        Mono<FunctionOutput> mono = fn.execute(params)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));

        if (withRequest)
            mono = mono.contextWrite(Context.of(EPRemoteFunction.FORWARDED_HOST, HOST, EPRemoteFunction.FORWARDED_PORT,
                    PORT));

        return inDefinition ? mono.contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, "true")) : mono;
    }

    private static Mono<FunctionOutput> run(AbstractReactiveFunction fn, Map<String, JsonElement> args) {
        return run(fn, args, fin(), true, true);
    }

    private static JsonElement out(FunctionOutput fo, String key) {
        return fo.next().getResult().get(key);
    }

    private static boolean status(Throwable e, HttpStatus status) {
        return e instanceof GenericException ge && ge.getStatusCode() == status;
    }

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    private static void assertForwarded(Object[] a) {
        assertEquals(TOKEN, a[0]);
        assertEquals(HOST, a[1]);
        assertEquals(PORT, a[2]);
        assertEquals("SYSTEM", a[3]); // the request's (URL) client code
        assertEquals("leadzumptwo", a[4]); // the request's app code
    }

    // ------------------------------------------------------------- the gate

    @Test
    void refusesOutsideADefinition() {
        CallerStub stub = new CallerStub();
        StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(111)), fin(),
                false, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    @Test
    void refusesWithoutARequestToActAs() {
        // An event action, a schedule or a storage trigger: no forwarded host in the context.
        CallerStub stub = new CallerStub();
        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of(), fin(), true, false))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN)
                        && e.getMessage().contains("no request to act as"))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    @Test
    void refusesAnAnonymousCallerOrOneWithoutAToken() {
        CallerStub stub = new CallerStub();
        ContextAuthentication anonymous = SecurityFunctionTestSupport.anonymous(7, "FIN");
        anonymous.setAccessToken(TOKEN);
        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of(), anonymous, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();

        ContextAuthentication noToken = SecurityFunctionTestSupport.user(110, 7, "FIN");
        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of(), noToken, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN) && e.getMessage().contains("token"))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    // ------------------------------------------------------------ QueryUsers

    @Test
    void queryUsersForwardsTheCallerAndPassesQueryAndFlagsThrough() {
        String page = "{\"content\":[{\"id\":111,\"clientId\":7}],\"totalElements\":1,\"number\":0}";
        CallerStub stub = new CallerStub().answer("queryUsers", page);

        JsonObject query = json("{\"page\":0,\"size\":50,\"condition\":{\"field\":\"statusCode\",\"value\":\"ACTIVE\"}}")
                .getAsJsonObject();

        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of(
                "query", query,
                "fetchDesignation", new JsonPrimitive(true),
                "fetchReportingTo", new JsonPrimitive(true),
                "fetchProfiles", new JsonPrimitive(false))))
                .assertNext(fo -> assertEquals(json(page), out(fo, "page")))
                .verifyComplete();

        Object[] a = stub.last("queryUsers");
        assertForwarded(a);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> params = (MultiValueMap<String, String>) a[5];
        assertEquals("true", params.getFirst("fetchDesignation"));
        assertEquals("true", params.getFirst("fetchReportingTo"));
        assertFalse(params.containsKey("fetchProfiles"));
        JsonNode body = (JsonNode) a[6];
        assertEquals(50, body.get("size").asInt());
        assertEquals("statusCode", body.get("condition").get("field").asText());
    }

    @Test
    void queryUsersRefusesAConditionNamingAnAncestorOrUnrelatedClient() {
        for (String cond : List.of(
                "{\"field\":\"clientId\",\"value\":1}", // SYSTEM, an ancestor
                "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"statusCode\",\"value\":\"ACTIVE\"},"
                        + "{\"field\":\"clientId\",\"operator\":\"IN\",\"multiValue\":[90,55]}]}", // 55 unrelated
                "{\"field\":\"clientId\",\"operator\":\"IN\",\"value\":\"7, 55\"}")) {

            CallerStub stub = new CallerStub().answer("queryUsers", "{}");
            JsonObject query = new JsonObject();
            query.add("condition", json(cond));

            StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of("query", query)))
                    .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN)
                            && e.getMessage().contains("is neither the caller's client nor one it manages"))
                    .verify();
            // a read: security answered first, the tenant rule then refused what it named
            assertEquals(List.of("queryUsers"), stub.names, cond);
        }
    }

    @Test
    void queryUsersAllowsOwnAndManagedClientsAndIgnoresNegatedOrFieldValues() {
        for (String cond : List.of(
                "{\"field\":\"clientId\",\"value\":\"7\"}",
                "{\"field\":\"clientId\",\"operator\":\"IN\",\"multiValue\":[\"7\",90]}",
                "{\"field\":\"clientId\",\"value\":55,\"negate\":true}",
                "{\"field\":\"clientId\",\"value\":\"createdBy\",\"isValueField\":true}")) {

            CallerStub stub = new CallerStub().answer("queryUsers", "{\"content\":[]}");
            JsonObject query = new JsonObject();
            query.add("condition", json(cond));

            StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of("query", query)))
                    .assertNext(fo -> assertTrue(out(fo, "page").isJsonObject()))
                    .verifyComplete();
            assertEquals(1, stub.names.size(), cond);
        }
    }

    @Test
    void queryUsersAndsTheClientIdArgument() {
        CallerStub stub = new CallerStub().answer("queryUsers", "{\"content\":[]}");
        JsonObject query = json("{\"size\":5,\"condition\":{\"field\":\"statusCode\",\"value\":\"ACTIVE\"}}")
                .getAsJsonObject();

        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of("query", query,
                "clientId", new JsonPrimitive("90"))))
                .expectNextCount(1)
                .verifyComplete();

        JsonNode cond = ((JsonNode) stub.last("queryUsers")[6]).get("condition");
        assertEquals("AND", cond.get("operator").asText());
        assertEquals("statusCode", cond.get("conditions").get(0).get("field").asText());
        assertEquals("clientId", cond.get("conditions").get(1).get("field").asText());
        assertEquals(90, cond.get("conditions").get(1).get("value").asInt());
        // the caller's query object is not changed
        assertEquals("statusCode", query.getAsJsonObject("condition").get("field").getAsString());

        // no condition at all: the clientId filter alone
        CallerStub plain = new CallerStub().answer("queryUsers", "{}");
        StepVerifier.create(run(fn("QueryUsers", plain, security()), Map.of("clientId", new JsonPrimitive(90))))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals("clientId", ((JsonNode) plain.last("queryUsers")[6]).get("condition").get("field").asText());

        // an unrelated client argument is refused
        CallerStub refused = new CallerStub().answer("queryUsers", "{\"content\":[]}");
        StepVerifier.create(run(fn("QueryUsers", refused, security()), Map.of("clientId", new JsonPrimitive(55))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void aPartnerCannotReachItsManagingClient() {
        // A BP1 (managed by FIN) admin: FIN is its ancestor, never a target of a list or a write.
        ContextAuthentication bp = caller(501, 90, "BP1");
        CallerStub stub = new CallerStub().answer("queryUsers", "{\"content\":[{\"id\":111,\"clientId\":7}]}");

        JsonObject query = new JsonObject();
        query.add("condition", json("{\"field\":\"clientId\",\"value\":7}"));
        StepVerifier.create(run(fn("QueryUsers", stub, security()), Map.of("query", query), bp, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();

        StepVerifier.create(run(fn("LogClientActivity", stub, security()),
                Map.of("activity", json("{\"clientId\":7,\"activityName\":\"x\"}")), bp, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();

        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "status", new JsonPrimitive("ACTIVE")), bp, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        assertEquals(List.of("queryUsers"), stub.names, "no write reached security");
    }

    @Test
    void aPartnerReadsItsManagingClientsUserByIdButNoOtherAncestorOrPartner() {
        // Deals of a partner are assigned to the managing client's users: reading one by id is allowed.
        ContextAuthentication bp = caller(501, 90, "BP1");
        CallerStub stub = usersById();

        StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(111)), bp,
                true, true))
                .assertNext(fo -> assertEquals(7, out(fo, "user").getAsJsonObject().get("clientId").getAsInt()))
                .verifyComplete();

        // SYSTEM (FIN's manager, not BP1's direct one) and OTHER (unrelated) stay refused
        for (long user : new long[] { 700, 600 })
            StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(user)), bp,
                    true, true))
                    .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                    .verify();

        // OTHER's partner (55 is managed by SYSTEM, not by BP1's chain) reading FIN's user: refused
        ContextAuthentication other = caller(601, 55, "OTHER");
        StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(111)), other,
                true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
    }

    // ------------------------------------------------------------- users

    @Test
    void getUserOfAManagedClientAndSelf() {
        CallerStub stub = new CallerStub().on("getUser",
                a -> Mono.just("{\"id\":" + a[5] + ",\"firstName\":\"F\"}"));
        FeignStub sec = security();

        StepVerifier.create(run(fn("GetUser", stub, sec), Map.of("userId", new JsonPrimitive("500"))))
                .assertNext(fo -> assertEquals(500, out(fo, "user").getAsJsonObject().get("id").getAsInt()))
                .verifyComplete();
        assertForwarded(stub.last("getUser"));
        assertEquals(BigInteger.valueOf(500), stub.last("getUser")[5]);

        long before = sec.count("getUserInternal");
        StepVerifier.create(run(fn("GetUser", stub, sec), Map.of("userId", new JsonPrimitive(110))))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(before, sec.count("getUserInternal"), "the caller themself needs no placing");
    }

    @Test
    void getUserRefusesUnrelatedAndSystemUsersAndKeepsSecuritysOwnRefusal() {
        CallerStub stub = usersById();
        // OTHER is unrelated; SYSTEM manages FIN but is the system client, never readable this way
        for (long user : new long[] { 600, 700 })
            StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(user))))
                    .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                    .verify();

        // unknown: security's own 404 and text
        StepVerifier.create(run(fn("GetUser", stub, security()), Map.of("userId", new JsonPrimitive(999))))
                .expectErrorMatches(e -> message(e, HttpStatus.NOT_FOUND, "User with id 999 not found"))
                .verify();

        // no User READ: security's own text, not the tenant rule's
        CallerStub noRead = new CallerStub().on("getUser", a -> Mono.error(feign(403,
                "{\"message\":\"Permission/Role User READ is required to use this operation\"}")));
        StepVerifier.create(run(fn("GetUser", noRead, security()), Map.of("userId", new JsonPrimitive(600))))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN,
                        "Permission/Role User READ is required to use this operation"))
                .verify();

        CallerStub none = new CallerStub();
        StepVerifier.create(run(fn("GetUser", none, security()), Map.of()))
                .expectErrorMatches(e -> status(e, HttpStatus.BAD_REQUEST))
                .verify();
        assertTrue(none.names.isEmpty());
    }

    @Test
    void createUserDefaultsToTheCallersClientAndChecksAGivenOne() {
        CallerStub stub = new CallerStub().on("createUser", a -> Mono.just("{\"id\":800,\"clientId\":90}"));

        StepVerifier.create(run(fn("CreateUser", stub, security()),
                Map.of("user", json("{\"firstName\":\"A\",\"emailId\":\"a@x.com\"}"))))
                .assertNext(fo -> assertEquals(800, out(fo, "user").getAsJsonObject().get("id").getAsInt()))
                .verifyComplete();
        assertForwarded(stub.last("createUser"));
        assertEquals("a@x.com", ((JsonNode) stub.last("createUser")[5]).get("emailId").asText());

        StepVerifier.create(run(fn("CreateUser", stub, security()),
                Map.of("user", json("{\"firstName\":\"A\",\"clientId\":\"90\"}"))))
                .expectNextCount(1)
                .verifyComplete();

        CallerStub refused = new CallerStub();
        StepVerifier.create(run(fn("CreateUser", refused, security()),
                Map.of("user", json("{\"firstName\":\"A\",\"clientId\":55}"))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        assertTrue(refused.names.isEmpty());
    }

    @Test
    void updateUserPatchesByDefaultAndPutsWhenNotPartial() {
        CallerStub stub = new CallerStub()
                .on("patchUser", a -> Mono.just("{\"id\":500,\"firstName\":\"P\"}"))
                .on("putUser", a -> Mono.just("{\"id\":500,\"firstName\":\"Q\"}"));

        StepVerifier.create(run(fn("UpdateUser", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "user", json("{\"firstName\":\"P\"}"))))
                .assertNext(fo -> assertEquals("P", out(fo, "user").getAsJsonObject().get("firstName").getAsString()))
                .verifyComplete();
        assertForwarded(stub.last("patchUser"));
        assertEquals(BigInteger.valueOf(500), stub.last("patchUser")[5]);
        assertEquals(1, ((JsonNode) stub.last("patchUser")[6]).size());

        // the id may come from the user itself, as the pages' PUT body carries it
        StepVerifier.create(run(fn("UpdateUser", stub, security()),
                Map.of("user", json("{\"id\":500,\"clientId\":90,\"firstName\":\"Q\"}"),
                        "partial", new JsonPrimitive(false))))
                .assertNext(fo -> assertEquals("Q", out(fo, "user").getAsJsonObject().get("firstName").getAsString()))
                .verifyComplete();
        assertEquals(BigInteger.valueOf(500), stub.last("putUser")[5]);
    }

    @Test
    void updateUserRefusesAnOutOfScopeUserOrAMoveToAnUnrelatedClient() {
        CallerStub stub = new CallerStub();
        StepVerifier.create(run(fn("UpdateUser", stub, security()),
                Map.of("userId", new JsonPrimitive(600), "user", json("{\"firstName\":\"x\"}"))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        StepVerifier.create(run(fn("UpdateUser", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "user", json("{\"clientId\":55}"))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    @Test
    void deleteUserIsTrueOnANoContentAnswer() {
        CallerStub stub = new CallerStub().answer("deleteUser", null);
        StepVerifier.create(run(fn("DeleteUser", stub, security()), Map.of("userId", new JsonPrimitive(500))))
                .assertNext(fo -> assertTrue(out(fo, "deleted").getAsBoolean()))
                .verifyComplete();
        assertForwarded(stub.last("deleteUser"));

        CallerStub zero = new CallerStub().answer("deleteUser", "0");
        StepVerifier.create(run(fn("DeleteUser", zero, security()), Map.of("userId", new JsonPrimitive(500))))
                .assertNext(fo -> assertFalse(out(fo, "deleted").getAsBoolean()))
                .verifyComplete();
    }

    @Test
    void setUserStatusPicksTheRouteAndValidatesTheStatus() {
        CallerStub stub = new CallerStub().answer("makeUserActive", "true").answer("makeUserInActive", "true");

        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "status", new JsonPrimitive("ACTIVE"))))
                .assertNext(fo -> assertTrue(out(fo, "result").getAsBoolean()))
                .verifyComplete();
        assertEquals(BigInteger.valueOf(500), stub.last("makeUserActive")[5]);

        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "status", new JsonPrimitive("in_active"))))
                .expectNextCount(1)
                .verifyComplete();
        assertForwarded(stub.last("makeUserInActive"));

        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "status", new JsonPrimitive("DELETED"))))
                .expectErrorMatches(e -> status(e, HttpStatus.BAD_REQUEST))
                .verify();
    }

    @Test
    void assignAndRemoveProfile() {
        CallerStub stub = new CallerStub().answer("assignProfile", "true").answer("removeProfile", "false");

        StepVerifier.create(run(fn("AssignProfile", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "profileId", new JsonPrimitive("207"))))
                .assertNext(fo -> assertTrue(out(fo, "result").getAsBoolean()))
                .verifyComplete();
        Object[] a = stub.last("assignProfile");
        assertForwarded(a);
        assertEquals(BigInteger.valueOf(500), a[5]);
        assertEquals(BigInteger.valueOf(207), a[6]);

        StepVerifier.create(run(fn("RemoveProfile", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "profileId", new JsonPrimitive(207))))
                .assertNext(fo -> assertFalse(out(fo, "result").getAsBoolean()))
                .verifyComplete();

        StepVerifier.create(run(fn("AssignProfile", stub, security()), Map.of("userId", new JsonPrimitive(500))))
                .expectErrorMatches(e -> status(e, HttpStatus.BAD_REQUEST))
                .verify();
    }

    // ----------------------------------------------------------- clients

    @Test
    void clientStatusAndDeleteActOnAManagedClientOnly() {
        CallerStub stub = new CallerStub().answer("makeClientInActive", "true").answer("deleteClient", null);

        StepVerifier.create(run(fn("SetClientStatus", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "status", new JsonPrimitive("INACTIVE"))))
                .assertNext(fo -> assertTrue(out(fo, "result").getAsBoolean()))
                .verifyComplete();
        assertForwarded(stub.last("makeClientInActive"));

        StepVerifier.create(run(fn("DeleteClient", stub, security()), Map.of("clientId", new JsonPrimitive(90))))
                .assertNext(fo -> assertTrue(out(fo, "deleted").getAsBoolean()))
                .verifyComplete();

        int calls = stub.names.size();
        for (String name : List.of("SetClientStatus", "DeleteClient"))
            for (long client : new long[] { 7, 1, 55 }) // own, ancestor, unrelated
                StepVerifier.create(run(fn(name, stub, security()), "DeleteClient".equals(name)
                        ? Map.of("clientId", new JsonPrimitive(client))
                        : Map.of("clientId", new JsonPrimitive(client), "status", new JsonPrimitive("ACTIVE"))))
                        .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                        .verify();
        assertEquals(calls, stub.names.size());
    }

    @Test
    void updateClientPatchesOrPuts() {
        CallerStub stub = new CallerStub()
                .on("patchClient", a -> Mono.just("{\"id\":90,\"name\":\"N\"}"))
                .on("putClient", a -> Mono.just("{\"id\":90,\"name\":\"M\"}"));

        StepVerifier.create(run(fn("UpdateClient", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "client", json("{\"name\":\"N\"}"))))
                .assertNext(fo -> assertEquals("N", out(fo, "client").getAsJsonObject().get("name").getAsString()))
                .verifyComplete();
        assertEquals(BigInteger.valueOf(90), stub.last("patchClient")[5]);

        StepVerifier.create(run(fn("UpdateClient", stub, security()),
                Map.of("client", json("{\"id\":\"90\",\"name\":\"M\"}"), "partial", new JsonPrimitive(false))))
                .expectNextCount(1)
                .verifyComplete();
        assertForwarded(stub.last("putClient"));
        assertEquals(BigInteger.valueOf(90), stub.last("putClient")[5]);

        StepVerifier.create(run(fn("UpdateClient", new CallerStub(), security()),
                Map.of("clientId", new JsonPrimitive(1), "client", json("{\"name\":\"x\"}"))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
    }

    // --------------------------------------------------- client managers

    @Test
    void clientManagerRoutes() {
        CallerStub stub = new CallerStub()
                .answer("addClientManager", "true")
                .answer("removeClientManager", "true")
                .answer("replaceClientManager", "true")
                .answer("setClientManagers", "true")
                .answer("migrateClientManagers", "3");

        StepVerifier.create(run(fn("AddClientManager", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "clientId", new JsonPrimitive(90))))
                .assertNext(fo -> assertTrue(out(fo, "result").getAsBoolean()))
                .verifyComplete();
        assertEquals(BigInteger.valueOf(111), stub.last("addClientManager")[5]);
        assertEquals(BigInteger.valueOf(90), stub.last("addClientManager")[6]);

        StepVerifier.create(run(fn("RemoveClientManager", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "clientId", new JsonPrimitive(90))))
                .expectNextCount(1)
                .verifyComplete();
        assertForwarded(stub.last("removeClientManager"));

        StepVerifier.create(run(fn("ReplaceClientManager", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "newManagerId", new JsonPrimitive(111))))
                .expectNextCount(1)
                .verifyComplete();
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> p1 = (MultiValueMap<String, String>) stub.last("replaceClientManager")[6];
        assertEquals("111", p1.getFirst("newManagerId"));
        assertNull(p1.getFirst("oldManagerId"));

        StepVerifier.create(run(fn("ReplaceClientManager", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "newManagerId", new JsonPrimitive(111),
                        "oldManagerId", new JsonPrimitive("110"))))
                .expectNextCount(1)
                .verifyComplete();
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> p2 = (MultiValueMap<String, String>) stub.last("replaceClientManager")[6];
        assertEquals("110", p2.getFirst("oldManagerId"));

        JsonArray managers = new JsonArray();
        managers.add(110);
        managers.add("111");
        StepVerifier.create(run(fn("SetClientManagers", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "managerIds", managers)))
                .expectNextCount(1)
                .verifyComplete();
        JsonNode list = (JsonNode) stub.last("setClientManagers")[6];
        assertTrue(list.isArray());
        assertEquals(2, list.size());
        assertEquals(111, list.get(1).asInt());

        StepVerifier.create(run(fn("MigrateClientManagers", stub, security()),
                Map.of("fromUserId", new JsonPrimitive(110), "toUserId", new JsonPrimitive(111))))
                .assertNext(fo -> assertEquals(3, out(fo, "count").getAsInt()))
                .verifyComplete();
        Object[] m = stub.last("migrateClientManagers");
        assertForwarded(m);
        assertEquals(BigInteger.valueOf(111), m[5]);
        assertEquals(BigInteger.valueOf(110), m[6]);
        assertEquals(Boolean.TRUE, m[7]);
    }

    @Test
    void clientManagerRoutesRefuseOutOfScopeUsersAndClients() {
        CallerStub stub = new CallerStub();
        JsonArray managers = new JsonArray();
        managers.add(110);
        managers.add(600); // OTHER's user

        StepVerifier.create(run(fn("SetClientManagers", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "managerIds", managers)))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        StepVerifier.create(run(fn("AddClientManager", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "clientId", new JsonPrimitive(55))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        StepVerifier.create(run(fn("MigrateClientManagers", stub, security()),
                Map.of("fromUserId", new JsonPrimitive(700), "toUserId", new JsonPrimitive(111))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        StepVerifier.create(run(fn("ReplaceClientManager", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "newManagerId", new JsonPrimitive(111),
                        "oldManagerId", new JsonPrimitive(600))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    // ------------------------------------------------- client activities

    @Test
    void logAndQueryClientActivities() {
        CallerStub stub = new CallerStub()
                .on("createClientActivity", a -> Mono.just("{\"id\":1,\"clientId\":7,\"activityName\":\"T\"}"))
                .answer("queryClientActivities", "{\"content\":[],\"totalElements\":0}");

        StepVerifier.create(run(fn("LogClientActivity", stub, security()),
                Map.of("activity", json("{\"activityName\":\"T\",\"description\":\"d\"}"))))
                .assertNext(fo -> assertEquals("T",
                        out(fo, "activity").getAsJsonObject().get("activityName").getAsString()))
                .verifyComplete();
        assertForwarded(stub.last("createClientActivity"));
        assertEquals("d", ((JsonNode) stub.last("createClientActivity")[5]).get("description").asText());

        StepVerifier.create(run(fn("LogClientActivity", stub, security()),
                Map.of("activity", json("{\"activityName\":\"T\",\"clientId\":90}"))))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(run(fn("QueryClientActivities", stub, security()),
                Map.of("clientId", new JsonPrimitive(90), "query", json("{\"page\":0,\"size\":20}"))))
                .assertNext(fo -> assertEquals(0, out(fo, "page").getAsJsonObject().get("totalElements").getAsInt()))
                .verifyComplete();
        assertEquals(BigInteger.valueOf(90), stub.last("queryClientActivities")[5]);
        assertEquals(20, ((JsonNode) stub.last("queryClientActivities")[6]).get("size").asInt());

        StepVerifier.create(run(fn("QueryClientActivities",
                new CallerStub().answer("queryClientActivities", "{\"content\":[]}"), security()),
                Map.of("clientId", new JsonPrimitive(55))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN))
                .verify();

        // security's own refusal keeps its text
        CallerStub refusing = new CallerStub().on("queryClientActivities", a -> Mono.error(feign(403,
                "{\"message\":\"Permission/Role Client Activity is required to use this operation\"}")));
        StepVerifier.create(run(fn("QueryClientActivities", refusing, security()),
                Map.of("clientId", new JsonPrimitive(55))))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN,
                        "Permission/Role Client Activity is required to use this operation"))
                .verify();
    }

    // -------------------------------------------------------------- apps

    @Test
    void appProfilesByCodeOrId() {
        CallerStub stub = new CallerStub()
                .answer("getAppByCode", "{\"id\":848,\"appCode\":\"leadzumptwo\"}")
                .answer("getAppProfiles", "{\"content\":[{\"id\":207}],\"totalElements\":1}");

        StepVerifier.create(run(fn("GetAppProfiles", stub, security()), Map.of()))
                .assertNext(fo -> assertEquals(207, out(fo, "page").getAsJsonObject().getAsJsonArray("content")
                        .get(0).getAsJsonObject().get("id").getAsInt()))
                .verifyComplete();
        assertEquals("leadzumptwo", stub.last("getAppByCode")[5]);
        Object[] a = stub.last("getAppProfiles");
        assertForwarded(a);
        assertEquals(BigInteger.valueOf(848), a[5]);
        assertTrue(((MultiValueMap<?, ?>) a[6]).isEmpty());

        StepVerifier.create(run(fn("GetAppProfiles", stub, security()), Map.of("appId", new JsonPrimitive(9),
                "size", new JsonPrimitive(100), "sort", new JsonPrimitive("name,asc"))))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(BigInteger.valueOf(9), stub.last("getAppProfiles")[5]);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> p = (MultiValueMap<String, String>) stub.last("getAppProfiles")[6];
        assertEquals("100", p.getFirst("size"));
        assertEquals("name,asc", p.getFirst("sort"));
        assertNull(p.getFirst("page"));

        CallerStub none = new CallerStub().answer("getAppByCode", null);
        StepVerifier.create(run(fn("GetAppProfiles", none, security()), Map.of("appCode", new JsonPrimitive("x"))))
                .expectErrorMatches(e -> status(e, HttpStatus.NOT_FOUND))
                .verify();
    }

    // ------------------------------------------------------------ errors

    @Test
    void securitysRefusalKeepsItsStatusAndMessage() {
        Request request = Request.create(Request.HttpMethod.PATCH, "http://security/api/security/users/500",
                Map.of(), null, StandardCharsets.UTF_8, null);
        FeignException fe = FeignException.errorStatus("patchUser", Response.builder()
                .status(403)
                .reason("Forbidden")
                .request(request)
                .headers(Map.of())
                .body("{\"message\":\"Forbidden to update user\"}", StandardCharsets.UTF_8)
                .build());

        CallerStub stub = new CallerStub().on("patchUser", a -> Mono.error(fe));
        StepVerifier.create(run(fn("UpdateUser", stub, security()),
                Map.of("userId", new JsonPrimitive(500), "user", json("{\"firstName\":\"x\"}"))))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN)
                        && "Forbidden to update user".equals(e.getMessage()))
                .verify();
    }

    @Test
    void aPlainTextRefusalIsTheMessage() {
        // Spring Security's own refusal (no authority) is text/plain "Access Denied".
        Request request = Request.create(Request.HttpMethod.PATCH, "http://security/api/security/users/makeUserActive",
                Map.of(), null, StandardCharsets.UTF_8, null);
        FeignException fe = FeignException.errorStatus("makeUserActive", Response.builder()
                .status(403)
                .reason("Forbidden")
                .request(request)
                .headers(Map.of())
                .body("Access Denied", StandardCharsets.UTF_8)
                .build());

        CallerStub stub = new CallerStub().on("makeUserActive", a -> Mono.error(fe));
        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(111), "status", new JsonPrimitive("ACTIVE"))))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN, "Access Denied"))
                .verify();

        // no body at all: the generic text
        CallerStub empty = new CallerStub().on("makeUserActive", a -> Mono.error(feign(502, "")));
        StepVerifier.create(run(fn("SetUserStatus", empty, security()),
                Map.of("userId", new JsonPrimitive(111), "status", new JsonPrimitive("ACTIVE"))))
                .expectErrorMatches(e -> message(e, HttpStatus.BAD_GATEWAY,
                        "CoreServices.Security.SetUserStatus: security answered 502"))
                .verify();
    }

    @Test
    void aTenantRefusalOfAWriteIsWordedAsSecurityWouldAndSendsNothing() {
        CallerStub stub = new CallerStub();

        // without the route's @PreAuthorize authority: security's Access Denied
        StepVerifier.create(run(fn("SetUserStatus", stub, security()),
                Map.of("userId", new JsonPrimitive(600), "status", new JsonPrimitive("ACTIVE")), fin(), true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN, "Access Denied"))
                .verify();

        ContextAuthentication admin = finWith("Authorities.User_UPDATE", "Authorities.User_DELETE",
                "Authorities.Profile_READ", "Authorities.Client_UPDATE", "Authorities.Client_DELETE",
                "Authorities.User_CREATE");

        Map<String, Map<String, JsonElement>> calls = new java.util.LinkedHashMap<>();
        Map<String, String> expected = new HashMap<>();
        calls.put("SetUserStatus", Map.of("userId", new JsonPrimitive(600), "status", new JsonPrimitive("INACTIVE")));
        expected.put("SetUserStatus", "selected user doesn't have access to change status code");
        calls.put("UpdateUser", Map.of("userId", new JsonPrimitive(600), "user", json("{\"firstName\":\"x\"}")));
        expected.put("UpdateUser", "Cannot update user for the selected client");
        calls.put("DeleteUser", Map.of("userId", new JsonPrimitive(600)));
        expected.put("DeleteUser", "Cannot update user for the selected client");
        calls.put("CreateUser", Map.of("user", json("{\"clientId\":55}")));
        expected.put("CreateUser", "Cannot create User for the selected client");
        calls.put("AssignProfile", Map.of("userId", new JsonPrimitive(600), "profileId", new JsonPrimitive(206)));
        expected.put("AssignProfile", "206 profile forbidden for the 600 user");
        calls.put("SetClientStatus", Map.of("clientId", new JsonPrimitive(55), "status", new JsonPrimitive("ACTIVE")));
        expected.put("SetClientStatus", "selected client doesn't have access to change status code");
        calls.put("UpdateClient", Map.of("clientId", new JsonPrimitive(55), "client", json("{\"name\":\"x\"}")));
        expected.put("UpdateClient", "Permission/Role Client UPDATE is required to use this operation");
        calls.put("DeleteClient", Map.of("clientId", new JsonPrimitive(55)));
        expected.put("DeleteClient", "Permission/Role Client READ is required to use this operation");
        calls.put("AddClientManager", Map.of("userId", new JsonPrimitive(111), "clientId", new JsonPrimitive(55)));
        expected.put("AddClientManager", "Permission/Role Client Manager CREATE is required to use this operation");
        calls.put("RemoveClientManager", Map.of("userId", new JsonPrimitive(600), "clientId", new JsonPrimitive(90)));
        expected.put("RemoveClientManager", "Permission/Role Client Manager DELETE is required to use this operation");
        calls.put("ReplaceClientManager", Map.of("clientId", new JsonPrimitive(55), "newManagerId",
                new JsonPrimitive(111)));
        expected.put("ReplaceClientManager", "Permission/Role Client Manager UPDATE is required to use this operation");
        JsonArray managers = new JsonArray();
        managers.add(600);
        calls.put("SetClientManagers", Map.of("clientId", new JsonPrimitive(90), "managerIds", managers));
        expected.put("SetClientManagers", "Permission/Role Client Manager SYNC is required to use this operation");
        calls.put("MigrateClientManagers", Map.of("fromUserId", new JsonPrimitive(600), "toUserId",
                new JsonPrimitive(111)));
        expected.put("MigrateClientManagers", "Permission/Role Client Manager MIGRATE is required to use this operation");
        calls.put("LogClientActivity", Map.of("activity", json("{\"clientId\":55,\"activityName\":\"x\"}")));
        expected.put("LogClientActivity", "Permission/Role Client Activity is required to use this operation");

        calls.forEach((name, args) -> StepVerifier.create(run(fn(name, stub, security()), args, admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN, expected.get(name)))
                .verify());

        // the caller's own client stays refused by SetClientStatus with the tenant rule's own text
        StepVerifier.create(run(fn("SetClientStatus", stub, security()),
                Map.of("clientId", new JsonPrimitive(7), "status", new JsonPrimitive("ACTIVE")), admin, true, true))
                .expectErrorMatches(e -> status(e, HttpStatus.FORBIDDEN) && e.getMessage().contains("own client"))
                .verify();

        assertTrue(stub.names.isEmpty(), "nothing was sent to security: " + stub.names);
    }

    @Test
    void anUnknownUserIsAnsweredAsSecurityWouldOnThatRoute() {
        CallerStub stub = new CallerStub();
        ContextAuthentication admin = finWith("Authorities.User_UPDATE", "Authorities.User_DELETE",
                "Authorities.Profile_READ", "Authorities.Client_UPDATE");

        StepVerifier.create(run(fn("DeleteUser", stub, security()), Map.of("userId", new JsonPrimitive(999)), admin,
                true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.NOT_FOUND, "User with id 999 not found"))
                .verify();
        StepVerifier.create(run(fn("UpdateUser", stub, security()), Map.of("userId", new JsonPrimitive(999), "user",
                json("{\"id\":999}"), "partial", new JsonPrimitive(false)), admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.NOT_FOUND, "User with id 999 not found"))
                .verify();
        StepVerifier.create(run(fn("UpdateUser", stub, security()), Map.of("userId", new JsonPrimitive(999), "user",
                json("{\"firstName\":\"x\"}")), admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN, "Cannot update user for the selected client"))
                .verify();
        StepVerifier.create(run(fn("SetUserStatus", stub, security()), Map.of("userId", new JsonPrimitive(999),
                "status", new JsonPrimitive("ACTIVE")), admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN,
                        "selected user doesn't have access to change status code"))
                .verify();
        StepVerifier.create(run(fn("AssignProfile", stub, security()), Map.of("userId", new JsonPrimitive(999),
                "profileId", new JsonPrimitive(206)), admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.NOT_FOUND, "User with id 999 not found"))
                .verify();
        StepVerifier.create(run(fn("MigrateClientManagers", stub, security()), Map.of("fromUserId",
                new JsonPrimitive(999), "toUserId", new JsonPrimitive(111)), admin, true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN,
                        "Permission/Role Client Manager MIGRATE is required to use this operation"))
                .verify();

        // a caller without the route's authority: Access Denied first, as security's @PreAuthorize
        StepVerifier.create(run(fn("DeleteUser", stub, security()), Map.of("userId", new JsonPrimitive(999)), fin(),
                true, true))
                .expectErrorMatches(e -> message(e, HttpStatus.FORBIDDEN, "Access Denied"))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    @Test
    void aBlankActivityNameIsRefusedFirstAsSecurityDoes() {
        CallerStub stub = new CallerStub();
        StepVerifier.create(run(fn("LogClientActivity", stub, security()),
                Map.of("activity", json("{\"clientId\":55,\"activityName\":\" \"}"))))
                .expectErrorMatches(e -> message(e, HttpStatus.BAD_REQUEST, "Activity name is required"))
                .verify();
        assertTrue(stub.names.isEmpty());
    }

    // ------------------------------------------------------ registration

    @Test
    void registeredInCoreServicesSecurityOnlyWhenTheClientIsWired() {
        IFeignSecurityService sec = security().service();

        CoreFunctionRepository wired = new CoreFunctionRepository(new CoreFunctionRepositoryBuilder()
                .setSecurityService(sec)
                .setObjectMapper(new ObjectMapper())
                .setGson(new Gson())
                .setSecurityAsCaller(new CallerStub().service()));

        for (String name : List.of("QueryUsers", "GetUser", "CreateUser", "UpdateUser", "DeleteUser",
                "SetUserStatus", "AssignProfile", "RemoveProfile", "SetClientStatus", "UpdateClient", "DeleteClient",
                "AddClientManager", "RemoveClientManager", "ReplaceClientManager", "SetClientManagers",
                "MigrateClientManagers", "LogClientActivity", "QueryClientActivities", "GetAppProfiles"))
            assertNotNull(wired.find("CoreServices.Security", name).block(), name);

        // the existing GetClient is untouched
        assertNotNull(wired.find("CoreServices.Security", "GetClient").block());

        CoreFunctionRepository bare = new CoreFunctionRepository(new CoreFunctionRepositoryBuilder()
                .setSecurityService(sec)
                .setObjectMapper(new ObjectMapper())
                .setGson(new Gson()));
        assertNull(bare.find("CoreServices.Security", "QueryUsers").block());
    }
}
