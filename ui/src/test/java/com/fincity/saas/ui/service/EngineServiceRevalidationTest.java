package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;

import reactor.core.publisher.Mono;

/**
 * QA-0125: after clearing the token and a hard refresh, a logged-in page stayed
 * open. Page and application definitions were served with a seven day lifetime,
 * so the browser reused the logged-in definition without asking. They now
 * revalidate on every use, and the ETag still answers with a 304.
 *
 * The cache is an in-memory stand-in for CacheService, keyed exactly as the
 * service keys it, so the login dimension of the keys is under test too.
 */
class EngineServiceRevalidationTest {

    private static final String APP = "testapp";
    private static final String CLIENT = "SYSTEM";

    private ApplicationService appService;
    private PageService pageService;
    private EngineService engine;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {

        this.appService = mock(ApplicationService.class);
        this.pageService = mock(PageService.class);
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);
        when(security.getAppStatusByCode(anyString())).thenReturn(Mono.just("ACTIVE"));

        CacheService cacheService = mock(CacheService.class);
        when(cacheService.put(anyString(), any(), any(Object[].class))).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            this.cache.put(key(args[0], Arrays.copyOfRange(args, 2, args.length)), args[1]);
            return Mono.just(args[1]);
        });
        when(cacheService.cacheValueOrGet(anyString(), any(), any(Object[].class))).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            String k = key(args[0], Arrays.copyOfRange(args, 2, args.length));
            Object hit = this.cache.get(k);
            if (hit != null)
                return Mono.just(hit);
            @SuppressWarnings("unchecked")
            Supplier<Mono<Object>> supplier = (Supplier<Mono<Object>>) args[1];
            return supplier.get().doOnNext(v -> this.cache.put(k, v));
        });

        this.engine = new EngineService(this.appService, this.pageService, null, null, security, cacheService);
        ReflectionTestUtils.setField(this.engine, "cacheAge", 604800);
    }

    private static String key(Object cacheName, Object[] keys) {
        return cacheName + ":" + String.join(":", Arrays.stream(keys).map(String::valueOf).toList());
    }

    private static ContextAuthentication auth(boolean authenticated) {

        ContextUser user = new ContextUser();
        user.setId(BigInteger.ONE);
        user.setClientId(BigInteger.ONE);
        user.setUserName("tester");
        user.setStringAuthorities(List.of());

        ContextAuthentication ca = new ContextAuthentication()
                .setUser(user)
                .setClientCode(CLIENT)
                .setUrlClientCode(CLIENT)
                .setUrlAppCode(APP);
        ca.setAuthenticated(authenticated);
        return ca;
    }

    private static <T> T as(boolean authenticated, Mono<T> mono) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(authenticated))).block();
    }

    /** Logged in: the page itself. Anonymous: the login page, as PageService.read does for a protected page. */
    private void protectedPage() {
        when(this.pageService.read("campaigns", APP, CLIENT)).thenAnswer(inv -> SecurityContextUtil
                .getUsersContextAuthentication()
                .map(ca -> ca.isAuthenticated()
                        ? new ObjectWithUniqueID<>(new Page().setName("campaigns"), "PAGEUID")
                        : new ObjectWithUniqueID<>(new Page().setName("login"), "LOGINUID")));
    }

    /** The shell inlined into the app changes with the login, and so does the uniqueId. */
    private void loginDependentApp() {
        when(this.appService.read(APP, APP, CLIENT)).thenAnswer(inv -> SecurityContextUtil
                .getUsersContextAuthentication()
                .map(ca -> {
                    Application app = new Application();
                    app.setName(ca.isAuthenticated() ? "withShell" : "withForbidden");
                    return new ObjectWithUniqueID<>(app, ca.isAuthenticated() ? "APPSHELL" : "APPFORBIDDEN");
                }));
    }

    @Test
    @DisplayName("a page is served no-cache with its login-specific ETag")
    void pageNoCache() {

        protectedPage();

        ResponseEntity<Page> r = as(true, this.engine.readPage(null, "campaigns", APP, CLIENT));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
        assertEquals("W/lg-PAGEUID", r.getHeaders().getFirst("ETag"));
    }

    @Test
    @DisplayName("revalidating an unchanged page is still a 304")
    void pageNotModified() {

        protectedPage();
        String eTag = as(true, this.engine.readPage(null, "campaigns", APP, CLIENT)).getHeaders().getFirst("ETag");

        ResponseEntity<Page> r = as(true, this.engine.readPage(eTag, "campaigns", APP, CLIENT));

        assertEquals(HttpStatus.NOT_MODIFIED, r.getStatusCode());
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @DisplayName("the logged-in ETag revalidated without a token gets the login page, not a 304")
    void pageAfterLogout() {

        protectedPage();
        String eTag = as(true, this.engine.readPage(null, "campaigns", APP, CLIENT)).getHeaders().getFirst("ETag");

        ResponseEntity<Page> r = as(false, this.engine.readPage(eTag, "campaigns", APP, CLIENT));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("login", r.getBody().getName());
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @DisplayName("the application is served no-cache and still answers 304 when unchanged")
    void applicationNoCache() {

        loginDependentApp();

        ResponseEntity<Application> r = as(true, this.engine.readApplication(null, APP, CLIENT));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
        assertEquals("W/APPSHELL", r.getHeaders().getFirst("ETag"));
        assertEquals(CLIENT, r.getBody().getUrlClientCode());

        ResponseEntity<Application> nm = as(true,
                this.engine.readApplication(r.getHeaders().getFirst("ETag"), APP, CLIENT));
        assertEquals(HttpStatus.NOT_MODIFIED, nm.getStatusCode());
    }

    @Test
    @DisplayName("the logged-in application ETag revalidated without a token is not served from the logged-in cache entry")
    void applicationAfterLogout() {

        loginDependentApp();
        String eTag = as(true, this.engine.readApplication(null, APP, CLIENT)).getHeaders().getFirst("ETag");

        ResponseEntity<Application> r = as(false, this.engine.readApplication(eTag, APP, CLIENT));

        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "the cache entry stored for the logged-in read answered the anonymous one");
        assertEquals("withForbidden", r.getBody().getName());
        assertEquals("W/APPFORBIDDEN", r.getHeaders().getFirst("ETag"));
    }

    @Test
    @DisplayName("an application read through the ETag branch on a cache miss still carries urlClientCode")
    void applicationETagMissKeepsUrlClientCode() {

        loginDependentApp();

        ResponseEntity<Application> r = as(true, this.engine.readApplication("W/STALE", APP, CLIENT));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(CLIENT, r.getBody().getUrlClientCode());
        assertTrue(r.getHeaders().getFirst("ETag").contains("APPSHELL"));
    }
}
