package com.fincity.saas.commons.security.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Hierarchy used throughout: SYSTEM(1) manages FIN(28) manages BP1(90) manages SUB(91); OTHER(55)
 * is a sibling of FIN under SYSTEM. FIN has write access to leadzumptwo; SYSTEM has write access to everything.
 */
class ClientRelationUtilTest {

    private static final BigInteger SYSTEM = BigInteger.ONE;
    private static final BigInteger FIN = BigInteger.valueOf(28);
    private static final BigInteger BP1 = BigInteger.valueOf(90);
    private static final BigInteger OTHER = BigInteger.valueOf(55);
    private static final BigInteger SUB = BigInteger.valueOf(91);

    private static final Map<BigInteger, Client> CLIENTS = Map.of(
            SYSTEM, new Client().setId(SYSTEM).setCode("SYSTEM").setTypeCode("SYS"),
            FIN, new Client().setId(FIN).setCode("FIN").setTypeCode("BUS"),
            BP1, new Client().setId(BP1).setCode("BP1").setTypeCode("BUS"),
            OTHER, new Client().setId(OTHER).setCode("OTHER").setTypeCode("BUS"),
            SUB, new Client().setId(SUB).setCode("SUB").setTypeCode("BUS"));

    private static final Map<BigInteger, List<BigInteger>> CHAINS = Map.of(
            SYSTEM, List.of(SYSTEM),
            FIN, List.of(FIN, SYSTEM),
            BP1, List.of(BP1, FIN, SYSTEM),
            OTHER, List.of(OTHER, SYSTEM),
            SUB, List.of(SUB, BP1, FIN, SYSTEM));

    private static Client byCode(String code) {
        return CLIENTS.values().stream().filter(c -> c.getCode().equals(code)).findFirst().orElse(null);
    }

    private static boolean manages(BigInteger managing, BigInteger client) {
        return CHAINS.getOrDefault(client, List.of()).contains(managing);
    }

    private final List<String> calls = new ArrayList<>();

    private IFeignSecurityService stub() {
        Map<String, Function<Object[], Object>> answers = new HashMap<>();
        answers.put("doesClientManageClient", a -> Mono.just(manages((BigInteger) a[0], (BigInteger) a[1])));
        answers.put("doesClientManageClientCode",
                a -> Mono.just(manages(byCode((String) a[0]).getId(), byCode((String) a[1]).getId())));
        answers.put("getClientHierarchy", a -> Mono.justOrEmpty(CHAINS.get((BigInteger) a[0])));
        answers.put("getClientById", a -> Mono.justOrEmpty(CLIENTS.get((BigInteger) a[0])));
        answers.put("getClientByCode", a -> Mono.justOrEmpty(byCode((String) a[0])));
        answers.put("hasWriteAccess", a -> Mono.just("SYSTEM".equals(a[1])
                || ("leadzumptwo".equals(a[0]) && "FIN".equals(a[1]))));

        return (IFeignSecurityService) Proxy.newProxyInstance(IFeignSecurityService.class.getClassLoader(),
                new Class<?>[] { IFeignSecurityService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                        return method.invoke(this, args);
                    this.calls.add(method.getName() + Arrays.toString(args));
                    Function<Object[], Object> answer = answers.get(method.getName());
                    if (answer == null)
                        throw new UnsupportedOperationException("unexpected call " + method.getName());
                    return answer.apply(args);
                });
    }

    private static void expect(Mono<Boolean> mono, boolean expected) {
        StepVerifier.create(mono).expectNext(expected).verifyComplete();
    }

    @Test
    void ownClientIsRelatedWithoutAskingSecurity() {
        expect(ClientRelationUtil.isRelatedClient(stub(), FIN, FIN, "leadzumptwo"), true);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "FIN", "FIN", null), true);
        assertEquals(List.of(), this.calls);
    }

    @Test
    void downwardIsUnchangedAtAnyDepth() {
        expect(ClientRelationUtil.isRelatedClient(stub(), FIN, BP1, null), true);
        expect(ClientRelationUtil.isRelatedClient(stub(), SYSTEM, BP1, null), true);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "SYSTEM", "FIN", ""), true);
    }

    @Test
    void aPartnerReachesTheManagingClientThatHoldsTheApp() {
        expect(ClientRelationUtil.isRelatedClient(stub(), BP1, FIN, "leadzumptwo"), true);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "BP1", "FIN", "leadzumptwo"), true);
    }

    @Test
    void aPartnerDoesNotReachTheManagingClientForAnAppItCannotEdit() {
        expect(ClientRelationUtil.isRelatedClient(stub(), BP1, FIN, "someOtherApp"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "BP1", "FIN", "someOtherApp"), false);
    }

    @Test
    void noClientReachesSystemUpward() {
        // SYSTEM has write access to every app, and still is never reachable upward.
        expect(ClientRelationUtil.isRelatedClient(stub(), FIN, SYSTEM, "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClient(stub(), BP1, SYSTEM, "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "FIN", "SYSTEM", "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "OTHER", "SYSTEM", "anything"), false);
    }

    @Test
    void aBlankAppNeverAllowsUpward() {
        expect(ClientRelationUtil.isRelatedClient(stub(), BP1, FIN, null), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "BP1", "FIN", " "), false);
        assertFalse(this.calls.stream().anyMatch(c -> c.startsWith("hasWriteAccess")));
    }

    @Test
    void siblingsAreUnrelated() {
        expect(ClientRelationUtil.isRelatedClient(stub(), FIN, OTHER, "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "OTHER", "FIN", "leadzumptwo"), false);
        assertTrue(this.calls.stream().noneMatch(c -> c.startsWith("hasWriteAccess")));
    }

    @Test
    void unknownOrMissingClientsAreUnrelated() {
        expect(ClientRelationUtil.isRelatedClient(stub(), null, FIN, "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "FIN", null, "leadzumptwo"), false);
    }

    @Test
    void upwardStopsAtTheDirectManagingClient() {
        // FIN has write access to leadzumptwo but is SUB's grandparent, not its direct manager.
        expect(ClientRelationUtil.isRelatedClient(stub(), SUB, FIN, "leadzumptwo"), false);
        expect(ClientRelationUtil.isRelatedClientCode(stub(), "SUB", "FIN", "leadzumptwo"), false);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "SUB", "FIN", "leadzumptwo"), false);
    }

    @Test
    void actingAsATenantIsOwnOrDirectManagerOnly() {
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "FIN", "FIN", null), true);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "BP1", "FIN", "leadzumptwo"), true);
        // Managing a client is not speaking as it.
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "FIN", "BP1", "leadzumptwo"), false);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "SYSTEM", "FIN", "leadzumptwo"), false);
        // Never SYSTEM, even as the direct manager.
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "FIN", "SYSTEM", "leadzumptwo"), false);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "BP1", "FIN", "someOtherApp"), false);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "BP1", "FIN", " "), false);
        expect(ClientRelationUtil.isSelfOrDirectManagingClientCode(stub(), "OTHER", "FIN", "leadzumptwo"), false);
    }
}
