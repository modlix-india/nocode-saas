package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappOutboxDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.WhatsappOutbox;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.message.WhatsappPacingService.Decision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Layer 2 of the pacing design: the thing that actually decides, every fifteen minutes, whether a
 * queued message may go.
 *
 * <p>Without this the outbox is a table nobody reads. The gates in {@link WhatsappPacingService} only
 * matter because something comes back and re-asks them: the 24-hour rule releases when a lead replies,
 * quiet hours release when the window opens, and a daily cap releases tomorrow. None of those can be
 * decided at the moment a message is queued, which is the whole reason this is a sweeper and not a
 * delayed send.
 *
 * <p>A held row is re-evaluated from scratch on every pass rather than remembering its previous
 * verdict. State that a lead has replied, or that a number's reply rate has recovered, arrives from
 * outside this service entirely, so a cached decision would go stale in exactly the direction that
 * keeps messages held forever.
 *
 * <p><b>Nothing here can force.</b> The force flag is checked against a real user context on the
 * interactive path, and this task has no user. That is the server-side half of "automation cannot
 * override the pacing rules", and it holds against a hand-crafted request because the flag is not
 * read on this path at all.
 */
@Component
public class WhatsappOutboxSweeper {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappOutboxSweeper.class);

    /** Recorded on the row so a send can be told apart from a hold that simply expired. */
    private static final String DECISION_RELEASED_BY_REPLY = "RELEASED_BY_REPLY";

    private static final String DECISION_RELEASED_BY_TIMER = "RELEASED_BY_TIMER";

    private final WhatsappOutboxDAO outboxDao;
    private final WhatsappSessionService sessionService;
    private final TicketService ticketService;

    @Value("${processor.whatsapp.outbox.sweep-batch-size:50}")
    private int batchSize;

    @Value("${processor.whatsapp.outbox.max-attempts:5}")
    private int maxAttempts;

    /** How long a row may sit pending before it is worth telling somebody about. */
    @Value("${processor.whatsapp.outbox.stale-after-hours:72}")
    private int staleAfterHours;

    public WhatsappOutboxSweeper(
            WhatsappOutboxDAO outboxDao, WhatsappSessionService sessionService, TicketService ticketService) {
        this.outboxDao = outboxDao;
        this.sessionService = sessionService;
        this.ticketService = ticketService;
    }

    /**
     * Fifteen minutes, which is the coarse end of the two timescales on purpose.
     *
     * <p>Layer 1 in the bridge handles seconds. Nothing here needs to be prompt: the shortest hold
     * this evaluates is quiet hours and the longest is a day, so a message released a few minutes
     * after it technically could have gone costs nothing, while a tighter loop would mean re-running
     * every gate for every tenant far more often than any of them change.
     */
    @Scheduled(
            initialDelayString = "${processor.whatsapp.outbox.sweep-initial-delay-ms:120000}",
            fixedDelayString = "${processor.whatsapp.outbox.sweep-interval-ms:900000}")
    public void sweep() {
        this.outboxDao
                .readDue(LocalDateTime.now(ZoneOffset.UTC), this.batchSize)
                .flatMapMany(Flux::fromIterable)
                // Strictly one at a time, and in queued order. A packet drains in sequence and stops
                // if one of its messages fails, which cannot be decided if several are in flight at
                // once. Concurrency here would also defeat the per-session caps, since each row
                // would read a count taken before its siblings sent.
                .concatMap(row -> this.process(row).onErrorResume(e -> {
                    logger.error("Sweeper could not process outbox row {}.", row.getId(), e);
                    return this.outboxDao
                            .recordFailure(
                                    row.getId(),
                                    e.getMessage(),
                                    row.getAttempts() == null ? 0 : row.getAttempts(),
                                    this.maxAttempts)
                            .then();
                }))
                .then(this.reportStale())
                .subscribe(null, e -> logger.error("WhatsApp outbox sweep failed.", e));
    }

    private Mono<Void> process(WhatsappOutbox row) {

        if (row.getTicketId() == null || row.getBodyText() == null || row.getBodyText().isBlank())
            return this.cancel(row, "MALFORMED");

        return this.ticketService
                .findById(row.getTicketId())
                .flatMap(ticket -> this.evaluateAndSend(row, ticket))
                // The deal is gone. Cancelled rather than left pending, because a row that can never
                // be evaluated would otherwise be retried every fifteen minutes forever and would
                // eventually be the only thing in the stale-row alert.
                .switchIfEmpty(this.cancel(row, "TICKET_MISSING").then(Mono.empty()))
                .then();
    }

    private Mono<Void> evaluateAndSend(WhatsappOutbox row, Ticket ticket) {

        // Checked before anything else and terminal rather than held. An opt-out arriving after a
        // message was queued is the common case, since the queueing happened when the deal was
        // created and the lead replied afterwards.
        if (Boolean.TRUE.equals(ticket.getWhatsappOptedOut()))
            return this.outboxDao
                    .cancelPendingForTicket(ticket.getId(), WhatsappHoldReason.OPTED_OUT)
                    .then();

        return this.outboxDao
                .hasEarlierFailure(row.getTicketId(), row.getSequenceOrder() == null ? 0 : row.getSequenceOrder())
                .flatMap(earlierFailed -> {
                    // Stop the packet rather than firing the remaining messages into the same wall.
                    // A brochure that arrives without the introduction that was meant to precede it
                    // is worse than one that never arrives.
                    if (Boolean.TRUE.equals(earlierFailed))
                        return this.cancel(row, WhatsappHoldReason.PREVIOUS_FAILED);

                    return this.sessionFor(row, ticket)
                            .flatMap(session -> this.sessionService
                                    .evaluate(
                                            row.getAppCode(),
                                            row.getClientCode(),
                                            session,
                                            List.of(ticket.getId()),
                                            Boolean.FALSE,
                                            ticket)
                                    .flatMap(decision -> this.act(row, ticket, session, decision)));
                });
    }

    /**
     * The number this row should go out on.
     *
     * <p>The one it was queued against, when it has one. Enqueue resolves and stamps the session so
     * the sending caps are computed against the number that will actually do the sending, and this
     * used to re-resolve from the product instead and ignore the stamp. That was harmless only while
     * the mapping could not change: now that a product can be pointed at a different number, a
     * message queued an hour ago would jump to the new number and be counted against the wrong one's
     * budget, over a cap the scheduler believed it was under.
     *
     * <p>Falls back to resolving from the product for rows queued before the stamp existed.
     */
    private Mono<Map<String, Object>> sessionFor(WhatsappOutbox row, Ticket ticket) {

        if (StringUtil.safeIsBlank(row.getBridgeSessionId()))
            return this.sessionService.resolveForProduct(row.getAppCode(), row.getClientCode(), ticket.getProductId());

        return this.sessionService.resolveByCode(row.getAppCode(), row.getClientCode(), row.getBridgeSessionId());
    }

    private Mono<Void> act(WhatsappOutbox row, Ticket ticket, Map<String, Object> session, Decision decision) {

        if (!decision.allowed()) {

            // A lead who has stopped answering is not a hold, it is the end of the sequence. Holding
            // would leave the rest of the packet pending indefinitely and keep counting against the
            // unanswered window, which is itself part of what throttles the number.
            if (WhatsappHoldReason.LEAD_QUIET.equals(decision.reason()))
                return this.outboxDao
                        .cancelPendingForTicket(ticket.getId(), WhatsappHoldReason.LEAD_QUIET)
                        .doOnNext(count -> logger.info(
                                "Deal {} has stopped replying; cancelled {} queued WhatsApp message(s) and left it"
                                        + " for a person.",
                                ticket.getId(),
                                count))
                        .then();

            return this.outboxDao
                    .hold(row.getId(), decision.reason(), decision.retryAt())
                    .then();
        }

        return this.sessionService
                .sendQueued(row.getAppCode(), row.getClientCode(), session, row.getToPhone(), row.getBodyText())
                .flatMap(response -> this.outboxDao.markSent(
                        row.getId(),
                        string(response, "messageId"),
                        // Which of the two released it, so the caps can be judged later. A message
                        // released because the lead answered says something quite different about
                        // the sequence from one released because a day elapsed.
                        this.releaseReason(row),
                        null,
                        LocalDateTime.now(ZoneOffset.UTC)))
                .onErrorResume(e -> {
                    logger.error(
                            "Queued WhatsApp message {} on deal {} failed to send.", row.getId(), ticket.getId(), e);
                    return this.outboxDao.recordFailure(
                            row.getId(),
                            e.getMessage(),
                            row.getAttempts() == null ? 0 : row.getAttempts(),
                            this.maxAttempts);
                })
                .then();
    }

    /**
     * Whether this went out because the lead replied or because the timer ran down.
     *
     * <p>Read off the hold that was previously recorded on the row. A row that was never held went
     * out on its first sweep, which is the timer case as far as anybody reading this back cares.
     */
    private String releaseReason(WhatsappOutbox row) {
        return WhatsappHoldReason.WAITING_24H.equals(row.getHoldReason())
                ? DECISION_RELEASED_BY_REPLY
                : DECISION_RELEASED_BY_TIMER;
    }

    private Mono<Void> cancel(WhatsappOutbox row, String reason) {
        return this.outboxDao.cancelPendingForTicket(row.getTicketId(), reason).then();
    }

    /**
     * Rows that have been waiting far longer than any gate should hold them.
     *
     * <p>The outbox is meant to drain. A row still pending after three days usually means a number
     * was never reconnected or a reply rate never recovered, and both are things a person has to fix.
     * Logged at error deliberately: this is the signal that automated outreach has quietly stopped
     * working for somebody, which otherwise looks exactly like a quiet week.
     */
    private Mono<Void> reportStale() {
        return this.outboxDao
                .countStale(LocalDateTime.now(ZoneOffset.UTC).minusHours(this.staleAfterHours))
                .doOnNext(count -> {
                    if (count > 0)
                        logger.error(
                                "{} queued WhatsApp message(s) have been held for more than {} hours. Automated"
                                        + " outreach has stopped for at least one number and nobody has been told.",
                                count,
                                this.staleAfterHours);
                })
                .then();
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
