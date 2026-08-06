package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.WhatsappPacingService.Decision;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Resolves which linked number a deal sends from, decides whether it may send, and sends.
 *
 * <p>Sits between the deal and the message service. Everything about instances, placement and
 * routing is on the far side of the feign calls: this service knows a session id and nothing about
 * where it lives, which is the simplification the control-plane decision bought.
 */
@Service
public class WhatsappSessionService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappSessionService.class);

    private static final String KEY_ID = "code";
    private static final String KEY_STATE = "sessionState";
    private static final String KEY_PHONE = "displayPhoneNumber";
    private static final String KEY_LINKED_AT = "linkedAt";

    private final IFeignMessageService feignMessageService;
    private final WhatsappPacingService pacingService;
    private final WhatsappMessageDAO messageDao;

    public WhatsappSessionService(
            IFeignMessageService feignMessageService,
            WhatsappPacingService pacingService,
            WhatsappMessageDAO messageDao) {
        this.feignMessageService = feignMessageService;
        this.pacingService = pacingService;
        this.messageDao = messageDao;
    }

    /**
     * The session a deal's product sends from.
     *
     * <p>Empty rather than an error when nothing is linked. The caller turns that into
     * {@code SESSION_NOT_READY}, which is a state a person can fix by linking a number, where an
     * exception here would read as a platform fault.
     */
    public Mono<Map<String, Object>> resolveForTicket(ProcessorAccess access, Ticket ticket) {
        return this.resolveForProduct(access.getAppCode(), access.getClientCode(), ticket.getProductId());
    }

    /**
     * The same resolution without a user context, for the sweeper.
     *
     * <p>Takes the codes rather than a {@link ProcessorAccess} because the sweeper is a background
     * task and has no caller. Each outbox row carries the app and client it was queued under and the
     * send is made against those, so nothing is widened by not having one.
     */
    public Mono<Map<String, Object>> resolveForProduct(String appCode, String clientCode, ULong productId) {

        if (productId == null) return Mono.just(Map.of());

        return this.feignMessageService
                .getWhatsappSessionByProduct(appCode, clientCode, productId.toBigInteger())
                .defaultIfEmpty(Map.of())
                .onErrorResume(e -> {
                    logger.error(
                            "Could not resolve a WhatsApp session for product {}; treating it as unlinked.",
                            productId,
                            e);
                    return Mono.just(Map.of());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.resolveForProduct"));
    }

    /**
     * Runs the Layer-2 gate for one deal.
     *
     * <p>Used by both the interactive path, where a person may override the answer, and the sweeper,
     * where nothing may. Deliberately the same computation for both: an override panel that showed
     * different reasoning from the one actually holding the message would be worse than no panel.
     */
    public Mono<Decision> evaluateForTicket(
            ProcessorAccess access, Ticket ticket, List<ULong> ticketIds, Map<String, Object> session) {

        return this.evaluate(
                access.getAppCode(),
                access.getClientCode(),
                session,
                ticketIds,
                Boolean.TRUE.equals(ticket.getWhatsappOptedOut()));
    }

    /**
     * The gate, without a user context.
     *
     * <p>Deliberately the same computation the interactive path runs. The sweeper and the composer
     * must never disagree about why a message is being held: the override panel exists so a person
     * can decide on the strength of those numbers, and it is worthless if the rule actually holding
     * the message was a different one.
     */
    public Mono<Decision> evaluate(
            String appCode, String clientCode, Map<String, Object> session, List<ULong> ticketIds, boolean optedOut) {

        String sessionId = string(session, KEY_ID);

        if (sessionId == null) return Mono.just(Decision.hold(WhatsappHoldReason.SESSION_NOT_READY));

        boolean sendable = "CONNECTED".equals(string(session, KEY_STATE));

        return this.healthFor(appCode, clientCode, session, ticketIds)
                .flatMap(health -> this.messageDao
                        .consecutiveUnanswered(appCode, clientCode, ticketIds)
                        .map(unanswered -> this.pacingService.evaluate(health, optedOut, sendable, unanswered)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.evaluate"));
    }

    /** Session health for a deal, or for the tenant when no deal is in view. */
    public Mono<WhatsappSessionHealth> health(
            ProcessorAccess access, Map<String, Object> session, List<ULong> ticketIds) {
        return this.healthFor(access.getAppCode(), access.getClientCode(), session, ticketIds);
    }

    public Mono<WhatsappSessionHealth> healthFor(
            String appCode, String clientCode, Map<String, Object> session, List<ULong> ticketIds) {

        return this.pacingService.health(
                appCode,
                clientCode,
                string(session, KEY_ID),
                string(session, KEY_PHONE),
                string(session, KEY_STATE),
                dateTime(session, KEY_LINKED_AT),
                ticketIds == null ? List.of() : ticketIds);
    }

    /**
     * Sends a queued automated message.
     *
     * <p>Separate from {@link #sendInteractive} because the two have genuinely different rules, not
     * merely different callers. This one has no user, cannot force, and its outcome is written back
     * to an outbox row. Sharing one method with a nullable user and a boolean would make "automation
     * cannot override the gate" a property of how carefully each caller passes its arguments.
     */
    public Mono<Map<String, Object>> sendQueued(
            String appCode, String clientCode, String sessionId, String toPhone, String text) {

        if (sessionId == null || toPhone == null || text == null || text.isBlank())
            return Mono.error(new IllegalArgumentException("A session, a recipient and message text are required."));

        return this.feignMessageService
                .sendWhatsappSessionMessage(appCode, clientCode, sessionId, Map.of("to", toPhone, "text", text))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.sendQueued"));
    }

    /**
     * Sends a message a person typed.
     *
     * <p>Layer 1 still applies on the far side: the bridge waits its randomised five to fifteen
     * seconds and shows a typing indicator even for a forced send. Forcing skips the 24-hour rule
     * and nothing else, because the seconds cost nothing and are what make the traffic look human.
     *
     * <p>The decision is recorded against the send. If a number is banned months later, who forced
     * what and what the number's state was at the time is the only account of it that exists.
     */
    public Mono<Map<String, Object>> sendInteractive(
            ProcessorAccess access,
            Ticket ticket,
            Map<String, Object> session,
            Map<String, Object> request,
            Decision decision,
            boolean forced,
            ULong userId) {

        String sessionId = string(session, KEY_ID);
        String text = string(request, "text");

        if (sessionId == null || text == null || text.isBlank())
            return Mono.error(new IllegalArgumentException("A session and message text are both required."));

        Map<String, Object> body = Map.of(
                "to", ticket.getPhoneNumber() == null ? "" : ticket.getPhoneNumber(),
                "text", text);

        String outcome = forced && !decision.allowed() ? "FORCED" : "INTERACTIVE";

        return this.feignMessageService
                .sendWhatsappSessionMessage(access.getAppCode(), access.getClientCode(), sessionId, body)
                .doOnNext(response -> {
                    if (forced && !decision.allowed())
                        // Deliberately at error level. An override is rare, consequential and worth
                        // finding in a log without knowing to look for it.
                        logger.error(
                                "User {} FORCED a WhatsApp send on deal {} past hold '{}' using session {}."
                                        + " Forcing raises the chance this number is blocked, and a blocked"
                                        + " number cannot be appealed.",
                                userId,
                                ticket.getId(),
                                decision.reason(),
                                sessionId);
                    else logger.debug("Sent an interactive WhatsApp message on deal {}.", ticket.getId());
                })
                .map(response -> withDecision(response, outcome, decision, userId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.sendInteractive"));
    }

    /**
     * Echoes the pacing decision back to the caller.
     *
     * <p>So the composer can show what happened rather than guessing, and so a forced send reads as
     * forced in the response rather than looking identical to an ordinary one.
     */
    private static Map<String, Object> withDecision(
            Map<String, Object> response, String outcome, Decision decision, ULong userId) {

        java.util.Map<String, Object> merged = new java.util.HashMap<>(response == null ? Map.of() : response);
        merged.put("sendDecision", outcome);
        if (decision.reason() != null) merged.put("overriddenHold", decision.reason());
        if ("FORCED".equals(outcome)) merged.put("forcedBy", userId == null ? null : userId.toBigInteger());
        return merged;
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /**
     * Parses a timestamp that may arrive with or without an offset.
     *
     * <p>The message service's own rows serialise as local date-times while anything relayed from
     * the bridge carries a Z. Both reach this method, and treating an unparseable value as absent is
     * right: a missing link date only costs the warm-up ramp, where failing the read would blank the
     * panel a person is using to decide whether to override.
     */
    private static LocalDateTime dateTime(Map<String, Object> map, String key) {
        String raw = string(map, key);
        if (raw == null) return null;

        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(raw);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
