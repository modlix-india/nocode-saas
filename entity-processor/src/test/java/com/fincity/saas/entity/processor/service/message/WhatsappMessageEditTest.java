package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/**
 * Message editing: the current wording replaces the old one, and every old one is kept.
 *
 * <p>Before this existed an edit arrived as a ProtocolMessage the bridge did not decode, was typed
 * "system" with no body and stored under its own id, so the original kept its stale text and an
 * empty bubble appeared beside it. 63 such rows existed on local, every one of them empty.
 *
 * <p>These cover the consumer half. The bridge half - reading the replacement out of
 * ProtocolMessage.EditedMessage rather than the unrelated Message.EditedMessage field that unwrap
 * descends into - is covered in the bridge's own event_test.go.
 */
class WhatsappMessageEditTest {

    private static final String REVISIONS = "revisions";
    private static final LocalDateTime FIRST_EDIT = LocalDateTime.of(2026, 9, 4, 14, 8, 40);
    private static final LocalDateTime SECOND_EDIT = LocalDateTime.of(2026, 9, 4, 14, 12, 0);

    private WhatsappMessageDAO dao;
    private WhatsappInboundService service;

    @BeforeEach
    void setUp() {
        this.dao = mock(WhatsappMessageDAO.class);
        this.service = new WhatsappInboundService(this.dao, null, null, null, null);
        // the update is the last step; hand back whatever it was given
        when(this.dao.update(any(WhatsappMessage.class)))
                .thenAnswer(i -> Mono.just(i.getArgument(0, WhatsappMessage.class)));
    }

    private WhatsappMessage apply(WhatsappMessage message, WhatsappInboundRequest request) {
        Mono<WhatsappMessage> result = ReflectionTestUtils.invokeMethod(this.service, "applyEdit", message, request);
        assertNotNull(result);
        return result.block();
    }

    private static WhatsappInboundRequest edit(String body, LocalDateTime at) {
        return new WhatsappInboundRequest()
                .setEventType("MESSAGE_EDIT")
                .setMetaMessageId("m1")
                .setBodyText(body)
                .setOccurredAt(at);
    }

    private static WhatsappMessage stored(String body) {
        return (WhatsappMessage) new WhatsappMessage().setBodyText(body);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> revisionsOf(WhatsappMessage m) {
        assertNotNull(m.getBodyRevisions(), "no revision trail was written");
        return (List<Map<String, Object>>) m.getBodyRevisions().get(REVISIONS);
    }

    @Test
    @DisplayName("the new wording becomes current and the old one is kept")
    void firstEditKeepsTheOriginal() {

        WhatsappMessage result = apply(stored("50L works for me"), edit("45L works for me", FIRST_EDIT));

        assertEquals("45L works for me", result.getBodyText(), "bodyText must be the current wording");
        assertEquals(FIRST_EDIT, result.getEditedAt());

        List<Map<String, Object>> revisions = revisionsOf(result);
        assertEquals(1, revisions.size());
        assertEquals("50L works for me", revisions.getFirst().get("text"));
        assertEquals(FIRST_EDIT.toString(), revisions.getFirst().get("replacedAt"));
        assertEquals(1, revisions.getFirst().get("version"), "version 1 is the original wording");
    }

    /**
     * The requirement in full: however many times they edit it, every wording survives, oldest
     * first, with the original at the front.
     */
    @Test
    @DisplayName("keeps every wording across repeated edits, oldest first")
    void keepsEveryRevision() {

        WhatsappMessage m = stored("50L works for me");
        m = apply(m, edit("45L works for me", FIRST_EDIT));
        m = apply(m, edit("40L is my budget", SECOND_EDIT));
        m = apply(m, edit("38L final", SECOND_EDIT.plusMinutes(5)));

        assertEquals("38L final", m.getBodyText());

        List<Map<String, Object>> revisions = revisionsOf(m);
        assertEquals(
                List.of("50L works for me", "45L works for me", "40L is my budget"),
                revisions.stream().map(r -> r.get("text")).toList(),
                "the trail must read oldest first, starting at what the sender originally wrote");
        assertEquals(
                List.of(1, 2, 3),
                revisions.stream().map(r -> r.get("version")).toList(),
                "versions must number 1..n so the thread can label the original without an index");
    }

    /**
     * Required, not tidy. Every handoff in this chain is redeliverable and the outbox replays, so
     * the same edit arriving twice must not record the same wording twice.
     */
    @Test
    @DisplayName("a redelivered edit records nothing new")
    void redeliveryIsANoOp() {

        WhatsappMessage m = apply(stored("50L works"), edit("45L works", FIRST_EDIT));
        int after = revisionsOf(m).size();

        WhatsappMessage again = apply(m, edit("45L works", FIRST_EDIT));

        assertEquals(after, revisionsOf(again).size(), "a repeat of the same wording added a revision");
        assertEquals("45L works", again.getBodyText());
    }

    @Test
    @DisplayName("an edit that changes nothing is not recorded")
    void unchangedBodyIsNotRecorded() {

        WhatsappMessage result = apply(stored("same text"), edit("same text", FIRST_EDIT));

        assertNull(result.getBodyRevisions());
        assertNull(result.getEditedAt());
        verify(this.dao, never()).update(any(WhatsappMessage.class));
    }

    /**
     * An edit into nothing is a deletion and arrives as a DELETED status instead, so a blank body
     * here is a shape nobody decoded. Blanking the bubble it claims to correct is worse than
     * ignoring it.
     */
    @Test
    @DisplayName("a blank replacement never blanks the message")
    void blankReplacementIsIgnored() {

        for (String blank : new String[] {null, "", "   "}) {
            WhatsappMessage result = apply(stored("real content"), edit(blank, FIRST_EDIT));

            assertEquals("real content", result.getBodyText(), "a blank edit overwrote the body");
            assertNull(result.getEditedAt());
        }
        verify(this.dao, never()).update(any(WhatsappMessage.class));
    }

    /**
     * A caption added to a photo that arrived without one. Unusual but real, and dropping the empty
     * first entry would make the trail claim the caption was the original wording.
     */
    @Test
    @DisplayName("an edit onto an empty body still records that it was empty")
    void editOntoAnEmptyBodyRecordsTheGap() {

        WhatsappMessage result = apply(stored(null), edit("caption added later", FIRST_EDIT));

        assertEquals("caption added later", result.getBodyText());
        List<Map<String, Object>> revisions = revisionsOf(result);
        assertEquals(1, revisions.size());
        assertNull(revisions.getFirst().get("text"), "the empty original must still occupy the first slot");
    }

    /** Falls back to now rather than failing, so a handoff missing its timestamp still applies. */
    @Test
    @DisplayName("an edit with no timestamp still applies")
    void missingTimestampStillApplies() {

        WhatsappMessage result = apply(stored("before"), edit("after", null));

        assertEquals("after", result.getBodyText());
        assertNotNull(result.getEditedAt(), "editedAt must be set even when the handoff carries no time");
    }

    /** MESSAGE_EDIT has to be recognised, and must not be confused with the other patch kinds. */
    @Test
    @DisplayName("the event type is recognised and distinct")
    void eventTypeIsRecognised() {

        WhatsappInboundRequest e = edit("x", FIRST_EDIT);
        assertTrue(e.isMessageEdit());
        assertTrue(!e.isMediaReady() && !e.isStatusUpdate() && !e.isProfilePicture());

        assertTrue(new WhatsappInboundRequest().setEventType("message_edit").isMessageEdit(), "casing");
        assertTrue(!new WhatsappInboundRequest().setEventType("MESSAGE_STATUS").isMessageEdit());
    }
}
