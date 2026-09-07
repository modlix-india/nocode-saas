package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * Draft and publish.
 *
 * The property that matters most is that a draft save cannot leak: it writes to
 * a separate collection and never touches the live document, so there is nothing
 * to evict and no cache to get wrong. Most of these tests assert that negative.
 */
@DisplayName("Draft and publish")
class DraftPublishIntegrationTest extends AbstractIntegrationTest {

    private static final String MID = "LZCLA";
    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    private Page storedPage(String clientCode, String baseClientCode, Map<String, Object> properties) {

        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
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

    @Nested
    @DisplayName("saving a draft")
    class Saving {

        @Test
        @Timeout(30)
        @DisplayName("does not touch the live document")
        void doesNotTouchLive() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            Draft draft = asClient(pageService.saveDraft(edited), SYSTEM);

            assertNotNull(draft);

            Page stored = mongoTemplate.findById(live.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("original", stored.getProperties().get("title"),
                    "a draft save must leave the live document exactly as it was");
            assertEquals(1, stored.getVersion(), "a draft save must not move the live version");
        }

        @Test
        @Timeout(30)
        @DisplayName("is invisible to the runtime read")
        void invisibleToRuntime() {

            storedPage(SYSTEM, null, props("title", "original"));
            setInheritance(List.of(SYSTEM));

            Page live = mongoTemplate.findAll(Page.class).blockFirst();
            assertNotNull(live);

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            var runtime = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(runtime);
            assertEquals("original", runtime.getObject().getProperties().get("title"),
                    "the runtime surface must not see unpublished work");
        }

        @Test
        @Timeout(30)
        @DisplayName("is visible to the editor read")
        void visibleToEditorRead() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            Page draftRead = asClient(pageService.readDraft(live.getId()), SYSTEM);
            assertNotNull(draftRead);
            assertEquals("drafted", draftRead.getProperties().get("title"));

            Page liveRead = asClient(pageService.read(live.getId()), SYSTEM);
            assertNotNull(liveRead);
            assertEquals("original", liveRead.getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("replaces the previous draft rather than accumulating")
        void replacesPreviousDraft() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page first = asClient(pageService.read(live.getId()), SYSTEM);
            first.getProperties().put("title", "draft one");
            asClient(pageService.saveDraft(first), SYSTEM);

            Page second = asClient(pageService.read(live.getId()), SYSTEM);
            second.getProperties().put("title", "draft two");
            asClient(pageService.saveDraft(second), SYSTEM);

            Long count = mongoTemplate.findAll(Draft.class).count().block();
            assertEquals(1L, count, "one draft per object, not one per save");

            Page draftRead = asClient(pageService.readDraft(live.getId()), SYSTEM);
            assertNotNull(draftRead);
            assertEquals("draft two", draftRead.getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("falls back to live when there is no draft")
        void fallsBackToLive() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page draftRead = asClient(pageService.readDraft(live.getId()), SYSTEM);
            assertNotNull(draftRead);
            assertEquals("original", draftRead.getProperties().get("title"));
        }
    }

    @Nested
    @DisplayName("publishing")
    class Publishing {

        @Test
        @Timeout(30)
        @DisplayName("promotes the draft to live and removes it")
        void promotesAndRemoves() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));
            setInheritance(List.of(SYSTEM));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            Page published = asClient(pageService.publish(live.getId(), "shipping it"), SYSTEM);
            assertNotNull(published);
            assertEquals("drafted", published.getProperties().get("title"));

            Page stored = mongoTemplate.findById(live.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("drafted", stored.getProperties().get("title"));
            assertEquals(2, stored.getVersion(), "publish goes through update(), so the version moves once");

            Long drafts = mongoTemplate.findAll(Draft.class).count().block();
            assertEquals(0L, drafts, "the draft must be gone once published");
        }

        @Test
        @Timeout(30)
        @DisplayName("writes a version row, because it routes through update()")
        void writesVersionRow() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            Long before = mongoTemplate
                    .findAll(com.fincity.saas.commons.mongo.document.Version.class).count().block();
            assertEquals(0L, before, "a draft save must not write version history");

            asClient(pageService.publish(live.getId(), "shipping it"), SYSTEM);

            Long after = mongoTemplate
                    .findAll(com.fincity.saas.commons.mongo.document.Version.class).count().block();
            assertEquals(1L, after, "publish must snapshot, which it gets for free from update()");
        }

        @Test
        @Timeout(30)
        @DisplayName("is rejected when the live document moved on underneath the draft")
        void rejectsStalePublish() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            // Someone saves live after the draft was taken.
            Page concurrent = asClient(pageService.read(live.getId()), SYSTEM);
            concurrent.getProperties().put("title", "live edit");
            asClient(pageService.update(concurrent), SYSTEM);

            boolean rejected = false;
            try {
                asClient(pageService.publish(live.getId(), "shipping it"), SYSTEM);
            } catch (Exception e) {
                rejected = true;
            }

            assertTrue(rejected, "a stale publish must be rejected, not silently overwrite the live edit");

            Long drafts = mongoTemplate.findAll(Draft.class).count().block();
            assertEquals(1L, drafts, "a rejected publish must leave the draft intact, not lose the work");
        }

        @Test
        @Timeout(30)
        @DisplayName("fails when there is nothing to publish")
        void failsWithNoDraft() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            boolean failed = false;
            try {
                asClient(pageService.publish(live.getId(), "nothing"), SYSTEM);
            } catch (Exception e) {
                failed = true;
            }
            assertTrue(failed);

            Page stored = mongoTemplate.findById(live.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals(1, stored.getVersion(), "a failed publish must have no side effects");
        }
    }

    @Nested
    @DisplayName("discarding")
    class Discarding {

        @Test
        @Timeout(30)
        @DisplayName("removes the draft and leaves live untouched")
        void removesDraftOnly() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));

            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.getProperties().put("title", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            Boolean discarded = asClient(pageService.discardDraft(live.getId()), SYSTEM);
            assertTrue(Boolean.TRUE.equals(discarded));

            assertEquals(0L, mongoTemplate.findAll(Draft.class).count().block());

            Page stored = mongoTemplate.findById(live.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("original", stored.getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("reports false when there was nothing to discard")
        void reportsFalseWhenNothing() {

            Page live = storedPage(SYSTEM, null, props("title", "original"));
            Boolean discarded = asClient(pageService.discardDraft(live.getId()), SYSTEM);
            assertFalse(Boolean.TRUE.equals(discarded));
        }
    }

    @Nested
    @DisplayName("across an override chain")
    class OverrideChain {

        @Test
        @Timeout(30)
        @DisplayName("a draft at the base does not reach the child's live surface")
        void baseDraftDoesNotReachChild() {

            setInheritance(List.of(SYSTEM, MID));
            Page base = storedPage(SYSTEM, null, props("fromRoot", "original"));
            Page child = storedPage(MID, SYSTEM, props("fromChild", "c"));

            Page edited = asClient(pageService.read(base.getId()), SYSTEM);
            edited.getProperties().put("fromRoot", "drafted");
            asClient(pageService.saveDraft(edited), SYSTEM);

            Page childRead = asClient(pageService.read(child.getId()), MID);
            assertNotNull(childRead);
            assertEquals("original", childRead.getProperties().get("fromRoot"),
                    "the child must merge the base's published content, not its draft");
        }

        @Test
        @Timeout(30)
        @DisplayName("publishing the child leaves the base alone")
        void publishingChildLeavesBase() {

            setInheritance(List.of(SYSTEM, MID));
            Page base = storedPage(SYSTEM, null, props("fromRoot", "r"));
            Page child = storedPage(MID, SYSTEM, props("fromChild", "c"));

            Page edited = asClient(pageService.read(child.getId()), MID);
            edited.getProperties().put("fromChild", "c2");
            asClient(pageService.saveDraft(edited), MID);
            asClient(pageService.publish(child.getId(), "child change"), MID);

            Page storedBase = mongoTemplate.findById(base.getId(), Page.class).block();
            assertNotNull(storedBase);
            assertEquals(1, storedBase.getVersion(), "publishing an override must not touch its base");
            assertEquals("r", storedBase.getProperties().get("fromRoot"));
        }
    }

    @Nested
    @DisplayName("never-published objects")
    class NeverPublished {

        /**
         * These used to write published=FALSE straight into Mongo with
         * mongoTemplate.save(). That made them pass over a filter no production path
         * could actually trigger, because the create route they assumed did not
         * exist. Everything here goes through the service.
         */
        private Page createUnpublished(String clientCode) {
            Page page = new Page();
            page.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(clientCode)
                    .setVersion(1);
            page.setProperties(props("title", "wip"));
            page.setRootComponent("rootComp");
            return asClient(pageService.createUnpublished(page), clientCode);
        }

        @Test
        @Timeout(30)
        @DisplayName("are created through the route, not fabricated")
        void createdThroughTheRoute() {

            setInheritance(List.of(SYSTEM));
            Page created = createUnpublished(SYSTEM);

            assertNotNull(created);
            assertNotNull(created.getId(), "an unpublished object is still a real object with a real id");
            assertEquals(Boolean.FALSE, created.getPublished());
        }

        @Test
        @Timeout(30)
        @DisplayName("are invisible on the live surface but readable by id")
        void invisibleAtRuntime() {

            setInheritance(List.of(SYSTEM));
            Page created = createUnpublished(SYSTEM);

            assertNull(asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM),
                    "an unpublished page must not resolve on the live surface");

            Page byId = asClient(pageService.read(created.getId()), SYSTEM);
            assertNotNull(byId, "it must still be addressable for the editor");
            assertEquals("wip", byId.getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("become visible once published")
        void visibleOncePublished() {

            setInheritance(List.of(SYSTEM));
            Page created = createUnpublished(SYSTEM);

            Page edited = asClient(pageService.read(created.getId()), SYSTEM);
            edited.getProperties().put("title", "ready");
            asClient(pageService.saveDraft(edited), SYSTEM);
            asClient(pageService.publish(created.getId(), "go live"), SYSTEM);

            var runtime = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(runtime);
            assertEquals("ready", runtime.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("legacy documents with no published field are treated as published")
        void legacyIsPublished() {

            setInheritance(List.of(SYSTEM));
            Page legacy = storedPage(SYSTEM, null, props("title", "existing"));
            assertNull(legacy.getPublished(), "the fixture must have no published field, as every existing row does");

            var runtime = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(runtime, "existing documents must keep working with no migration");
            assertEquals("existing", runtime.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("cannot be hidden by a request body")
        void publishedNotSettableFromBody() {

            setInheritance(List.of(SYSTEM));
            Page live = storedPage(SYSTEM, null, props("title", "live"));

            // A body claiming the object is unpublished must not hide a live page.
            Page edited = asClient(pageService.read(live.getId()), SYSTEM);
            edited.setPublished(Boolean.FALSE);
            asClient(pageService.update(edited), SYSTEM);

            Page stored = mongoTemplate.findById(live.getId(), Page.class).block();
            assertNotNull(stored);
            assertNull(stored.getPublished(), "a request body was able to set server-controlled state");

            assertNotNull(asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM),
                    "an ordinary save hid a live page from the live surface");
        }

        @Test
        @Timeout(30)
        @DisplayName("an ordinary save does not clear the flag on an unpublished object")
        void ordinarySaveDoesNotPublish() {

            setInheritance(List.of(SYSTEM));
            Page created = createUnpublished(SYSTEM);

            Page edited = asClient(pageService.read(created.getId()), SYSTEM);
            edited.getProperties().put("title", "still wip");
            asClient(pageService.update(edited), SYSTEM);

            Page stored = mongoTemplate.findById(created.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals(Boolean.FALSE, stored.getPublished(),
                    "a plain update omitting the field nulled it, silently publishing the object");
        }
    }
}
