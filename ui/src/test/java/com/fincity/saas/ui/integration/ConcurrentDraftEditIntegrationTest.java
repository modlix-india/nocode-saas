package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Two people editing the same draft.
 *
 * There is one draft row per (app, type, name, clientCode), so a draft belongs to
 * a CLIENT and not to a user: A and B share it. Before `Draft.version` the second
 * save simply replaced the first's content through the upsert and neither person
 * was told, so A's work vanished with no error anywhere.
 *
 * `baseVersion` cannot detect this and never could. It records the LIVE document's
 * version, and A and B both read the same live document, so both send the same
 * number and the check passes for both. Only a counter on the draft row itself
 * moves when someone else saves.
 *
 * The check is opt-in: omit the expected version and the old last-write-wins
 * behaviour is unchanged, so no existing caller starts failing.
 */
@DisplayName("Concurrent draft edits")
class ConcurrentDraftEditIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "sharedDraftPage";

    @Autowired
    private PageService pageService;

    private <T> T asUser(Mono<T> mono, String user) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Page"));
        ca.getUser().setUserName(user);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Page storedPage() {
        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(7);
        page.setRootComponent("liveRoot");
        return this.insertRaw(page);
    }

    private Page edit(Page stored, String rootComponent) {
        Page e = new Page();
        e.setId(stored.getId());
        e.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(stored.getVersion());
        e.setRootComponent(rootComponent);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", rootComponent);
        e.setProperties(properties);
        return e;
    }

    private Draft theDraft() {
        List<Draft> drafts = this.mongoTemplate.findAll(Draft.class).collectList().block();
        assertEquals(1, drafts.size());
        return drafts.get(0);
    }

    @Test
    @Timeout(60)
    @DisplayName("the draft version moves on every save, while baseVersion stays put")
    void versionMovesBaseVersionDoesNot() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        assertNotNull(asUser(this.pageService.saveDraft(edit(page, "one")), "A"));
        assertEquals(1, theDraft().getVersion());
        assertEquals(7, theDraft().getBaseVersion());

        assertNotNull(asUser(this.pageService.saveDraft(edit(page, "two")), "A"));
        assertEquals(2, theDraft().getVersion(), "the draft counter did not move, so no one can detect a clash");
        assertEquals(7, theDraft().getBaseVersion(), "baseVersion must stay frozen at the live version");
    }

    @Test
    @Timeout(60)
    @DisplayName("the read hands back the version the save expects")
    void readReturnsTheVersion() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        // No draft yet: version 0 means "this is the live document".
        Tuple2<Page, Integer> beforeAny = asUser(this.pageService.readDraftWithVersion(page.getId()), "A");
        assertEquals(0, beforeAny.getT2());
        assertEquals("liveRoot", beforeAny.getT1().getRootComponent());

        asUser(this.pageService.saveDraft(edit(page, "one")), "A");

        Tuple2<Page, Integer> afterSave = asUser(this.pageService.readDraftWithVersion(page.getId()), "A");
        assertEquals(1, afterSave.getT2());
        assertEquals("one", afterSave.getT1().getRootComponent());
    }

    @Test
    @Timeout(60)
    @DisplayName("B cannot silently overwrite A's draft")
    void secondEditorIsRefused() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        // A and B both open the object. Neither has saved, so both see version 0.
        int aSaw = asUser(this.pageService.readDraftWithVersion(page.getId()), "A").getT2();
        int bSaw = asUser(this.pageService.readDraftWithVersion(page.getId()), "B").getT2();
        assertEquals(0, aSaw);
        assertEquals(0, bSaw);

        assertNotNull(asUser(this.pageService.saveDraft(edit(page, "fromA"), aSaw), "A"));

        // B saves against the version B last saw, which is now stale.
        GenericException thrown = assertThrows(GenericException.class,
                () -> asUser(this.pageService.saveDraft(edit(page, "fromB"), bSaw), "B"),
                "B overwrote A's draft with no error, which is the loss this field exists to stop");
        assertEquals(HttpStatus.PRECONDITION_FAILED, thrown.getStatusCode());

        assertEquals("fromA", theDraft().getContent().get("rootComponent"),
                "A's work must survive a refused save");
        assertEquals(1, theDraft().getVersion(), "a refused save must not advance the counter");
    }

    @Test
    @Timeout(60)
    @DisplayName("B can save once B has re-read")
    void secondEditorSucceedsAfterReReading() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        asUser(this.pageService.saveDraft(edit(page, "fromA"), 0), "A");

        // The recovery path, and the reason a 412 here is workable rather than a
        // dead end: B re-reads, sees A's content and the current version, and saves.
        Tuple2<Page, Integer> reread = asUser(this.pageService.readDraftWithVersion(page.getId()), "B");
        assertEquals("fromA", reread.getT1().getRootComponent());

        assertNotNull(asUser(this.pageService.saveDraft(edit(page, "fromB"), reread.getT2()), "B"));
        assertEquals("fromB", theDraft().getContent().get("rootComponent"));
        assertEquals(2, theDraft().getVersion());
    }

    @Test
    @Timeout(60)
    @DisplayName("omitting the expected version keeps the old last-write-wins behaviour")
    void checkIsOptIn() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        asUser(this.pageService.saveDraft(edit(page, "fromA")), "A");

        // No version sent, so no check. Existing callers keep working unchanged
        // until they start round-tripping the header.
        assertNotNull(asUser(this.pageService.saveDraft(edit(page, "fromB")), "B"));
        assertEquals("fromB", theDraft().getContent().get("rootComponent"));
    }

    @Test
    @Timeout(60)
    @DisplayName("claiming there is no draft when one has appeared is refused")
    void staleZeroIsRefused() {

        setInheritance(List.of(SYSTEM));
        Page page = storedPage();

        // B read the live document before any draft existed, so B holds 0. Sending
        // 0 asserts "no draft existed when I started", which is now false.
        asUser(this.pageService.saveDraft(edit(page, "fromA"), 0), "A");

        assertThrows(GenericException.class,
                () -> asUser(this.pageService.saveDraft(edit(page, "fromB"), 0), "B"),
                "B started from the live document and clobbered a draft that appeared meanwhile");
    }
}
