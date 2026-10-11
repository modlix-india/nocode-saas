package com.fincity.saas.commons.core.functions.security;

import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.ids;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.output;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.run;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.ClientDenormData;
import com.fincity.saas.commons.security.model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** GetClientsByIds' client-manager / count options, GetClientUsersPage and GetClientsDenorm. */
class PartnerSecurityFunctionsTest {

    private static User u(long id, long clientId) {
        return new User().setId(BigInteger.valueOf(id)).setClientId(BigInteger.valueOf(clientId))
                .setFirstName("F" + id).setLastName("L").setStatusCode("ACTIVE");
    }

    private static JsonArray arr(long... values) {
        JsonArray a = new JsonArray();
        for (long v : values)
            a.add(new JsonPrimitive(v));
        return a;
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientManagersAndCountsWhenAsked() {
        FeignStub stub = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertEquals("true", ((MultiValueMap<String, String>) a[1]).getFirst("fetchClientManagers"));
                    return Mono.just(List.of(new Client().setId(BigInteger.valueOf(90)).setCode("BP1")
                            .setClientManagers(List.of(u(114, 7)))));
                })
                .on("doesClientManageClient", a -> Mono.just(Boolean.TRUE))
                .withHierarchy();

        StepVerifier.create(run(new GetClientsByIds(stub.service()),
                Map.of("ids", arr(90), "fetchClientManagers", new JsonPrimitive(true),
                        "withCounts", new JsonPrimitive(true)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject bp = output(fo).get("clients").getAsJsonObject().getAsJsonObject("90");
                    assertEquals(114, bp.getAsJsonArray("clientManagers").get(0).getAsJsonObject().get("id")
                            .getAsLong());
                    assertEquals(0, bp.get("activeUsers").getAsInt());
                    assertEquals(0, bp.get("totalUsers").getAsInt());
                    assertFalse(bp.has("owners"));
                })
                .verifyComplete();

        // default: neither key
        FeignStub plain = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertTrue(((MultiValueMap<?, ?>) a[1]).isEmpty());
                    return Mono.just(List.of(new Client().setId(BigInteger.valueOf(90)).setCode("BP1")));
                })
                .on("doesClientManageClient", a -> Mono.just(Boolean.TRUE))
                .withHierarchy();
        StepVerifier.create(run(new GetClientsByIds(plain.service()), Map.of("ids", arr(90)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject bp = output(fo).get("clients").getAsJsonObject().getAsJsonObject("90");
                    assertFalse(bp.has("clientManagers"));
                    assertFalse(bp.has("activeUsers"));
                })
                .verifyComplete();
    }

    @Test
    void usersPageOfAManagedClientAddsTheClientFilter() {
        FeignStub stub = new FeignStub()
                .on("readUserPageFilterInternal", a -> {
                    Query q = (Query) a[0];
                    assertEquals(1, q.getPage());
                    assertEquals(2, q.getSize());
                    assertEquals("DESC", q.getSort().getOrderFor("id").getDirection().name());
                    ComplexCondition cc = (ComplexCondition) q.getCondition();
                    FilterCondition fc = (FilterCondition) cc.getConditions().get(1);
                    assertEquals("clientId", fc.getField());
                    assertEquals(BigInteger.valueOf(90), fc.getValue());
                    return Mono.just(new PageImpl<>(List.of(u(500, 90), u(501, 90)), PageRequest.of(1, 2), 5));
                })
                .on("doesClientManageClient", a -> Mono.just(Boolean.TRUE))
                .withHierarchy();

        StepVerifier.create(run(new GetClientUsersPage(stub.service(), null),
                Map.of("clientId", new JsonPrimitive("90"),
                        "condition", JsonParser.parseString("{\"field\":\"statusCode\",\"value\":\"ACTIVE\"}"),
                        "page", new JsonPrimitive(1), "size", new JsonPrimitive(2),
                        "sort", JsonParser.parseString("{\"property\":\"id\",\"direction\":\"DESC\"}")),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    var out = output(fo);
                    assertEquals(2, out.get("users").getAsJsonArray().size());
                    assertEquals(5, out.get("totalElements").getAsLong());
                    assertEquals(3, out.get("totalPages").getAsInt());
                    assertEquals(1, out.get("number").getAsInt());
                })
                .verifyComplete();
    }

    @Test
    void usersPageOfAnUnrelatedClientIsForbidden() {
        FeignStub stub = new FeignStub()
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        StepVerifier.create(run(new GetClientUsersPage(stub.service(), null),
                Map.of("clientId", new JsonPrimitive(55)), user(110, 7, "FIN"), true))
                .expectErrorMatches(e -> e instanceof GenericException ge
                        && ge.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();
        assertEquals(0, stub.count("readUserPageFilterInternal"));

        StepVerifier.create(run(new GetClientUsersPage(stub.service(), null),
                Map.of("clientId", new JsonPrimitive(90)), user(110, 7, "FIN"), false))
                .expectErrorMatches(e -> e instanceof GenericException ge
                        && ge.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();
    }

    @Test
    void denormOnlyForRelatedClients() {
        FeignStub stub = new FeignStub()
                .on("getClientsDenormFull", a -> {
                    assertEquals(ids(90), a[0]);
                    return Mono.just(Map.of(BigInteger.valueOf(90), new ClientDenormData().setClientName("BP One")
                            .setStatusCode("ACTIVE").setActiveUsers(3).setUserNames("A, B")
                            .setClientManagerIds(",114,")));
                })
                .on("doesClientManageClient", a -> Mono.just(BigInteger.valueOf(90).equals(a[1])))
                .withHierarchy();

        StepVerifier.create(run(new GetClientsDenorm(stub.service()), Map.of("ids", arr(90, 55)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject m = output(fo).get("denorm").getAsJsonObject();
                    assertEquals(1, m.size());
                    JsonObject d = m.getAsJsonObject("90");
                    assertEquals("BP One", d.get("clientName").getAsString());
                    assertEquals(3, d.get("activeUsers").getAsInt());
                    assertEquals(",114,", d.get("clientManagerIds").getAsString());
                    assertFalse(d.has("userPhones"));
                })
                .verifyComplete();
    }

    @Test
    void anUnknownClientIdIsLeftOutInsteadOfFailingTheCall() {
        FeignStub stub = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertEquals(ids(90), a[0]);
                    return Mono.just(List.of(new Client().setId(BigInteger.valueOf(90)).setCode("BP1")));
                })
                .on("doesClientManageClient", a -> BigInteger.valueOf(99999999).equals(a[1])
                        ? Mono.error(new RuntimeException("No client hierarchy found for client id: 99999999"))
                        : Mono.just(Boolean.TRUE))
                .withHierarchy();

        StepVerifier.create(run(new GetClientsByIds(stub.service()), Map.of("ids", arr(90, 99999999)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject m = output(fo).get("clients").getAsJsonObject();
                    assertEquals(1, m.size());
                    assertTrue(m.has("90"));
                })
                .verifyComplete();
    }
}
