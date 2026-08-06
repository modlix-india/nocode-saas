package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.TicketService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Receives WhatsApp events from the message service and makes them a deal's conversation.
 *
 * <p>Every path here keys on Meta's message id and upserts. That single decision is what makes the
 * handoff safe without coordination: a webhook Meta redelivers, an outbox row replayed because its
 * delete failed, and a delivery receipt that overtakes the message it belongs to all converge on
 * the same row instead of duplicating or erroring.
 */
@Service
public class WhatsappInboundService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappInboundService.class);

    private final WhatsappMessageDAO dao;
    private final TicketService ticketService;

    public WhatsappInboundService(WhatsappMessageDAO dao, TicketService ticketService) {
        this.dao = dao;
        this.ticketService = ticketService;
    }

    public Mono<WhatsappMessage> accept(String appCode, String clientCode, WhatsappInboundRequest request) {

        if (request == null || request.getMetaMessageId() == null || request.getMetaMessageId().isBlank())
            return Mono.error(new IllegalArgumentException(
                    "A WhatsApp handoff needs Meta's message id: it is the idempotency key."));

        return this.dao
                .readByMessageId(appCode, clientCode, request.getMetaMessageId())
                .flatMap(existing -> this.merge(appCode, clientCode, existing, request))
                .switchIfEmpty(Mono.defer(() -> this.insert(appCode, clientCode, request)))
                .flatMap(message -> this.applyOptOut(request, message))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.accept"));
    }

    /**
     * Flags the deal when a lead has asked us to stop.
     *
     * <p>Acted on the moment the message lands rather than at the next sweep. The gap between the two
     * is fifteen minutes, and a message going out in that window is precisely the one that turns an
     * annoyed lead into a report against the number.
     *
     * <p>Only genuine inbound text is examined. A status update carries no body, and an outbound
     * message is our own words: matching on those would let a salesperson opt a lead out by typing
     * "shall I stop sending these?".
     *
     * <p>The triggering message is stored on the deal. Detection is a text match and text matches are
     * wrong sometimes, and since the flag is permanent and blocks all automated sending, whoever looks
     * at it later needs to see what actually caused it before deciding whether to clear it.
     */
    private Mono<WhatsappMessage> applyOptOut(WhatsappInboundRequest request, WhatsappMessage message) {

        if (message.getTicketId() == null
                || request.isStatusUpdate()
                || Boolean.TRUE.equals(request.getOutbound())
                || !WhatsappOptOutDetector.isOptOut(message.getBodyText())) return Mono.just(message);

        return this.ticketService
                .markWhatsappOptedOut(message.getTicketId(), message.getBodyText())
                .doOnSuccess(ticket -> logger.warn(
                        "Deal {} asked to stop receiving WhatsApp messages. Automated sending is now off for it"
                                + " permanently until somebody clears the flag.",
                        message.getTicketId()))
                .thenReturn(message)
                .onErrorResume(e -> {
                    // Loud, because the consequence of missing this is the one failure in the whole
                    // design that cannot be undone.
                    logger.error(
                            "Detected an opt-out on deal {} but could not record it. This deal may keep receiving"
                                    + " automated messages after asking not to.",
                            message.getTicketId(),
                            e);
                    return Mono.just(message);
                });
    }

    /**
     * First time we have seen this message id.
     *
     * <p>A status update landing here means the receipt beat its own message, which Meta does not
     * guarantee against. The row is written anyway, carrying the status, and the message content
     * fills in when it arrives rather than being dropped for arriving second.
     */
    private Mono<WhatsappMessage> insert(String appCode, String clientCode, WhatsappInboundRequest request) {

        WhatsappMessage message = new WhatsappMessage()
                .setMessageId(request.getMetaMessageId())
                .setWhatsappPhoneNumberId(ULongUtil.valueOf(request.getWhatsappPhoneNumberId()))
                .setWhatsappBusinessAccountId(ULongUtil.valueOf(request.getWhatsappBusinessAccountId()))
                .setWhatsappPhoneNumber(request.getWhatsappPhoneNumber())
                .setCustomerWaId(request.getCustomerWaId())
                .setCustomerDialCode(request.getCustomerDialCode())
                .setCustomerPhoneNumber(request.getCustomerPhoneNumber())
                .setFrom(request.getFrom())
                .setTo(request.getTo())
                .setMessageType(parseType(request.getMessageType()))
                .setMessageStatus(parseStatus(request.getMessageStatus()))
                .setBodyText(request.getBodyText())
                .setOutbound(Boolean.TRUE.equals(request.getOutbound()))
                .setFailureReason(request.getFailureReason())
                .setMessage(request.getMessage())
                .setInMessage(request.getInMessage())
                .setMessageResponse(request.getMessageResponse());

        message.setAppCode(appCode);
        message.setClientCode(clientCode);

        applyMediaFileDetail(message, request);
        applyStatusTimes(message, message.getMessageStatus(), occurredAt(request));

        return this.attachTicket(appCode, clientCode, request, message)
                .flatMap(this.dao::create);
    }

    /**
     * The row already exists, so this is a redelivery, a replay, or a later status.
     *
     * <p>Status only moves forward. Without that guard a {@code SENT} receipt arriving after a
     * {@code READ} would walk the message backwards, which shows up in the UI as a conversation
     * un-reading itself.
     */
    private Mono<WhatsappMessage> merge(
            String appCode, String clientCode, WhatsappMessage existing, WhatsappInboundRequest request) {

        WhatsappMessageStatus incoming = parseStatus(request.getMessageStatus());
        boolean statusAdvanced = incoming != null && incoming.isAfter(existing.getMessageStatus());

        if (statusAdvanced) {
            existing.setMessageStatus(incoming);
            applyStatusTimes(existing, incoming, occurredAt(request));
            if (request.getFailureReason() != null) existing.setFailureReason(request.getFailureReason());
        }

        // Content only arrives with the message itself. A status update carries none, so it must
        // not blank what is already stored.
        if (!request.isStatusUpdate()) {
            if (request.getBodyText() != null) existing.setBodyText(request.getBodyText());
            if (request.getMessage() != null) existing.setMessage(request.getMessage());
            if (request.getInMessage() != null) existing.setInMessage(request.getInMessage());
            if (request.getMessageResponse() != null) existing.setMessageResponse(request.getMessageResponse());
            if (request.getMessageType() != null) existing.setMessageType(parseType(request.getMessageType()));
            applyMediaFileDetail(existing, request);
        }

        // A stub written by an early status update has no deal yet, so resolve it now that the
        // message itself has turned up.
        Mono<WhatsappMessage> withTicket = existing.getTicketId() != null
                ? Mono.just(existing)
                : this.attachTicket(appCode, clientCode, request, existing);

        return withTicket.flatMap(this.dao::update);
    }

    /**
     * Finds or creates the deal this message belongs to, and moves it up the conversation list.
     *
     * <p>Delegates to {@code registerWhatsappMessage}, which owns the product scoping, the fan-out
     * across every deal on that number, and the decision to create one when a stranger messages in.
     * A failure here is logged and the message still stores with no deal: losing what the customer
     * said is worse than filing it late.
     */
    private Mono<WhatsappMessage> attachTicket(
            String appCode, String clientCode, WhatsappInboundRequest request, WhatsappMessage message) {

        String customerNumber = request.getCustomerPhoneNumber() != null
                ? request.getCustomerPhoneNumber()
                : request.getCustomerWaId();

        if (customerNumber == null || customerNumber.isBlank()) return Mono.just(message);

        return this.ticketService
                .registerWhatsappMessage(
                        appCode,
                        clientCode,
                        ULongUtil.valueOf(request.getProductId()),
                        PhoneNumber.of(customerNumber),
                        occurredAt(request),
                        // Only a real inbound message justifies creating a deal. A status update is
                        // about something we already sent, so it never should.
                        !request.isStatusUpdate() && !Boolean.TRUE.equals(request.getOutbound()))
                .map(ticket -> message.setTicketId(ticket.getId()))
                .defaultIfEmpty(message)
                .onErrorResume(e -> {
                    logger.error(
                            "Stored WhatsApp message {} but could not attach it to a deal.",
                            request.getMetaMessageId(),
                            e);
                    return Mono.just(message);
                });
    }

    private void applyStatusTimes(WhatsappMessage message, WhatsappMessageStatus status, LocalDateTime at) {
        if (status == null) return;
        switch (status) {
            case SENT -> message.setSentTime(at);
            case DELIVERED -> message.setDeliveredTime(at);
            case READ -> message.setReadTime(at);
            case FAILED -> message.setFailedTime(at);
            case DELETED -> {
                /* no dedicated timestamp column */
            }
        }
        // Ordering falls back to SENT_TIME, so a message whose first event was a later status still
        // needs one or it sorts to the bottom of the thread.
        if (message.getSentTime() == null) message.setSentTime(at);
    }

    private void applyMediaFileDetail(WhatsappMessage message, WhatsappInboundRequest request) {
        if (request.getMediaFileDetail() == null || request.getMediaFileDetail().isEmpty()) return;
        FileDetail detail = new FileDetail();
        Object name = request.getMediaFileDetail().get("name");
        Object url = request.getMediaFileDetail().get("url");
        if (name instanceof String s) detail.setName(s);
        if (url instanceof String s) detail.setUrl(s);
        message.setMediaFileDetail(detail);
    }

    private LocalDateTime occurredAt(WhatsappInboundRequest request) {
        return request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now(ZoneOffset.UTC);
    }

    private WhatsappMessageType parseType(String value) {
        if (value == null || value.isBlank()) return WhatsappMessageType.TEXT;
        try {
            return WhatsappMessageType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Meta adds message types without warning. An unrecognised one must store, not fail.
            logger.warn("Unrecognised WhatsApp message type {}, storing as UNKNOWN.", value);
            return WhatsappMessageType.UNKNOWN;
        }
    }

    private WhatsappMessageStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return WhatsappMessageStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unrecognised WhatsApp message status {}, leaving the status unchanged.", value);
            return null;
        }
    }

}
