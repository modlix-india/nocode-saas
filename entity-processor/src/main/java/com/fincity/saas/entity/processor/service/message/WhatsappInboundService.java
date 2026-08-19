package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.TicketAudienceService;
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
    private final WhatsappEventService eventService;
    private final TicketAudienceService audienceService;

    public WhatsappInboundService(
            WhatsappMessageDAO dao,
            TicketService ticketService,
            WhatsappEventService eventService,
            TicketAudienceService audienceService) {
        this.audienceService = audienceService;
        this.dao = dao;
        this.ticketService = ticketService;
        this.eventService = eventService;
    }

    public Mono<WhatsappMessage> accept(String appCode, String clientCode, WhatsappInboundRequest request) {

        if (request == null || request.getMetaMessageId() == null || request.getMetaMessageId().isBlank())
            return Mono.error(new IllegalArgumentException(
                    "A WhatsApp handoff needs Meta's message id: it is the idempotency key."));

        // A media handoff is a patch, never an insert. It carries only the attachment, so letting it
        // fall through to merge would blank the body and the status of the message it completes, and
        // letting it insert would put an empty bubble in the thread beside the real one.
        // Announced like everything else. The attachment lands a moment after the bubble it belongs
        // to, so without this the picture is written and nobody is told: the thread shows an empty
        // frame until the agent reloads the page by hand, which is how this was found.
        if (request.isMediaReady())
            return this.dao
                    .readByMessageId(appCode, clientCode, request.getMetaMessageId())
                    .flatMap(existing -> this.applyMedia(existing, request))
                    .flatMap(message -> this.announce(appCode, clientCode, request, message))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.acceptMedia"));

        return this.dao
                .readByMessageId(appCode, clientCode, request.getMetaMessageId())
                .flatMap(existing -> this.merge(appCode, clientCode, existing, request))
                .switchIfEmpty(Mono.defer(() -> this.insert(appCode, clientCode, request)))
                .flatMap(message -> this.applyOptOut(request, message))
                .flatMap(message -> this.announce(appCode, clientCode, request, message))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.accept"));
    }

    /**
     * Tells any browser looking at this deal that it has something to refetch.
     *
     * <p>Placed here, at the end of the one funnel every inbound message, outbound mirror and status
     * receipt passes through, so there is a single place that can fall out of step with what was
     * actually written. Placed <i>after</i> the write for the same reason: a browser told to refetch
     * before the row is committed reads the old thread and stops asking.
     *
     * <p>Silent when the message has no deal attached. The client keys on a deal id, so a ping
     * without one has nothing to say, and this is not the place to notice orphans: that is the
     * inbound resolution's job and it logs it there.
     *
     * <p>Returns the message unchanged and cannot fail the chain. A stale screen is a nuisance; a
     * customer message rejected because a Redis publish failed is a lost conversation.
     */
    private Mono<WhatsappMessage> announce(
            String appCode, String clientCode, WhatsappInboundRequest request, WhatsappMessage message) {

        if (message.getTicketId() == null) return Mono.just(message);

        // A patch completes a bubble that has already been announced, rather than adding one.
        boolean patch = request.isStatusUpdate() || request.isMediaReady();

        // One indexed read plus one audience resolution per stored message. Both used to be free,
        // because the event carried only a deal id and every browser worked out for itself whether
        // it cared. That cost one authenticated ticket read per open browser per event; this costs
        // one resolution per event, whoever is watching.
        return this.ticketService
                .findById(message.getTicketId())
                .flatMap(ticket -> this.audienceService.audienceFor(ticket).map(recipients -> {
                    // The body only rides along for a real message. A status receipt has none, an
                    // outbound mirror's text is already on the sender's screen, and a media patch
                    // belongs to a bubble whose body was announced when it arrived - repeating it
                    // would raise a second toast for one message.
                    String body = patch ? null : message.getBodyText();
                    return new WhatsappEventService.TicketRouting(
                            ticket.getId(),
                            ticket.getProductId(),
                            ticket.getName(),
                            ticket.getCode(),
                            body,
                            recipients);
                }))
                // STATUS rather than MESSAGE for both kinds of patch, which is what narrows them to
                // whoever is actually looking at the thread. A receipt and a late-arriving picture
                // are both changes to a bubble already on screen; only someone watching that deal
                // has anywhere to put them.
                .flatMap(routing -> patch
                        ? this.eventService.publishStatus(appCode, clientCode, routing)
                        : this.eventService.publishMessage(appCode, clientCode, routing))
                .onErrorResume(e -> {
                    logger.warn("Stored WhatsApp message {} but could not announce it.", request.getMetaMessageId(), e);
                    return Mono.empty();
                })
                .thenReturn(message);
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
                .setBridgeSessionId(request.getBridgeSessionId())
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

    /**
     * Fills in the attachment on a message that already exists.
     *
     * <p>Touches the media fields and nothing else. The message, its body, its ticket and its
     * delivery status were settled when it arrived and may well have moved on since - a read receipt
     * can easily overtake a download - so this must not carry any of them backwards.
     *
     * <p>Answers empty when the message is unknown, which is not an error worth failing the batch
     * for: it means the attachment overtook the message it belongs to, and the bridge will redeliver.
     */
    private Mono<WhatsappMessage> applyMedia(WhatsappMessage message, WhatsappInboundRequest request) {

        this.applyMediaFileDetail(message, request);

        if (!StringUtil.safeIsBlank(request.getMediaError())) {
            // Nothing to store and nothing more to wait for. Recorded on the row so the thread can
            // say the attachment is not coming rather than showing a frame that never fills.
            logger.warn(
                    "Attachment for message {} will not arrive: {}",
                    request.getMetaMessageId(),
                    request.getMediaError());
        }

        return this.dao.update(message);
    }

    /**
     * Copies the attachment's description across.
     *
     * <p>Reads more than the two keys it used to. name and url are where the bytes are; the mimetype
     * decides which player the UI mounts, and the voice-note flag decides whether an audio message
     * is drawn as a waveform or as a file - neither is recoverable later.
     */
    private void applyMediaFileDetail(WhatsappMessage message, WhatsappInboundRequest request) {

        if (request.getMediaMimeType() != null) message.setMediaMimeType(request.getMediaMimeType());
        if (request.getMediaSize() != null) message.setMediaSize(request.getMediaSize());
        if (request.getMediaDurationSeconds() != null)
            message.setMediaDurationSeconds(request.getMediaDurationSeconds());
        if (request.getMediaIsVoiceNote() != null) message.setMediaIsVoiceNote(request.getMediaIsVoiceNote());
        if (request.getReactionToMessageId() != null)
            message.setReactionToMessageId(request.getReactionToMessageId());

        if (request.getMediaFileDetail() == null || request.getMediaFileDetail().isEmpty()) return;

        FileDetail detail = new FileDetail();
        Object name = request.getMediaFileDetail().get("name");
        Object url = request.getMediaFileDetail().get("url");
        Object filePath = request.getMediaFileDetail().get("filePath");
        Object size = request.getMediaFileDetail().get("size");
        if (name instanceof String s) detail.setName(s);
        if (url instanceof String s) detail.setUrl(s);
        // filePath is the real location and the only handle the retention sweep has once the keyed
        // URL on the response has replaced url. Dropping it would leave files nothing can delete.
        if (filePath instanceof String s) detail.setFilePath(s);
        if (size instanceof Number n) detail.setSize(n.longValue());
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
