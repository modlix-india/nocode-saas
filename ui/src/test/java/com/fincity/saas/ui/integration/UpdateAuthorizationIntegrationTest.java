package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * Which document a plain update() actually writes.
 *
 * update() authorised entity.getAppCode()/getClientCode() straight out of the
 * request BODY, while updatableEntity keyed the write on entity.getId(), and
 * nothing compared the two. The controller sets the id from the path, so a
 * caller that put its OWN codes in the body passed accessCheck against its own
 * client -- via hasWriteAccess, which is true for it -- and then repo.save wrote
 * whatever document that id resolved to. Another client's live object.
 *
 * saveDraft and publish were both hardened against this shape already; update()
 * was the one left, and cross-client editing makes holding another client's id
 * the normal case rather than an oddity.
 *
 * The attacker here is a DERIVED client writing onto its BASE, which is the
 * reachable shape and not an obvious one. An unrelated tenant is already stopped
 * further down: updatableEntity calls read(id), whose READ check requires the
 * inheritance chain to contain BOTH codes, and an unrelated tenant's chain
 * contains neither the target nor a path to it. A derived client's chain contains
 * both by construction -- that is what an override chain IS -- so the read check
 * waves it through and repo.save writes the base's live row.
 *
 * A first version of this test used two unrelated tenants and passed with the fix
 * reverted, which is to say it pinned nothing.
 *
 * As in DraftAuthorizationIntegrationTest, the assertions are "did anything
 * land", not only "was it refused": a refusal after the write is not a refusal.
 */
@DisplayName("Update write authorization")
class UpdateAuthorizationIntegrationTest extends AbstractIntegrationTest {

    /** The derived client. Its chain is [VICTIM, ATTACKER], so it can read the base. */
    private static final String ATTACKER = "LZCLA";
    /** The base client whose live document the derived client must not overwrite. */
    private static final String VICTIM = "LZCLB";

    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    private Page storedPage(String clientCode, Map<String, Object> properties) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setVersion(1);
        page.setProperties(properties == null ? null : new HashMap<>(properties));
        page.setRootComponent("rootComp");
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

    /** Neither tenant manages the other; the base class stubs that permissively. */
    private void isolateTenants() {
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
    }

    private boolean refused(Runnable action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Nested
    @DisplayName("a tenant PUTting onto another tenant's page")
    class CrossTenant {

        /** Body carries the attacker's own codes; the id points at the victim. */
        private Page attemptAttack(Page victimPage) {

            Page payload = new Page();
            payload.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(ATTACKER)
                    .setVersion(victimPage.getVersion());
            payload.setId(victimPage.getId());
            payload.setProperties(props("title", "attacker payload"));
            payload.setRootComponent("rootComp");
            return payload;
        }

        @Test
        @Timeout(30)
        @DisplayName("is refused")
        void isRefused() {

            isolateTenants();
            // The override chain: ATTACKER derives from VICTIM. This is what makes
            // read(id) inside updatableEntity accept the base's document.
            setInheritance(List.of(VICTIM, ATTACKER));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            assertTrue(refused(() -> asClient(pageService.update(attemptAttack(victimPage)), ATTACKER)),
                    "authorising the request body let a tenant overwrite another tenant's live object");
        }

        @Test
        @Timeout(30)
        @DisplayName("leaves the victim's document untouched, content and version")
        void victimRowUnchanged() {

            isolateTenants();
            // The override chain: ATTACKER derives from VICTIM. This is what makes
            // read(id) inside updatableEntity accept the base's document.
            setInheritance(List.of(VICTIM, ATTACKER));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            refused(() -> asClient(pageService.update(attemptAttack(victimPage)), ATTACKER));

            Page stored = mongoTemplate.findById(victimPage.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("victim live", stored.getProperties().get("title"),
                    "a refusal that happens after the write is not a refusal");
            assertEquals(1, stored.getVersion(),
                    "the version moved, so something wrote even if the content happened to match");
        }
    }

    @Nested
    @DisplayName("identity fields in the body")
    class IdentityFromStored {

        /**
         * A contract characterization, not a regression pin: verified by reverting
         * the fix, and it passes either way for Page.
         *
         * The guarantee currently comes from PageService.updatableEntity, which
         * copies a whitelist of CONTENT fields onto the stored document and so never
         * lets name, clientCode or baseClientCode through. update()'s overwrite makes
         * the same guarantee hold for any type whose updatableEntity is less strict,
         * and states it once at the base rather than relying on every subclass to
         * keep getting its whitelist right.
         */
        @Test
        @Timeout(30)
        @DisplayName("cannot rename or re-home the document")
        void cannotRenameOrRehome() {

            setInheritance(List.of(SYSTEM));
            Page page = storedPage(SYSTEM, props("title", "live"));

            Page payload = new Page();
            payload.setName("someOtherName")
                    .setAppCode(APP_CODE)
                    .setClientCode(SYSTEM)
                    .setBaseClientCode(ATTACKER)
                    .setVersion(page.getVersion());
            payload.setId(page.getId());
            payload.setProperties(props("title", "edited"));
            payload.setRootComponent("rootComp");

            asClient(pageService.update(payload), SYSTEM);

            Page stored = mongoTemplate.findById(page.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals(PAGE_NAME, stored.getName(), "a PUT renamed the object");
            assertEquals(SYSTEM, stored.getClientCode(), "a PUT moved the object to another client");
            assertEquals(null, stored.getBaseClientCode(),
                    "a PUT re-parented the object onto a base the caller chose");
        }
    }

    @Nested
    @DisplayName("terminator")
    class Terminator {

        /**
         * update() had no switchIfEmpty. The controller does
         * update(entity).map(ResponseEntity::ok), so an empty Mono became an empty
         * 200 and a refused write looked like a success.
         *
         * This pins the outcome, not the layer: before the change an unknown id was
         * refused further down by updatableEntity's own read(), with a different
         * status. Load-first moves the refusal earlier and the terminator is what
         * keeps it a refusal rather than an empty success. Worth keeping because the
         * terminator is easy to drop in a later refactor and nothing else would
         * notice.
         */
        @Test
        @Timeout(30)
        @DisplayName("an unknown id is refused, not answered as an empty success")
        void unknownIdIsRefused() {

            setInheritance(List.of(SYSTEM));

            Page payload = new Page();
            payload.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(SYSTEM)
                    .setVersion(1);
            payload.setId("64b7f1c2e4b0a1c2d3e4f5a6");
            payload.setProperties(props("title", "edited"));

            assertTrue(refused(() -> asClient(pageService.update(payload), SYSTEM)),
                    "an update against an id that does not exist answered success");
        }
    }

    @Nested
    @DisplayName("the ordinary case")
    class HappyPath {

        /** The regression guard: load-first must not break a normal owner update. */
        @Test
        @Timeout(30)
        @DisplayName("an owner's update still works")
        void ownerUpdateStillWorks() {

            setInheritance(List.of(SYSTEM));
            Page page = storedPage(SYSTEM, props("title", "live"));

            Page edit = asClient(pageService.read(page.getId()), SYSTEM);
            edit.getProperties().put("title", "edited");

            Page updated = asClient(pageService.update(edit), SYSTEM);

            assertNotNull(updated, "a legitimate update was refused");
            assertEquals("edited", updated.getProperties().get("title"));

            Page stored = mongoTemplate.findById(page.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("edited", stored.getProperties().get("title"));
            assertEquals(2, stored.getVersion(), "the version should move on a real update");
        }
    }
}
