package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.WhatsappPacingService.Decision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Copying the gate's verdict onto a health reading.
 *
 * <p>Small, but it is the only thing standing between the override panel and being decorative. The
 * panel is built entirely around "why is this held and when does it release", and both answers come
 * from here. This was found missing rather than broken: {@code health()} populated every cap and
 * every rate but never the reason, so the composer would have known a message was held and had
 * nothing to say about it.
 */
class WhatsappSessionHealthDecisionTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC);

    @Test
    @DisplayName("a hold carries its reason and the sentence a person reads")
    void holdCarriesReasonAndExplanation() {

        WhatsappSessionHealth health = WhatsappSessionService.applyDecision(
                new WhatsappSessionHealth(), Decision.hold(WhatsappHoldReason.LEAD_QUIET));

        assertEquals(WhatsappHoldReason.LEAD_QUIET, health.getHoldReason());
        assertNotNull(health.getHoldExplanation(), "a reason with no sentence is unreadable in the panel");
        assertEquals(WhatsappHoldReason.explain(WhatsappHoldReason.LEAD_QUIET), health.getHoldExplanation());
    }

    @Test
    @DisplayName("heldUntil comes from the decision, not from the 24-hour computation")
    void heldUntilFollowsTheActualHold() {

        // health() fills heldUntil from the 24-hour rule alone. When the rule actually holding the
        // message is a different one, that value describes the wrong clock entirely.
        LocalDateTime twentyFourHourClock = NOW.plusHours(3);
        LocalDateTime windowOpens = NOW.plusHours(11);

        WhatsappSessionHealth health = WhatsappSessionService.applyDecision(
                new WhatsappSessionHealth().setHeldUntil(twentyFourHourClock),
                Decision.holdUntil(WhatsappHoldReason.QUIET_HOURS, windowOpens));

        assertEquals(
                windowOpens,
                health.getHeldUntil(),
                "the countdown must be the one belonging to the rule that is holding the message");
    }

    @Test
    @DisplayName("nothing held clears a stale countdown rather than leaving it to render")
    void allowedClearsEverything() {

        // The dangerous direction. A leftover heldUntil renders as a countdown on a message that is
        // not being held at all, and it reads as fact to whoever is deciding whether to override.
        WhatsappSessionHealth health = WhatsappSessionService.applyDecision(
                new WhatsappSessionHealth()
                        .setHeldUntil(NOW.plusHours(6))
                        .setHoldReason(WhatsappHoldReason.WAITING_24H)
                        .setHoldExplanation("stale"),
                Decision.allow());

        assertNull(health.getHoldReason());
        assertNull(health.getHoldExplanation());
        assertNull(health.getHeldUntil(), "a countdown with nothing behind it is worse than no countdown");
    }

    @Test
    @DisplayName("a hold with no natural release reports no countdown")
    void permanentHoldHasNoCountdown() {

        // Opt-out never releases on a timer. Showing any time here would promise the sequence
        // resumes by itself, which is the opposite of what an opt-out means.
        WhatsappSessionHealth health = WhatsappSessionService.applyDecision(
                new WhatsappSessionHealth().setHeldUntil(NOW.plusHours(2)),
                Decision.hold(WhatsappHoldReason.OPTED_OUT));

        assertEquals(WhatsappHoldReason.OPTED_OUT, health.getHoldReason());
        assertNull(health.getHeldUntil());
    }

    @Test
    @DisplayName("a missing decision is treated as nothing held")
    void nullDecisionIsNotAHold() {

        WhatsappSessionHealth health =
                WhatsappSessionService.applyDecision(new WhatsappSessionHealth().setHeldUntil(NOW.plusHours(1)), null);

        assertNull(health.getHoldReason());
        assertNull(health.getHeldUntil());
    }
}
