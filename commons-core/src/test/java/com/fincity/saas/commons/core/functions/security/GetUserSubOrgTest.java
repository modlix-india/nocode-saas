package com.fincity.saas.commons.core.functions.security;

import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.anonymous;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.ids;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.output;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.run;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetUserSubOrgTest {

    private static JsonArray ints(long... values) {
        JsonArray a = new JsonArray();
        for (long v : values)
            a.add(v);
        return a;
    }

    @Test
    void signatureTakesAnOptionalUserId() {
        var sig = new GetUserSubOrg(null).getSignature();
        assertEquals("CoreServices.Security.GetUserSubOrg", sig.getFullName());
        assertEquals(new JsonPrimitive(0), sig.getParameters().get("userId").getSchema().getDefaultValue());
        assertTrue(sig.getEvents().get("output").getParameters().containsKey("userIds"));
    }

    @Test
    void noUserIdMeansTheCurrentUser() {
        FeignStub stub = new FeignStub().on("getUserSubOrgInternal", a -> {
            assertEquals(BigInteger.valueOf(110), a[0]);
            assertEquals("leadzumptwo", a[1]);
            assertEquals(BigInteger.valueOf(7), a[2]);
            return Mono.just(ids(110, 111, 112));
        });

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of(), user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(ints(110, 111, 112), output(fo).get("userIds")))
                .verifyComplete();
    }

    @Test
    void theCurrentUsersOwnIdNeedsNoLookup() {
        FeignStub stub = new FeignStub().on("getUserSubOrgInternal", a -> Mono.just(ids(110)));

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive("110")),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(ints(110), output(fo).get("userIds")))
                .verifyComplete();

        assertEquals(0, stub.count("getUserInternal"));
    }

    @Test
    void anotherUserOfTheSameClientIsAnsweredWithTheirClient() {
        FeignStub stub = new FeignStub()
                .on("getUserInternal", a -> Mono.just(new User().setId(BigInteger.valueOf(120))
                        .setClientId(BigInteger.valueOf(7))))
                .on("getUserSubOrgInternal", a -> {
                    assertEquals(BigInteger.valueOf(120), a[0]);
                    assertEquals(BigInteger.valueOf(7), a[2]);
                    return Mono.just(ids(120, 121));
                });

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(120)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(ints(120, 121), output(fo).get("userIds")))
                .verifyComplete();
    }

    @Test
    void aUserOfAManagedClientIsAllowed() {
        FeignStub stub = new FeignStub()
                .on("getUserInternal", a -> Mono.just(new User().setId(BigInteger.valueOf(500))
                        .setClientId(BigInteger.valueOf(90))))
                .on("doesClientManageClient", a -> Mono.just(
                        BigInteger.valueOf(7).equals(a[0]) && BigInteger.valueOf(90).equals(a[1])))
                .on("getUserSubOrgInternal", a -> {
                    assertEquals(BigInteger.valueOf(90), a[2]);
                    return Mono.just(ids(500));
                });

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(500)),
                user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(ints(500), output(fo).get("userIds")))
                .verifyComplete();
    }

    @Test
    void aUserOfAnUnrelatedClientIsRefused() {
        FeignStub stub = new FeignStub()
                .on("getUserInternal", a -> Mono.just(new User().setId(BigInteger.valueOf(900))
                        .setClientId(BigInteger.valueOf(55))))
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(900)),
                user(110, 7, "FIN"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();

        assertEquals(0, stub.count("getUserSubOrgInternal"));
    }

    @Test
    void aSystemUserIsRefusedEvenThoughSystemManagesTheCaller() {
        FeignStub stub = new FeignStub()
                .on("getUserInternal", a -> Mono.just(new User().setId(BigInteger.ONE)
                        .setClientId(BigInteger.ONE)))
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .withHierarchy();

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(1)),
                user(500, 90, "BP1"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();

        assertEquals(0, stub.count("getUserSubOrgInternal"));
    }

    @Test
    void aPartnerMayReadItsManagingClientsUser() {
        FeignStub stub = new FeignStub()
                .on("getUserInternal", a -> Mono.just(new User().setId(BigInteger.valueOf(110))
                        .setClientId(BigInteger.valueOf(7))))
                .on("doesClientManageClient", a -> Mono.just(Boolean.FALSE))
                .on("getUserSubOrgInternal", a -> Mono.just(ids(110, 111)))
                .withHierarchy();

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(110)),
                user(500, 90, "BP1"), true))
                .assertNext(fo -> assertEquals(ints(110, 111), output(fo).get("userIds")))
                .verifyComplete();
    }

    @Test
    void anUnknownUserIsABadRequest() {
        FeignStub stub = new FeignStub().on("getUserInternal", a -> Mono.empty());

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of("userId", new JsonPrimitive(901)),
                user(110, 7, "FIN"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.BAD_REQUEST,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void aNonNumericUserIdIsABadRequest() {
        StepVerifier.create(run(new GetUserSubOrg(new FeignStub().service()),
                Map.of("userId", new JsonPrimitive("abc")), user(110, 7, "FIN"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.BAD_REQUEST,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void anAnonymousCallerHasNoSubOrg() {
        FeignStub stub = new FeignStub();

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), Map.of(), anonymous(7, "FIN"), true))
                .assertNext(fo -> assertEquals(new JsonArray(), output(fo).get("userIds")))
                .verifyComplete();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void outsideAFunctionDefinitionItIsRefused() {
        FeignStub stub = new FeignStub();
        Map<String, JsonElement> args = Map.of();

        StepVerifier.create(run(new GetUserSubOrg(stub.service()), args, user(110, 7, "FIN"), false))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();

        assertEquals(List.of(), stub.calls);
    }
}
