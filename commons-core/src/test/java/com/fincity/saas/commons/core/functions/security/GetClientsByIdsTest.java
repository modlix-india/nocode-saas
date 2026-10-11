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
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetClientsByIdsTest {

    private static Client c(long id, String code, String name) {
        return new Client().setId(BigInteger.valueOf(id)).setCode(code).setName(name).setTypeCode("BUS")
                .setLevelType("CUSTOMER").setStatusCode("ACTIVE");
    }

    private static User owner(long id, long clientId) {
        return new User().setId(BigInteger.valueOf(id)).setClientId(BigInteger.valueOf(clientId))
                .setFirstName("O" + id).setLastName("L").setEmailId("o" + id + "@x.com").setStatusCode("ACTIVE");
    }

    private static JsonArray arr(Object... values) {
        JsonArray a = new JsonArray();
        for (Object v : values)
            a.add(v instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(v.toString()));
        return a;
    }

    @Test
    @SuppressWarnings("unchecked")
    void relatedClientsComeBackKeyedByIdWithOwnersWhenAsked() {
        FeignStub stub = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertEquals(ids(7, 90), a[0]);
                    assertEquals("true", ((MultiValueMap<String, String>) a[1]).getFirst("fetchOwners"));
                    return Mono.just(List.of(c(7, "FIN", "Fincity"), c(90, "BP1", "Partner One")
                            .setOwners(List.of(owner(500, 90)))));
                })
                .on("doesClientManageClient", a -> Mono.just(
                        BigInteger.valueOf(7).equals(a[0]) && BigInteger.valueOf(90).equals(a[1])))
                .withHierarchy();

        StepVerifier.create(run(new GetClientsByIds(stub.service()),
                Map.of("ids", arr(7, "90", 55, ""), "fetchOwners", new JsonPrimitive(true)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject clients = output(fo).get("clients").getAsJsonObject();
                    assertEquals(2, clients.size());
                    assertFalse(clients.has("55"), "an unrelated client is left out");
                    JsonObject bp = clients.getAsJsonObject("90");
                    assertEquals(90, bp.get("id").getAsLong());
                    assertEquals("BP1", bp.get("code").getAsString());
                    assertEquals("Partner One", bp.get("name").getAsString());
                    assertEquals(1, bp.getAsJsonArray("owners").size());
                    JsonObject o = bp.getAsJsonArray("owners").get(0).getAsJsonObject();
                    assertEquals(500, o.get("id").getAsLong());
                    assertEquals(90, o.get("clientId").getAsLong());
                    assertEquals("O500", o.get("firstName").getAsString());
                    assertTrue(clients.getAsJsonObject("7").getAsJsonArray("owners").isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void noOwnersKeyUnlessAskedAndNothingFetchedForUnrelatedIds() {
        FeignStub stub = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertTrue(((MultiValueMap<?, ?>) a[1]).isEmpty());
                    return Mono.just(List.of(c(7, "FIN", "Fincity")));
                })
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        StepVerifier.create(run(new GetClientsByIds(stub.service()), Map.of("ids", arr(7, 1)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject clients = output(fo).get("clients").getAsJsonObject();
                    assertEquals(1, clients.size());
                    assertFalse(clients.getAsJsonObject("7").has("owners"));
                    assertFalse(clients.has("1"), "SYSTEM is never visible upward");
                })
                .verifyComplete();

        // only unrelated ids: security is not asked for any client at all
        FeignStub none = new FeignStub()
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();
        StepVerifier.create(run(new GetClientsByIds(none.service()), Map.of("ids", arr(55)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(0, output(fo).get("clients").getAsJsonObject().size()))
                .verifyComplete();
        assertEquals(0, none.count("getClientInternalBatch"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void managingClientIsAddedWhenAsked() {
        FeignStub stub = new FeignStub()
                .on("getClientInternalBatch", a -> {
                    assertEquals("true", ((MultiValueMap<String, String>) a[1]).getFirst("fetchManagingClient"));
                    return Mono.just(List.of(c(90, "BP1", "Partner One").setManagagingClient(c(7, "FIN", "Fincity"))));
                })
                .withHierarchy();

        StepVerifier.create(run(new GetClientsByIds(stub.service()),
                Map.of("ids", arr(90), "fetchManagingClient", new JsonPrimitive(true)),
                user(500, 90, "BP1"), true))
                .assertNext(fo -> {
                    JsonObject bp = output(fo).get("clients").getAsJsonObject().getAsJsonObject("90");
                    assertEquals("FIN", bp.getAsJsonObject("managingClient").get("code").getAsString());
                    assertFalse(bp.has("owners"));
                })
                .verifyComplete();
    }

    @Test
    void refusedOutsideAServerFunction() {
        FeignStub stub = new FeignStub();
        StepVerifier.create(run(new GetClientsByIds(stub.service()), Map.of("ids", arr(7)),
                user(110, 7, "FIN"), false))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN, ((GenericException) e).getStatusCode()))
                .verify();
    }
}
