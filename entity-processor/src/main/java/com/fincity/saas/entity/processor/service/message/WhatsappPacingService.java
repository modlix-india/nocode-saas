package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Decides whether an automated message is allowed out yet, and computes the numbers that decision is
 * made from.
 *
 * <p>This is Layer 2. Layer 1 lives in the bridge and spaces individual sends seconds apart; nothing
 * here is about seconds. What is here is the set of rules that used to be Meta's problem and are now
 * ours, because on the linked-device protocol nothing outside this codebase stops a burst of
 * messages.
 *
 * <p><b>Every refusal returns a reason.</b> Not one path returns a bare false. A held message with no
 * reason is unexplainable when somebody asks in three months, and it is also untunable: there is no
 * way to tell whether a cap is set right if nothing records that it fired.
 */
@Service
public class WhatsappPacingService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappPacingService.class);

    /** How the outcome of a gate is reported: allowed, or held with a reason and possibly a retry time. */
    public record Decision(boolean allowed, String reason, LocalDateTime retryAt) {

        public static Decision allow() {
            return new Decision(true, null, null);
        }

        public static Decision hold(String reason) {
            return new Decision(false, reason, null);
        }

        /** Held, but with a known time to try again, e.g. quiet hours ending. */
        public static Decision holdUntil(String reason, LocalDateTime retryAt) {
            return new Decision(false, reason, retryAt);
        }
    }

    private final WhatsappMessageDAO messageDao;

    /**
     * The gap a lead who has not replied gets before we write again.
     *
     * <p>Was Meta's policy and enforced by their API. Here it is our own rule and nothing enforces it
     * but this, which makes it the single most load-bearing thing between a linked number and a ban.
     */
    @Value("${processor.whatsapp.pacing.reply-window-hours:24}")
    private int replyWindowHours;

    /** New conversations one number may open in a day. */
    @Value("${processor.whatsapp.pacing.new-contacts-per-day:20}")
    private int newContactsPerDay;

    /** Matches the bridge's own Layer-1 ceiling, checked here to avoid a call that will be refused. */
    @Value("${processor.whatsapp.pacing.sends-per-hour:30}")
    private int sendsPerHour;

    /** Below this, caps halve. Below the critical floor, automated sending stops entirely. */
    @Value("${processor.whatsapp.pacing.reply-rate-low:0.30}")
    private double replyRateLow;

    @Value("${processor.whatsapp.pacing.reply-rate-critical:0.15}")
    private double replyRateCritical;

    /** Consecutive unanswered messages before a sequence stops and a person is asked to look. */
    @Value("${processor.whatsapp.pacing.max-unanswered:3}")
    private int maxUnanswered;

    /** Days over which the reply rate is measured. */
    @Value("${processor.whatsapp.pacing.reply-rate-window-days:14}")
    private int replyRateWindowDays;

    @Value("${processor.whatsapp.pacing.warm-up-days:14}")
    private int warmUpDays;

    @Value("${processor.whatsapp.pacing.quiet-hours-start:21:00}")
    private String quietHoursStart;

    @Value("${processor.whatsapp.pacing.quiet-hours-end:09:00}")
    private String quietHoursEnd;

    /**
     * The clock quiet hours fall back to when the lead's own number says nothing.
     *
     * <p>A fallback, not the rule. The rule is the <b>lead's</b> local time, derived from their phone
     * number, because the lead is the person being woken up and the person who reports the number.
     * This is only reached when the number cannot be placed at all: a malformed one, or a
     * non-geographic range such as a satellite number.
     *
     * <p>Everything else in this service works in UTC because that is how the timestamps are stored,
     * and comparing a UTC wall clock against local hours is off by the whole offset: with 21:00 to
     * 09:00 and a UTC server, 13:30 in India is 08:00 UTC, which reads as the middle of the quiet
     * window, so every automated message was held through the entire Indian working morning. The
     * reverse is worse, because 21:00 local falls outside the UTC window and the messages that were
     * held all day would have gone out at nine in the evening.
     */
    @Value("${processor.whatsapp.pacing.quiet-hours-zone:Asia/Kolkata}")
    private String quietHoursZone;

    public WhatsappPacingService(WhatsappMessageDAO messageDao) {
        this.messageDao = messageDao;
    }

    /**
     * Everything known about a number's standing, in one read.
     *
     * <p>Feeds the standing panel on the integration page and the override panel in the composer
     * from the same computation, so the two cannot show different numbers to somebody deciding
     * whether to override.
     */
    public Mono<WhatsappSessionHealth> health(
            String appCode,
            String clientCode,
            String sessionId,
            String phoneNumber,
            String state,
            LocalDateTime linkedAt,
            List<ULong> ticketIds) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime dayStart = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime hourAgo = now.minusHours(1);
        LocalDateTime rateWindow = now.minusDays(this.replyRateWindowDays);

        return Mono.zip(
                        this.messageDao.replyRate(appCode, clientCode, sessionId, rateWindow),
                        this.messageDao.firstContactsSince(appCode, clientCode, sessionId, dayStart),
                        this.messageDao.sentSince(appCode, clientCode, sessionId, hourAgo),
                        this.messageDao.recentFailures(appCode, clientCode, sessionId, now.minusHours(6)),
                        this.messageDao
                                .lastOutboundAt(appCode, clientCode, ticketIds)
                                .map(Maybe::of)
                                .defaultIfEmpty(Maybe.empty()),
                        this.messageDao
                                .lastInboundAt(appCode, clientCode, ticketIds)
                                .map(Maybe::of)
                                .defaultIfEmpty(Maybe.empty()),
                        this.messageDao.consecutiveUnanswered(appCode, clientCode, ticketIds),
                        // Total sends today, which is a different quantity from first contacts and
                        // is what the warm-up ramp actually bounds.
                        this.messageDao.sentSince(appCode, clientCode, sessionId, dayStart))
                .map(t -> {
                    double replyRate = t.getT1();
                    int firstContacts = t.getT2();
                    int sentLastHour = t.getT3();
                    int failures = t.getT4();
                    LocalDateTime lastOutbound = t.getT5().value();
                    LocalDateTime lastInbound = t.getT6().value();
                    int unanswered = t.getT7();
                    int sentToday = t.getT8();

                    Integer warmUpDay = this.warmUpDay(linkedAt, now);

                    return new WhatsappSessionHealth()
                            .setSessionId(sessionId)
                            .setPhoneNumber(phoneNumber)
                            .setState(state)
                            .setLinkedAt(linkedAt)
                            .setWarmUpDay(warmUpDay)
                            .setWarmUpCap(this.effectiveDailyCap(warmUpDay, replyRate))
                            .setSentToday(sentToday)
                            .setFirstContactsToday(firstContacts)
                            .setFirstContactCap(this.effectiveContactCap(replyRate))
                            .setSentLastHour(sentLastHour)
                            .setHourlyCap(this.sendsPerHour)
                            .setReplyRate(replyRate)
                            .setReplyRateBand(this.band(replyRate))
                            .setUnansweredRolling30d(unanswered)
                            .setRecentFailures(failures)
                            .setLastOutboundAt(lastOutbound)
                            .setLastInboundAt(lastInbound)
                            .setHeldUntil(this.heldUntil(lastOutbound, lastInbound))
                            .setQuietHoursStart(this.quietHoursStart)
                            .setQuietHoursEnd(this.quietHoursEnd)
                            .setAutomationSuspended(replyRate < this.replyRateCritical);
                });
    }

    /**
     * Applies every gate, in the order a person would.
     *
     * <p>Cheapest and most permanent first. Opt-out before caps, because an opted-out lead should
     * never be evaluated against a quota at all, and because that answer never changes so there is
     * no point computing anything else.
     */
    public Decision evaluate(
            WhatsappSessionHealth health,
            boolean optedOut,
            boolean sessionSendable,
            int unanswered,
            List<ZoneId> leadZones) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (optedOut) return Decision.hold(WhatsappHoldReason.OPTED_OUT);

        if (!sessionSendable) return Decision.hold(WhatsappHoldReason.SESSION_NOT_READY);

        // Stop chasing. Continuing is not merely useless: unanswered messages accumulate and are
        // themselves part of what throttles the number.
        if (unanswered >= this.maxUnanswered) return Decision.hold(WhatsappHoldReason.LEAD_QUIET);

        Double replyRate = health.getReplyRate();
        if (replyRate != null && replyRate < this.replyRateCritical)
            return Decision.hold(WhatsappHoldReason.REPLY_RATE_LOW);

        // The 24-hour rule. A reply since our last message releases it with no extra state, because
        // the gate is a comparison rather than a flag somebody has to remember to clear.
        LocalDateTime lastOutbound = health.getLastOutboundAt();
        LocalDateTime lastInbound = health.getLastInboundAt();

        if (lastOutbound != null) {
            boolean repliedSince = lastInbound != null && lastInbound.isAfter(lastOutbound);
            LocalDateTime releasesAt = lastOutbound.plusHours(this.replyWindowHours);

            if (!repliedSince && now.isBefore(releasesAt))
                return Decision.holdUntil(WhatsappHoldReason.WAITING_24H, releasesAt);
        }

        if (health.getSentLastHour() != null && health.getSentLastHour() >= this.sendsPerHour)
            return Decision.holdUntil(WhatsappHoldReason.HOURLY_CAP, now.plusMinutes(15));

        LocalDateTime tomorrow = now.truncatedTo(ChronoUnit.DAYS).plusDays(1);

        // Two separate daily limits, and conflating them is easy to do and wrong.
        //
        // The warm-up ramp bounds TOTAL messages a young number sends in a day. The new-contact cap
        // bounds how many NEW conversations any number opens in a day, whatever its age. A number
        // three weeks old has no ramp left but still may not cold-message fifty people, and a number
        // three days old may not send its 200th message even if they are all replies.
        if (health.getWarmUpDay() != null
                && health.getSentToday() != null
                && health.getWarmUpCap() != null
                && health.getSentToday() >= health.getWarmUpCap())
            return Decision.holdUntil(WhatsappHoldReason.WARM_UP_CAP, tomorrow);

        if (health.getFirstContactsToday() != null
                && health.getFirstContactCap() != null
                && health.getFirstContactsToday() >= health.getFirstContactCap())
            return Decision.holdUntil(WhatsappHoldReason.NEW_CONTACT_CAP, tomorrow);

        LocalDateTime windowOpens = this.quietHoursHold(now, leadZones);
        if (windowOpens != null) return Decision.holdUntil(WhatsappHoldReason.QUIET_HOURS, windowOpens);

        return Decision.allow();
    }

    /**
     * Whether now is inside quiet hours <b>where the lead is</b>, and when the window next opens.
     *
     * <p>Judged on the lead's clock, not ours. They are the person a 3am message wakes up and the
     * person who reports the number for it, so the business's own hours are the wrong measure the
     * moment it writes to anyone abroad. That is not a hypothetical here: an India-hosted number
     * selling to Gulf buyers is the case B6c explicitly refuses to restrict, and India to the UAE is
     * an hour and a half apart, so 21:00 in Bangalore is 19:30 in Dubai. Judging the Dubai lead on
     * Indian hours holds a message that was fine to send and, worse, releases one at half past seven
     * in the morning.
     *
     * <p><b>Several zones means quiet if any of them is quiet.</b> A number that cannot be pinned to
     * one zone is usually one of a handful of adjacent candidates, and the two ways of being wrong
     * are not symmetrical: waiting a few extra hours costs a few extra hours, and guessing early
     * costs a complaint against a number that cannot be appealed. The release time is then the last
     * of the candidate windows to open, for the same reason.
     *
     * <p>Handles a window that wraps midnight, which the default one does. Reschedules rather than
     * dropping: the message is still wanted, just not now.
     *
     * @param zones the lead's candidate zones, from {@link PhoneUtil#zonesOf}. Empty falls back to
     *     the configured zone, which is all that can be done for a number that cannot be placed.
     */
    public LocalDateTime quietHoursHold(LocalDateTime now, List<ZoneId> zones) {
        LocalTime start = this.parseTime(this.quietHoursStart);
        LocalTime end = this.parseTime(this.quietHoursEnd);

        if (start == null || end == null || start.equals(end)) return null;

        LocalDateTime latestOpening = null;

        for (ZoneId zone : zones == null || zones.isEmpty() ? List.of(this.fallbackZone()) : zones) {
            LocalDateTime opens = this.quietHoursHoldIn(now, start, end, zone);

            // Quiet in any candidate holds the message, and the last window to open is when every
            // candidate is clear. Both follow from not knowing which zone the lead is actually in.
            if (opens != null && (latestOpening == null || opens.isAfter(latestOpening))) latestOpening = opens;
        }

        return latestOpening;
    }

    /**
     * The same test in one specific zone.
     *
     * <p>Into that clock before comparing, and back to UTC before returning. The caller works in UTC
     * throughout; only this comparison is local, because only these hours are local.
     */
    private LocalDateTime quietHoursHoldIn(LocalDateTime now, LocalTime start, LocalTime end, ZoneId zone) {

        ZonedDateTime local = now.atZone(ZoneOffset.UTC).withZoneSameInstant(zone);

        LocalTime at = local.toLocalTime();
        boolean wraps = start.isAfter(end);
        boolean quiet = wraps ? (!at.isBefore(start) || at.isBefore(end)) : (!at.isBefore(start) && at.isBefore(end));

        if (!quiet) return null;

        ZonedDateTime opensToday = local.toLocalDate().atTime(end).atZone(zone);
        ZonedDateTime opens = at.isBefore(end) ? opensToday : opensToday.plusDays(1);

        return opens.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * The clock a lead's quiet hours are being judged on, named for display.
     *
     * <p>Lists every candidate rather than picking one when the number does not pin a zone down,
     * because the hold really is against all of them: reporting one of three would make a countdown
     * derived from a different one look like an error.
     */
    public String quietHoursZoneLabel(List<ZoneId> leadZones) {

        if (leadZones == null || leadZones.isEmpty()) return this.fallbackZone().getId();

        return leadZones.stream().map(ZoneId::getId).collect(Collectors.joining(", "));
    }

    /** Falls back to UTC on an unusable zone, so a typo cannot hold every message indefinitely. */
    private ZoneId fallbackZone() {
        try {
            return ZoneId.of(this.quietHoursZone);
        } catch (Exception e) {
            logger.error(
                    "quiet-hours-zone '{}' is not a zone id, so quiet hours are being judged in UTC."
                            + " Messages will be held at the wrong times until this is corrected.",
                    this.quietHoursZone,
                    e);
            return ZoneOffset.UTC;
        }
    }

    /**
     * Day within the ramp, or null once past it.
     *
     * <p>Derived from the link date rather than stored, so it cannot be edited. Cold start is when a
     * ban is most likely, which makes a settable cap a field somebody raises on exactly the
     * afternoon it should not be raised.
     */
    public Integer warmUpDay(LocalDateTime linkedAt, LocalDateTime now) {
        if (linkedAt == null) return null;

        long days = Duration.between(linkedAt, now).toDays();
        if (days < 0 || days >= this.warmUpDays) return null;

        return (int) days + 1;
    }

    /**
     * Total messages a number may send on a given day of its ramp.
     *
     * <p>The conservative end of the bands whapi publishes: 20 a day for the first three days, 50 to
     * day seven, 100 to day fourteen, unlimited by this rule after that. Their upper figures (50,
     * 100, 200) are what they call survivable; there is no reason to sit at the top of a range whose
     * downside is somebody's business number.
     */
    public int warmUpCap(int warmUpDay) {
        if (warmUpDay <= 3) return 20;
        if (warmUpDay <= 7) return 50;
        return 100;
    }

    /**
     * The daily total cap in force, or null once the number is past its ramp.
     *
     * <p>Distinct from {@link #effectiveContactCap}: this bounds every message, that one bounds only
     * conversations started from scratch.
     */
    public Integer effectiveDailyCap(Integer warmUpDay, Double replyRate) {
        if (warmUpDay == null) return null;
        return this.throttled(this.warmUpCap(warmUpDay), replyRate);
    }

    /**
     * New conversations a number may open in a day, at any age.
     *
     * <p>Does not lift when the ramp ends. Cold outreach volume is the part that carries essentially
     * all of the ban risk, so it stays capped for the life of the number.
     */
    public int effectiveContactCap(Double replyRate) {
        return this.throttled(this.newContactsPerDay, replyRate);
    }

    /**
     * Halves a cap when too few people are replying.
     *
     * <p>The throttle doing its job: a number people are not answering should be talking to fewer
     * new people, not to the same number of them more slowly.
     */
    private int throttled(int base, Double replyRate) {
        if (replyRate != null && replyRate < this.replyRateLow) return Math.max(1, base / 2);
        return base;
    }

    public String band(Double replyRate) {
        if (replyRate == null) return "HEALTHY";
        if (replyRate < this.replyRateCritical) return "CRITICAL";
        if (replyRate < this.replyRateLow) return "LOW";
        return "HEALTHY";
    }

    /** When the 24-hour rule would release on its own, or null if nothing is holding. */
    public LocalDateTime heldUntil(LocalDateTime lastOutbound, LocalDateTime lastInbound) {
        if (lastOutbound == null) return null;
        if (lastInbound != null && lastInbound.isAfter(lastOutbound)) return null;

        LocalDateTime releases = lastOutbound.plusHours(this.replyWindowHours);
        return releases.isAfter(LocalDateTime.now(ZoneOffset.UTC)) ? releases : null;
    }

    public int getMaxUnanswered() {
        return this.maxUnanswered;
    }

    private LocalTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value.trim());
        } catch (Exception e) {
            logger.error("Could not parse the quiet-hours boundary {}; treating it as unset.", value, e);
            return null;
        }
    }

    /**
     * A nullable carried through {@code Mono.zip}, which drops empties.
     *
     * <p>Without it, a deal with no outbound message yet would silently collapse the entire zip and
     * the health read would return nothing at all, which reads as "healthy" to every caller. That is
     * the wrong default in the one place where wrong defaults get a number banned.
     */
    private record Maybe(LocalDateTime value) {

        static Maybe of(LocalDateTime value) {
            return new Maybe(value);
        }

        static Maybe empty() {
            return new Maybe(null);
        }
    }
}
