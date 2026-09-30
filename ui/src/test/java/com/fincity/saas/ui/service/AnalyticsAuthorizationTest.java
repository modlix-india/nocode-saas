package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * These exist because the endpoint was open on production.
 *
 * On 2026-09-30, with no token at all:
 *
 * <pre>
 * curl -X POST https://modlix.com/api/ui/analytics/query \
 *      -H 'appCode: leadzump' -H 'clientCode: SYSTEM' -d '{"widget":"topPages"}'
 *   -> 200, another tenant's page paths, event counts and visitor numbers
 * </pre>
 *
 * Any host served any tenant. Three faults lined up: nothing asked whether the caller was
 * authenticated; {@code hasWriteAccess} was called with the TARGET client rather than the
 * caller's, so it answered a question nobody asked; and the scope test reduced to "does SYSTEM
 * manage SYSTEM", which is trivially true for every app under SYSTEM.
 *
 * Each test below is one of those faults. The engine mock is asserted to be untouched on every
 * refusal, because a check that throws after the data has been fetched is not a check.
 */
class AnalyticsAuthorizationTest {

    private static final String APP = "leadzump";
    private static final String SYSTEM = "SYSTEM";
    private static final String OTHER = "OTHERCO";
    private static final String APPLICATION_UPDATE = "Authorities.Application_UPDATE";

    /** Mirrors AbstractIntegrationTest.authFor; setAuthenticated returns void so it cannot chain. */
    private static ContextAuthentication authFor(String clientCode, boolean authenticated, String... authorities) {

        ContextUser user = new ContextUser();
        user.setId(BigInteger.ONE);
        user.setClientId(BigInteger.ONE);
        user.setUserName("test-" + clientCode);
        user.setStringAuthorities(List.of(authorities));

        ContextAuthentication ca = new ContextAuthentication()
                .setUser(user)
                .setLoggedInFromClientId(BigInteger.ONE)
                .setLoggedInFromClientCode(clientCode)
                .setClientTypeCode(ContextAuthentication.CLIENT_TYPE_SYSTEM)
                .setClientCode(clientCode)
                .setAccessToken("test-token")
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP);
        ca.setAuthenticated(authenticated);
        return ca;
    }

    private static AnalyticsService service(FeignAuthenticationService security) {
        AnalyticsService s = new AnalyticsService(null, null, null, new UIMessageResourceService(), security);
        // engineClient is built in @PostConstruct from config, and a null one means "analytics is
        // not configured" - a 503 raised BEFORE any authorization runs. Without this the whole
        // suite passes for the wrong reason: every case errors, but on the configured check
        // rather than on the check under test.
        try {
            java.lang.reflect.Field f = AnalyticsService.class.getDeclaredField("engineClient");
            f.setAccessible(true);
            f.set(s, org.springframework.web.reactive.function.client.WebClient.create("http://engine.invalid"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not stub engineClient", e);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Mono<ContextAuthentication> check(AnalyticsService service, ContextAuthentication ca,
            String clientCode) throws Exception {
        Method m = AnalyticsService.class.getDeclaredMethod("checkConfiguredAndAuthorized", String.class,
                String.class);
        m.setAccessible(true);
        return ((Mono<ContextAuthentication>) m.invoke(service, APP, clientCode))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));
    }

    private static void expectStatus(Mono<?> mono, HttpStatus expected) {
        StepVerifier.create(mono)
                .expectErrorSatisfies(e -> {
                    GenericException ge = assertInstanceOf(GenericException.class, e);
                    assertEquals(expected, ge.getStatusCode());
                })
                .verify();
    }

    @Test
    @DisplayName("an anonymous caller is refused, and security is never consulted")
    void anonymousIsRefused() throws Exception {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);

        // Anonymous, but carrying the host's client code - which is exactly the shape that used
        // to pass, because the client then "manages itself".
        ContextAuthentication ca = authFor(SYSTEM, false, "Authorities._Anonymous");

        expectStatus(check(service(security), ca, SYSTEM), HttpStatus.FORBIDDEN);

        verify(security, never()).hasWriteAccess(anyString(), anyString());
        verify(security, never()).doesClientManageClientCode(anyString(), anyString());
    }

    @Test
    @DisplayName("authenticated but without Application_UPDATE is refused")
    void authenticatedWithoutTheAuthorityIsRefused() throws Exception {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);

        ContextAuthentication ca = authFor(SYSTEM, true, "Authorities.Logged_IN");

        expectStatus(check(service(security), ca, SYSTEM), HttpStatus.FORBIDDEN);

        verify(security, never()).hasWriteAccess(anyString(), anyString());
    }

    @Test
    @DisplayName("hasWriteAccess is asked about the CALLER's client, not the target's")
    void writeAccessIsCheckedForTheCaller() throws Exception {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);
        when(security.hasWriteAccess(anyString(), anyString())).thenReturn(Mono.just(false));

        ContextAuthentication ca = authFor(OTHER, true, APPLICATION_UPDATE);

        // Refused because hasWriteAccess says no; what matters is the argument it was given.
        expectStatus(check(service(security), ca, SYSTEM), HttpStatus.FORBIDDEN);

        // The bug: this used to be (APP, SYSTEM) - the client being READ, which says nothing
        // about whether the caller may read it.
        verify(security).hasWriteAccess(APP, OTHER);
    }

    @Test
    @DisplayName("a client that does not manage the target is refused")
    void unmanagedTargetIsRefused() throws Exception {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);
        when(security.hasWriteAccess(anyString(), anyString())).thenReturn(Mono.just(true));
        when(security.doesClientManageClientCode(OTHER, SYSTEM)).thenReturn(Mono.just(false));

        ContextAuthentication ca = authFor(OTHER, true, APPLICATION_UPDATE);

        expectStatus(check(service(security), ca, SYSTEM), HttpStatus.FORBIDDEN);

        verify(security).doesClientManageClientCode(OTHER, SYSTEM);
    }

    @Test
    @DisplayName("reading your own client does not need a management lookup")
    void ownClientSkipsTheManagementCall() throws Exception {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);
        when(security.hasWriteAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        ContextAuthentication ca = authFor(SYSTEM, true, APPLICATION_UPDATE);

        // It still refuses, on the analytics-enabled check further down, because appService is
        // null here. The point of this test is the call that must NOT happen.
        StepVerifier.create(check(service(security), ca, SYSTEM)).expectError().verify();

        verify(security, never()).doesClientManageClientCode(anyString(), anyString());
    }
}
