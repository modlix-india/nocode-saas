package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Read side of WhatsApp conversations, gated on deal access.
 *
 * <p>This exists because the message service cannot answer "may this user see this conversation?".
 * It knows about numbers and messages, not deals, reporting lines or product rules. Those live
 * here, in {@code TicketDAO}'s access condition, so this service is the only public way into a
 * WhatsApp thread and the message service's own listing endpoints are closed.
 */
@Service
public class TicketWhatsappConversationService {

    private final TicketService ticketService;
    private final ProductService productService;
    private final WhatsappMessageDAO whatsappMessageDAO;
    private final WhatsappCswService cswService;
    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;

    public TicketWhatsappConversationService(
            TicketService ticketService,
            ProductService productService,
            WhatsappMessageDAO whatsappMessageDAO,
            WhatsappCswService cswService,
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService) {
        this.ticketService = ticketService;
        this.productService = productService;
        this.whatsappMessageDAO = whatsappMessageDAO;
        this.cswService = cswService;
        this.feignMessageService = feignMessageService;
        this.msgService = msgService;
    }

    /**
     * A deal's WhatsApp thread.
     *
     * <p>The gate is {@code readByIdentity(access, ...)}, which runs the same condition every other
     * deal read uses: assigned-user within the caller's reporting tree, business-partner client
     * scoping, and per-product read rules. A ticket the caller cannot see fails there, before the
     * message service is ever called.
     *
     * <p>Deliberately no separate WhatsApp authority. This app scopes roles to entities (Deal,
     * Lead, Product, Partner) and not to the tabs within a deal: notes, tasks, activities and call
     * logs all ride on deal access alone. A conversation is deal sub-data, so it does the same. A
     * second gate here would only add a way for the feature to be silently dead wherever the role
     * was never provisioned.
     */
    public Mono<Page<WhatsappMessage>> readTicketThread(
            Identity ticketId, String search, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        // The gate. Everything after this is scoped by what it returns.
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.whatsappMessageDAO.readThread(
                                access.getAppCode(), access.getClientCode(), ticketIds, search, pageable))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readTicketThread"));
    }

    /**
     * Every deal on this customer's number that the caller can see, including the one they opened.
     *
     * <p>The thread is a union rather than one ticket because a customer's conversation can be
     * spread across several deals: they may hold more than one, a business number change splits the
     * history, and an inbound message on a default number is filed against whichever deal moved
     * most recently. Reading a single ticket would show a fragment of what the customer sees on
     * their handset.
     *
     * <p>Falls back to the opened ticket alone when it carries no phone number, which is the only
     * case where there is nothing to union over.
     */
    private Mono<List<ULong>> visibleDealsOnSameNumber(ProcessorAccess access, Ticket ticket) {

        if (ticket.getPhoneNumber() == null || ticket.getPhoneNumber().isBlank())
            return Mono.just(List.of(ticket.getId()));

        return this.ticketService
                .readAccessibleTicketIdsByPhone(access, ticket.getPhoneNumber(), ticket.getProductId())
                .map(ids -> ids.isEmpty() ? List.of(ticket.getId()) : ids);
    }

    /**
     * Sends a free-form message on a deal.
     *
     * <p>Two gates, both here because neither can be evaluated downstream. The deal check is the
     * usual {@code readByIdentity}. The 24-hour window check is new to this service: it moved with
     * the message history, so the message service can no longer refuse an out-of-window send and
     * would simply forward it to Meta and take the policy hit.
     *
     * <p>Refuses rather than silently converting to a template. A template costs money per send and
     * reads differently to the customer, so substituting one for a message the agent typed is not a
     * decision to make on their behalf.
     */
    public Mono<Map<String, Object>> sendMessage(Identity ticketId, Map<String, Object> request) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) ->
                                this.cswService.status(access.getAppCode(), access.getClientCode(), ticketIds),
                        (access, ticket, ticketIds, csw) -> {
                            // A template is exactly what Meta permits once the window shuts, so the
                            // check applies to free-form only. Refusing templates here would break
                            // the cold-lead case the whole template mechanism exists for.
                            if (!csw.windowOpen() && !isTemplate(request))
                                return this.msgService.<Map<String, Object>>throwMessage(
                                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                        ProcessorMessageResourceService.WHATSAPP_WINDOW_CLOSED);

                            return SecurityContextUtil.getUsersContextAuthentication()
                                    .flatMap(ca -> this.feignMessageService.sendWhatsappMessageByTicket(
                                            bearer(ca),
                                            access.getAppCode(),
                                            access.getClientCode(),
                                            withTicketId(request, ticket.getId())));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.sendMessage"));
    }

    /**
     * Sends an approved template on a deal.
     *
     * <p>No window check: a template is precisely what is allowed when the window is shut, and is
     * the only way to reach a lead who has never replied.
     */
    public Mono<Map<String, Object>> sendTemplate(Identity ticketId, Map<String, Object> request) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> SecurityContextUtil.getUsersContextAuthentication()
                                .flatMap(ca -> this.feignMessageService.sendWhatsappTemplateByTicket(
                                        bearer(ca),
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        withTicketId(request, ticket.getId()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.sendTemplate"));
    }

    /**
     * Fetches a media file for a message and remembers where it was stored.
     *
     * <p>Meta only keeps media for a limited window and every fetch costs a round trip, so the
     * result is saved on the row: asking twice returns the stored {@code mediaFileDetail} rather
     * than downloading again.
     *
     * <p>The media id is read from this service's own copy of the payload, not passed in by the
     * caller. That matters: accepting a caller-supplied media id would let anyone who can see one
     * deal pull down media belonging to a conversation they cannot see.
     */
    public Mono<WhatsappMessage> downloadMedia(Identity ticketId, ULong messageId, String connectionName) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.whatsappMessageDAO
                                .readById(messageId)
                                // The message must belong to a deal the caller can see. Without
                                // this, pairing your own ticket id with someone else's message id
                                // walks straight through the gate above.
                                .filter(message -> message.getTicketId() != null
                                        && ticketIds.contains(message.getTicketId()))
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.IDENTITY_WRONG,
                                        "WhatsApp message",
                                        String.valueOf(messageId))),
                        (access, ticket, ticketIds, message) -> {
                            if (message.getMediaFileDetail() != null) return Mono.just(message);

                            String mediaId = mediaIdOf(message);
                            if (mediaId == null) return Mono.just(message);

                            return this.feignMessageService
                                    .downloadWhatsappMedia(
                                            access.getAppCode(),
                                            access.getClientCode(),
                                            Map.of(
                                                    "connectionName",
                                                    connectionName == null ? "whatsapp_connection" : connectionName,
                                                    "mediaId",
                                                    mediaId,
                                                    "fileLocation",
                                                    mediaPathOf(message)))
                                    .flatMap(fileDetail -> this.whatsappMessageDAO.update(
                                            message.setMediaFileDetail(toFileDetail(fileDetail))));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.downloadMedia"));
    }

    /**
     * Digs Meta's media id out of the raw payload.
     *
     * <p>Untyped because this service stores provider payloads verbatim and never models them. The
     * id sits under the message type's own key ({@code image.id}, {@code document.id} and so on),
     * so the type tells us where to look.
     */
    @SuppressWarnings("unchecked")
    private static String mediaIdOf(WhatsappMessage message) {

        Map<String, Object> payload = message.isOutbound() ? message.getMessage() : message.getInMessage();
        if (payload == null || message.getMessageType() == null) return null;

        Object media = payload.get(message.getMessageType().name().toLowerCase());
        if (!(media instanceof Map)) return null;

        Object id = ((Map<String, Object>) media).get("id");
        return id instanceof String s && !s.isBlank() ? s : null;
    }

    /** Mirrors the message service's old layout so downloaded media stays where people expect. */
    private static String mediaPathOf(WhatsappMessage message) {
        return "whatsapp/" + (message.isOutbound() ? "outgoing" : "incoming") + "/" + message.getCustomerWaId() + "/"
                + message.getCode();
    }

    private static FileDetail toFileDetail(Map<String, Object> raw) {
        FileDetail detail = new FileDetail();
        if (raw == null) return detail;
        if (raw.get("name") instanceof String s) detail.setName(s);
        if (raw.get("url") instanceof String s) detail.setUrl(s);
        return detail;
    }

    /**
     * Whether the payload carries an approved template rather than free-form content.
     *
     * <p>Read off {@code message.type} because that is how the UI has always built these, and it is
     * what Meta itself keys on. Anything unrecognised counts as free-form, so a malformed payload
     * gets the stricter treatment rather than slipping past the window check.
     */
    @SuppressWarnings("unchecked")
    private static boolean isTemplate(Map<String, Object> request) {
        if (request == null) return false;
        Object message = request.get("message");
        if (!(message instanceof Map)) return false;
        Object type = ((Map<String, Object>) message).get("type");
        return type instanceof String s && "template".equalsIgnoreCase(s);
    }

    /**
     * Overwrites whatever ticket id the caller sent with the one we actually gated on.
     *
     * <p>Without this the body is unchecked: a caller could pass a deal they can see in the path
     * and a different one in the payload, and the send would go against the second.
     */
    private Map<String, Object> withTicketId(Map<String, Object> request, ULong ticketId) {
        Map<String, Object> body = request == null ? new HashMap<>() : new HashMap<>(request);
        body.put("ticketId", Map.of("id", ticketId.toBigInteger()));
        return body;
    }

    private String bearer(ContextAuthentication ca) {
        String token = ca.getAccessToken();
        return token != null && token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    /**
     * Whether Meta's 24-hour window is open on this conversation.
     *
     * <p>Drives whether the composer offers free text or forces a template. Evaluated over the same
     * visible union as the thread, so a customer reply filed against a sibling deal still counts as
     * having opened the window.
     */
    public Mono<WhatsappCswService.CswStatus> readCswStatus(Identity ticketId) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) ->
                                this.cswService.status(access.getAppCode(), access.getClientCode(), ticketIds))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readCswStatus"));
    }

    /**
     * Marks a deal's conversation read.
     *
     * <p>Scoped to the same visible union as the thread, so opening a conversation clears its badge
     * rather than leaving unread counts on sibling deals the agent just read through.
     */
    public Mono<Integer> markRead(Identity ticketId) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.whatsappMessageDAO.markRead(
                                access.getAppCode(),
                                access.getClientCode(),
                                ticketIds,
                                LocalDateTime.now(ZoneOffset.UTC)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.markRead"));
    }

    /**
     * The inbox: one row per customer number, over the deals the caller can see.
     *
     * <p>No access logic of its own. The list is the deal list, so the gate is whatever {@code
     * TicketDAO.processorAccessCondition} says, which is the same rule the Deals screen runs on.
     * That is the point of building the inbox this way: there is no second definition of visibility
     * that could drift from the first.
     *
     * @param productId optional, narrows to one product's deals
     * @param search optional, matches deal name or customer number
     */
    public Mono<Page<WhatsappConversationResponse>> readConversations(
            Identity productId, String search, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> productId == null || productId.isNull()
                                ? Mono.just(Optional.<ULong>empty())
                                : this.productService
                                        .readByIdentity(access, productId)
                                        .map(product -> Optional.of(product.getId())),
                        (access, resolvedProductId) -> this.ticketService.readConversations(
                                access, resolvedProductId.orElse(null), search, pageable),
                        (access, resolvedProductId, page) -> this.enrich(access, page))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readConversations"));
    }

    /**
     * Fills in unread counts and preview lines for the page on screen.
     *
     * <p>Two batched queries for the whole page rather than anything stored on the ticket. A read
     * receipt changes the count on every message, so a denormalised copy would be wrong more often
     * than right. This is the same shape {@code TicketDAO} already uses for {@code latestComment}.
     *
     * <p>Counts are summed across the deals sharing a number, because the row is the customer, not
     * the deal: an agent looking at one conversation should see one badge covering all of it.
     */
    private Mono<Page<WhatsappConversationResponse>> enrich(
            ProcessorAccess access, Page<WhatsappConversationResponse> page) {

        List<WhatsappConversationResponse> rows = page.getContent();
        if (rows.isEmpty()) return Mono.just(page);

        List<ULong> ticketIds = rows.stream()
                .filter(row -> row.getDeals() != null)
                .flatMap(row -> row.getDeals().stream())
                .map(WhatsappConversationResponse.Deal::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ticketIds.isEmpty()) return Mono.just(page);

        return Mono.zip(
                        this.whatsappMessageDAO.summarise(access.getAppCode(), access.getClientCode(), ticketIds),
                        this.whatsappMessageDAO.latestBodies(access.getAppCode(), access.getClientCode(), ticketIds))
                .map(tuple -> {
                    Map<ULong, WhatsappMessageDAO.ThreadSummary> summaries = tuple.getT1();
                    Map<ULong, String> bodies = tuple.getT2();

                    rows.forEach(row -> {
                        if (row.getDeals() == null) return;

                        int unread = 0;
                        LocalDateTime newest = null;
                        ULong newestTicket = null;

                        for (WhatsappConversationResponse.Deal deal : row.getDeals()) {
                            WhatsappMessageDAO.ThreadSummary summary = summaries.get(deal.getId());
                            if (summary == null) continue;
                            unread += summary.unreadCount();
                            if (summary.lastMessageAt() != null
                                    && (newest == null || summary.lastMessageAt().isAfter(newest))) {
                                newest = summary.lastMessageAt();
                                newestTicket = deal.getId();
                            }
                        }

                        row.setUnreadCount(unread);
                        if (newestTicket != null) row.setLastMessagePreview(bodies.get(newestTicket));
                    });

                    return page;
                });
    }
}
