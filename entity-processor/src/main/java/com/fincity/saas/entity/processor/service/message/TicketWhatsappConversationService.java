package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.entity.processor.feign.IFeignFilesService;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer;
import java.nio.ByteBuffer;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.enums.message.WhatsappHoldReason;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.model.response.message.WhatsappThreadWindow;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Thirty days, in minutes, matching what the message service stamps on inbound attachments.
     *
     * <p>Set on the file at upload rather than enforced by a sweep looking for old files. Only a
     * file that was given a lifetime can ever be deleted, which is what stops retention reaching the
     * product assets that are also sent through this thread.
     */
    private static final int OUTGOING_RETENTION_MINUTES = 30 * 24 * 60;

    /**
     * Where a product's assets live in the tenant's static tree, matching what the product editor
     * creates in {@code addProduct.createAndFetchFiles}.
     *
     * <p>Static rather than secured, and that is what makes referencing them work. Files there are
     * permanent, shared and already public, so one brochure stays one object however many
     * conversations send it - and because the path is not under {@code /whatsapp/}, the retention
     * sweep never marks those messages as expired.
     */
    private static final String PRODUCT_ASSET_ROOT = "_withInSubClient/products/";

    private final TicketService ticketService;
    private final ProductService productService;
    private final WhatsappMessageDAO whatsappMessageDAO;
    private final WhatsappSessionService sessionService;
    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;
    private final IFeignFilesService filesService;

    public TicketWhatsappConversationService(
            TicketService ticketService,
            ProductService productService,
            WhatsappMessageDAO whatsappMessageDAO,
            WhatsappSessionService sessionService,
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService,
            IFeignFilesService filesService) {
        this.ticketService = ticketService;
        this.productService = productService;
        this.whatsappMessageDAO = whatsappMessageDAO;
        this.sessionService = sessionService;
        this.feignMessageService = feignMessageService;
        this.msgService = msgService;
        this.filesService = filesService;
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
    public Mono<WhatsappThreadWindow> readTicketThread(
            Identity ticketId, String search, String before, String after, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        // The gate. Everything after this is scoped by what it returns.
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.window(access, ticketIds, search, before, after, pageable))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readTicketThread"));
    }

    /**
     * Reads one window, by cursor when given one and by page number otherwise.
     *
     * <p>The two paths exist because two screens read this. The inbox walks by cursor, which is the
     * only thing that stays correct while messages are arriving; the deal profile still pages by
     * number and must keep seeing what it always saw.
     */
    private Mono<WhatsappThreadWindow> window(
            ProcessorAccess access, List<ULong> ticketIds, String search, String before, String after,
            Pageable pageable) {

        boolean byCursor = !StringUtil.safeIsBlank(before) || !StringUtil.safeIsBlank(after);

        if (!byCursor && pageable.getPageNumber() > 0)
            // A numbered page beyond the first can only come from the older caller, so answer it the
            // way it expects rather than reinterpreting it as a cursor read.
            return this.whatsappMessageDAO
                    .readThread(access.getAppCode(), access.getClientCode(), ticketIds, search, pageable)
                    .map(page -> toWindow(new ArrayList<>(page.getContent()), page.getTotalElements(), false));

        int size = pageable.getPageSize();

        // One more than asked for, purely to learn whether anything is behind it. Counting instead
        // would be a second query whose answer is already stale by the time it is compared against
        // what is on screen.
        return this.whatsappMessageDAO
                .readThreadWindow(
                        access.getAppCode(),
                        access.getClientCode(),
                        ticketIds,
                        search,
                        size + 1,
                        cursorOf(before),
                        cursorOf(after))
                .map(rows -> {
                    boolean hasMore = rows.size() > size;
                    List<WhatsappMessage> content = new ArrayList<>(hasMore ? rows.subList(0, size) : rows);
                    // Total is only meaningful to the numbered-page caller, and this branch is not
                    // it. Left at the window's own size rather than a fabricated figure.
                    return toWindow(content, content.size(), hasMore);
                });
    }

    private static WhatsappThreadWindow toWindow(List<WhatsappMessage> content, long total, boolean hasMore) {

        foldReactions(content);

        WhatsappThreadWindow window = new WhatsappThreadWindow()
                .setContent(content)
                .setTotalElements(total)
                .setHasMore(hasMore);

        if (content.isEmpty()) return window;

        // Taken from the rows actually returned, at each end, so a caller can walk in either
        // direction from exactly where it stopped.
        return window.setNewerCursor(cursorFor(content.get(0)))
                .setOlderCursor(cursorFor(content.get(content.size() - 1)));
    }

    /**
     * The sort position of one message, as an opaque string.
     *
     * <p>Both halves matter: the timestamp is a coalesce of two columns and is not unique, so a
     * cursor without the id loses every row that ties across a window boundary.
     */
    private static String cursorFor(WhatsappMessage message) {

        LocalDateTime at = message.getSentTime() != null ? message.getSentTime() : message.getCreatedAt();
        if (at == null || message.getId() == null) return null;

        return at.toInstant(ZoneOffset.UTC).toEpochMilli() + "_" + message.getId();
    }

    /**
     * Reads a cursor back, or answers null.
     *
     * <p>Null on anything unparseable rather than an error. A cursor is a position, and the worst a
     * bad one should do is start the reader at the newest message again.
     */
    private static WhatsappMessageDAO.ThreadCursor cursorOf(String raw) {

        if (StringUtil.safeIsBlank(raw)) return null;

        int split = raw.indexOf('_');
        if (split <= 0 || split == raw.length() - 1) return null;

        try {
            long millis = Long.parseLong(raw.substring(0, split));
            ULong id = ULong.valueOf(raw.substring(split + 1));
            return new WhatsappMessageDAO.ThreadCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC), id);
        } catch (NumberFormatException e) {
            logger.warn("Ignoring an unreadable thread cursor: {}", raw);
            return null;
        }
    }

    /**
     * Moves each reaction's emoji onto the message it applies to.
     *
     * <p>A reaction is stored as its own row, which is right for writing and wrong for reading. The
     * thread renders through a repeater, one row per message, and a row cannot see its neighbours -
     * so a reaction arriving as its own entry drew its own bubble, and since every content element
     * hides for this type, that bubble contained nothing but a timestamp. That stray bubble is the
     * visible half of the bug; the missing badge is the other half.
     *
     * <p>The reaction rows are left in the list rather than filtered out, and hidden by the page
     * instead. Dropping them here would make a page of size N return fewer than N messages while the
     * total still counted them, so the thread's growing window would quietly shrink by however many
     * reactions it contained.
     *
     * <p>Scoped to the page in hand. A reaction whose target is outside the loaded window has nowhere
     * to attach and is simply not shown, which is the same as today's behaviour and self-corrects as
     * soon as the reader scrolls far enough back to load the message it belongs to.
     */
    private static void foldReactions(List<WhatsappMessage> content) {

        if (content.isEmpty()) return;

        Map<String, WhatsappMessage> byMessageId = new HashMap<>();
        for (WhatsappMessage message : content)
            if (message.getMessageId() != null) byMessageId.put(message.getMessageId(), message);

        for (WhatsappMessage message : content) {

            if (message.getMessageType() != WhatsappMessageType.REACTION) continue;

            String emoji = message.getBodyText();
            WhatsappMessage target = message.getReactionToMessageId() == null
                    ? null
                    : byMessageId.get(message.getReactionToMessageId());

            // An empty body is how WhatsApp says a reaction was withdrawn, so it must not become a
            // blank badge on the message it was removed from.
            if (target == null || StringUtil.safeIsBlank(emoji)) continue;

            String existing = target.getReactionEmoji();

            // Same emoji twice is one badge. It happens legitimately: a reaction that is changed and
            // changed back arrives as separate rows, all of which survive in the thread.
            if (existing == null) target.setReactionEmoji(emoji);
            else if (!existing.contains(emoji)) target.setReactionEmoji(existing + emoji);
        }
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

        String number = ticket.whatsappOrPhoneNumber();

        if (number == null || number.isBlank()) return Mono.just(List.of(ticket.getId()));

        return this.ticketService
                .readAccessibleTicketIdsByWhatsappNumber(access, number, ticket.getProductId())
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
    /**
     * Sends an attachment on a conversation.
     *
     * <p>The bytes arrive here rather than being uploaded from the browser. Letting the page write
     * straight to the tenant's secured storage would mean granting every agent write access to that
     * path, and the check that actually matters - may this person see this deal - lives here and
     * nowhere else. So the file comes in on the same request that names the deal, and the upload
     * happens after the access check rather than before it.
     *
     * <p>Runs the same pacing gate as text. An attachment is not a way around a hold.
     */
    public Mono<Map<String, Object>> sendMedia(
            Identity ticketId, FilePart file, String caption, String kind, boolean voiceNote, boolean requestedForce) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.sessionService.resolveForTicket(access, ticket),
                        (access, ticket, ticketIds, session) -> this.sessionService
                                .evaluateForTicket(access, ticket, ticketIds, session)
                                .flatMap(decision -> {
                                    ULong userId = access.getUserId();
                                    boolean force = requestedForce
                                            && userId != null
                                            && !BigInteger.ZERO.equals(userId.toBigInteger());

                                    if (!decision.allowed() && (!force || !isForceable(decision.reason())))
                                        return this.msgService.<Map<String, Object>>throwMessage(
                                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                                ProcessorMessageResourceService.WHATSAPP_SEND_HELD,
                                                WhatsappHoldReason.explain(decision.reason()));

                                    // Read from the part before the body is consumed, because the
                                    // stored FileDetail does not carry it: its "type" is the
                                    // filename extension, and using that as a mimetype is what
                                    // sent the first attachment out as "txt".
                                    String declared = file.headers().getContentType() == null
                                            ? null
                                            : file.headers().getContentType().toString();

                                    return this.storeOutgoing(access, ticket, file)
                                            .flatMap(stored -> this.sessionService.sendMedia(
                                                    access,
                                                    ticket,
                                                    session,
                                                    stored,
                                                    kind,
                                                    caption,
                                                    voiceNote,
                                                    declared,
                                                    // Uploaded just now, so it is in the
                                                    // conversation tree, not the asset library.
                                                    "secured",
                                                    userId));
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.sendMedia"));
    }

    /**
     * Puts an outgoing attachment where inbound ones live.
     *
     * <p>Same tree and same naming as the inbound path, so one conversation's files sit together
     * rather than being split by which way they went. Secured, like everything else here.
     */
    private Mono<FileDetail> storeOutgoing(ProcessorAccess access, Ticket ticket, FilePart file) {

        // The number the thread runs on, so an outgoing attachment lands in the same customer folder
        // the inbound ones do. Filing it under the phone number would split one conversation's files
        // across two directories on exactly the deals someone corrected.
        String number = ticket.whatsappOrPhoneNumber();
        String customer = number == null ? "unknown" : number.replaceAll("\\D", "");
        String directory = "/whatsapp/" + access.getAppCode() + "/outgoing/" + customer;

        return DataBufferUtils.join(file.content())
                .map(buffer -> {
                    ByteBuffer bytes = ByteBuffer.wrap(toBytes(buffer));
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(bytes -> this.filesService.create(
                        "secured",
                        access.getClientCode(),
                        Boolean.FALSE,
                        directory,
                        file.filename(),
                        // Same lifetime as an inbound attachment. What an agent sends is as much
                        // conversation history as what arrives, and the two sit in one thread.
                        OUTGOING_RETENTION_MINUTES,
                        bytes));
    }

    private static byte[] toBytes(DataBuffer buffer) {
        byte[] out = new byte[buffer.readableByteCount()];
        buffer.read(out);
        return out;
    }

    /**
     * Sends a file the tenant already holds - a brochure, a price list - rather than one the agent
     * just picked off their machine.
     *
     * <p>Copies the asset into the conversation instead of pointing at it, which is not an
     * inefficiency but the only thing that works. Two independent reasons:
     *
     * <ul>
     *   <li>The bridge refuses to fetch a path outside {@code whatsapp/{appCode}/}. A message
     *       referencing {@code /products/brochure.pdf} would pass every check here and then be
     *       rejected at the last hop, which is the worst place to discover it.
     *   <li>Retention is per-file. A referenced brochure would be a file that many deals point at,
     *       and any lifetime put on it would eventually delete it out from under all of them. The
     *       copy carries the thirty-day conversation lifetime; the library original carries none and
     *       stays outside the sweep entirely.
     * </ul>
     *
     * <p>No declared mimetype, deliberately. Unlike an upload there is no request part to read one
     * from, so this leans on the extension fallback in {@code WhatsappSessionService.mimeTypeOf} -
     * which is precisely the case that fallback exists for.
     */
    public Mono<Map<String, Object>> sendAsset(
            Identity ticketId, String assetPath, String caption, String kind, boolean requestedForce) {

        if (StringUtil.safeIsBlank(assetPath))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "assetPath");

        // Rejected rather than normalised, on the same reasoning as the bridge's own check:
        // normalising invites an argument about encodings, and nothing legitimate produces a ".."
        // here. Every path the asset browser offers was listed by the files service itself.
        if (assetPath.contains(".."))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "assetPath");

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.visibleDealsOnSameNumber(access, ticket),
                        (access, ticket, ticketIds) -> this.sessionService.resolveForTicket(access, ticket),
                        (access, ticket, ticketIds, session) -> this.sessionService
                                .evaluateForTicket(access, ticket, ticketIds, session)
                                .flatMap(decision -> {
                                    ULong userId = access.getUserId();
                                    boolean force = requestedForce
                                            && userId != null
                                            && !BigInteger.ZERO.equals(userId.toBigInteger());

                                    // The same gate as text and as an upload. An attachment from the
                                    // library is not a way around a hold.
                                    if (!decision.allowed() && (!force || !isForceable(decision.reason())))
                                        return this.msgService.<Map<String, Object>>throwMessage(
                                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                                ProcessorMessageResourceService.WHATSAPP_SEND_HELD,
                                                WhatsappHoldReason.explain(decision.reason()));

                                    if (!withinProduct(assetPath, ticket))
                                        return this.msgService.<Map<String, Object>>throwMessage(
                                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                                ProcessorMessageResourceService.INVALID_PARAMETERS,
                                                "assetPath");

                                    return this.sessionService.sendMedia(
                                            access,
                                            ticket,
                                            session,
                                            assetReference(assetPath, access.getClientCode()),
                                            kind,
                                            caption,
                                            false,
                                            null,
                                            WhatsappSessionService.STATIC_RESOURCE,
                                            userId);
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.sendAsset"));
    }

    /**
     * Whether this asset belongs to the deal's own product.
     *
     * <p>The picker only ever shows one product's folder, but the picker is not the security
     * boundary: the path arrives over HTTP and anyone can name a different one. A deal has exactly
     * one product, and it has already been access-checked by the time this runs, so the folder that
     * product owns is the whole permitted set.
     *
     * <p>Without this, an agent who can see one deal could send any file in the tenant's asset tree,
     * including another product's price list.
     */
    private static boolean withinProduct(String assetPath, Ticket ticket) {

        if (ticket.getProductId() == null) return false;

        String relative = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        // Trailing slash on the prefix so product 12 cannot be reached from a deal on product 123.
        return relative.startsWith(PRODUCT_ASSET_ROOT + ticket.getProductId() + "/");
    }

    /**
     * Points a message at an asset without moving it.
     *
     * <p>No size, and that is not a gap: it is not known without reading the object and nothing
     * downstream needs it. The {@code type} is not optional though, whatever an earlier version of
     * this comment claimed. {@code WhatsappSessionService.mimeTypeOf} derives the mimetype from
     * {@code getType()} and from nothing else, so leaving it unset sent every library asset as
     * {@code application/octet-stream}, which then filed a PNG as a DOCUMENT and reached the
     * handset as a plain file.
     *
     * <p>The url is the public static one. Product assets live in the static tree, so the path is
     * already world-readable and stable; there is no key to mint and nothing to expire. Without it
     * the thread stores an empty url and every bubble renders "Attachment unavailable" even though
     * the message itself went out fine.
     */
    private static FileDetail assetReference(String assetPath, String clientCode) {

        String relative = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        int slash = relative.lastIndexOf('/');
        String fileName = slash < 0 || slash == relative.length() - 1 ? relative : relative.substring(slash + 1);

        int dot = fileName.lastIndexOf('.');
        String type = dot < 0 || dot == fileName.length() - 1 ? null : fileName.substring(dot + 1);

        return new FileDetail()
                .setFilePath(relative)
                .setName(fileName)
                .setType(type)
                .setUrl("api/files/static/file/" + clientCode + "/" + relative);
    }

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
