package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * A client with WRITE access to an app it does not own, editing that app's
 * objects.
 *
 * The app is owned by SYSTEM; TENANT holds security_app_access.EDIT_ACCESS = 1.
 * Before this, every edit 403d: saveDraft authorised the STORED document's client
 * code, so TENANT editing SYSTEM's page fell through to
 * doesClientManageClientCode(TENANT, SYSTEM), which is false, and nothing created
 * a row for TENANT to edit instead.
 *
 * The shape now: the first draft save forks the object to TENANT as an override
 * that has never gone live, and attaches the draft to that. TENANT's own runtime
 * keeps serving SYSTEM's version until TENANT publishes, and SYSTEM's document is
 * never touched at any point.
 */
@DisplayName("Cross-client fork on first draft save")
class CrossClientForkIntegrationTest extends AbstractIntegrationTest {

    private static final String TENANT = "LZCLB";
    private static final String MID = "LZMID";
    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    private Page storedPage(String clientCode, String baseClientCode, Map<String, Object> properties,
            String rootComponent) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
                .setVersion(1);
        page.setProperties(properties == null ? null : new HashMap<>(properties));
        page.setRootComponent(rootComponent);
        return this.insertRaw(page);
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2)
            map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    /**
     * The tenant is NOT managed by anyone and manages no one. The base class stubs
     * doesClientManageClientCode permissively, which would let the tenant through
     * the ordinary manager path and make every assertion here pass for the wrong
     * reason.
     */
    private void tenantWithWriteAccessOnly() {
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
        Mockito.when(this.feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
    }

    private Page tenantEdit(Page basePage, String title) {
        Page edit = asClient(pageService.read(basePage.getId()), TENANT);
        assertNotNull(edit);
        if (edit.getProperties() == null)
            edit.setProperties(new HashMap<>());
        edit.getProperties().put("title", title);
        return edit;
    }

    private boolean refused(Runnable action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private List<Page> rawPages() {
        return findAllRaw(Page.class).collectList().block();
    }

    private Page rawFor(String clientCode) {
        return rawPages().stream().filter(p -> clientCode.equals(p.getClientCode())).findFirst().orElse(null);
    }

    @Nested
    @DisplayName("the first draft save")
    class FirstSave {

        @Test
        @Timeout(30)
        @DisplayName("creates an override for the editing client")
        void createsOverride() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            Draft draft = asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            assertNotNull(draft, "a client with write access could not edit the app it was granted");
            assertEquals(TENANT, draft.getClientCode(), "the draft was attached to the wrong client");

            Page fork = rawFor(TENANT);
            assertNotNull(fork, "no override row was created");
            assertEquals(SYSTEM, fork.getBaseClientCode(), "the fork is not based on the owner's row");
            assertEquals(Boolean.FALSE, fork.getPublished(), "the fork must not be live before publish");
        }

        @Test
        @Timeout(30)
        @DisplayName("answers with the fork's id, not the id it was sent to")
        void answersWithForkId() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            Draft draft = asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            assertNotNull(draft);
            assertNotEquals(basePage.getId(), draft.getObjectId(),
                    "the editor cannot learn it was forked, so it keeps saving to the base's id forever");
            assertEquals(rawFor(TENANT).getId(), draft.getObjectId());
        }

        /**
         * The fork must be a DELTA, not a copy. A copy passes every other assertion
         * in this class and only diverges later, the first time the owner changes
         * something the tenant never touched.
         */
        @Test
        @Timeout(30)
        @DisplayName("stores a delta, so the owner's later changes still flow through")
        void storesADelta() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            Page fork = rawFor(TENANT);
            assertNull(fork.getRootComponent(),
                    "rootComponent is identical on both, so a real delta nulls it; a copy carries it");

            // The owner changes something the tenant never touched.
            Page base = mongoTemplate.findById(basePage.getId(), Page.class).block();
            base.setRootComponent("newRootFromOwner");
            mongoTemplate.save(base).block();

            Page merged = asClient(pageService.read(fork.getId()), TENANT);
            assertEquals("newRootFromOwner", merged.getRootComponent(),
                    "the fork froze a snapshot of the owner and stopped inheriting");
        }
    }

    @Nested
    @DisplayName("surfaces")
    class Surfaces {

        @Test
        @Timeout(30)
        @DisplayName("the tenant's LIVE runtime still serves the owner's version")
        void liveUnchanged() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            var live = asClient(pageService.read(PAGE_NAME, APP_CODE, TENANT), TENANT);
            assertNotNull(live);
            assertEquals("system live", live.getObject().getProperties().get("title"),
                    "an unpublished fork leaked onto the tenant's live surface");
        }

        @Test
        @Timeout(30)
        @DisplayName("the tenant's DRAFT surface serves the draft")
        void draftSurfaceServesIt() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            var drafted = onDraftSurface(pageService.read(PAGE_NAME, APP_CODE, TENANT)
                    .contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(authFor(TENANT, allAuthoritiesFor("Page")))))
                    .block();

            assertNotNull(drafted);
            assertEquals("tenant draft", drafted.getObject().getProperties().get("title"),
                    "the tenant's own draft is not visible on its own draft surface");
        }

        @Test
        @Timeout(30)
        @DisplayName("the owner's document is untouched, content and version")
        void ownerUntouched() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);

            Page base = mongoTemplate.findById(basePage.getId(), Page.class).block();
            assertEquals("system live", base.getProperties().get("title"));
            assertEquals(1, base.getVersion(), "the owner's version moved, so something wrote to it");
        }
    }

    @Nested
    @DisplayName("idempotence")
    class Idempotence {

        @Test
        @Timeout(30)
        @DisplayName("a second edit reuses the fork rather than making another")
        void secondEditReusesFork() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            Draft first = asClient(pageService.saveDraft(tenantEdit(basePage, "one")), TENANT);
            Page fork = rawFor(TENANT);

            Page second = asClient(pageService.read(fork.getId()), TENANT);
            second.getProperties().put("title", "two");
            Draft again = asClient(pageService.saveDraft(second), TENANT);

            assertEquals(first.getObjectId(), again.getObjectId());
            assertEquals(1L, rawPages().stream().filter(p -> TENANT.equals(p.getClientCode())).count(),
                    "a second fork row was created");
        }

        /**
         * The editor keeps the base's id in its URL until it is told otherwise, and
         * gets it again on every reload. Detecting "already forked" by id rather
         * than by client code would fork once per save.
         */
        @Test
        @Timeout(30)
        @DisplayName("a stale ancestor id still lands on the fork")
        void staleAncestorIdLandsOnFork() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "one")), TENANT);

            // Save again against the BASE's id, as a stale editor would.
            Draft again = asClient(pageService.saveDraft(tenantEdit(basePage, "two")), TENANT);

            assertEquals(rawFor(TENANT).getId(), again.getObjectId());
            assertEquals(1L, rawPages().stream().filter(p -> TENANT.equals(p.getClientCode())).count(),
                    "a stale id forked a second time");
        }
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @Timeout(30)
        @DisplayName("promotes the tenant's own row and leaves the owner alone")
        void publishesTheFork() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);
            Page fork = rawFor(TENANT);

            asClient(pageService.publish(fork.getId(), "ship"), TENANT);

            Page published = mongoTemplate.findById(fork.getId(), Page.class).block();
            assertEquals(Boolean.TRUE, published.getPublished(), "publish did not make the fork live");

            Page base = mongoTemplate.findById(basePage.getId(), Page.class).block();
            assertEquals("system live", base.getProperties().get("title"), "publish wrote into the owner's row");
            assertEquals(1, base.getVersion());

            Long drafts = mongoTemplate.findAll(Draft.class).count().block();
            assertEquals(0L, drafts, "the draft should be gone after publish");
        }

        @Test
        @Timeout(30)
        @DisplayName("makes it live for the tenant and nobody else")
        void liveForTenantOnly() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            tenantWithWriteAccessOnly();

            asClient(pageService.saveDraft(tenantEdit(basePage, "tenant draft")), TENANT);
            asClient(pageService.publish(rawFor(TENANT).getId(), "ship"), TENANT);

            var tenantLive = asClient(pageService.read(PAGE_NAME, APP_CODE, TENANT), TENANT);
            assertEquals("tenant draft", tenantLive.getObject().getProperties().get("title"));

            setInheritance(List.of(SYSTEM));
            var ownerLive = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertEquals("system live", ownerLive.getObject().getProperties().get("title"),
                    "the tenant's publish changed what the owner sees");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @Timeout(30)
        @DisplayName("no write access to the app means no fork")
        void noWriteAccessNoFork() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            Page edit = tenantEdit(basePage, "tenant draft");

            Mockito.when(feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                    Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
            Mockito.when(feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            assertTrue(refused(() -> asClient(pageService.saveDraft(edit), TENANT)));
            assertNull(rawFor(TENANT), "a refusal that creates a row is not a refusal");
        }

        /**
         * The anti-forgery gate. Write access to SOME app must not be a licence to
         * fork any document whose id you can guess.
         */
        @Test
        @Timeout(30)
        @DisplayName("an id outside the caller's own chain is refused")
        void outsideChainRefused() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            Page edit = tenantEdit(basePage, "tenant draft");

            tenantWithWriteAccessOnly();
            // The caller's own resolution of this app does not include the owner.
            setInheritance(List.of(TENANT));

            assertTrue(refused(() -> asClient(pageService.saveDraft(edit), TENANT)));
            assertNull(rawFor(TENANT), "an id from outside the chain forked anyway");
        }

        @Test
        @Timeout(30)
        @DisplayName("a notOverridable object is refused")
        void notOverridableRefused() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            Page edit = tenantEdit(basePage, "tenant draft");

            basePage.setNotOverridable(Boolean.TRUE);
            mongoTemplate.save(basePage).block();
            tenantWithWriteAccessOnly();

            assertTrue(refused(() -> asClient(pageService.saveDraft(edit), TENANT)));
            assertNull(rawFor(TENANT), "an object marked not overridable was overridden");
        }

        /**
         * getMergedSources fetches the base regardless of `published`, so forking off
         * an unpublished owner row and publishing would put the OWNER's unfinished
         * work on the tenant's live surface.
         */
        @Test
        @Timeout(30)
        @DisplayName("an unpublished base is refused")
        void unpublishedBaseRefused() {

            setInheritance(List.of(SYSTEM, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            Page edit = tenantEdit(basePage, "tenant draft");

            basePage.setPublished(Boolean.FALSE);
            mongoTemplate.save(basePage).block();
            tenantWithWriteAccessOnly();

            assertTrue(refused(() -> asClient(pageService.saveDraft(edit), TENANT)));
            assertNull(rawFor(TENANT), "forked off work the owner has not published");
        }
    }

    @Nested
    @DisplayName("choosing the base")
    class BaseChoice {

        /**
         * With a chain [SYSTEM, MID, TENANT] and the editor holding SYSTEM's id,
         * forking off SYSTEM would silently drop everything MID contributes and the
         * tenant would watch the content change as it started typing.
         */
        @Test
        @Timeout(30)
        @DisplayName("bases off the most derived ancestor, not the id it was sent")
        void basesOffMostDerived() {

            setInheritance(List.of(SYSTEM, MID, TENANT));
            Page basePage = storedPage(SYSTEM, null, props("title", "system live"), "rootComp");
            storedPage(MID, SYSTEM, props("title", "mid live"), null);
            tenantWithWriteAccessOnly();

            // The editor holds SYSTEM's id, the top of the chain.
            Page edit = asClient(pageService.read(basePage.getId()), TENANT);
            edit.getProperties().put("subtitle", "tenant addition");
            asClient(pageService.saveDraft(edit), TENANT);

            Page fork = rawFor(TENANT);
            assertNotNull(fork);
            assertEquals(MID, fork.getBaseClientCode(),
                    "forking off the id that was sent drops every override in between");
        }
    }

    @Nested
    @DisplayName("regression")
    class Regression {

        /**
         * Cross-tenant editing by a MANAGING client is a supported, pre-existing
         * feature. The fork path must not capture it: a manager edits the managed
         * client's own object, it does not fork that object into itself.
         */
        @Test
        @Timeout(30)
        @DisplayName("a managing client still writes the managed client's draft, and does not fork")
        void managerStillWritesInPlace() {

            setInheritance(List.of(TENANT));
            Page target = storedPage(TENANT, null, props("title", "live"), "rootComp");

            Page payload = new Page();
            payload.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(TENANT)
                    .setVersion(target.getVersion());
            payload.setId(target.getId());
            payload.setProperties(props("title", "managed edit"));

            Draft draft = asClient(pageService.saveDraft(payload), MID);

            assertNotNull(draft);
            assertEquals(TENANT, draft.getClientCode(), "the draft belongs to the target, not the manager");
            assertEquals(target.getId(), draft.getObjectId());
            assertNull(rawFor(MID), "the manager forked the object into itself");
        }
    }
}
