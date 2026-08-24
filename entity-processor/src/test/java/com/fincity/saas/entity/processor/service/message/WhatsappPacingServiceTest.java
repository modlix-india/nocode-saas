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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
        assertTrue(this.pacing.evaluate(this.healthy(), false, true, 0, List.of()).allowed());
    }

    @Test
    @DisplayName("opt-out beats everything, and is checked before any cap is computed")
    void optOutWins() {
        Decision decision = this.pacing.evaluate(this.healthy(), true, true, 0, List.of());

        assertFalse(decision.allowed());
        assertEquals(WhatsappHoldReason.OPTED_OUT, decision.reason());
    }

    @Test
    @DisplayName("a disconnected number holds rather than failing the send")
    void holdsWhenNotConnected() {
        assertEquals(
                WhatsappHoldReason.SESSION_NOT_READY,
                this.pacing.evaluate(this.healthy(), false, false, 0, List.of()).reason());
    }

    @Test
    @DisplayName("the sequence stops after too many unanswered messages")
    void stopsChasingQuietLeads() {
        assertEquals(
                WhatsappHoldReason.LEAD_QUIET,
                this.pacing.evaluate(this.healthy(), false, true, 3, List.of()).reason());
    }

    @Test
    @DisplayName("a reply rate under the critical floor suspends automated sending")
    void suspendsOnCriticalReplyRate() {
        WhatsappSessionHealth health = this.healthy().setReplyRate(0.10);

        assertEquals(
                WhatsappHoldReason.REPLY_RATE_LOW,
                this.pacing.evaluate(health, false, true, 0, List.of()).reason());
    }

    @Test
    @DisplayName("the 24-hour rule holds a lead who has not replied, and says when it releases")
    void holdsInsideTheReplyWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WhatsappSessionHealth health = this.healthy().setLastOutboundAt(now.minusHours(3));

        Decision decision = this.pacing.evaluate(health, false, true, 0, List.of());

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

        assertTrue(this.pacing.evaluate(health, false, true, 0, List.of()).allowed());
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
                this.pacing.evaluate(atRampCeiling, false, true, 0, List.of()).reason());

        // A mature number, no ramp left, that has already opened its allowance of new conversations.
        // The contact cap does not expire with the ramp.
        WhatsappSessionHealth atContactCeiling =
                this.healthy().setWarmUpDay(null).setWarmUpCap(null).setSentToday(500).setFirstContactsToday(20);

        assertEquals(
                WhatsappHoldReason.NEW_CONTACT_CAP,
                this.pacing.evaluate(atContactCeiling, false, true, 0, List.of()).reason());

        // And the case the conflation bug produced: a young number well inside its ramp with a
        // handful of new contacts used. Neither cap is reached, so it must send. Before the fix the
        // effective cap was min(rampCap, contactCap), which pinned everything at 20 and meant a
        // number on day 10 behaved exactly like one on day 1.
        WhatsappSessionHealth midRamp =
                this.healthy().setWarmUpDay(10).setWarmUpCap(100).setSentToday(45).setFirstContactsToday(5);

        assertTrue(this.pacing.evaluate(midRamp, false, true, 0, List.of()).allowed());
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
        // UTC, so the times below read as both the wall clock and the instant. The zone is set
        // explicitly rather than left to the default, because the whole point of the test below is
        // that those two are not the same thing.
        ReflectionTestUtils.setField(this.pacing, "quietHoursZone", "UTC");

        // 23:00 is inside the window, which opens at 09:00 the following morning.
        LocalDateTime lateEvening = LocalDateTime.of(2026, 8, 6, 23, 0);
        LocalDateTime opens = this.pacing.quietHoursHold(lateEvening, List.of());
        assertEquals(LocalDateTime.of(2026, 8, 7, 9, 0), opens);

        // 03:00 is the same window seen from the other side of midnight, and opens the same morning.
        LocalDateTime earlyMorning = LocalDateTime.of(2026, 8, 7, 3, 0);
        assertEquals(LocalDateTime.of(2026, 8, 7, 9, 0), this.pacing.quietHoursHold(earlyMorning, List.of()));

        // Mid-afternoon is not quiet at all.
        assertNull(this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 7, 15, 0), List.of()));
    }

    @Test
    @DisplayName("with no zone from the number, quiet hours fall back to the configured one, not to UTC")
    void quietHoursFallBackToTheConfiguredZone() {
        // Local hours against a UTC clock. The caller passes UTC because that is what the schema
        // stores, so the two have to be reconciled here or the whole offset is lost.
        ReflectionTestUtils.setField(this.pacing, "quietHoursStart", "21:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursEnd", "09:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursZone", "Asia/Kolkata");

        // The case that was actually broken. 08:05 UTC is 13:35 in India: the middle of a working
        // afternoon, and nowhere near the 21:00-09:00 window. Compared as a UTC wall clock it looks
        // like early morning, so every automated message was held right through the working day.
        assertNull(
                this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 8, 8, 5), List.of()),
                "13:35 in India is business hours; nothing should be held");

        // And the mirror of it, which is the one that would have done damage. 16:00 UTC is 21:30 in
        // India, inside the window. Judged in UTC it looks like a fine time to send, so the backlog
        // held all day would have gone out at half past nine in the evening.
        LocalDateTime opens = this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 8, 16, 0), List.of());
        assertNotNull(opens, "21:30 in India is inside quiet hours");

        // Opens at 09:00 India the next morning, which is 03:30 UTC, and the answer is in UTC
        // because that is what the caller schedules against.
        assertEquals(LocalDateTime.of(2026, 8, 9, 3, 30), opens);
    }

    @Test
    @DisplayName("quiet hours follow the lead's clock, not the business's")
    void quietHoursFollowTheLead() {
        ReflectionTestUtils.setField(this.pacing, "quietHoursStart", "21:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursEnd", "09:00");
        // An Indian business, which is what the fallback would have judged everybody on.
        ReflectionTestUtils.setField(this.pacing, "quietHoursZone", "Asia/Kolkata");

        List<ZoneId> gulf = List.of(ZoneId.of("Asia/Dubai"));

        // 15:45 UTC is 21:15 in Bangalore and 19:45 in Dubai. On the business's clock this lead is
        // inside quiet hours and the message waits; on their own clock it is a quarter to eight in
        // the evening and perfectly sendable. India to the Gulf is exactly the case B6c refuses to
        // restrict, so getting this backwards costs real messages on a real route.
        assertNull(
                this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 8, 15, 45), gulf),
                "19:45 where the lead is; nothing should be held");

        // The direction that matters more. 17:15 UTC is 21:15 in Dubai, so it is quiet where the
        // lead is even though the numbers differ from India's.
        LocalDateTime opens = this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 8, 17, 15), gulf);
        assertEquals(
                LocalDateTime.of(2026, 8, 9, 5, 0), opens, "opens at 09:00 in Dubai, which is 05:00 UTC");
    }

    @Test
    @DisplayName("when a number could be in several zones, quiet in any one of them holds it")
    void severalZonesHoldIfAnyIsQuiet() {
        ReflectionTestUtils.setField(this.pacing, "quietHoursStart", "21:00");
        ReflectionTestUtils.setField(this.pacing, "quietHoursEnd", "09:00");

        // A +1 number the mapper cannot pin down. The two coasts are three hours apart, so there is
        // no single answer and picking one silently would be wrong for half the leads it applies to.
        List<ZoneId> unsureAboutAmerica = List.of(ZoneId.of("America/New_York"), ZoneId.of("America/Los_Angeles"));

        // 01:30 UTC is 21:30 on the east coast and 18:30 on the west. Quiet in one of them, so it
        // holds: waiting a few extra hours costs a few extra hours, and guessing early costs a
        // complaint against a number that cannot be appealed.
        LocalDateTime opens = this.pacing.quietHoursHold(LocalDateTime.of(2026, 8, 9, 1, 30), unsureAboutAmerica);

        // And it opens at 09:00 on the coast that was quiet, which is 13:00 UTC. Taking the earliest
        // opening instead would release the message while the east coast was still asleep.
        assertEquals(LocalDateTime.of(2026, 8, 9, 13, 0), opens);
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
            Decision decision = this.pacing.evaluate(health, false, true, 0, List.of());
            assertFalse(decision.allowed(), "fixture was expected to be held");
            assertNotNull(decision.reason(), "a hold with no reason is unexplainable later");
        }

        assertNotNull(this.pacing.evaluate(this.healthy(), true, true, 0, List.of()).reason());
        assertNotNull(this.pacing.evaluate(this.healthy(), false, false, 0, List.of()).reason());
        assertNotNull(this.pacing.evaluate(this.healthy(), false, true, 5, List.of()).reason());
    }
}
