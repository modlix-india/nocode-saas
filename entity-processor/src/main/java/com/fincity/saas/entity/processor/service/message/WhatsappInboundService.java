package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.message.WhatsappInboundRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.TicketAudienceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    /** The single key inside BODY_REVISIONS. Named once so the writer and every reader agree. */
    private static final String REVISIONS_KEY = "revisions";

    private static final Logger logger = LoggerFactory.getLogger(WhatsappInboundService.class);

    /**
     * The day attachments began being stored with a lifetime.
     *
     * <p>A floor on what the expiry stamp may touch. Files uploaded before it carry no lifetime, so
     * the cleanup will never remove them, so their messages must never be told the attachment is
     * gone. Configurable rather than hard-coded because it is a fact about a deployment's history,
     * and a fresh environment has a different one.
     */
    @Value("${processor.whatsapp.media.ttl-epoch:2026-08-19}")
    private String ttlEpoch;

    private final WhatsappMessageDAO dao;
    private final TicketService ticketService;
    private final WhatsappEventService eventService;
    private final TicketAudienceService audienceService;
    private final ProductService productService;

    public WhatsappInboundService(
            WhatsappMessageDAO dao,
            TicketService ticketService,
            WhatsappEventService eventService,
            TicketAudienceService audienceService,
            ProductService productService) {
        this.audienceService = audienceService;
        this.dao = dao;
        this.ticketService = ticketService;
        this.eventService = eventService;
        this.productService = productService;
    }

    public Mono<WhatsappMessage> accept(String appCode, String clientCode, WhatsappInboundRequest request) {

        if (request == null || request.getMetaMessageId() == null || request.getMetaMessageId().isBlank())
            return Mono.error(new IllegalArgumentException(
                    "A WhatsApp handoff needs Meta's message id: it is the idempotency key."));

        // Before any of the message paths. A profile picture carries a synthetic message id so the
        // outbox upstream has an idempotency key, and letting that reach the insert would put an
        // empty bubble in the thread every time a customer changed their avatar.
        if (request.isProfilePicture())
            return this.applyProfilePicture(appCode, clientCode, request)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.acceptProfilePicture"));

        // A media handoff is a patch, never an insert: it carries only the attachment, so falling
        // through to merge would blank the body and status of the message it completes. Announced
        // like everything else, because the attachment lands a moment after its bubble and without
        // that the picture is written and nobody is told.
        if (request.isMediaReady())
            return this.dao
                    .readByMessageId(appCode, clientCode, request.getMetaMessageId())
                    .flatMap(existing -> this.applyMedia(existing, request))
                    .flatMap(message -> this.announce(appCode, clientCode, request, message))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.acceptMedia"));

        // A rewrite of a message that already arrived. A patch like media, never an insert: it
        // carries the original's id and the new wording only, so falling through would file the
        // edit as a second bubble and leave the first one saying the old thing - which is exactly
        // what happened before this existed. Empty when the original is unknown, which means the
        // edit overtook the message it corrects; the bridge redelivers.
        if (request.isMessageEdit())
            return this.dao
                    .readByMessageId(appCode, clientCode, request.getMetaMessageId())
                    .flatMap(existing -> this.applyEdit(existing, request))
                    .flatMap(message -> this.announce(appCode, clientCode, request, message))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.acceptEdit"));

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
     * <p>The kind decides whether anybody is interrupted, and only a message from the customer is.
     * {@code MESSAGE} reaches everyone who can see the deal and raises a toast; {@code STATUS}
     * reaches only whoever has that thread open and raises nothing. Both write the ping the thread
     * reloads on, so nothing stops refreshing live either way.
     *
     * <p>Returns the message unchanged and cannot fail the chain. A stale screen is a nuisance; a
     * customer message rejected because a Redis publish failed is a lost conversation.
     */
    private Mono<WhatsappMessage> announce(
            String appCode, String clientCode, WhatsappInboundRequest request, WhatsappMessage message) {

        if (message.getTicketId() == null) return Mono.just(message);

        // A patch completes a bubble that has already been announced, rather than adding one.
        boolean patch = request.isStatusUpdate() || request.isMediaReady() || request.isMessageEdit();

        // Only a message from the customer is worth interrupting somebody for.
        //
        // An outbound mirror used to be announced as MESSAGE, exactly like an inbound one, so
        // sending a message raised a notification and the customer's reply raised a second: one
        // conversational turn, two alerts, and the first of them telling the sender what they had
        // just typed. STATUS is the right kind for it, not silence: it still reaches whoever has
        // that thread open, so a colleague watching sees the bubble appear live, and it raises
        // nothing for anyone who is not looking.
        boolean announceable = !patch && !Boolean.TRUE.equals(request.getOutbound());

        // One indexed read plus one audience resolution per stored message. Both used to be free,
        // because the event carried only a deal id and every browser worked out for itself whether
        // it cared. That cost one authenticated ticket read per open browser per event; this costs
        // one resolution per event, whoever is watching.
        return this.ticketService
                .findById(message.getTicketId())
                .flatMap(ticket -> this.audienceService.audienceFor(ticket).map(recipients -> {
                    // The body only rides along for something that will actually be announced. It is
                    // the notification's text and nothing else reads it, so carrying it on a status
                    // receipt, a media patch or our own outbound mirror only risks a second toast
                    // for one message. This tracked `patch` alone until the outbound case moved,
                    // which meant an outbound mirror carried a body it had no use for.
                    String body = announceable ? message.getBodyText() : null;
                    return new WhatsappEventService.TicketRouting(
                            ticket.getId(),
                            ticket.getProductId(),
                            ticket.getName(),
                            ticket.getCode(),
                            body,
                            recipients);
                }))
                // STATUS narrows to whoever is actually looking at the thread. A receipt, a
                // late-arriving picture and a message we sent ourselves are all changes to a
                // conversation somebody may have open; none of them is news.
                .flatMap(routing -> announceable
                        ? this.eventService.publishMessage(appCode, clientCode, routing)
                        : this.eventService.publishStatus(appCode, clientCode, routing))
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
                .setInMessage(inMessageWithButtons(request))
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
            if (request.getInMessage() != null || request.getButtons() != null)
                existing.setInMessage(inMessageWithButtons(request));
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
     * <p>Delegates to {@code registerWhatsappMessage}, which owns the fan-out across the deals on
     * that number and the decision to create one when a stranger messages in. The product scope is
     * resolved here, because the mapping lives on this side. A failure is logged and the message
     * still stores with no deal: losing what the customer said is worse than filing it late.
     *
     * <p>An outbound send is not resolved at all. See {@link #attachKnownTicket}.
     */
    private Mono<WhatsappMessage> attachTicket(
            String appCode, String clientCode, WhatsappInboundRequest request, WhatsappMessage message) {

        if (request.getTicketId() != null) return this.attachKnownTicket(request, message);

        String customerNumber = request.getCustomerPhoneNumber() != null
                ? request.getCustomerPhoneNumber()
                : request.getCustomerWaId();

        if (customerNumber == null || customerNumber.isBlank()) return Mono.just(message);

        return this.resolveProducts(appCode, clientCode, request)
                .flatMap(scope -> this.ticketService.registerWhatsappMessage(
                        appCode,
                        clientCode,
                        new TicketService.WhatsappOrigin(
                                scope.productIds(),
                                scope.createUnder(),
                                // Only from a genuine inbound message. On an outbound mirror the
                                // push name is our own linked handset's, so naming a deal from it
                                // would label the lead with the salesperson's WhatsApp name.
                                Boolean.TRUE.equals(request.getOutbound()) ? null : request.getPushName()),
                        PhoneNumber.of(customerNumber),
                        occurredAt(request),
                        // Only a real, live inbound message justifies creating a deal. A status
                        // update is about something we already sent, so it never should - and neither
                        // does recovered history, however genuinely inbound it was: a sync blob names
                        // every contact on the handset, and creating for those would read as a flood
                        // of new leads rather than as the backfill it is.
                        !request.isStatusUpdate()
                                && !Boolean.TRUE.equals(request.getOutbound())
                                && !Boolean.TRUE.equals(request.getBackfilled())))
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

    /**
     * Files a message against the deal the caller already knows it belongs to.
     *
     * <p>The outbound path, and the only path where the deal is knowable rather than inferred: a
     * person clicked Send on a deal, or the sweeper released a message queued against one. Resolving
     * it by phone number instead was wrong for any customer holding more than one deal, because the
     * resolution answers with the most recently updated match. An agent sending from one deal could
     * have their own message filed against another, and since each deal's thread reads only the
     * messages stamped to it, one conversation ended up scattered across several.
     *
     * <p>The deal is still moved up the conversation list, because sending is activity on it. Only
     * that one deal, unlike the inbound fan-out: a message we sent went to a particular deal.
     */
    private Mono<WhatsappMessage> attachKnownTicket(WhatsappInboundRequest request, WhatsappMessage message) {

        return this.ticketService
                .touchWhatsappConversation(request.getTicketId(), occurredAt(request))
                .thenReturn(message.setTicketId(request.getTicketId()))
                .onErrorResume(e -> {
                    // The stamp is the part that matters and it is already applied; only the sort
                    // order is lost, and the next message on the thread corrects it.
                    logger.warn(
                            "Filed WhatsApp message {} against deal {} but could not move that deal up the"
                                    + " conversation list.",
                            request.getMetaMessageId(),
                            request.getTicketId(),
                            e);
                    return Mono.just(message.setTicketId(request.getTicketId()));
                });
    }

    /**
     * Which products a message on this business number belongs to, and where a new deal goes.
     *
     * <p>{@code productIds} scopes the match and can hold several, because one number may serve
     * several products. {@code createUnder} is the single product a brand-new deal is created in, and
     * is null when nothing can be resolved, which sends the caller to the tenant's oldest active
     * product as before.
     */
    private record ProductScope(List<ULong> productIds, ULong createUnder) {

        /**
         * Nothing resolved: match on the customer's number alone and let creation fall back to the
         * tenant's oldest active product, which is what happened before any of this existed.
         */
        private static ProductScope unscoped() {
            return new ProductScope(List.of(), null);
        }
    }

    /**
     * Resolves the product scope for an inbound message from the number it arrived on.
     *
     * <p><b>This is the mapping the customer configures, and until now nothing on the inbound path
     * read it.</b> The message service dispatches {@code productId} from its own
     * {@code MESSAGE_WHATSAPP_PHONE_NUMBERS.PRODUCT_ID}, a column left over from the Cloud API that
     * only the link call ever writes and that the numbers page has never sent. So it was null for
     * every linked number, every inbound deal was created with no product, and creation fell through
     * to the tenant's oldest active product: every WhatsApp deal landed on one product regardless of
     * how the numbers screen was set up.
     *
     * <p>The live mapping is the reverse direction, {@code Product.whatsappSessionCode}, which is
     * what the numbers screen writes and what outbound sending already resolves through. So this
     * reads it, keyed on the bridge session id the dispatch already carries.
     *
     * <p>A declared {@code productId} still wins when one is present. It is how pre-pivot Cloud API
     * rows and any future caller that genuinely knows the product express it, and honouring it costs
     * a branch.
     *
     * <p><b>Which products the number serves is two questions, not one.</b> The products that name
     * this session, always; plus, when this session is the tenant's <i>default</i>, the products that
     * name no session at all, because those send through the default and so their deals' own traffic
     * arrives on this number. Leaving the second set out was the trap: a deal on an unmapped product
     * would stop matching, its sent messages would vanish from its own thread, and the customer's
     * replies would manufacture a second deal for them.
     *
     * <p>Never fails the handoff. Every unresolved case degrades to matching on the customer's number
     * alone, which is what happened before any of this existed, because a message filed under the
     * wrong product is recoverable by a person and a message rejected at ingest is not.
     */
    private Mono<ProductScope> resolveProducts(String appCode, String clientCode, WhatsappInboundRequest request) {

        ULong declared = ULongUtil.valueOf(request.getProductId());
        if (declared != null) return Mono.just(new ProductScope(List.of(declared), declared));

        if (StringUtil.safeIsBlank(request.getBridgeSessionId())) return Mono.just(ProductScope.unscoped());

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);
        boolean isDefault = Boolean.TRUE.equals(request.getSessionIsDefault());

        return this.productService
                .getByWhatsappSessionCode(access, request.getBridgeSessionId())
                .defaultIfEmpty(List.of())
                .flatMap(mapped -> (isDefault
                                ? this.productService.getWithoutWhatsappSession(access).defaultIfEmpty(List.of())
                                : Mono.just(List.<Product>of()))
                        .map(unmapped -> this.toScope(request.getBridgeSessionId(), mapped, unmapped)))
                .onErrorResume(e -> {
                    logger.error(
                            "Could not resolve which products WhatsApp number {} serves. Filing this message"
                                    + " without a product scope, which may put a new deal on the wrong product.",
                            request.getBridgeSessionId(),
                            e);
                    return Mono.just(ProductScope.unscoped());
                });
    }

    /**
     * Turns the products a number serves into a scope.
     *
     * <p><b>Matching</b> spans every product in the scope, mapped and default-inherited, and includes
     * <i>inactive</i> ones: a deal that already exists on a product somebody has since deactivated is
     * still that customer's deal, and excluding it would create a duplicate on their next message.
     * Matching used to ignore the scope entirely and match on the customer's number alone, which is
     * how one customer's conversation ended up spread across deals on unrelated products.
     *
     * <p><b>Creation</b> has to pick exactly one, and only from the active products, because {@code
     * createFromInboundWhatsapp} refuses an inactive one and would store the message with no deal at
     * all. A product that explicitly names this number is preferred over one that merely inherits it
     * as the default, which is the whole point of configuring the mapping. Lowest id, the oldest, is
     * the only stable ordering a product carries.
     *
     * <p>Ambiguity is logged rather than guessed at silently. Two products sharing one number is a
     * real configuration, and a message from a stranger carries nothing saying which they meant, so
     * somebody has to know it is happening.
     */
    private ProductScope toScope(String sessionCode, List<Product> mapped, List<Product> defaultInherited) {

        List<ULong> scope = Stream.concat(mapped.stream(), defaultInherited.stream())
                .map(Product::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (scope.isEmpty()) return ProductScope.unscoped();

        List<Product> mappedActive = activeByAge(mapped);
        List<Product> inheritedActive = activeByAge(defaultInherited);

        // Named beats inherited. A product that was pointed at this number deliberately is a better
        // home for a new lead than one that only reaches it because nobody configured it.
        List<Product> candidates = mappedActive.isEmpty() ? inheritedActive : mappedActive;

        if (candidates.isEmpty()) {
            logger.warn(
                    "WhatsApp number {} serves only inactive product(s) {}. A new deal will go to the"
                            + " tenant's oldest active product instead.",
                    sessionCode,
                    scope);
            return new ProductScope(scope, null);
        }

        if (candidates.size() > 1)
            logger.warn(
                    "WhatsApp number {} serves {} products. A message from a stranger carries nothing that"
                            + " says which, so a new deal goes to the oldest of them, {}. Give each product"
                            + " its own number to make this unambiguous.",
                    sessionCode,
                    candidates.size(),
                    candidates.getFirst().getId());

        return new ProductScope(scope, candidates.getFirst().getId());
    }

    private static List<Product> activeByAge(List<Product> products) {
        return products.stream()
                .filter(product -> product.getId() != null)
                .filter(Product::isActive)
                .sorted(Comparator.comparing(Product::getId))
                .toList();
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
     * Rewrites a message's wording, keeping everything it used to say.
     *
     * <p>Touches the body, the revision trail and the edited timestamp, and nothing else. The
     * message's ticket, direction, type and delivery status were settled when it arrived and are not
     * what an edit changes - a read receipt can easily overtake an edit, so carrying any of them
     * here would drag them backwards.
     *
     * <p><b>Idempotent, which is required rather than tidy.</b> Every handoff in this chain is safe
     * to redeliver, and an outbox replay of the same edit must not append the same wording to the
     * trail twice. Comparing against the current body is what makes a repeat a no-op, and it also
     * absorbs the case where WhatsApp reports an edit that changed nothing.
     */
    private Mono<WhatsappMessage> applyEdit(WhatsappMessage message, WhatsappInboundRequest request) {

        String newBody = request.getBodyText();

        // Never blank a real bubble. The bridge already drops an edit it could not decode, so this
        // is the second guard on the same thing: an edit into nothing is a deletion, and a deletion
        // arrives as a DELETED status instead.
        if (StringUtil.safeIsBlank(newBody)) return Mono.just(message);

        String currentBody = message.getBodyText();
        if (newBody.equals(currentBody)) return Mono.just(message);

        LocalDateTime editedAt = request.getOccurredAt() != null
                ? request.getOccurredAt()
                : LocalDateTime.now(ZoneOffset.UTC);

        message.setBodyRevisions(appendRevision(message.getBodyRevisions(), currentBody, editedAt));
        message.setBodyText(newBody);
        message.setEditedAt(editedAt);

        return this.dao.update(message);
    }

    /**
     * Pushes the wording being replaced onto the end of the trail.
     *
     * <p>Rebuilt rather than mutated in place: the map came out of a JSON column and may be
     * immutable or shared, and appending to it directly is the kind of thing that works until the
     * converter changes.
     *
     * <p>A null previous wording is still recorded. A message that arrived with no body and was
     * then edited into having one is unusual but real - a caption added to a photo after the fact -
     * and dropping the empty first entry would make the trail claim the caption was the original.
     */
    private static Map<String, Object> appendRevision(
            Map<String, Object> existing, String replacedBody, LocalDateTime replacedAt) {

        List<Object> revisions = new ArrayList<>();

        if (existing != null && existing.get(REVISIONS_KEY) instanceof List<?> prior) revisions.addAll(prior);

        Map<String, Object> entry = new LinkedHashMap<>();
        // 1-based, so version 1 is always what the sender originally wrote. Stored rather than left
        // to the reader's position in the list because the thread renders these through a nested
        // repeater, which has no index to test - and a label computed from data beats one computed
        // from where a row happens to sit.
        entry.put("version", revisions.size() + 1);
        entry.put("text", replacedBody);
        entry.put("replacedAt", replacedAt.toString());
        revisions.add(entry);

        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put(REVISIONS_KEY, revisions);
        return updated;
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
            // Stored, not merely logged. This comment claimed the row carried it and the code only
            // wrote a log line, which is why a message whose attachment never arrived rendered as a
            // bubble containing nothing but its timestamp: no body, no picture and no explanation.
            message.setMediaError(
                    request.getMediaError().substring(0, Math.min(request.getMediaError().length(), 500)));

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

        // Recorded from whichever event carries it, not only from MEDIA_READY. A backfilled message
        // says up front that its attachment is not coming - there will never be a MEDIA_READY for it -
        // and without this that message would render as a media bubble with nothing in it.
        if (!StringUtil.safeIsBlank(request.getMediaError()))
            message.setMediaError(
                    request.getMediaError().substring(0, Math.min(request.getMediaError().length(), 500)));

        if (request.getMediaMimeType() != null) message.setMediaMimeType(request.getMediaMimeType());
        if (request.getMediaSize() != null) message.setMediaSize(request.getMediaSize());
        if (request.getMediaPageCount() != null) message.setMediaPageCount(request.getMediaPageCount());

        // The preview and the attachment are stored independently and arrive at different times, so
        // each is applied on its own. Folding them together would mean a MEDIA_READY, which carries
        // no preview, blanking the one that came with the message.
        FileDetail thumbnail = toFileDetail(request.getMediaThumbnailFileDetail());
        if (thumbnail != null) message.setMediaThumbnailFileDetail(thumbnail);
        if (request.getMediaDurationSeconds() != null)
            message.setMediaDurationSeconds(request.getMediaDurationSeconds());
        if (request.getMediaIsVoiceNote() != null) message.setMediaIsVoiceNote(request.getMediaIsVoiceNote());
        if (request.getReactionToMessageId() != null)
            message.setReactionToMessageId(request.getReactionToMessageId());

        FileDetail detail = toFileDetail(request.getMediaFileDetail());
        if (detail != null) {
            message.setMediaFileDetail(detail);
            // The bytes arrived after all, so whatever we last said about them not coming is no
            // longer true. A retry that finally succeeds must not leave the thread still apologising.
            message.setMediaError(null);
        }
    }

    /**
     * Puts a customer's avatar on every deal that shares their number.
     *
     * <p>Not on a message, because it is not one. The picture belongs to the person, so it goes on
     * the records that stand for that person, and it goes on all of them: a customer holding several
     * deals should not appear as several different people.
     *
     * <p>Returns empty. There is no message to hand back and nothing downstream expects one, which
     * also keeps this off the announce path: an avatar changing is not worth waking every open
     * browser for, and the next thread read picks it up.
     */
    private Mono<WhatsappMessage> applyProfilePicture(
            String appCode, String clientCode, WhatsappInboundRequest request) {

        String phone = request.getCustomerPhoneNumber() != null
                ? request.getCustomerPhoneNumber()
                : request.getCustomerWaId();

        if (StringUtil.safeIsBlank(phone)) return Mono.empty();

        // Null is the instruction to clear, which is what a customer removing their picture means.
        FileDetail detail = toFileDetail(request.getProfilePictureFileDetail());

        return this.ticketService
                .updateWhatsappProfilePicture(
                        appCode, clientCode, PhoneNumber.of(phone).getNumber(), detail, request.getProfilePictureId())
                .doOnNext(updated -> {
                    if (updated > 0)
                        logger.debug("Stored a WhatsApp avatar against {} deal(s) on {}", updated, phone);
                })
                .onErrorResume(e -> {
                    // A face is decoration. Losing one must not fail the handoff, because the bridge
                    // would then retry the whole batch and hold real messages behind it.
                    logger.warn("Could not store the WhatsApp avatar for {}", phone, e);
                    return Mono.empty();
                })
                .then(Mono.empty());
    }

    /**
     * Reads the files service's description of a stored file into our own shape.
     *
     * <p>Shared by the attachment and its preview, which are two files described identically and
     * applied at different moments.
     */
    private static FileDetail toFileDetail(Map<String, Object> source) {

        if (source == null || source.isEmpty()) return null;

        FileDetail detail = new FileDetail();
        Object name = source.get("name");
        Object url = source.get("url");
        Object filePath = source.get("filePath");
        Object size = source.get("size");
        if (name instanceof String s) detail.setName(s);
        if (url instanceof String s) detail.setUrl(s);
        // filePath is the real location and the only handle the retention sweep has once the keyed
        // URL on the response has replaced url. Dropping it would leave files nothing can delete.
        if (filePath instanceof String s) detail.setFilePath(s);
        if (size instanceof Number n) detail.setSize(n.longValue());
        return detail;
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


    /**
     * Marks messages whose attachment the files service has already collected.
     *
     * <p>Deletion and this are deliberately separate. The bytes go on a lifetime stamped on each
     * file at upload, which is what makes a file without one impossible to delete; this only changes
     * what the thread says about a bubble whose file is gone. Nothing here removes anything.
     *
     * <p>The cutoff is pushed a day past the retention window so this can never claim an attachment
     * has expired while it is still sitting in the bucket. Being a day late is invisible; being an
     * hour early tells somebody a file is gone while they can still open it.
     */
    public Mono<Integer> stampExpiredMedia(int retentionDays, int limit) {

        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays + 1L);

        return this.dao
                .stampExpiredMedia(cutoff, LocalDateTime.parse(ttlEpoch + "T00:00:00"), limit)
                .doOnNext(count -> {
                    if (count > 0) logger.info("Marked {} WhatsApp attachment(s) as expired", count);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappInboundService.stampExpiredMedia"));
    }

    /**
     * Folds the bridge's flattened buttons into the inbound payload map.
     *
     * <p>They ride inside {@code inMessage} rather than in a column of their own. That column is
     * already the "what actually arrived" bag, it is empty for every bridge-sourced row, and a
     * dedicated column would cost a migration plus a jOOQ regeneration for a field only the chat
     * pane ever reads.
     */
    private Map<String, Object> inMessageWithButtons(WhatsappInboundRequest request) {
        if (request.getButtons() == null || request.getButtons().isEmpty()) return request.getInMessage();

        Map<String, Object> merged =
                request.getInMessage() == null ? new HashMap<>() : new HashMap<>(request.getInMessage());
        merged.put("buttons", request.getButtons());
        return merged;
    }

}
