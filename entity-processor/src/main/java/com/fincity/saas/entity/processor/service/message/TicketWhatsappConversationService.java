package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(TicketWhatsappConversationService.class);

    private final TicketService ticketService;
    private final ProductService productService;
    private final WhatsappMessageDAO whatsappMessageDAO;
    private final WhatsappSessionService sessionService;
    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;

    public TicketWhatsappConversationService(
            TicketService ticketService,
            ProductService productService,
            WhatsappMessageDAO whatsappMessageDAO,
            WhatsappSessionService sessionService,
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService) {
        this.ticketService = ticketService;
        this.productService = productService;
        this.whatsappMessageDAO = whatsappMessageDAO;
        this.sessionService = sessionService;
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
     * Sends a message a person typed, on a deal.
     *
     * <p>What used to gate this was Meta's 24-hour window, and a template was the sanctioned way
     * through it. Both are gone: on the linked-device protocol free-form is always technically
     * available, which means nothing outside this method stops an agent messaging a lead who has
     * gone quiet. So the same 24-hour rule is applied here as our own, with one difference that
     * matters: a person may override it, where Meta's API simply refused.
     *
     * <p><b>The override is enforced here, not in the UI.</b> A checkbox that is merely hidden has
     * not enforced anything, and the automated path constructs its own request and can never reach
     * this method. The flag is honoured only for a caller with a real user context, and who forced
     * it is recorded against the message, because if a number is later banned that record is the
     * only account of what actually happened.
     *
     * <p>Note what is <em>not</em> gated: a reply in a live conversation. An inbound message since
     * our last outbound releases the hold outright, so the override panel only ever appears when
     * somebody is chasing a lead who has not answered, which is exactly when they should be told.
     */
    public Mono<Map<String, Object>> sendMessage(Identity ticketId, Map<String, Object> request) {

        boolean requestedForce = isForced(request);

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.sessionService.resolveForTicket(access, ticket),
                        (access, ticket, ticketIds, session) -> this.sessionService
                                .evaluateForTicket(access, ticket, ticketIds, session)
                                .flatMap(decision -> {

                                    // A real user, not merely a flag in the body. The stage-rule
                                    // path never reaches this method, but the flag arrives over
                                    // HTTP and anyone can set it, so the authority to use it is
                                    // taken from the security context rather than the payload.
                                    ULong userId = access.getUserId();
                                    boolean force = requestedForce
                                            && userId != null
                                            && !BigInteger.ZERO.equals(userId.toBigInteger());

                                    if (!decision.allowed() && (!force || !isForceable(decision.reason()))) {
                                        // The plan asks for every send's pacing decision to be
                                        // recorded, and this is the half that is otherwise invisible:
                                        // the refusal reaches the person as one sentence about the
                                        // hold, which reads identically whether they never asked to
                                        // override, asked and were not entitled to, or asked for a
                                        // hold that cannot be overridden at all. Those are three
                                        // different bugs and they looked the same from the outside.
                                        logger.info(
                                                "Holding a WhatsApp send on deal {}: {}. Override requested={},"
                                                    + " user={}, this hold overridable={}",
                                                ticket.getId(),
                                                decision.reason(),
                                                requestedForce,
                                                userId,
                                                isForceable(decision.reason()));

                                        return this.msgService.<Map<String, Object>>throwMessage(
                                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                                ProcessorMessageResourceService.WHATSAPP_SEND_HELD,
                                                WhatsappHoldReason.explain(decision.reason()));
                                    }

                                    return this.sessionService.sendInteractive(
                                            access, ticket, session, request, decision, force, userId);
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.sendMessage"));
    }

    /**
     * Whether a person is allowed to override this particular hold.
     *
     * <p>Most of them yes: they are our own pacing rules, a person can see the state and there are
     * legitimate reasons to send anyway. Two are not overridable, for different reasons.
     *
     * <p>{@code SESSION_NOT_READY} is not a pacing rule at all, it means there is no connected
     * number to send through. Forcing it would fail deeper in with something far less comprehensible
     * than "connect a number first".
     *
     * <p>{@code OPTED_OUT} is deliberately not a checkbox. Someone asked us to stop, and a one-click
     * override on the send button is exactly how that becomes a report against the number. Clearing
     * an opt-out is a separate, recorded act ({@link #clearOptOut}), which is the right amount of
     * friction: still possible when a lead changes their mind, never accidental.
     */
    private static boolean isForceable(String reason) {
        return !WhatsappHoldReason.SESSION_NOT_READY.equals(reason) && !WhatsappHoldReason.OPTED_OUT.equals(reason);
    }

    /**
     * Whether the caller asked to override a hold.
     *
     * <p>Read from the request rather than trusted from anywhere else, and meaningless on its own:
     * it only has an effect further up, where a real user context is required. The stage-rule path
     * never passes through here at all.
     */
    private static boolean isForced(Map<String, Object> request) {
        Object force = request == null ? null : request.get("force");
        return force instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(force));
    }

    /**
     * What the composer needs to decide whether to override, and how the deal is placed against
     * every limit.
     *
     * <p>The same computation the gate itself runs, on purpose. A panel that showed different
     * reasoning from the rule actually holding the message would be worse than no panel: people
     * would learn to distrust it and tick through anyway.
     */
    public Mono<WhatsappSessionHealth> readHealth(Identity ticketId) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.sessionService.resolveForTicket(access, ticket),
                        (access, ticket, ticketIds, session) -> {
                            boolean optedOut = Boolean.TRUE.equals(ticket.getWhatsappOptedOut());

                            // With the decision, not without it. This read is what fills the
                            // composer's override panel, and a panel that knows a message is held
                            // but cannot say why is the thing that teaches people to click through
                            // it without reading.
                            return this.sessionService
                                    .healthWithDecision(
                                            access.getAppCode(),
                                            access.getClientCode(),
                                            session,
                                            ticketIds,
                                            optedOut,
                                            ticket)
                                    .map(health -> health.setOptedOut(optedOut));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readHealth"));
    }

    /**
     * Reverses an opt-out, because detection is a text match and text matches are wrong sometimes.
     *
     * <p>"Stop by the site on Sunday" is a lead asking for a visit, not asking us to go away. The
     * flag is permanent and checked before every automated send, so with no way back a single false
     * positive would silently end a real conversation. The original message is kept on the deal so
     * whoever clears it can see what actually triggered it.
     */
    public Mono<Ticket> clearOptOut(Identity ticketId) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.ticketService.update(ticket.setWhatsappOptedOut(Boolean.FALSE)
                                .setWhatsappOptedOutAt(null)
                                .setWhatsappOptedOutText(null)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.clearOptOut"));
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
    /**
     * Product id to name, for every product named across a page of conversations.
     *
     * <p>One batched read for the whole page rather than one per deal: a page of twenty numbers can
     * easily carry sixty deals and will normally span a handful of products.
     *
     * <p>Read through {@code getAllProducts}, which applies the access condition, so a product the
     * caller cannot read resolves to no name rather than leaking one. The chip then falls back to
     * the deal name on the client, which is the same information they had before.
     */
    private Mono<Map<ULong, String>> productNames(ProcessorAccess access, List<WhatsappConversationResponse> rows) {

        List<ULong> productIds = rows.stream()
                .filter(row -> row.getDeals() != null)
                .flatMap(row -> row.getDeals().stream())
                .map(WhatsappConversationResponse.Deal::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (productIds.isEmpty()) return Mono.just(Map.of());

        return this.productService
                .getAllProducts(access, productIds)
                .map(products -> products.stream()
                        .filter(product -> product.getId() != null && product.getName() != null)
                        .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a)))
                .defaultIfEmpty(Map.of());
    }

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
                        this.whatsappMessageDAO.latestBodies(access.getAppCode(), access.getClientCode(), ticketIds),
                        this.productNames(access, rows))
                .map(tuple -> {
                    Map<ULong, WhatsappMessageDAO.ThreadSummary> summaries = tuple.getT1();
                    Map<ULong, String> bodies = tuple.getT2();
                    Map<ULong, String> productNames = tuple.getT3();

                    rows.forEach(row -> {
                        if (row.getDeals() == null) return;
                        row.getDeals()
                                .forEach(deal -> deal.setProductName(
                                        deal.getProductId() == null ? null : productNames.get(deal.getProductId())));
                    });

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
