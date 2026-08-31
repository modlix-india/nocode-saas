package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
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
import java.util.LinkedHashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
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
        // The number we are about to message, not the one on file. Quiet hours are about where the
        // handset that will buzz actually is, and a lead whose WhatsApp number is in another country
        // would otherwise be paced against the wrong midnight.
        return lead == null
                ? List.of()
                : PhoneUtil.zonesOf(lead.whatsappOrPhoneDialCode(), lead.whatsappOrPhoneNumber());
    }

    /**
     * Where a message to this deal is addressed.
     *
     * <p>The single place the fallback is applied on the send path. Empty string rather than null
     * because the bridge request body is a map that is serialised as-is, and a null {@code to} fails
     * further away from here than it should.
     */
    /** The tenant's public asset tree, where product brochures and price lists live. */
    static final String STATIC_RESOURCE = "static";

    private static String destinationOf(Ticket ticket) {
        String number = ticket == null ? null : ticket.whatsappOrPhoneNumber();
        return number == null ? "" : number;
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
            String appCode,
            String clientCode,
            ULong ticketId,
            Map<String, Object> session,
            String toPhone,
            String text) {

        String sessionId = string(session, KEY_ID);

        if (sessionId == null || toPhone == null || text == null || text.isBlank())
            return Mono.error(new IllegalArgumentException("A session, a recipient and message text are required."));

        return this.feignMessageService
                .sendWhatsappSessionMessage(appCode, clientCode, sessionId, Map.of("to", toPhone, "text", text))
                .flatMap(response -> this.recordOutbound(
                                appCode, clientCode, ticketId, session, toPhone, text, response)
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
            ULong ticketId,
            Map<String, Object> session,
            String toPhone,
            String text,
            Map<String, Object> response) {
        return this.recordOutbound(appCode, clientCode, ticketId, session, toPhone, text, response, null, null, null);
    }

    /**
     * The same, for a send that carried an attachment.
     *
     * <p>The type is a parameter rather than a constant because it was a constant, and that was
     * wrong in a way nothing reported: every outbound message was filed as text, so an outbound
     * photo reached the UI as a text bubble with an empty body and the picture nowhere - a bug that
     * only appears once media can be sent at all.
     */
    private Mono<Void> recordOutbound(
            String appCode,
            String clientCode,
            ULong ticketId,
            Map<String, Object> session,
            String toPhone,
            String text,
            Map<String, Object> response,
            WhatsappMessageType type,
            FileDetail asset,
            String mimeType) {

        String messageId = string(response, "messageId");
        if (messageId == null || messageId.isBlank()) {
            logger.error(
                    "A WhatsApp send returned no message id, so it cannot be recorded against the"
                            + " conversation. The customer has the message; the thread will not show it.");
            return Mono.empty();
        }

        WhatsappInboundRequest sent = new WhatsappInboundRequest()
                .setMetaMessageId(messageId)
                // The deal this went to, which every caller here already knows. Left out until now,
                // so an outbound message was filed by resolving the customer's number - and that
                // resolution answers with the most recently updated match, so on a customer holding
                // two deals an agent's own message could land on the other one.
                .setTicketId(ticketId)
                .setEventType("MESSAGE")
                .setMessageType((type == null ? WhatsappMessageType.TEXT : type).getValue())
                .setMessageStatus("sent")
                .setOutbound(Boolean.TRUE)
                .setBodyText(text)
                .setMediaFileDetail(asset == null ? null : Map.of(
                        "name", asset.getName() == null ? "" : asset.getName(),
                        "url", asset.getUrl() == null ? "" : asset.getUrl(),
                        "filePath", asset.getFilePath() == null ? "" : asset.getFilePath()))
                // The resolved media type, passed in rather than taken from the FileDetail: that
                // object carries the extension under a field called "type", and reading it here is
                // what put "txt" in a mimetype column.
                .setMediaMimeType(mimeType)
                // Inbound attachments carry a size and outbound ones did not, which is only visible
                // once both are in the same thread: the same picture read 815 KB one way and blank
                // the other.
                .setMediaSize(asset == null ? null : asset.getSize())
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
    /**
     * Sends an attachment on an interactive send.
     *
     * <p>Separate from {@link #sendInteractive} rather than a flag on it, because the two differ in
     * what they require: text is mandatory there and optional here, where a photo with no caption is
     * an ordinary thing to send.
     *
     * <p>The file is named, not carried. It already lives in the files service, and pushing bytes
     * through two more hops to reach a bridge that has to fetch them anyway would buy nothing.
     */
    public Mono<Map<String, Object>> sendMedia(
            ProcessorAccess access,
            Ticket ticket,
            Map<String, Object> session,
            FileDetail asset,
            String kind,
            String caption,
            boolean voiceNote,
            String declaredMimeType,
            /*
             * Which storage tree the bridge should read this from. "secured" for something an agent
             * uploaded, "static" for a product asset the tenant already holds. Carried rather than
             * inferred from the path, because the two trees can hold the same-looking path and
             * guessing wrong means the bridge fetches nothing and the send fails at the last hop.
             */
            String resourceType,
            ULong userId) {

        String sessionId = string(session, KEY_ID);

        if (sessionId == null || asset == null || StringUtil.safeIsBlank(asset.getFilePath()))
            return Mono.error(new IllegalArgumentException("A session and a stored file are both required."));

        String mimeType = mimeTypeOf(declaredMimeType, asset);

        WhatsappMessageType type = typeFor(kind, mimeType);

        String to = destinationOf(ticket);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        body.put("filePath", asset.getFilePath());
        // Omitted when secured, so a bridge that predates library sends sees exactly the body it
        // has always seen and keeps defaulting to the conversation tree.
        if (STATIC_RESOURCE.equals(resourceType)) body.put("resourceType", STATIC_RESOURCE);
        // The resolved kind, not the caller's. The bridge picks which WhatsApp message shape to
        // build from this, so sending it the raw value would have the handset receive a photo as a
        // file attachment whenever the caller said nothing.
        body.put("kind", type.getValue());
        body.put("mimeType", mimeType);
        body.put("fileName", asset.getName() == null ? "" : asset.getName());
        body.put("text", caption == null ? "" : caption);
        body.put("voiceNote", voiceNote);

        return this.feignMessageService
                .sendWhatsappSessionMessage(access.getAppCode(), access.getClientCode(), sessionId, body)
                .doOnNext(response -> logger.debug(
                        "Sent a {} attachment on deal {}.", type.getValue(), ticket.getId()))
                .flatMap(response -> this.recordOutbound(
                                access.getAppCode(),
                                access.getClientCode(),
                                ticket.getId(),
                                session,
                                to,
                                caption,
                                response,
                                type,
                                asset,
                                mimeType)
                        .thenReturn(response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSessionService.sendMedia"));
    }

    /**
     * The attachment's media type, which is emphatically not {@code FileDetail.getType()}.
     *
     * <p>That field is the filename extension, lowercased, set by the files service when it parses
     * the name. Reading it as a media type is a mistake that compiles, runs, and stores {@code
     * "txt"} where {@code "text/plain"} belonged - which is exactly what the first working send did.
     *
     * <p>It matters in two places that both fail quietly. WhatsApp decides how a recipient's handset
     * renders an attachment from the mimetype it is sent with, so an extension there delivers a
     * photo that will not preview; and the inbox picks which player to mount from the stored value,
     * so the same message comes back as a bubble with no picture in it.
     *
     * <p>The browser declares a real type on the multipart part, so that is preferred whenever it
     * says anything specific. {@code application/octet-stream} is treated as saying nothing, because
     * it is what arrives from a client that did not bother - curl sends no type at all - and it is
     * never a better answer than the extension.
     */
    private static String mimeTypeOf(String declared, FileDetail asset) {

        if (!StringUtil.safeIsBlank(declared) && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(declared))
            return declared;

        String extension = asset == null || asset.getType() == null
                ? null
                : asset.getType().toLowerCase();

        // Deliberately short. These are the types this product actually sends, and a guess dressed
        // up as a long table would only hide how narrow the real coverage is.
        String mapped =
                switch (extension == null ? "" : extension) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "gif" -> "image/gif";
                    case "webp" -> "image/webp";
                    case "pdf" -> "application/pdf";
                    case "mp4" -> "video/mp4";
                    case "3gp" -> "video/3gpp";
                    case "mp3" -> "audio/mpeg";
                    case "ogg", "opus" -> "audio/ogg";
                    case "m4a" -> "audio/mp4";
                    case "txt" -> "text/plain";
                    case "csv" -> "text/csv";
                    case "doc" -> "application/msword";
                    case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "xls" -> "application/vnd.ms-excel";
                    case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    default -> null;
                };

        // Unknown stays octet-stream rather than becoming the extension. It sends as a plain file,
        // which is the honest outcome; the extension would be a value no client can interpret.
        return mapped == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mapped;
    }

    /**
     * How the message is filed, from the caller's stated shape or else from what the file actually
     * is.
     *
     * <p>The mimetype fallback is the part that matters. The composer sends a file and a caption and
     * says nothing about kind, so every attachment fell to the default and a sent photo was filed as
     * a DOCUMENT: the row read {@code MESSAGE_TYPE=DOCUMENT, MEDIA_MIME_TYPE=image/jpeg}, and the
     * inbox showed a filename where the picture should be, because its image element is shown only
     * for {@code messageType = "image"}.
     *
     * <p>Deriving it here rather than making the client send it is deliberate. The bytes are the
     * authority on what they are, the mimetype is now resolved from the upload rather than guessed
     * from an extension, and a caller that says nothing should still get the right answer. A caller
     * that states a kind is still believed, so an explicit choice can override the sniff.
     */
    private static WhatsappMessageType typeFor(String kind, String mimeType) {

        if (kind != null)
            switch (kind.toLowerCase()) {
                case "image":
                    return WhatsappMessageType.IMAGE;
                case "video":
                    return WhatsappMessageType.VIDEO;
                case "audio":
                    return WhatsappMessageType.AUDIO;
                case "document":
                    return WhatsappMessageType.DOCUMENT;
                default:
                    // Falls through to the mimetype rather than to DOCUMENT: an unrecognised kind is
                    // a caller mistake, and the file itself still knows what it is.
                    break;
            }

        String mime = mimeType == null ? "" : mimeType.toLowerCase();

        if (mime.startsWith("image/")) return WhatsappMessageType.IMAGE;
        if (mime.startsWith("video/")) return WhatsappMessageType.VIDEO;
        if (mime.startsWith("audio/")) return WhatsappMessageType.AUDIO;

        // Anything else is a document, which is the shape that loses no information: it keeps the
        // filename and the bytes either way.
        return WhatsappMessageType.DOCUMENT;
    }

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

        String to = destinationOf(ticket);

        Map<String, Object> body = Map.of("to", to, "text", text);

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
                                ticket.getId(),
                                session,
                                to,
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
