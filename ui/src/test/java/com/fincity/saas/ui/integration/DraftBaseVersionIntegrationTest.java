package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * A draft records the live version it was taken FROM, and keeps it.
 *
 * That frozen number is the entire mechanism behind limitation 2 in the handoff:
 * nothing reconciles a draft with a live edit made after the draft was taken, so
 * the publish has to fail rather than merge. Re-stamping baseVersion on every save
 * made the optimistic-lock check compare a version against itself, so it always
 * passed and a publish silently overwrote newer live content with work derived
 * from an older copy. The check existed and could never fire.
 *
 * Deliberately recoverable rather than terminal: discard and save again takes the
 * current live version as a fresh base.
 */
@DisplayName("Draft baseVersion")
class DraftBaseVersionIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "baseVersionPage";

    @Autowired
    private PageService pageService;

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Page storedPage(int version) {
        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(version);
        page.setRootComponent("liveRoot");
        return this.insertRaw(page);
    }

    private void saveDraft(Page stored, String rootComponent) {

        Page edit = new Page();
        edit.setId(stored.getId());
        edit.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(stored.getVersion());
        edit.setRootComponent(rootComponent);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", rootComponent);
        edit.setProperties(properties);

        assertNotNull(asClient(this.pageService.saveDraft(edit)));
    }

    private Draft theDraft() {
        List<Draft> drafts = this.mongoTemplate.findAll(Draft.class).collectList().block();
        assertEquals(1, drafts.size(), "expected exactly one draft, got " + drafts.size());
        return drafts.get(0);
    }

    @Test
    @Timeout(60)
    @DisplayName("is taken from the live document on the first save")
    void firstSaveStampsTheLiveVersion() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage(7);

        saveDraft(page, "draftOne");

        assertEquals(7, theDraft().getBaseVersion());
    }

    @Test
    @Timeout(60)
    @DisplayName("does not move when the draft is saved again")
    void secondSaveKeepsTheOriginalBase() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage(7);
        saveDraft(page, "draftOne");

        // The live document moves on underneath the draft: someone else published,
        // or the same person edited live in another tab.
        Page live = this.mongoTemplate.findAll(Page.class).blockFirst();
        live.setVersion(9);
        live.setRootComponent("liveMovedOn");
        this.mongoTemplate.save(live).block();

        saveDraft(page, "draftTwo");

        assertEquals(7, theDraft().getBaseVersion(),
                "baseVersion was re-stamped, so the publish would silently overwrite the newer live content");

        // The content did update; it is only the base that is frozen.
        assertEquals("draftTwo", theDraft().getContent().get("rootComponent"));
    }

    @Test
    @Timeout(60)
    @DisplayName("a live change under a draft makes the publish fail rather than clobber")
    void publishFailsAfterALiveChange() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage(7);
        saveDraft(page, "draftOne");

        Page live = this.mongoTemplate.findAll(Page.class).blockFirst();
        live.setVersion(9);
        live.setRootComponent("liveMovedOn");
        this.mongoTemplate.save(live).block();

        // The realistic sequence, and the one that discriminates: the author keeps
        // working on their draft after the live document has moved on underneath
        // them. With baseVersion re-stamped on every save, this second save silently
        // rebased onto 9 and the publish below then succeeded and clobbered.
        saveDraft(page, "draftTwo");

        assertThrows(Exception.class, () -> asClient(this.pageService.publish(page.getId(), null)),
                "the publish went through and overwrote a newer live document");

        Page after = this.mongoTemplate.findAll(Page.class).blockFirst();
        assertEquals("liveMovedOn", after.getRootComponent(), "the newer live content was overwritten");
        assertTrue(this.mongoTemplate.findAll(Draft.class).collectList().block().size() == 1,
                "a failed publish must leave the draft intact so the work is not lost");
    }

    @Test
    @Timeout(60)
    @DisplayName("discarding and re-saving takes a fresh base, so the conflict is recoverable")
    void discardAndResaveRebases() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage(7);
        saveDraft(page, "draftOne");

        Page live = this.mongoTemplate.findAll(Page.class).blockFirst();
        live.setVersion(9);
        this.mongoTemplate.save(live).block();

        asClient(this.pageService.discardDraft(page.getId()));
        saveDraft(page, "draftTwo");

        assertEquals(9, theDraft().getBaseVersion(),
                "a re-saved draft must rebase on the current live version, or the conflict is unrecoverable");
    }
}
