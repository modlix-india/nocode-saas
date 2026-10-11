package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Which tenant an explicit clientCode on a storage call resolves to.
 *
 * leadzumptwo keeps a business partner's (CUSTOMER client's) rows in its MANAGING
 * client's database, as entity-processor did, so a partner user's server function
 * has to pass clientCode = the managing client. The gate already allows that: a
 * clientCode that manages the caller's client is accepted, as is one the caller's
 * client manages. These pin that down so it does not regress, and pin the
 * refusal for a client related in neither direction.
 */
class AppDataServiceClientCodeTest {

    /** (managing, managed) pairs the stubbed security knows about. */
    private static final Set<List<String>> MANAGES = Set.of(List.of("FIN", "BP1"), List.of("FIN", "FINSUB"));

    private final List<String> calls = new ArrayList<>();

    private AppDataService service() throws Exception {
        IFeignSecurityService security = (IFeignSecurityService) Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] { IFeignSecurityService.class },
                (proxy, method, args) -> {
                    if (!"doesClientManageClientCode".equals(method.getName()))
                        throw new UnsupportedOperationException(method.getName());
                    this.calls.add(args[0] + ">" + args[1]);
                    if ("GHOST".equals(args[0]) || "GHOST".equals(args[1]))
                        return Mono.empty(); // security answers empty for an unknown client
                    return Mono.just(MANAGES.contains(List.of((String) args[0], (String) args[1])));
                });

        AppDataService service = new AppDataService();
        set(service, "securityService", security);
        set(service, "msgService", new CoreMessageResourceService());
        return service;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = AppDataService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private Mono<String> clientCode(String clientCode, ContextAuthentication ca) throws Exception {
        Method m = AppDataService.class.getDeclaredMethod("clientCode", String.class);
        m.setAccessible(true);
        return ((Mono<String>) m.invoke(this.service(), clientCode))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));
    }

    private static ContextAuthentication caller(String clientCode, String levelType, boolean authenticated) {
        ContextAuthentication ca = new ContextAuthentication()
                .setUser(new ContextUser().setId(BigInteger.valueOf(500)).setClientId(BigInteger.valueOf(90))
                        .setStringAuthorities(List.of("Authorities.Logged_IN")))
                .setClientCode(clientCode)
                .setClientLevelType(levelType)
                .setUrlClientCode("SYSTEM")
                .setUrlAppCode("leadzumptwo");
        ca.setAuthenticated(authenticated);
        return ca;
    }

    @Test
    void aPartnerUserMayNameItsManagingClient() throws Exception {
        StepVerifier.create(this.clientCode("FIN", caller("BP1", "CUSTOMER", true)))
                .expectNext("FIN")
                .verifyComplete();

        assertEquals(List.of("FIN>BP1"), this.calls);
    }

    @Test
    void aManagingClientMayNameAClientItManages() throws Exception {
        StepVerifier.create(this.clientCode("BP1", caller("FIN", "CLIENT", true)))
                .expectNext("BP1")
                .verifyComplete();
    }

    @Test
    void noClientCodeIsTheCallersOwn() throws Exception {
        StepVerifier.create(this.clientCode("", caller("BP1", "CUSTOMER", true)))
                .expectNext("BP1")
                .verifyComplete();

        StepVerifier.create(this.clientCode(null, caller("BP1", "CUSTOMER", true)))
                .expectNext("BP1")
                .verifyComplete();

        assertEquals(List.of(), this.calls);
    }

    @Test
    void aSiblingPartnerIsRefused() throws Exception {
        // BP1 and FINSUB are both managed by FIN, but neither manages the other.
        StepVerifier.create(this.clientCode("FINSUB", caller("BP1", "CUSTOMER", true)))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void anUnknownClientIsRefusedNotEmpty() throws Exception {
        StepVerifier.create(this.clientCode("GHOST", caller("BP1", "CUSTOMER", true)))
                .expectErrorSatisfies(e -> assertEquals(HttpStatus.FORBIDDEN,
                        ((GenericException) e).getStatusCode()))
                .verify();
    }

    @Test
    void anAnonymousCallerKeepsWhatItSent() throws Exception {
        StepVerifier.create(this.clientCode("FIN", caller("SYSTEM", "SYSTEM", false)))
                .expectNext("FIN")
                .verifyComplete();

        StepVerifier.create(this.clientCode("", caller("SYSTEM", "SYSTEM", false)))
                .expectNext("SYSTEM")
                .verifyComplete();

        assertFalse(this.calls.contains("FIN>SYSTEM"));
    }
}
