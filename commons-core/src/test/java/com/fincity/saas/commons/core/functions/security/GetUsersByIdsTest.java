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

import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetUsersByIdsTest {

    private static User u(long id, long clientId, String first) {
        return new User().setId(BigInteger.valueOf(id)).setClientId(BigInteger.valueOf(clientId))
                .setFirstName(first).setLastName("L" + id).setEmailId(first + "@x.com").setPhoneNumber("+91" + id)
                .setUserName(first.toLowerCase()).setStatusCode("ACTIVE");
    }

    private static JsonArray arr(Object... values) {
        JsonArray a = new JsonArray();
        for (Object v : values)
            a.add(v instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(v.toString()));
        return a;
    }

    @Test
    void usersComeBackKeyedByIdWithTheirPlainFields() {
        FeignStub stub = new FeignStub()
                .on("getUsersInternalBatch", a -> {
                    assertEquals(ids(110, 111), a[0]);
                    return Mono.just(List.of(u(110, 7, "Asha"), u(111, 7, "Ravi")));
                });

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(110, "111", 110, "")),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject users = output(fo).get("users").getAsJsonObject();
                    assertEquals(2, users.size());
                    JsonObject asha = users.getAsJsonObject("110");
                    assertEquals(110, asha.get("id").getAsLong());
                    assertEquals(7, asha.get("clientId").getAsLong());
                    assertEquals("Asha", asha.get("firstName").getAsString());
                    assertEquals("L110", asha.get("lastName").getAsString());
                    assertEquals("Asha@x.com", asha.get("emailId").getAsString());
                    assertEquals("+91110", asha.get("phoneNumber").getAsString());
                    assertEquals("asha", asha.get("userName").getAsString());
                    assertEquals("ACTIVE", asha.get("statusCode").getAsString());
                    assertFalse(asha.has("middleName"));
                })
                .verifyComplete();

        // one client, and it is the caller's own: nothing to ask
        assertEquals(0, stub.count("doesClientManageClient"));
    }

    @Test
    void usersOfUnrelatedClientsAreLeftOut() {
        FeignStub stub = new FeignStub()
                .on("getUsersInternalBatch", a -> Mono.just(List.of(u(110, 7, "Asha"), u(500, 90, "Partner"),
                        u(900, 55, "Stranger"), u(901, 55, "Stranger2"))))
                .on("doesClientManageClient", a -> Mono.just(
                        BigInteger.valueOf(7).equals(a[0]) && BigInteger.valueOf(90).equals(a[1])))
                .withHierarchy();

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(110, 500, 900, 901)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject users = output(fo).get("users").getAsJsonObject();
                    assertTrue(users.has("110"));
                    assertTrue(users.has("500"));
                    assertFalse(users.has("900"));
                    assertFalse(users.has("901"));
                })
                .verifyComplete();

        // client 55 is checked once for its two users: downward, then it is not in FIN's chain upward
        assertEquals(1, stub.calls.stream().filter(c -> c.contains("doesClientManageClient") && c.contains("55"))
                .count());
        assertEquals(0, stub.count("hasWriteAccess"));
    }

    @Test
    void systemUsersAreNeverVisibleUpward() {
        // SYSTEM manages everyone and has write access to every app, and still its people stay out.
        FeignStub stub = new FeignStub()
                .on("getUsersInternalBatch", a -> Mono.just(List.of(u(1, 1, "Root"), u(2, 1, "Root2"),
                        u(110, 7, "Asha"))))
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(1, 2, 110)),
                user(500, 90, "BP1"), true))
                .assertNext(fo -> {
                    JsonObject users = output(fo).get("users").getAsJsonObject();
                    assertFalse(users.has("1"));
                    assertFalse(users.has("2"));
                    assertTrue(users.has("110"));
                })
                .verifyComplete();

        // FIN, directly under SYSTEM, sees its own user and still none of SYSTEM's.
        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(1, 2, 110)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> {
                    JsonObject users = output(fo).get("users").getAsJsonObject();
                    assertFalse(users.has("1"));
                    assertFalse(users.has("2"));
                    assertTrue(users.has("110"));
                })
                .verifyComplete();
    }

    @Test
    void aPartnerDoesNotSeeTheManagingClientsUsersForAnAppItDoesNotEdit() {
        FeignStub stub = new FeignStub()
                .on("getUsersInternalBatch", a -> Mono.just(List.of(u(110, 7, "Asha"))))
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        var ca = user(500, 90, "BP1").setUrlAppCode("someOtherApp");

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(110)), ca, true))
                .assertNext(fo -> assertEquals(new JsonObject(), output(fo).get("users")))
                .verifyComplete();
    }

    @Test
    void aPartnerSeesItsManagingClientsUsers() {
        FeignStub stub = new FeignStub()
                .on("getUsersInternalBatch", a -> Mono.just(List.of(u(110, 7, "Asha"))))
                .on("doesClientManageClient", a -> Mono.just(
                        BigInteger.valueOf(7).equals(a[0]) && BigInteger.valueOf(90).equals(a[1])))
                .withHierarchy();

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of("ids", arr(110)),
                user(500, 90, "BP1"), true))
                .assertNext(fo -> assertTrue(output(fo).get("users").getAsJsonObject().has("110")))
                .verifyComplete();
    }

    @Test
    void noIdsIsAnEmptyMapWithoutAskingSecurity() {
        FeignStub stub = new FeignStub();

        StepVerifier.create(run(new GetUsersByIds(stub.service()), Map.of(), user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(new JsonObject(), output(fo).get("users")))
                .verifyComplete();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void outsideAFunctionDefinitionItIsRefused() {
        StepVerifier.create(run(new GetUsersByIds(new FeignStub().service()), Map.of("ids", arr(110)),
                user(110, 7, "FIN"), false))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }
}
