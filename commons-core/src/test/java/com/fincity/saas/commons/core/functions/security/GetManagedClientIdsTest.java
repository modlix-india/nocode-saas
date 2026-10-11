package com.fincity.saas.commons.core.functions.security;

import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.anonymous;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.ids;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.output;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.run;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** The three cases of entity-processor's getUserInheritanceInfo, and its refusals. */
class GetManagedClientIdsTest {

    private static final String OWNER = "Authorities.ROLE_Owner";
    private static final String PARTNER_MANAGER = "Authorities.ROLE_Partner_Manager";

    private static JsonArray ints(long... values) {
        JsonArray a = new JsonArray();
        for (long v : values)
            a.add(v);
        return a;
    }

    @Test
    void anOwnerGetsEveryClientTheirClientManages() {
        FeignStub stub = new FeignStub().on("getManagingClientIds", a -> {
            assertEquals(BigInteger.valueOf(7), a[0]);
            return Mono.just(ids(7, 90, 91));
        });

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(),
                user(110, 7, "FIN", OWNER), true))
                .assertNext(fo -> assertEquals(ints(7, 90, 91), output(fo).get("clientIds")))
                .verifyComplete();
    }

    @Test
    void anOwnerWhoIsAlsoAPartnerManagerTakesTheOwnerPath() {
        FeignStub stub = new FeignStub().on("getManagingClientIds", a -> Mono.just(ids(7, 90)));

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(),
                user(110, 7, "FIN", OWNER, PARTNER_MANAGER), true))
                .assertNext(fo -> assertEquals(ints(7, 90), output(fo).get("clientIds")))
                .verifyComplete();

        assertEquals(0, stub.count("getClientIdsOfManagers"));
    }

    @Test
    void aPartnerManagerGetsTheClientsTheyAndTheirSubOrgManage() {
        FeignStub stub = new FeignStub()
                .on("getUserSubOrgInternal", a -> {
                    assertEquals(BigInteger.valueOf(110), a[0]);
                    assertEquals("leadzumptwo", a[1]);
                    assertEquals(BigInteger.valueOf(7), a[2]);
                    return Mono.just(ids(110, 120, 121));
                })
                .on("getClientIdsOfManagers", a -> {
                    // the user is in their own sub-org already; asked once, not twice
                    assertEquals(ids(110, 120, 121), a[0]);
                    return Mono.just(ids(90, 92));
                });

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(),
                user(110, 7, "FIN", PARTNER_MANAGER), true))
                .assertNext(fo -> assertEquals(ints(90, 92), output(fo).get("clientIds")))
                .verifyComplete();
    }

    @Test
    void aPartnerManagerMissingFromTheirSubOrgIsStillAsked() {
        FeignStub stub = new FeignStub()
                .on("getUserSubOrgInternal", a -> Mono.just(List.of()))
                .on("getClientIdsOfManagers", a -> {
                    assertEquals(ids(110), a[0]);
                    return Mono.just(ids(90));
                });

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(),
                user(110, 7, "FIN", PARTNER_MANAGER), true))
                .assertNext(fo -> assertEquals(ints(90), output(fo).get("clientIds")))
                .verifyComplete();
    }

    @Test
    void anyoneElseGetsNoneWithoutAskingSecurity() {
        FeignStub stub = new FeignStub();

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(),
                user(110, 7, "FIN", "Authorities.LEADZUMPTWO.ROLE_Deal_READ"), true))
                .assertNext(fo -> assertEquals(new JsonArray(), output(fo).get("clientIds")))
                .verifyComplete();

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of(), anonymous(7, "FIN"), true))
                .assertNext(fo -> assertEquals(new JsonArray(), output(fo).get("clientIds")))
                .verifyComplete();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void theCurrentUsersOwnIdIsAccepted() {
        FeignStub stub = new FeignStub().on("getManagingClientIds", a -> Mono.just(ids(7)));

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of("userId", new JsonPrimitive("110")),
                user(110, 7, "FIN", OWNER), true))
                .assertNext(fo -> assertEquals(ints(7), output(fo).get("clientIds")))
                .verifyComplete();
    }

    @Test
    void anotherUsersIdIsRefused() {
        FeignStub stub = new FeignStub();

        StepVerifier.create(run(new GetManagedClientIds(stub.service()), Map.of("userId", new JsonPrimitive(120)),
                user(110, 7, "FIN", OWNER), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void outsideAFunctionDefinitionItIsRefused() {
        StepVerifier.create(run(new GetManagedClientIds(new FeignStub().service()), Map.of(),
                user(110, 7, "FIN", OWNER), false))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }
}
