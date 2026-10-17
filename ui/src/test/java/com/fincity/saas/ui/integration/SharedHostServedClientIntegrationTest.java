package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.service.EngineService;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * What a signed-in user is served on a shared host.
 *
 * The browser names no client, so the gateway sends the HOST's client (SYSTEM on
 * leadzumptwo's host). The read used to be for that client alone: a tenant's own
 * published page never reached its users. These requests are shaped the way the
 * browser's are: clientCode = the host, urlClientCode = the host, the token is the
 * tenant user's. Hierarchy: SYSTEM hosts and owns the app, MID is managed by SYSTEM,
 * LEAF by MID.
 */
@DisplayName("Engine reads on a shared host follow the signed-in client")
class SharedHostServedClientIntegrationTest extends AbstractIntegrationTest {

    private static final String MID = "LZMID";
    private static final String LEAF = "LZLEAF";
    private static final String PAGE_NAME = "sharedHostPage";

    @Autowired
    private EngineService engineService;

    @Autowired
    private PageService pageService;

    @BeforeEach
    void hierarchy() {
        // App inheritance as the platform builds it: [owner, url client, client], no repeats.
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(inv -> {
                    List<String> out = new ArrayList<>();
                    for (String c : new String[] { SYSTEM, inv.getArgument(1), inv.getArgument(2) })
                        if (c != null && !out.contains(c))
                            out.add(c);
                    return Mono.just(out);
                });
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(SYSTEM))
                .thenReturn(Mono.just(List.of(SYSTEM)));
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(MID))
                .thenReturn(Mono.just(List.of(MID, SYSTEM)));
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(LEAF))
                .thenReturn(Mono.just(List.of(LEAF, MID, SYSTEM)));
        Mockito.when(this.feignAuthenticationService.hasReadAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
    }

    private Page page(String clientCode, String baseClientCode, String label) {
        ComponentDefinition btn = new ComponentDefinition();
        btn.setKey("btn");
        btn.setName("btn");
        btn.setType("Button");
        btn.setProperties(new HashMap<>(Map.of("label", label)));

        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(clientCode).setVersion(1);
        page.setBaseClientCode(baseClientCode);
        page.setComponentDefinition(new HashMap<>(Map.of("btn", btn)));
        page.setRootComponent("btn");
        return this.insertRaw(page);
    }

    /** A user of `clientCode` on the host `host`, the way JWTTokenFilter builds it. */
    private ContextAuthentication user(String clientCode, String host) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        ca.setUrlClientCode(host);
        return ca;
    }

    private ContextAuthentication anonymous(String host) {
        ContextAuthentication ca = this.user(SYSTEM, host);
        ca.setAuthenticated(false);
        return ca;
    }

    /** GET api/ui/page/{name} as the browser sends it: clientCode is the host. */
    private ResponseEntity<Page> engineRead(ContextAuthentication ca, String host, String eTag) {
        return this.engineService.readPage(eTag, PAGE_NAME, APP_CODE, host)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
                .block();
    }

    private static Object label(ResponseEntity<Page> r) {
        return r.getBody().getComponentDefinition().get("btn").getProperties().get("label");
    }

    @Test
    @Timeout(30)
    @DisplayName("a tenant's user on the shared host gets the tenant's own published page")
    void tenantGetsItsOwnPage() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        assertEquals("MID label", label(engineRead(user(MID, SYSTEM), SYSTEM, null)));
    }

    @Test
    @Timeout(30)
    @DisplayName("anonymous visitors and the host's own users still get the host's page")
    void hostAndAnonymousUnchanged() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        assertEquals("Submit", label(engineRead(anonymous(SYSTEM), SYSTEM, null)));
        assertEquals("Submit", label(engineRead(user(SYSTEM, SYSTEM), SYSTEM, null)));
    }

    @Test
    @Timeout(30)
    @DisplayName("a tenant with no copy of its own gets its managing client's")
    void leafGetsItsManagersCopy() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        assertEquals("MID label", label(engineRead(user(LEAF, SYSTEM), SYSTEM, null)));
    }

    @Test
    @Timeout(30)
    @DisplayName("a tenant without read access to the app is served the host's page")
    void noReadAccessGetsHost() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");
        Mockito.when(this.feignAuthenticationService.hasReadAccess(APP_CODE, MID))
                .thenReturn(Mono.just(Boolean.FALSE));

        assertEquals("Submit", label(engineRead(user(MID, SYSTEM), SYSTEM, null)));
    }

    @Test
    @Timeout(30)
    @DisplayName("on a client's own site, a user of a client above it still sees that client's site")
    void clientsOwnSiteUnchanged() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        assertEquals("MID label", label(engineRead(user(SYSTEM, MID), MID, null)));
    }

    @Test
    @Timeout(30)
    @DisplayName("the host's cached page never answers for a tenant, with or without the host's eTag")
    void hostCacheNeverAnswersForTenant() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        ResponseEntity<Page> host = engineRead(user(SYSTEM, SYSTEM), SYSTEM, null);
        assertEquals("Submit", label(host));
        String hostETag = host.getHeaders().getETag();

        // The browser still holds the host's eTag from before the tenant signed in.
        ResponseEntity<Page> tenant = engineRead(user(MID, SYSTEM), SYSTEM, hostETag);
        assertEquals(200, tenant.getStatusCode().value());
        assertEquals("MID label", label(tenant));
        assertEquals("Authorization, clientCode, appCode", tenant.getHeaders().getFirst("Vary"));
    }

    @Test
    @Timeout(30)
    @DisplayName("every overridable read follows it, not only the engine (the service read itself)")
    void serviceReadFollowsSignedInClient() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        Page read = this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(user(MID, SYSTEM)))
                .block()
                .getObject();
        assertEquals("MID label", read.getComponentDefinition().get("btn").getProperties().get("label"));
    }

    @Test
    @Timeout(30)
    @DisplayName("a client named on purpose (not the host) is read as named")
    void explicitClientUnchanged() {
        page(SYSTEM, null, "Submit");
        page(MID, SYSTEM, "MID label");

        Page read = this.pageService.read(PAGE_NAME, APP_CODE, MID)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(user(SYSTEM, SYSTEM)))
                .block()
                .getObject();
        assertEquals("MID label", read.getComponentDefinition().get("btn").getProperties().get("label"));
    }
}
