package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * A draft publishes onto a live document that moved on underneath it.
 *
 * This used to be a flat refusal. The refusal was right about the danger and
 * wrong about the remedy: the live edit and the draft are almost never the same
 * field, and telling the author to discard their whole draft in order to pick up
 * someone else's unrelated change cost more work than it protected.
 *
 * The draft already records the live version it was taken FROM, and version
 * history keeps that version's content, so all three corners of a three-way merge
 * are on hand: base to draft is what the author did, base to live is what
 * happened underneath them, and publish replays the first onto the second.
 *
 * What stays refused: a drift with no base snapshot to reason from, and a live
 * write that lands between the merge and the save. Reconciliation removes a stale
 * conflict, not the optimistic lock.
 */
@DisplayName("Publish reconciliation")
class DraftReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "reconcilePage";
    private static final String MID = "LZCLA";

    @Autowired
    private PageService pageService;

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asClient(Mono<T> mono) {
        return this.asClient(mono, SYSTEM);
    }

    private <T> T asMid(Mono<T> mono) {
        return this.asClient(mono, MID);
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2)
            map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    /**
     * Through the service, not insertRaw. The version snapshot the merge reads is
     * written by create(), so a fixture that bypasses it is testing the fallback
     * rather than the merge.
     */
    private Page createPage(Map<String, Object> properties) {

        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM);
        page.setRootComponent("rootComp");
        page.setProperties(properties);

        Page created = asClient(this.pageService.create(page));
        assertNotNull(created);
        return created;
    }

    /** Someone else's edit, landing on the live document after the draft was taken. */
    private void liveEdit(String id, java.util.function.Consumer<Page> change) {

        Page concurrent = asClient(this.pageService.read(id));
        change.accept(concurrent);
        assertNotNull(asClient(this.pageService.update(concurrent)));
    }

    private void draftEdit(String id, java.util.function.Consumer<Page> change) {

        Page edited = asClient(this.pageService.read(id));
        change.accept(edited);
        assertNotNull(asClient(this.pageService.saveDraft(edited)));
    }

    private Page stored(String id) {
        Page page = this.mongoTemplate.findById(id, Page.class).block();
        assertNotNull(page);
        return page;
    }

    @Test
    @Timeout(60)
    @DisplayName("a live change in a different field is carried into the published result")
    void disjointLiveChangeIsCarriedIn() {

        Page live = createPage(props("shared", "same"));

        draftEdit(live.getId(), p -> p.getProperties().put("fromDraft", "d"));
        liveEdit(live.getId(), p -> p.getProperties().put("fromLive", "L"));

        Page published = asClient(this.pageService.publish(live.getId(), "shipping"));
        assertNotNull(published, "the publish was refused over a change the draft never mentioned");

        Page after = stored(live.getId());
        assertEquals("d", after.getProperties().get("fromDraft"), "the draft's own change was lost");
        assertEquals("L", after.getProperties().get("fromLive"),
                "the live change made after the draft was taken was overwritten");
        assertEquals("same", after.getProperties().get("shared"));

        assertEquals(0L, this.mongoTemplate.findAll(Draft.class).count().block(),
                "a successful publish must remove the draft");
    }

    @Test
    @Timeout(60)
    @DisplayName("a live change outside properties is carried in too, not just inside one map")
    void driftAnywhereInTheDocument() {

        Page live = createPage(props("title", "t"));

        draftEdit(live.getId(), p -> p.getProperties().put("title", "drafted title"));
        liveEdit(live.getId(), p -> p.setRootComponent("newRoot"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        Page after = stored(live.getId());
        assertEquals("newRoot", after.getRootComponent(), "a live change to a top-level field was clobbered");
        assertEquals("drafted title", after.getProperties().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("a key the live document deleted stays deleted")
    void liveDeletionSurvives() {

        Page live = createPage(props("keep", "k", "doomed", "d"));

        draftEdit(live.getId(), p -> p.getProperties().put("keep", "k2"));
        liveEdit(live.getId(), p -> p.getProperties().remove("doomed"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        Page after = stored(live.getId());
        assertEquals("k2", after.getProperties().get("keep"));
        assertFalse(after.getProperties().containsKey("doomed"),
                "the publish resurrected a key the live document had deleted");
    }

    @Test
    @Timeout(60)
    @DisplayName("both sides editing the same field keeps the draft and records which field")
    void realConflictKeepsTheDraftAndSaysSo() {

        Page live = createPage(props("title", "original"));

        draftEdit(live.getId(), p -> p.getProperties().put("title", "drafted"));
        liveEdit(live.getId(), p -> p.getProperties().put("title", "live edit"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        Page after = stored(live.getId());
        assertEquals("drafted", after.getProperties().get("title"),
                "the change being published must win the field it actually contests");

        // The history is the only place anyone can find out afterwards that a live
        // edit was overridden, so the publish has to say so there.
        assertNotNull(after.getMessage());
        assertTrue(after.getMessage().startsWith("shipping "), "the author's own message was replaced: "
                + after.getMessage());
        assertTrue(after.getMessage().contains("properties.title"),
                "the contested field is not named in the publish message: " + after.getMessage());
    }

    @Test
    @Timeout(60)
    @DisplayName("a publish with no drift is not annotated at all")
    void cleanPublishKeepsTheMessageVerbatim() {

        Page live = createPage(props("title", "original"));
        draftEdit(live.getId(), p -> p.getProperties().put("title", "drafted"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        assertEquals("shipping", stored(live.getId()).getMessage(),
                "the ordinary case must not acquire reconciliation noise");
    }

    @Test
    @Timeout(60)
    @DisplayName("a reconciled publish is annotated even when nothing conflicted")
    void cleanReconciliationIsStillRecorded() {

        Page live = createPage(props("shared", "same"));

        draftEdit(live.getId(), p -> p.getProperties().put("fromDraft", "d"));
        liveEdit(live.getId(), p -> p.getProperties().put("fromLive", "L"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        String message = stored(live.getId()).getMessage();
        assertTrue(message.contains("reconciled"),
                "a publish that silently rebased says nothing about it: " + message);
        assertFalse(message.contains("draft kept over live"),
                "a clean merge was reported as a conflict: " + message);
    }

    @Test
    @Timeout(60)
    @DisplayName("with no snapshot of the base version the publish is still refused")
    void noBaseSnapshotStillRefuses() {

        // insertRaw deliberately: an object that predates version history, or whose
        // history has been cleared. Nothing can say what the draft changed, so there
        // is no merge to attempt and the old refusal is the only honest answer.
        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(7);
        page.setRootComponent("liveRoot");
        page.setProperties(props("title", "original"));
        Page live = this.insertRaw(page);

        draftEdit(live.getId(), p -> p.getProperties().put("title", "drafted"));

        Page moved = stored(live.getId());
        moved.setVersion(9);
        moved.getProperties().put("title", "live edit");
        this.mongoTemplate.save(moved).block();

        assertThrows(Exception.class, () -> asClient(this.pageService.publish(live.getId(), "shipping")),
                "a drift that cannot be reasoned about must not be published over");

        assertEquals("live edit", stored(live.getId()).getProperties().get("title"),
                "the newer live content was overwritten");
        assertEquals(1L, this.mongoTemplate.findAll(Draft.class).count().block(),
                "a refused publish must leave the draft intact so the work is not lost");
    }

    @Test
    @Timeout(60)
    @DisplayName("the version snapshot the merge reads is the draft's own base, not the newest one")
    void mergesFromTheFrozenBase() {

        Page live = createPage(props("a", "1"));

        draftEdit(live.getId(), p -> p.getProperties().put("draftOnly", "d"));

        // Two live edits, so the newest snapshot is NOT the draft's base. Reading the
        // wrong one would make the draft's own addition look like a live deletion.
        liveEdit(live.getId(), p -> p.getProperties().put("liveOne", "1"));
        liveEdit(live.getId(), p -> p.getProperties().put("liveTwo", "2"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        Page after = stored(live.getId());
        assertEquals("d", after.getProperties().get("draftOnly"));
        assertEquals("1", after.getProperties().get("liveOne"));
        assertEquals("2", after.getProperties().get("liveTwo"));
    }

    /**
     * The corner most likely to go wrong. A stored override is a DELTA against its
     * base, while both the draft content and a version snapshot are the APPLIED
     * form. Merging the delta against the applied form would read every value the
     * child inherits as a change the child deleted, so the child's document would
     * come out of a publish carrying its base's whole content as its own override.
     */
    @Test
    @Timeout(60)
    @DisplayName("an override merges in its applied form, not against its stored delta")
    void reconcilesAcrossAnOverrideChain() {

        setInheritance(List.of(SYSTEM, MID));

        Page base = new Page();
        base.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM);
        base.setRootComponent("rootComp");
        base.setProperties(props("fromBase", "b"));
        assertNotNull(asClient(this.pageService.create(base)));

        Page child = new Page();
        child.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(MID).setBaseClientCode(SYSTEM);
        child.setProperties(props("fromBase", "b", "fromChild", "c"));
        Page created = asMid(this.pageService.create(child));
        assertNotNull(created);

        Page edited = asMid(this.pageService.read(created.getId()));
        edited.getProperties().put("fromDraft", "d");
        assertNotNull(asMid(this.pageService.saveDraft(edited)));

        Page concurrent = asMid(this.pageService.read(created.getId()));
        concurrent.getProperties().put("fromLive", "L");
        assertNotNull(asMid(this.pageService.update(concurrent)));

        assertNotNull(asMid(this.pageService.publish(created.getId(), "shipping")));

        Page after = asMid(this.pageService.read(created.getId()));
        assertEquals("d", after.getProperties().get("fromDraft"));
        assertEquals("L", after.getProperties().get("fromLive"));
        assertEquals("b", after.getProperties().get("fromBase"), "the inherited value was lost");
        assertEquals("c", after.getProperties().get("fromChild"));

        Page delta = stored(created.getId());
        assertFalse(delta.getProperties().containsKey("fromBase"),
                "the child's override absorbed a value it only inherits, so it no longer tracks its base");
    }

    /**
     * The draft SURFACE, not the publish. The draft holds a snapshot of the live
     * document as it stood when the draft was taken, so serving it verbatim hid
     * everything published to live afterwards. Measured on websmith: the draft host
     * served an app definition with no `title` and no `links`, both published to
     * live after the draft was taken, while the live host had them.
     */
    @Test
    @Timeout(60)
    @DisplayName("the draft surface shows live changes made after the draft was taken")
    void draftSurfaceCarriesLiveChangesIn() {

        Page live = createPage(props("shared", "same"));

        draftEdit(live.getId(), p -> p.getProperties().put("fromDraft", "d"));
        liveEdit(live.getId(), p -> p.getProperties().put("fromLive", "L"));

        var drafted = onDraftSurface(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM));
        var served = asClient(drafted);
        assertNotNull(served);

        Map<String, Object> shown = served.getObject().getProperties();
        assertEquals("d", shown.get("fromDraft"), "the draft surface stopped showing the draft");
        assertEquals("L", shown.get("fromLive"),
                "the draft host is serving a snapshot from before the live change, so it shows the app "
                        + "as it was rather than as it will be");
    }

    @Test
    @Timeout(60)
    @DisplayName("the draft surface shows exactly what publishing would produce")
    void draftSurfaceMatchesThePublishedResult() {

        Page live = createPage(props("shared", "same", "contested", "orig"));

        draftEdit(live.getId(), p -> {
            p.getProperties().put("fromDraft", "d");
            p.getProperties().put("contested", "draftWins");
        });
        liveEdit(live.getId(), p -> {
            p.getProperties().put("fromLive", "L");
            p.getProperties().put("contested", "liveLoses");
        });

        Map<String, Object> preview = asClient(onDraftSurface(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM)))
                .getObject().getProperties();

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));
        Map<String, Object> published = stored(live.getId()).getProperties();

        // The draft surface is where someone decides whether to publish, so a
        // preview that could differ from the result would be worse than none.
        assertEquals(published, preview,
                "what the draft host showed is not what publishing produced");
    }

    @Test
    @Timeout(60)
    @DisplayName("the live surface is untouched by a draft that reconciles")
    void liveSurfaceIsUnaffected() {

        Page live = createPage(props("shared", "same"));

        draftEdit(live.getId(), p -> p.getProperties().put("fromDraft", "d"));
        liveEdit(live.getId(), p -> p.getProperties().put("fromLive", "L"));

        Map<String, Object> onLive = asClient(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM))
                .getObject().getProperties();

        assertEquals("L", onLive.get("fromLive"));
        assertFalse(onLive.containsKey("fromDraft"),
                "unpublished work leaked onto the live surface through the reconciliation");
    }

    @Test
    @Timeout(60)
    @DisplayName("identity is still taken from the stored document, never from reconciled content")
    void identityIsNotMergeable() {

        Page live = createPage(props("title", "original"));

        // A draft body claiming to be a different object, somewhere else.
        Page edited = asClient(this.pageService.read(live.getId()));
        edited.getProperties().put("title", "drafted");
        assertNotNull(asClient(this.pageService.saveDraft(edited)));

        Draft draft = this.mongoTemplate.findAll(Draft.class).blockFirst();
        assertNotNull(draft);
        draft.getContent().put("clientCode", "ELSEWHERE");
        draft.getContent().put("name", "someOtherPage");
        draft.getContent().put("version", 999);
        this.mongoTemplate.save(draft).block();

        liveEdit(live.getId(), p -> p.getProperties().put("fromLive", "L"));

        assertNotNull(asClient(this.pageService.publish(live.getId(), "shipping")));

        Page after = stored(live.getId());
        assertEquals(SYSTEM, after.getClientCode(), "draft content was able to rehome the object");
        assertEquals(PAGE_NAME, after.getName(), "draft content was able to rename the object");
        assertNull(this.mongoTemplate.findAll(Page.class).collectList().block().stream()
                .filter(p -> "someOtherPage".equals(p.getName())).findAny().orElse(null));
    }
}
