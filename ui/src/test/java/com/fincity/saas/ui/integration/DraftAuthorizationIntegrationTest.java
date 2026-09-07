package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Who is allowed to write a draft, and onto whose object.
 *
 * saveDraft authorised the codes in the request BODY and then wrote keyed on the
 * codes of whatever document the id resolved to, with nothing comparing the two.
 * An attacker passed the check on an app they legitimately manage and wrote into
 * a victim's draft surface, destroying the victim's unpublished work on the way
 * because the Draft collection is unique on that key.
 *
 * The point of these tests is not only "is it refused" but "did anything land",
 * since a refusal after the write is worthless.
 */
@DisplayName("Draft write authorization")
class DraftAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String ATTACKER = "LZCLA";
    private static final String VICTIM = "LZCLB";
    private static final String MANAGER = "LZMGR";

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

    /**
     * Neither tenant manages the other. The base class stubs this permissively so
     * that tests about the override chain do not have to restate the access model;
     * a test about authorization has to undo that.
     */
    private void isolateTenants() {
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.eq(MANAGER),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.TRUE));
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
    @DisplayName("a tenant writing onto another tenant's page")
    class CrossTenant {

        /**
         * The attack: body carries the attacker's own codes, so the old check passed
         * on their own app, while the id points at the victim's page.
         */
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
            setInheritance(List.of(VICTIM));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            assertTrue(refused(() -> asClient(pageService.saveDraft(attemptAttack(victimPage)), ATTACKER)),
                    "authorising the request body let a tenant write onto another tenant's object");
        }

        @Test
        @Timeout(30)
        @DisplayName("writes no draft row at all")
        void writesNothing() {

            isolateTenants();
            setInheritance(List.of(VICTIM));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            refused(() -> asClient(pageService.saveDraft(attemptAttack(victimPage)), ATTACKER));

            Long drafts = mongoTemplate.findAll(Draft.class).count().block();
            assertEquals(0L, drafts, "a refusal that happens after the write is not a refusal");
        }

        @Test
        @Timeout(30)
        @DisplayName("does not destroy the victim's existing unpublished work")
        void doesNotDestroyVictimDraft() {

            setInheritance(List.of(VICTIM));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            // The victim has real unpublished work.
            Page victimEdit = asClient(pageService.read(victimPage.getId()), VICTIM);
            victimEdit.getProperties().put("title", "victim draft");
            asClient(pageService.saveDraft(victimEdit), VICTIM);

            isolateTenants();
            refused(() -> asClient(pageService.saveDraft(attemptAttack(victimPage)), ATTACKER));

            Draft stored = mongoTemplate.findAll(Draft.class).blockFirst();
            assertNotNull(stored, "the victim's draft must still exist");
            assertEquals("victim draft", ((Map<?, ?>) stored.getContent().get("properties")).get("title"),
                    "the upsert overwrote the victim's work");
        }

        @Test
        @Timeout(30)
        @DisplayName("does not change what the victim's editor sees")
        void victimEditorUnaffected() {

            setInheritance(List.of(VICTIM));
            Page victimPage = storedPage(VICTIM, props("title", "victim live"));

            Page victimEdit = asClient(pageService.read(victimPage.getId()), VICTIM);
            victimEdit.getProperties().put("title", "victim draft");
            asClient(pageService.saveDraft(victimEdit), VICTIM);

            isolateTenants();
            refused(() -> asClient(pageService.saveDraft(attemptAttack(victimPage)), ATTACKER));

            Page read = asClient(pageService.readDraft(victimPage.getId()), VICTIM);
            assertNotNull(read);
            assertEquals("victim draft", read.getProperties().get("title"),
                    "the victim's editor would have loaded attacker content and published it");
        }
    }

    @Nested
    @DisplayName("a client that genuinely manages the target")
    class LegitimateCrossTenant {

        /**
         * The regression guard. Cross-tenant editing is a supported feature: the fix
         * must only change WHICH object the decision is about, not forbid the case.
         */
        @Test
        @Timeout(30)
        @DisplayName("can still draft the managed client's page")
        void managerCanDraft() {

            isolateTenants();
            setInheritance(List.of(VICTIM));
            Page target = storedPage(VICTIM, props("title", "live"));

            Page payload = new Page();
            payload.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(VICTIM)
                    .setVersion(target.getVersion());
            payload.setId(target.getId());
            payload.setProperties(props("title", "managed edit"));
            payload.setRootComponent("rootComp");

            Draft draft = asClient(pageService.saveDraft(payload), MANAGER);
            assertNotNull(draft, "a managing client must still be able to edit a managed client's app");
            assertEquals(VICTIM, draft.getClientCode(), "the draft belongs to the target, not the manager");
        }
    }

    @Nested
    @DisplayName("publish identity")
    class PublishIdentity {

        /**
         * Publish rebuilds its entity from draft CONTENT, which is caller supplied.
         * Identity has to come from the stored document instead.
         */
        @Test
        @Timeout(30)
        @DisplayName("comes from the stored document, not from draft content")
        void identityFromStored() {

            setInheritance(List.of(VICTIM));
            Page target = storedPage(VICTIM, props("title", "live"));

            Page edit = asClient(pageService.read(target.getId()), VICTIM);
            edit.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edit), VICTIM);

            // Poison the stored draft content with foreign identity fields.
            Draft draft = mongoTemplate.findAll(Draft.class).blockFirst();
            assertNotNull(draft);
            draft.getContent().put("clientCode", ATTACKER);
            draft.getContent().put("appCode", "someotherapp");
            draft.getContent().put("name", "someOtherPage");
            mongoTemplate.save(draft).block();

            asClient(pageService.publish(target.getId(), "ship"), VICTIM);

            Page stored = mongoTemplate.findById(target.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals(VICTIM, stored.getClientCode(), "publish took the client code from draft content");
            assertEquals(APP_CODE, stored.getAppCode(), "publish took the app code from draft content");
            assertEquals(PAGE_NAME, stored.getName(), "publish took the name from draft content");
        }
    }

    @Nested
    @DisplayName("deleting an object")
    class DeleteEviction {

        /**
         * evictRecursively clears both surfaces; delete only cleared the live one, so
         * a deleted object kept being served from the draft cache.
         */
        @Test
        @Timeout(30)
        @DisplayName("evicts the draft cache as well as the live one")
        void deleteEvictsDraftCache() {

            setInheritance(List.of(SYSTEM));
            Page page = storedPage(SYSTEM, props("title", "live"));

            // Warm the draft cache.
            var warmed = onDraftSurface(pageService.read(PAGE_NAME, APP_CODE, SYSTEM)
                    .contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(authFor(SYSTEM, allAuthoritiesFor("Page")))))
                    .block();
            assertNotNull(warmed);

            asClient(pageService.delete(page.getId()), SYSTEM);

            var after = onDraftSurface(pageService.read(PAGE_NAME, APP_CODE, SYSTEM)
                    .contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(authFor(SYSTEM, allAuthoritiesFor("Page")))))
                    .block();
            assertNull(after, "the draft cache kept serving a deleted object");
        }
    }
}
