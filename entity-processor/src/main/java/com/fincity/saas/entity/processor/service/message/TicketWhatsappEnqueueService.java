package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappOutboxDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.MessageTemplate;
import com.fincity.saas.entity.processor.dto.message.WhatsappOutbox;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.enums.message.WhatsappOutboxStatus;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ActivityService;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Queues a stage rule's message. Does not send it.
 *
 * <p>Replaces the RabbitMQ holding queues, and the change is more than a swap of transport. Those
 * queues delayed a message by a fixed number of minutes and then handed it to Meta, which enforced
 * the 24-hour window on our behalf and refused anything outside it. Nothing enforces anything now,
 * so the delay has to become a decision, taken against live state at the moment of sending rather
 * than fixed at the moment of queueing. A row in the outbox can be examined, explained and held for
 * a day; a message in flight on a delayed queue can only arrive.
 *
 * <p>The row also outlives the send, which the queue message never did. That is the audit trail: if
 * a customer's number is banned, what went out, when, under which rule and past which gate is the
 * only account of what actually happened.
 *
 * <p><b>Nothing here can force.</b> The force flag lives on the interactive send path and is checked
 * against a real user context. This path constructs its own rows and has no user, which is the
 * server-side half of "automation cannot override the pacing rules" and the reason that property
 * holds even against a hand-crafted request.
 */
@Service
public class TicketWhatsappEnqueueService implements TicketChannelMessageService {

    private static final Logger logger = LoggerFactory.getLogger(TicketWhatsappEnqueueService.class);

    private final ActivityService activityService;
    private final WhatsappOutboxDAO outboxDao;
    private final WhatsappSessionService sessionService;
    private final MessageTemplateService messageTemplateService;

    public TicketWhatsappEnqueueService(
            ActivityService activityService,
            WhatsappOutboxDAO outboxDao,
            WhatsappSessionService sessionService,
            MessageTemplateService messageTemplateService) {
        this.activityService = activityService;
        this.outboxDao = outboxDao;
        this.sessionService = sessionService;
        this.messageTemplateService = messageTemplateService;
    }

    @Override
    public MessageChannelType getChannel() {
        return MessageChannelType.WHATS_APP_TEMPLATE;
    }

    @Override
    public Mono<Void> enqueueForStage(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {

        if (ticket.getPhoneNumber() == null || ticket.getPhoneNumber().isBlank()) return Mono.empty();

        // Checked here rather than only at the gate. An opted-out lead should never acquire a queued
        // row at all: a row that exists and is cancelled on the next sweep still shows up in the
        // deal's outbox as something we intended to send after being asked not to.
        if (Boolean.TRUE.equals(ticket.getWhatsappOptedOut())) return Mono.empty();

        return this.outboxDao
                .countForTicketAndConfig(ticket.getId(), config.getId())
                .flatMap(already -> {
                    // A deal can pass through the same stage more than once, and each pass re-runs
                    // the rules. Without this, bouncing a deal between two stages queues the welcome
                    // pack again every time, which is both spam and exactly the profile that gets a
                    // number reported.
                    if (already > 0) {
                        logger.debug(
                                "Rule {} has already queued for deal {}; not queueing again.",
                                config.getId(),
                                ticket.getId());
                        return Mono.empty();
                    }

                    return this.resolveBody(access, ticket, config)
                            .flatMap(body -> this.enqueue(access, ticket, config, body));
                })
                .then(this.activityService.acWhatsapp(ticket.getId(), null, ticket.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappEnqueueService.enqueueForStage"));
    }

    /**
     * Chooses the phrasing and fills its variables, at queue time rather than at send time.
     *
     * <p>Substituting now means the row carries the exact text that will go out, so the outbox shows
     * what a lead will receive rather than a template id somebody has to resolve by hand. It also
     * means a later edit to the library does not silently change a message already queued against a
     * deal, which would make the audit trail describe something that never happened.
     */
    private Mono<String> resolveBody(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {

        // Rotating on the deal id rather than at random keeps the choice reproducible: reading the
        // row back later tells you which variant went out and why that one.
        long rotation = ticket.getId() == null ? 0L : ticket.getId().longValue();

        Map<String, Object> variables = MessageTemplateService.variablesFor(
                ticket.getName(),
                ticket.getEmail(),
                ticket.getPhoneNumber(),
                ticket.getCode(),
                null,
                access.getUserName());

        String inline = config.variantFor(rotation);
        if (inline != null && !inline.isBlank())
            return Mono.just(MessageTemplateService.interpolate(inline, variables));

        if (config.getMessageTemplateId() == null) {
            logger.warn(
                    "Rule {} on product {} has neither a library message nor its own text; nothing to queue.",
                    config.getId(),
                    config.getProductId());
            return Mono.empty();
        }

        return this.messageTemplateService
                .readForSend(config.getMessageTemplateId())
                .map(MessageTemplate.class::cast)
                .mapNotNull(template -> template.variantFor(rotation))
                .map(body -> MessageTemplateService.interpolate(body, variables))
                .switchIfEmpty(Mono.fromRunnable(() -> logger.warn(
                        "Rule {} references library message {}, which is missing or has no body.",
                        config.getId(),
                        config.getMessageTemplateId())));
    }

    private Mono<WhatsappOutbox> enqueue(
            ProcessorAccess access, Ticket ticket, ProductMessageConfig config, String body) {

        return this.sessionService
                .resolveForTicket(access, ticket)
                .flatMap(session -> {
                    WhatsappOutbox row = new WhatsappOutbox()
                            .setTicketId(ticket.getId())
                            .setProductId(ticket.getProductId())
                            .setStageId(ticket.getStage())
                            .setConfigId(config.getId())
                            // Resolved now so the caps are computed against the number that will do
                            // the sending. Deciding at send time would let a message queued under
                            // one number's warm-up allowance go out under another's.
                            .setBridgeSessionId(sessionId(session))
                            .setToPhone(ticket.getPhoneNumber())
                            .setBodyText(body)
                            .setAssetFileDetail(config.getAssetFileDetail())
                            .setCaption(config.getCaption())
                            .setStatus(WhatsappOutboxStatus.PENDING)
                            .setSequenceOrder(config.getOrder() == null ? 0 : config.getOrder());

                    row.setAppCode(access.getAppCode());
                    row.setClientCode(access.getClientCode());

                    return this.outboxDao.create(row);
                });
    }

    private static String sessionId(Map<String, Object> session) {
        Object value = session == null ? null : session.get("code");
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /** Stops a deal's remaining queued messages, used when a lead opts out or goes quiet. */
    public Mono<Integer> cancelQueued(ULong ticketId, String reason) {
        return this.outboxDao.cancelPendingForTicket(ticketId, reason);
    }
}
