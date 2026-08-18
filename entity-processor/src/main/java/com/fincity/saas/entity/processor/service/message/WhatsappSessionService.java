package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.message.WhatsappPacingService.Decision;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import org.springframework.context.annotation.Lazy;
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

    /**
     * Used to record our own outbound messages into the conversation.
     *
     * <p>The same path inbound events take, deliberately: it is idempotent on the message id, so an
     * outbound row and the receipt that follows it converge on one row instead of racing.
     */
    private final WhatsappInboundService inboundService;

    /**
     * Where the product-to-number mapping lives.
     *
     * <p>{@link Lazy} for the same reason as {@code inboundService}: this service is reached from the
     * ticket and product graph, and resolving a product at construction time would close a cycle that
     * only exists at startup, never per message.
     */
    private final ProductService productService;

    /**
     * {@code inboundService} is {@link Lazy} to break a genuine cycle rather than to paper over a
     * design problem.
     *
     * <p>The chain is {@code TicketService -> TicketMessageService -> TicketWhatsappEnqueueService ->
     * WhatsappSessionService -> WhatsappInboundService -> TicketService}, and it closes because
     * recording a message needs to resolve which deal it belongs to, which is exactly what
     * TicketService is for. Both directions are legitimate: sending needs the deal, and storing a
     * message needs the deal too.
     *
     * <p>Lazy is the honest resolution here because the call is made per message rather than at
     * startup, so the proxy is resolved long after the context is built.
     */
    public WhatsappSessionService(
            IFeignMessageService feignMessageService,
            WhatsappPacingService pacingService,
            WhatsappMessageDAO messageDao,
            @Lazy WhatsappInboundService inboundService,
            @Lazy ProductService productService) {
        this.feignMessageService = feignMessageService;
        this.pacingService = pacingService;
        this.messageDao = messageDao;
        this.inboundService = inboundService;
        this.productService = productService;
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

        return this.sessionCodeOf(appCode, clientCode, productId)
                .flatMap(sessionCode -> this.feignMessageService.resolveWhatsappSession(
                        appCode, clientCode, sessionCode.isBlank() ? null : sessionCode))
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
     * The number a product names, as a code, or blank for "it names none".
     *
     * <p>Read here rather than passed in because both callers have a product id and neither has a
     * reason to know the mapping is stored on the product. A missing product is blank rather than an
     * error: it resolves to the tenant default, which is the same answer an unconfigured product
     * gets, and a deal whose product was deleted should still be answerable.
     */
    private Mono<String> sessionCodeOf(String appCode, String clientCode, ULong productId) {

        if (productId == null) return Mono.just("");

        return this.productService
                .read(productId)
                // Scoped here rather than by the read, because the sweeper has no caller to scope
                // by. The id always arrives from a ticket that was already tenant-checked, so this
                // is a guard against a future caller rather than against today's.
                .filter(product -> appCode.equals(product.getAppCode()) && clientCode.equals(product.getClientCode()))
                .map(product -> product.getWhatsappSessionCode() == null ? "" : product.getWhatsappSessionCode())
                .defaultIfEmpty("")
                .onErrorResume(e -> {
                    logger.warn("Could not read product {} for its WhatsApp number; using the default.", productId, e);
                    return Mono.just("");
                });
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
                Boolean.TRUE.equals(ticket.getWhatsappOptedOut()),
                ticket);
    }

    /**
     * The gate, without a user context.
     *
     * <p>Deliberately the same computation the interactive path runs. The sweeper and the composer
     * must never disagree about why a message is being held: the override panel exists so a person
     * can decide on the strength of those numbers, and it is worthless if the rule actually holding
     * the message was a different one.
     *
     * @param lead the deal being written to, used only for its phone number, which is what decides
     *     the clock quiet hours are judged on. Null for the tenant-level standing view, where there
     *     is no lead and quiet hours fall back to the configured zone.
     */
    public Mono<Decision> evaluate(
            String appCode,
            String clientCode,
            Map<String, Object> session,
            List<ULong> ticketIds,
            boolean optedOut,
            Ticket lead) {

        String sessionId = string(session, KEY_ID);

        if (sessionId == null) return Mono.just(Decision.hold(WhatsappHoldReason.SESSION_NOT_READY));

        boolean sendable = "CONNECTED".equals(string(session, KEY_STATE));
        List<ZoneId> leadZones = zonesOf(lead);

        return this.healthFor(appCode, clientCode, session, ticketIds)
                .flatMap(health -> this.messageDao
                        .consecutiveUnanswered(appCode, clientCode, ticketIds)
                        .map(unanswered ->
                                this.pacingService.evaluate(health, optedOut, sendable, unanswered, leadZones)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.evaluate"));
    }

    /**
     * The lead's candidate time zones, or empty when there is no usable number.
     *
     * <p>Empty rather than a default, so that {@link WhatsappPacingService#quietHoursHold} is the one
     * place that decides what to do about not knowing. A default invented here would be invisible to
     * it and indistinguishable from a number that really is in that zone.
     */
    private static List<ZoneId> zonesOf(Ticket lead) {
        return lead == null ? List.of() : PhoneUtil.zonesOf(lead.getDialCode(), lead.getPhoneNumber());
    }

    /** Session health for a deal, or for the tenant when no deal is in view. */
    public Mono<WhatsappSessionHealth> health(
            ProcessorAccess access, Map<String, Object> session, List<ULong> ticketIds) {
        return this.healthFor(access.getAppCode(), access.getClientCode(), session, ticketIds);
    }

    /**
     * Session health with the gate's verdict stamped on it.
     *
     * <p>Health on its own answers "how is this number doing". It does not answer "will the next
     * message actually go", and the override panel is built entirely around the second question:
     * without this the panel has a hold to explain and no reason to explain it with.
     *
     * <p>Runs the same {@link #evaluate} the send path runs, deliberately. A panel that worked out
     * the hold independently would eventually explain one rule while a different rule was the one
     * holding the message, which is worse than showing nothing.
     */
    public Mono<WhatsappSessionHealth> healthWithDecision(
            String appCode,
            String clientCode,
            Map<String, Object> session,
            List<ULong> ticketIds,
            boolean optedOut,
            Ticket lead) {

        String zoneLabel = this.pacingService.quietHoursZoneLabel(zonesOf(lead));

        return Mono.zip(
                        this.healthFor(appCode, clientCode, session, ticketIds),
                        this.evaluate(appCode, clientCode, session, ticketIds, optedOut, lead))
                .map(t -> applyDecision(t.getT1(), t.getT2()).setQuietHoursZone(zoneLabel))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.healthWithDecision"));
    }

    /**
     * Copies a decision onto a health reading.
     *
     * <p>{@code heldUntil} is taken from the decision rather than left as computed, because the
     * computed one only ever describes the 24-hour rule. A message held by quiet hours or a daily
     * cap releases at a different time entirely, and a countdown that quietly showed the wrong one
     * would be read as fact by whoever is deciding whether to override it.
     */
    static WhatsappSessionHealth applyDecision(WhatsappSessionHealth health, Decision decision) {

        if (decision == null || decision.allowed())
            return health.setHoldReason(null).setHoldExplanation(null).setHeldUntil(null);

        return health.setHoldReason(decision.reason())
                .setHoldExplanation(WhatsappHoldReason.explain(decision.reason()))
                .setHeldUntil(decision.retryAt());
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
    /**
     * Sends a queued message.
     *
     * <p>Takes the whole resolved session rather than its id. It used to take the id and rebuild a
     * one-entry map for the recording step, which meant every automated message was stored with a
     * null sending number: the stub had no {@code displayPhoneNumber} for {@code recordOutbound} to
     * copy. The caller has always had the full session in hand, so there was nothing to gain by
     * discarding it.
     */
    public Mono<Map<String, Object>> sendQueued(
            String appCode, String clientCode, Map<String, Object> session, String toPhone, String text) {

        String sessionId = string(session, KEY_ID);

        if (sessionId == null || toPhone == null || text == null || text.isBlank())
            return Mono.error(new IllegalArgumentException("A session, a recipient and message text are required."));

        return this.feignMessageService
                .sendWhatsappSessionMessage(appCode, clientCode, sessionId, Map.of("to", toPhone, "text", text))
                .flatMap(response -> this.recordOutbound(appCode, clientCode, session, toPhone, text, response)
                        .thenReturn(response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.sendQueued"));
    }

    /**
     * The session one code names, for a caller that already knows which number it wants.
     *
     * <p>Used by the sweeper to honour the number a message was queued against. Falls back to the
     * tenant default like any other resolution, so a number unlinked between queueing and sending
     * delays nothing.
     */
    public Mono<Map<String, Object>> resolveByCode(String appCode, String clientCode, String sessionCode) {
        return this.feignMessageService
                .resolveWhatsappSession(appCode, clientCode, sessionCode)
                .defaultIfEmpty(Map.of())
                .onErrorResume(e -> {
                    logger.error("Could not resolve WhatsApp session {}; treating it as unlinked.", sessionCode, e);
                    return Mono.just(Map.of());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.resolveByCode"));
    }

    /**
     * Writes the message we just sent into the conversation.
     *
     * <p>Nothing else does. whatsmeow does not echo this client's own sends back as inbound events,
     * so without this the thread has no record of anything we said: the deal profile showed "Chat has
     * not yet started" while the message sat delivered on the customer's handset.
     *
     * <p>What made it look stored was the receipts. Those arrive by message id, find no row, and
     * create a stub, which is why the table filled with rows carrying a status and a null ticket and
     * a null body. Recording through {@link WhatsappInboundService#accept} instead of inserting
     * directly is what makes the two meet: accept is idempotent on the message id, so whichever
     * arrives second merges into the same row rather than racing it.
     *
     * <p>A failure here is logged and swallowed. The message has already gone to the customer, and
     * turning a bookkeeping problem into a failed send would tell the person the opposite of what
     * happened and invite them to send it twice.
     */
    private Mono<Void> recordOutbound(
            String appCode,
            String clientCode,
            Map<String, Object> session,
            String toPhone,
            String text,
            Map<String, Object> response) {

        String messageId = string(response, "messageId");
        if (messageId == null || messageId.isBlank()) {
            logger.error(
                    "A WhatsApp send returned no message id, so it cannot be recorded against the"
                            + " conversation. The customer has the message; the thread will not show it.");
            return Mono.empty();
        }

        WhatsappInboundRequest sent = new WhatsappInboundRequest()
                .setMetaMessageId(messageId)
                .setEventType("MESSAGE")
                .setMessageType("text")
                .setMessageStatus("sent")
                .setOutbound(Boolean.TRUE)
                .setBodyText(text)
                .setCustomerPhoneNumber(toPhone)
                .setCustomerWaId(digitsOf(toPhone))
                .setWhatsappPhoneNumber(string(session, KEY_PHONE))
                // Which number carried it. The column has existed since the bridge pivot and nothing
                // ever wrote it, so WhatsappMessageDAO.sessionWindow matched no rows and every
                // number's recent-failure count was a constant zero - the one signal that is meant
                // to back a number off when it starts getting rejected.
                .setBridgeSessionId(string(session, KEY_ID))
                .setTo(digitsOf(toPhone))
                .setFrom(digitsOf(string(session, KEY_PHONE)))
                .setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));

        return this.inboundService
                .accept(appCode, clientCode, sent)
                .onErrorResume(e -> {
                    logger.error(
                            "Sent a WhatsApp message ({}) but could not record it against the"
                                    + " conversation. It will not appear in the thread.",
                            messageId,
                            e);
                    return Mono.empty();
                })
                .then();
    }

    /** JID user parts are digits only; numbers reach us in E.164 or display form. */
    private static String digitsOf(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
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
                .flatMap(response -> this.recordOutbound(
                                access.getAppCode(),
                                access.getClientCode(),
                                session,
                                ticket.getPhoneNumber(),
                                text,
                                response)
                        .thenReturn(response))
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
