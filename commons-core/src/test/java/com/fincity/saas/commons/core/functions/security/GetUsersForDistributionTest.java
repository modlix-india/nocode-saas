package com.fincity.saas.commons.core.functions.security;

import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.output;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.run;
import static com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.core.functions.security.SecurityFunctionTestSupport.FeignStub;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Mirrors BaseUserDistributionService.getUsersByRuleId: a user is in the pool when
 * they match ANY entry, and only ACTIVE app users (the ones security returns) count.
 */
class GetUsersForDistributionTest {

    private static final List<EntityProcessorUser> ACTIVE = List.of(
            new EntityProcessorUser().setId(1L).setRoleId(10L).setDesignationId(30L).setDepartmentId(40L)
                    .setProfileIds(Set.of(20L)),
            new EntityProcessorUser().setId(2L).setRoleId(11L).setDesignationId(31L).setDepartmentId(41L)
                    .setProfileIds(Set.of(21L, 22L)),
            new EntityProcessorUser().setId(3L).setRoleId(12L).setDesignationId(32L).setDepartmentId(42L)
                    .setProfileIds(Set.of()),
            new EntityProcessorUser().setId(4L).setRoleId(null).setDesignationId(null).setDepartmentId(null)
                    .setProfileIds(null),
            new EntityProcessorUser().setId(5L).setRoleId(13L).setDesignationId(33L).setDepartmentId(43L)
                    .setProfileIds(Set.of(23L)));

    private static JsonArray arr(Object... values) {
        JsonArray a = new JsonArray();
        for (Object v : values)
            a.add(v instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(v.toString()));
        return a;
    }

    private static FeignStub stubReturning(List<EntityProcessorUser> users, List<UsersListRequest> seen) {
        return new FeignStub()
                .on("getUsersForEntityProcessor", a -> {
                    seen.add((UsersListRequest) a[0]);
                    return Mono.just(users);
                })
                .on("doesClientManageClientCode", a -> Mono.just("FIN".equals(a[0]) && "BP1".equals(a[1])
                        || "BP1".equals(a[0]) && "BP1".equals(a[1])))
                .withHierarchy();
    }

    @Test
    void systemIsNeverADistributionPoolForAnotherClient() {
        var seen = new java.util.ArrayList<UsersListRequest>();

        for (var ca : List.of(user(500, 90, "BP1"), user(110, 7, "FIN"))) {
            StepVerifier.create(run(new GetUsersForDistribution(stubReturning(ACTIVE, seen).service()),
                    Map.of("roleIds", arr(10), "clientCode", new JsonPrimitive("SYSTEM")), ca, true))
                    .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                            ((GenericException) e).getStatusCode()))
                    .verify();
        }

        assertEquals(List.of(), seen);
    }

    @Test
    void eachKindOfEntryPicksItsUsersAndTheResultIsSorted() {
        var seen = new java.util.ArrayList<UsersListRequest>();
        Map<String, JsonElement> args = Map.of(
                "userIds", arr(5, 999),         // 999 is not an active app user
                "roleIds", arr("11"),
                "profileIds", arr(20),
                "designationIds", arr(32),
                "departmentIds", arr(77));

        StepVerifier.create(run(new GetUsersForDistribution(stubReturning(ACTIVE, seen).service()), args,
                user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(arr(1, 2, 3, 5), output(fo).get("userIds")))
                .verifyComplete();

        assertEquals(1, seen.size());
        assertEquals("FIN", seen.get(0).getClientCode());
        assertEquals("leadzumptwo", seen.get(0).getAppCode());
    }

    @Test
    void aProfileMatchesAnyOfAUsersProfiles() {
        var seen = new java.util.ArrayList<UsersListRequest>();
        StepVerifier.create(run(new GetUsersForDistribution(stubReturning(ACTIVE, seen).service()),
                Map.of("profileIds", arr(22)), user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(arr(2), output(fo).get("userIds")))
                .verifyComplete();
    }

    @Test
    void anEmptyDistributionIsAnEmptyPoolWithoutAskingSecurity() {
        FeignStub stub = new FeignStub();

        StepVerifier.create(run(new GetUsersForDistribution(stub.service()), Map.of(), user(110, 7, "FIN"), true))
                .assertNext(fo -> assertEquals(new JsonArray(), output(fo).get("userIds")))
                .verifyComplete();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void aPartnerMayDrawFromItsManagingClient() {
        var seen = new java.util.ArrayList<UsersListRequest>();

        StepVerifier.create(run(new GetUsersForDistribution(stubReturning(ACTIVE, seen).service()),
                Map.of("roleIds", arr(10), "clientCode", new JsonPrimitive("FIN")), user(500, 90, "BP1"), true))
                .assertNext(fo -> assertEquals(arr(1), output(fo).get("userIds")))
                .verifyComplete();

        assertEquals("FIN", seen.get(0).getClientCode());
    }

    @Test
    void anUnrelatedClientIsRefused() {
        var seen = new java.util.ArrayList<UsersListRequest>();

        StepVerifier.create(run(new GetUsersForDistribution(stubReturning(ACTIVE, seen).service()),
                Map.of("roleIds", arr(10), "clientCode", new JsonPrimitive("OTHER")), user(500, 90, "BP1"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();

        assertEquals(List.of(), seen);
    }

    @Test
    void aBadIdIsABadRequest() {
        StepVerifier.create(run(new GetUsersForDistribution(new FeignStub().service()),
                Map.of("roleIds", arr("ten")), user(110, 7, "FIN"), true))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.BAD_REQUEST,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void outsideAFunctionDefinitionItIsRefused() {
        StepVerifier.create(run(new GetUsersForDistribution(new FeignStub().service()),
                Map.of("roleIds", arr(10)), user(110, 7, "FIN"), false))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void signatureDefaultsEveryListToEmpty() {
        var params = new GetUsersForDistribution(null).getSignature().getParameters();
        for (String name : List.of("userIds", "roleIds", "profileIds", "designationIds", "departmentIds"))
            assertEquals(new JsonArray(), params.get(name).getSchema().getDefaultValue(), name);
        assertEquals(new JsonPrimitive(""), params.get("clientCode").getSchema().getDefaultValue());
    }
}
