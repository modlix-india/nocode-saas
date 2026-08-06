package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.WhatsappPacingService.Decision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The Layer-2 gate.
 *
 * <p>Worth testing directly because it is pure, because every branch of it is a rule that used to be
 * Meta's and is now only ours, and because a gate that is written but never actually fires is the
 * documented failure mode for this kind of code. The two-cap case below is here specifically: those
 * limits were conflated once, which pinned every number at the lowest band and meant the warm-up ramp
 * never ramped.
 */
class WhatsappPacingServiceTest {

    private WhatsappPacingService pacing;

    @BeforeEach
    void setUp() {
        // The real bean takes these from configuration. Set explicitly so the assertions below read
        // against known thresholds rather than against whatever the defaults happen to be today.
        this.pacing = new WhatsappPacingService(null);
        ReflectionTestUtils.setField(this.pacing, "replyWindowHours", 24);
        ReflectionTestUtils.setField(this.pacing, "newContactsPerDay", 20);
        ReflectionTestUtils.setField(this.pacing, "sendsPerHour", 30);
        ReflectionTestUtils.setField(this.pacing, "replyRateLow", 0.30);
        ReflectionTestUtils.setField(this.pacing, "replyRateCritical", 0.15);
        ReflectionTestUtils.setField(this.pacing, "maxUnanswered", 3);
        ReflectionTestUtils.setField(this.pacing, "replyRateWindowDays", 14);
        ReflectionTestUtils.setField(this.pacing, "warmUpDays", 14);
        // Quiet hours disabled for most cases, so an unrelated gate does not decide the assertion
        // depending on what time the suite happens to run.
        ReflectionTestUtils.setField(this.pacing, "quietHoursStart", "00:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursEnd", "00:00");
    }

    private WhatsappSessionHealth healthy() {
        return new WhatsappSessionHealth()
                .setState("CONNECTED")
                .setReplyRate(0.5)
                .setSentToday(0)
                .setSentLastHour(0)
                .setHourlyCap(30)
                .setFirstContactsToday(0)
                .setFirstContactCap(20);
    }

    @Test
    @DisplayName("a healthy number with nothing outstanding may send")
    void allowsHealthy() {
        assertTrue(this.pacing.evaluate(this.healthy(), false, true, 0).allowed());
    }

    @Test
    @DisplayName("opt-out beats everything, and is checked before any cap is computed")
    void optOutWins() {
        Decision decision = this.pacing.evaluate(this.healthy(), true, true, 0);

        assertFalse(decision.allowed());
        assertEquals(WhatsappHoldReason.OPTED_OUT, decision.reason());
    }

    @Test
    @DisplayName("a disconnected number holds rather than failing the send")
    void holdsWhenNotConnected() {
        assertEquals(
                WhatsappHoldReason.SESSION_NOT_READY,
                this.pacing.evaluate(this.healthy(), false, false, 0).reason());
    }

    @Test
    @DisplayName("the sequence stops after too many unanswered messages")
    void stopsChasingQuietLeads() {
        assertEquals(
                WhatsappHoldReason.LEAD_QUIET,
                this.pacing.evaluate(this.healthy(), false, true, 3).reason());
    }

    @Test
    @DisplayName("a reply rate under the critical floor suspends automated sending")
    void suspendsOnCriticalReplyRate() {
        WhatsappSessionHealth health = this.healthy().setReplyRate(0.10);

        assertEquals(
                WhatsappHoldReason.REPLY_RATE_LOW,
                this.pacing.evaluate(health, false, true, 0).reason());
    }

    @Test
    @DisplayName("the 24-hour rule holds a lead who has not replied, and says when it releases")
    void holdsInsideTheReplyWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WhatsappSessionHealth health = this.healthy().setLastOutboundAt(now.minusHours(3));

        Decision decision = this.pacing.evaluate(health, false, true, 0);

        assertEquals(WhatsappHoldReason.WAITING_24H, decision.reason());
        // A hold with no retry time is a hold somebody has to poll blindly.
        assertNotNull(decision.retryAt());
    }

    @Test
    @DisplayName("a reply since our last message releases the hold with no extra state")
    void replyReleasesTheHold() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WhatsappSessionHealth health = this.healthy()
                .setLastOutboundAt(now.minusHours(3))
                .setLastInboundAt(now.minusHours(1));

        assertTrue(this.pacing.evaluate(health, false, true, 0).allowed());
    }

    @Test
    @DisplayName("the warm-up cap and the new-contact cap are separate limits")
    void warmUpAndContactCapsAreIndependent() {

        // A young number that has sent its daily total but opened no new conversations. Held by the
        // ramp, which is the limit that actually applies.
        WhatsappSessionHealth atRampCeiling =
                this.healthy().setWarmUpDay(2).setWarmUpCap(20).setSentToday(20).setFirstContactsToday(0);

        assertEquals(
                WhatsappHoldReason.WARM_UP_CAP,
                this.pacing.evaluate(atRampCeiling, false, true, 0).reason());

        // A mature number, no ramp left, that has already opened its allowance of new conversations.
        // The contact cap does not expire with the ramp.
        WhatsappSessionHealth atContactCeiling =
                this.healthy().setWarmUpDay(null).setWarmUpCap(null).setSentToday(500).setFirstContactsToday(20);

        assertEquals(
                WhatsappHoldReason.NEW_CONTACT_CAP,
                this.pacing.evaluate(atContactCeiling, false, true, 0).reason());

        // And the case the conflation bug produced: a young number well inside its ramp with a
        // handful of new contacts used. Neither cap is reached, so it must send. Before the fix the
        // effective cap was min(rampCap, contactCap), which pinned everything at 20 and meant a
        // number on day 10 behaved exactly like one on day 1.
        WhatsappSessionHealth midRamp =
                this.healthy().setWarmUpDay(10).setWarmUpCap(100).setSentToday(45).setFirstContactsToday(5);

        assertTrue(this.pacing.evaluate(midRamp, false, true, 0).allowed());
    }

    @Test
    @DisplayName("the ramp bands are the conservative ones, and end after the configured window")
    void rampBands() {
        assertEquals(20, this.pacing.warmUpCap(1));
        assertEquals(20, this.pacing.warmUpCap(3));
        assertEquals(50, this.pacing.warmUpCap(4));
        assertEquals(50, this.pacing.warmUpCap(7));
        assertEquals(100, this.pacing.warmUpCap(8));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        assertEquals(1, this.pacing.warmUpDay(now, now));
        assertEquals(14, this.pacing.warmUpDay(now.minusDays(13), now));
        // Past the window there is no ramp, which is a different thing from a cap of zero.
        assertNull(this.pacing.warmUpDay(now.minusDays(14), now));
        assertNull(this.pacing.warmUpDay(null, now));
    }

    @Test
    @DisplayName("a low reply rate halves the caps rather than stopping outright")
    void lowReplyRateThrottles() {
        // Between the two floors: throttled, not suspended.
        assertEquals(10, this.pacing.effectiveContactCap(0.20));
        assertEquals(20, this.pacing.effectiveContactCap(0.50));
        assertEquals(10, this.pacing.effectiveDailyCap(2, 0.20));
        assertEquals(20, this.pacing.effectiveDailyCap(2, 0.50));
        // No ramp means no daily total cap, throttled or otherwise.
        assertNull(this.pacing.effectiveDailyCap(null, 0.20));
    }

    @Test
    @DisplayName("quiet hours reschedule to the window opening, including across midnight")
    void quietHoursWrapMidnight() {
        ReflectionTestUtils.setField(this.pacing, "quietHoursStart", "21:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursEnd", "09:00");

        // 23:00 is inside the window, which opens at 09:00 the following morning.
        LocalDateTime lateEvening = LocalDateTime.of(2026, 8, 6, 23, 0);
        LocalDateTime opens = this.pacing.quietHoursHold(lateEvening);
        assertEquals(LocalDateTime.of(2026, 8, 7, 9, 0), opens);

        // 03:00 is the same window seen from the other side of midnight, and opens the same morning.
        LocalDateTime earlyMorning = LocalDateTime.of(2026, 8, 7, 3, 0);
        assertEquals(LocalDateTime.of(2026, 8, 7, 9, 0), this.pacing.quietHoursHold(earlyMorning));

        // Mid-afternoon is not quiet at all.
        assertNull(this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 7, 15, 0)));
    }

    @Test
    @DisplayName("every refusal carries a reason")
    void everyHoldHasAReason() {
        // The property that makes this design explainable months later. A held row with a null reason
        // is both unexplainable and untunable, so no branch may return a bare refusal.
        WhatsappSessionHealth[] cases = {
            this.healthy().setReplyRate(0.10),
            this.healthy().setLastOutboundAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)),
            this.healthy().setSentLastHour(30),
            this.healthy().setWarmUpDay(1).setWarmUpCap(20).setSentToday(20),
            this.healthy().setFirstContactsToday(20)
        };

        for (WhatsappSessionHealth health : cases) {
            Decision decision = this.pacing.evaluate(health, false, true, 0);
            assertFalse(decision.allowed(), "fixture was expected to be held");
            assertNotNull(decision.reason(), "a hold with no reason is unexplainable later");
        }

        assertNotNull(this.pacing.evaluate(this.healthy(), true, true, 0).reason());
        assertNotNull(this.pacing.evaluate(this.healthy(), false, false, 0).reason());
        assertNotNull(this.pacing.evaluate(this.healthy(), false, true, 5).reason());
    }
}
