package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Scoping a backfill to the gap the unlink left, rather than to whatever WhatsApp hands over.
 *
 * <p>A newly linked device is offered the recent history of every conversation on the handset. On
 * local, one relink imported 40 messages across 8 unrelated deals, most of them bodyless: a personal
 * number's own chats, not the messages the customer missed. The boundary that matters is the last
 * message already held on that number, because that instant is when the number stopped receiving.
 */
class WhatsappBackfillScopeTest {

    private static final LocalDateTime WATERMARK = LocalDateTime.of(2026, 9, 4, 12, 0, 0);

    private final WhatsappInboundService service = new WhatsappInboundService(null, null, null, null, null);

    private boolean isAfter(LocalDateTime candidate, LocalDateTime watermark) {
        return Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(WhatsappInboundService.class, "isAfter", candidate, watermark));
    }

    @Test
    @DisplayName("a message from the gap is imported")
    void newerThanTheWatermarkIsImported() {
        assertTrue(isAfter(WATERMARK.plusMinutes(1), WATERMARK));
        assertTrue(isAfter(WATERMARK.plusDays(2), WATERMARK));
    }

    @Test
    @DisplayName("history older than the last message we hold is discarded")
    void olderIsDiscarded() {
        assertTrue(!isAfter(WATERMARK.minusMinutes(1), WATERMARK));
        assertTrue(!isAfter(WATERMARK.minusDays(30), WATERMARK), "a month-old chat is not gap history");
    }

    /** Strictly after, so redelivering the boundary message itself does not re-import it. */
    @Test
    @DisplayName("the boundary message itself is not re-imported")
    void theBoundaryIsExcluded() {
        assertTrue(!isAfter(WATERMARK, WATERMARK));
    }

    /**
     * The first-link case, and the reason no feature flag is needed any more. With nothing held for
     * the number there is no gap, so every candidate is history that predates us caring.
     */
    @Test
    @DisplayName("with no earlier message, nothing is imported")
    void firstLinkImportsNothing() {
        assertTrue(!isAfter(LocalDateTime.now(), null), "a first link must import nothing");
    }

    /** Without a time there is no way to place a message in the gap. */
    @Test
    @DisplayName("an undated candidate is not imported")
    void undatedIsNotImported() {
        assertTrue(!isAfter(null, WATERMARK));
        assertTrue(!isAfter(null, null));
    }

    /**
     * The watermark is read from SENT_TIME, and a status-less message used to be stored without one.
     * That made every inbound and every backfilled message undated - sorting them to the bottom of
     * the thread, and leaving the watermark with nothing to measure against.
     */
    @Test
    @DisplayName("a status-less message still gets a time, so the watermark has something to read")
    void statuslessMessageStillGetsASentTime() {
        WhatsappMessage message = new WhatsappMessage();

        ReflectionTestUtils.invokeMethod(this.service, "applyStatusTimes", message, null, WATERMARK);

        assertEquals(WATERMARK, message.getSentTime(), "a message with no delivery status was stored undated");
    }

    @Test
    @DisplayName("an explicit status still fills its own column")
    void explicitStatusStillApplies() {
        WhatsappMessage read = new WhatsappMessage();
        ReflectionTestUtils.invokeMethod(this.service, "applyStatusTimes", read, WhatsappMessageStatus.READ, WATERMARK);
        assertEquals(WATERMARK, read.getReadTime());
        assertEquals(WATERMARK, read.getSentTime(), "ordering still needs a sent time");

        WhatsappMessage failed = new WhatsappMessage();
        ReflectionTestUtils.invokeMethod(
                this.service, "applyStatusTimes", failed, WhatsappMessageStatus.FAILED, WATERMARK);
        assertEquals(WATERMARK, failed.getFailedTime());
        assertNull(failed.getDeliveredTime());
    }
}
