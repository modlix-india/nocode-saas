package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.PublishService;

import reactor.core.publisher.Mono;

/**
 * A deleted object must not leave its draft behind.
 *
 * It used to. `delete` evicted both caches and never touched the `Draft`
 * collection, and the consequences compounded:
 *
 * - the row stayed in `pending` forever, so the builder's pending count could
 *   never reach zero;
 * - `publishAll` could not clear it and did not even report it, because
 *   `publish` read the stored document by id, found nothing, and returned an
 *   empty Mono that `concatMap` silently dropped. The response said
 *   `attempted=0` with a draft still pending;
 * - and the draft lookup is keyed on NAME, not id, so creating a new object with
 *   the deleted one's name served the DEAD draft's content on the draft surface,
 *   carrying the dead object's id.
 *
 * The last one is the serious one: deleted content coming back under a different
 * object is not a stale-cache annoyance, it is wrong data.
 */
@DisplayName("Deleting an object with a draft")
class DeletedObjectDraftIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "deletedDraftPage";

    @Autowired
    private PageService pageService;

    @Autowired
    private PublishService publishService;

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Page storedPage() {
        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        page.setRootComponent("liveRoot");
        return this.insertRaw(page);
    }

    /** Save a draft against an existing page, through the service, never the repo. */
    private void draftAgainst(Page page, String rootComponent) {

        Page edit = new Page();
        edit.setId(page.getId());
        edit.setName(page.getName()).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        edit.setRootComponent(rootComponent);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", "drafted");
        edit.setProperties(properties);

        assertNotNull(asClient(this.pageService.saveDraft(edit)));
    }

    private List<Draft> drafts() {
        return this.mongoTemplate.findAll(Draft.class).collectList().block();
    }

    @Test
    @Timeout(60)
    @DisplayName("takes the draft with it")
    void deleteRemovesTheDraft() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");
        assertEquals(1, drafts().size());

        asClient(this.pageService.delete(page.getId()));

        assertTrue(drafts().isEmpty(), "the draft outlived the object it drafts");
    }

    @Test
    @Timeout(60)
    @DisplayName("clears it from the pending list")
    void deleteClearsPending() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");

        assertFalse(asClient(this.publishService.pending(APP_CODE, SYSTEM)).isEmpty(),
                "precondition: the draft should be pending before the delete");

        asClient(this.pageService.delete(page.getId()));

        assertTrue(asClient(this.publishService.pending(APP_CODE, SYSTEM)).isEmpty(),
                "a deleted object still counted as pending work, so the count could never reach zero");
    }

    @Test
    @Timeout(60)
    @DisplayName("does not resurrect its content under a new object of the same name")
    void deletedDraftDoesNotResurrect() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");
        asClient(this.pageService.delete(page.getId()));

        // Same name, new object, new id. This is an ordinary thing to do: delete a
        // page and build it again.
        Page recreated = new Page();
        recreated.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        recreated.setRootComponent("recreatedRoot");
        recreated = this.insertRaw(recreated);

        ObjectWithUniqueID<Page> read = this.onDraftSurface(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM)
                .contextWrite(ReactiveSecurityContextHolder
                        .withAuthentication(this.authFor(SYSTEM, allAuthoritiesFor("Page")))))
                .block();

        assertNotNull(read);
        assertEquals("recreatedRoot", read.getObject().getRootComponent(),
                "the deleted page's draft content came back under the new page");
        assertEquals(recreated.getId(), read.getObject().getId(),
                "the read carried the DELETED object's id, so a save would PUT to an id that no longer exists");
    }

    @Test
    @Timeout(60)
    @DisplayName("an app wipe sweeps drafts alongside versions")
    void deleteEverythingSweepsDrafts() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");
        assertEquals(1, drafts().size());

        asClient(this.pageService.deleteEverything(APP_CODE, SYSTEM));

        assertTrue(drafts().isEmpty(),
                "an app wipe left drafts behind, so the app's names stayed claimed in a pending list "
                        + "belonging to a client that no longer has the app");
    }

    @Test
    @Timeout(60)
    @DisplayName("an app wipe leaves another app's drafts alone")
    void deleteEverythingIsScopedToTheApp() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");

        // A second app's draft, written the same way, must survive. The sweep is
        // keyed on (appCode, objectType, clientCode) and a bug in any of the three
        // would show up as this one disappearing.
        Page other = new Page();
        other.setName(PAGE_NAME).setAppCode("otherapp").setClientCode(SYSTEM).setVersion(1);
        other.setRootComponent("liveRoot");
        other = this.insertRaw(other);

        Page otherEdit = new Page();
        otherEdit.setId(other.getId());
        otherEdit.setName(PAGE_NAME).setAppCode("otherapp").setClientCode(SYSTEM).setVersion(1);
        otherEdit.setRootComponent("otherDraftRoot");
        assertNotNull(asClient(this.pageService.saveDraft(otherEdit)));
        assertEquals(2, drafts().size());

        asClient(this.pageService.deleteEverything(APP_CODE, SYSTEM));

        List<Draft> left = drafts();
        assertEquals(1, left.size(), "the sweep crossed app boundaries");
        assertEquals("otherapp", left.get(0).getObjectAppCode());
    }

    @Test
    @Timeout(60)
    @DisplayName("an orphaned draft is reported by publishAll rather than silently skipped")
    void orphanedDraftIsReported() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();
        draftAgainst(page, "draftRoot");

        // Orphan it the only way that is still possible: remove the document
        // underneath the draft without going through delete(). This is the state
        // any draft row left over from before the fix above is already in, so
        // publishAll has to cope with it rather than pretend it is not there.
        this.mongoTemplate.remove(page).block();

        Map<String, Object> result = asClient(this.publishService.publishAll(APP_CODE, SYSTEM));

        assertNotNull(result);
        assertEquals(1, result.get("attempted"),
                "publishAll dropped the object from its own report, claiming it had attempted nothing");
        assertEquals(0L, result.get("published"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals(1, results.size());
        assertEquals(Boolean.FALSE, results.get(0).get("published"));
        assertNotNull(results.get(0).get("error"), "the caller was told nothing about why it did not ship");
    }
}
