package com.fincity.saas.entity.processor.dao.message;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorWhatsappMessages.ENTITY_PROCESSOR_WHATSAPP_MESSAGES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorWhatsappMessagesRecord;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WhatsappMessageDAO
        extends BaseUpdatableDAO<EntityProcessorWhatsappMessagesRecord, WhatsappMessage> {

    protected WhatsappMessageDAO() {
        super(
                WhatsappMessage.class,
                ENTITY_PROCESSOR_WHATSAPP_MESSAGES,
                ENTITY_PROCESSOR_WHATSAPP_MESSAGES.ID);
    }

    /**
     * A conversation thread: every message on any of the deals the caller can see.
     *
     * <p>Takes an already-resolved deal set rather than a single ticket, which is what makes the
     * thread survive a business number change. Both halves of a conversation split across two
     * numbers still carry ticket ids for the same customer, so the union shows the full history
     * where a per-number key would show only the current half.
     *
     * <p>An empty set returns an empty page rather than everything, since "no visible deals" must
     * never widen into "no filter".
     */
    public Mono<Page<WhatsappMessage>> readThread(
            String appCode, String clientCode, List<ULong> ticketIds, String search, Pageable pageable) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(Page.empty(pageable));

        Condition where = tenant(appCode, clientCode)
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                .and(this.isActiveTrue())
                // Skip the rows that would draw a bubble with nothing in it. See hasSomethingToShow.
                .and(this.hasSomethingToShow());

        if (search != null && !search.isBlank()) where = where.and(bodyMatches(search));

        Condition finalWhere = where;

        return Mono.zip(
                        Mono.from(this.dslContext
                                        .selectCount()
                                        .from(this.table)
                                        .where(finalWhere))
                                .map(rec -> rec.value1().longValue())
                                .defaultIfEmpty(0L),
                        Flux.from(this.dslContext
                                        .selectFrom(this.table)
                                        .where(finalWhere)
                                        .orderBy(orderKey().desc())
                                        .limit(pageable.getPageSize())
                                        .offset((int) pageable.getOffset()))
                                .map(rec -> rec.into(this.pojoClass))
                                .collectList())
                .map(tuple -> new PageImpl<>(tuple.getT2(), pageable, tuple.getT1()));
    }

    /**
     * One window of a thread, addressed by where the reader already is rather than by page number.
     *
     * <p>Offset paging is wrong for a live conversation and wrong in a way that only shows up in
     * use. Every arriving message shifts every row down by one, so "give me the next twenty older"
     * asked as an offset returns twenty rows measured against a list that has since moved: the
     * reader sees a message twice, or never sees one at all. The bug is invisible on a quiet thread
     * and constant on a busy one, which is the worst combination to debug.
     *
     * <p>The cursor is the pair {@code (orderKey, id)}, not the timestamp alone. {@code orderKey} is
     * a coalesce of two DATETIME columns and is emphatically not unique - a message and its own
     * status update routinely land in the same second, and a bulk send lands dozens together. Paging
     * on a non-unique key silently drops whichever rows tie across the boundary.
     *
     * @param before rows strictly older than this cursor. What "load more" sends.
     * @param after rows strictly newer than this cursor. What a live update sends, so an arriving
     *     message costs one small query instead of refetching everything on screen.
     */
    public Mono<List<WhatsappMessage>> readThreadWindow(
            String appCode,
            String clientCode,
            List<ULong> ticketIds,
            String search,
            int size,
            ThreadCursor before,
            ThreadCursor after) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(List.of());

        Condition where = tenant(appCode, clientCode)
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                .and(this.isActiveTrue())
                // Skip the rows that would draw a bubble with nothing in it. See hasSomethingToShow.
                .and(this.hasSomethingToShow());

        if (search != null && !search.isBlank()) where = where.and(bodyMatches(search));

        if (before != null) where = where.and(olderThan(before));
        if (after != null) where = where.and(newerThan(after));

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        // Newest first in both directions. An "after" window read ascending would
                        // return the OLDEST of the new messages when there are more than fit, which
                        // is the half a reader has least use for.
                        .where(where)
                        .orderBy(orderKey().desc(), ENTITY_PROCESSOR_WHATSAPP_MESSAGES.ID.desc())
                        .limit(size))
                .map(rec -> rec.into(this.pojoClass))
                .collectList();
    }

    /** Strictly older, with the id breaking ties on a timestamp shared by several rows. */
    private Condition olderThan(ThreadCursor cursor) {
        return orderKey()
                .lt(cursor.at())
                .or(orderKey().eq(cursor.at()).and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.ID.lt(cursor.id())));
    }

    /** Strictly newer, mirroring {@link #olderThan} so the two windows cannot overlap or gap. */
    private Condition newerThan(ThreadCursor cursor) {
        return orderKey()
                .gt(cursor.at())
                .or(orderKey().eq(cursor.at()).and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.ID.gt(cursor.id())));
    }

    /**
     * A reader's position in a thread.
     *
     * <p>Both halves are needed. See {@link #readThreadWindow} for why the timestamp alone loses
     * rows.
     */
    public record ThreadCursor(LocalDateTime at, ULong id) {}

    /**
     * Latest message and unread count per deal, for the page of conversations on screen.
     *
     * <p>Deliberately not denormalised onto the ticket: a read receipt would change the count on
     * every message, and a stored copy would drift. Follows the batched enrichment already used for
     * {@code latestComment}, so it is one query for the visible page rather than one per row.
     */
    public Mono<Map<ULong, ThreadSummary>> summarise(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(Map.of());

        Condition where = tenant(appCode, clientCode)
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                .and(this.isActiveTrue())
                // Same filter as the thread. An empty row left by a receipt is inbound and unread by
                // every column that decides those, so counting it put an unread badge on a
                // conversation with nothing new in it and floated the deal to the top of the inbox.
                .and(this.hasSomethingToShow());

        var unread = DSL.sum(DSL.when(
                        ENTITY_PROCESSOR_WHATSAPP_MESSAGES
                                .IS_OUTBOUND
                                .isFalse()
                                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.READ_TIME.isNull()),
                        DSL.inline(1))
                .otherwise(DSL.inline(0)));

        return Flux.from(this.dslContext
                        .select(
                                ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID,
                                DSL.max(orderKey()),
                                unread)
                        .from(this.table)
                        .where(where)
                        .groupBy(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID))
                .collectMap(
                        rec -> rec.get(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID),
                        rec -> new ThreadSummary(
                                rec.get(1, LocalDateTime.class),
                                rec.get(2, Integer.class) == null ? 0 : rec.get(2, Integer.class)));
    }

    /**
     * The most recent message text per deal, for the conversation list's preview line.
     *
     * <p>Separate from {@link #summarise} because a preview is a pick, not an aggregate: {@code
     * MAX} over text would return the alphabetically largest string, not the latest message. Uses
     * the same window-function shape as {@code TicketDAO.fetchLatestComments}, one query for the
     * whole visible page.
     */
    public Mono<Map<ULong, String>> latestBodies(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(Map.of());

        Field<Integer> rowNum = DSL.rowNumber()
                .over(DSL.partitionBy(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID)
                        .orderBy(orderKey().desc()))
                .as("rn");

        Table<?> sub = this.dslContext
                .select(
                        ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID,
                        ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BODY_TEXT,
                        ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_TYPE,
                        rowNum)
                .from(this.table)
                .where(tenant(appCode, clientCode))
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                // Otherwise the newest row wins the preview line and it has no text, so the inbox
                // shows a conversation with a blank last message.
                .and(this.hasSomethingToShow())
                .and(this.isActiveTrue())
                .asTable("wa_latest");

        return Flux.from(this.dslContext.selectFrom(sub).where(sub.field("rn", Integer.class).eq(1)))
                .collectMap(
                        rec -> rec.get(sub.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID)),
                        rec -> {
                            String body = rec.get(sub.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BODY_TEXT));
                            if (body != null && !body.isBlank()) return body;
                            // Media with no caption has no text at all, and an empty preview reads
                            // as a broken row. Name the type instead.
                            WhatsappMessageType type =
                                    rec.get(sub.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_TYPE));
                            return type == null ? "" : type.name();
                        });
    }

    /**
     * Marks a deal's inbound messages read.
     *
     * <p>Only touches rows that are not already read, so re-opening a conversation does not rewrite
     * timestamps that were already correct.
     */
    public Mono<Integer> markRead(String appCode, String clientCode, List<ULong> ticketIds, LocalDateTime readAt) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(0);

        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_WHATSAPP_MESSAGES)
                .set(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.READ_TIME, readAt)
                .set(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_STATUS, WhatsappMessageStatus.READ)
                .where(tenant(appCode, clientCode))
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isFalse())
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.READ_TIME.isNull()));
    }

    /**
     * When the customer last wrote to us across this conversation, which is what opens Meta's
     * 24-hour window.
     *
     * <p>Takes the whole visible deal set rather than one ticket. A reply filed against a sibling
     * deal still opened the window, and scoping to a single deal would report it shut and force a
     * template send that costs money and reads as impersonal.
     */
    public Mono<LocalDateTime> lastInboundAt(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.empty();

        return Mono.from(this.dslContext
                        .select(DSL.max(orderKey()))
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isFalse())
                        .and(this.isActiveTrue()))
                .mapNotNull(Record1::value1);
    }

    /** Looks a message up by Meta's id, which every write path keys on. */
    /**
     * When the newest message we already hold on this number was sent.
     *
     * <p>The high-water mark a backfill is measured against. WhatsApp hands a newly linked device
     * the recent history of every conversation on the handset, which is far more than the gap the
     * unlink actually left, so the only messages worth importing are the ones dated after the last
     * one we have.
     *
     * <p>Empty means we hold nothing for this number, and that is the first-link case: there is no
     * gap to close, so there is nothing to import. Answering empty rather than a zero date is what
     * lets the caller tell "nothing yet" from "nothing newer".
     *
     * <p>Keyed on the business number rather than per conversation, because the boundary being
     * recovered is when the number stopped receiving, and that is one instant across every
     * conversation on it.
     */
    public Mono<LocalDateTime> newestSentTime(String appCode, String clientCode, String whatsappPhoneNumber) {

        if (whatsappPhoneNumber == null || whatsappPhoneNumber.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .select(DSL.max(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.SENT_TIME))
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.WHATSAPP_PHONE_NUMBER.eq(whatsappPhoneNumber)))
                .mapNotNull(Record1::value1);
    }

    public Mono<WhatsappMessage> readByMessageId(String appCode, String clientCode, String messageId) {

        if (messageId == null || messageId.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_ID.eq(messageId)))
                .map(rec -> rec.into(this.pojoClass));
    }

    /**
     * Which of these deals have a WhatsApp message, so a caller can tell a real conversation from a
     * deal that merely exists.
     */
    public Mono<List<ULong>> ticketsWithMessages(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(List.of());

        return Flux.from(this.dslContext
                        .selectDistinct(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID)
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds)))
                .map(rec -> rec.value1())
                .collectList();
    }

    // ---------------------------------------------------------------------------------------------
    // Pacing.
    //
    // Every figure the Layer-2 gate decides on is computed here rather than stored, because this
    // table is the only honest record of what was actually sent. A counter kept alongside it would
    // drift the first time a send failed halfway, and it would drift silently in the direction that
    // lets more messages out.
    // ---------------------------------------------------------------------------------------------

    /** The last time we wrote to this deal. Half of the 24-hour comparison. */
    public Mono<LocalDateTime> lastOutboundAt(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.empty();

        return Mono.from(this.dslContext
                        .select(DSL.max(orderKey()))
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isTrue())
                        .and(this.isActiveTrue()))
                .mapNotNull(Record1::value1);
    }

    /**
     * Replies divided by sends for one session over a window.
     *
     * <p>Counted per deal rather than per message, and that is the meaningful denominator: a lead
     * who received four messages and answered once has replied, and scoring that as 25% would
     * penalise a number for following up properly. What the metric is really detecting is a number
     * writing to people who never write back at all.
     */
    public Mono<Double> replyRate(String appCode, String clientCode, String sessionId, LocalDateTime since) {

        if (sessionId == null || sessionId.isBlank()) return Mono.just(1.0d);

        Field<ULong> ticket = ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID;

        Mono<Integer> contacted = Mono.from(this.dslContext
                        .select(DSL.countDistinct(ticket))
                        .from(this.table)
                        .where(sessionWindow(appCode, clientCode, sessionId, since))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isTrue()))
                .map(Record1::value1)
                .defaultIfEmpty(0);

        Mono<Integer> replied = Mono.from(this.dslContext
                        .select(DSL.countDistinct(ticket))
                        .from(this.table)
                        .where(sessionWindow(appCode, clientCode, sessionId, since))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isFalse()))
                .map(Record1::value1)
                .defaultIfEmpty(0);

        return Mono.zip(contacted, replied)
                // A number that has written to nobody is not failing; it is new. Reporting 0% would
                // suspend a freshly linked session before it ever sent anything.
                .map(t -> t.getT1() == 0 ? 1.0d : (double) t.getT2() / (double) t.getT1());
    }

    /**
     * Deals this session opened a conversation with since a given time.
     *
     * <p>First contact means the first outbound to a deal that had no prior message either way, which
     * is the thing the daily cap is actually about. Replying to an existing thread is not opening a
     * conversation and must not count against it, or a busy support day would exhaust the allowance
     * for genuine outreach.
     */
    public Mono<Integer> firstContactsSince(
            String appCode, String clientCode, String sessionId, LocalDateTime since) {

        if (sessionId == null || sessionId.isBlank()) return Mono.just(0);

        Table<?> earlier = this.table.as("earlier");
        Field<ULong> earlierTicket = earlier.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID);
        Field<LocalDateTime> earlierAt = orderKeyOf(earlier);

        return Mono.from(this.dslContext
                        .select(DSL.countDistinct(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID))
                        .from(this.table)
                        .where(sessionWindow(appCode, clientCode, sessionId, since))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isTrue())
                        .andNotExists(this.dslContext
                                .selectOne()
                                .from(earlier)
                                .where(earlierTicket.eq(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID))
                                .and(earlierAt.lt(since))))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    /** Messages this session has sent in the last hour, so a pointless call to a capped session is avoided. */
    public Mono<Integer> sentSince(String appCode, String clientCode, String sessionId, LocalDateTime since) {

        if (sessionId == null || sessionId.isBlank()) return Mono.just(0);

        return Mono.from(this.dslContext
                        .selectCount()
                        .from(this.table)
                        .where(sessionWindow(appCode, clientCode, sessionId, since))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isTrue()))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    /**
     * Consecutive outbound messages to a deal with no reply after them.
     *
     * <p>Counts back from the most recent inbound, so a lead who answers resets it. Two or three of
     * these is where a sequence should stop and a person should look, both because continuing is
     * useless and because unanswered messages are themselves part of what throttles the number.
     */
    public Mono<Integer> consecutiveUnanswered(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(0);

        return this.lastInboundAt(appCode, clientCode, ticketIds)
                .defaultIfEmpty(LocalDateTime.of(1970, 1, 1, 0, 0))
                .flatMap(lastInbound -> Mono.from(this.dslContext
                                .selectCount()
                                .from(this.table)
                                .where(tenant(appCode, clientCode))
                                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.TICKET_ID.in(ticketIds))
                                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IS_OUTBOUND.isTrue())
                                .and(orderKey().gt(lastInbound))
                                .and(this.isActiveTrue()))
                        .map(Record1::value1)
                        .defaultIfEmpty(0));
    }

    /** Failed sends on this session recently, because repeated failures usually mean something is already wrong. */
    public Mono<Integer> recentFailures(String appCode, String clientCode, String sessionId, LocalDateTime since) {

        if (sessionId == null || sessionId.isBlank()) return Mono.just(0);

        return Mono.from(this.dslContext
                        .selectCount()
                        .from(this.table)
                        .where(sessionWindow(appCode, clientCode, sessionId, since))
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_STATUS.eq(WhatsappMessageStatus.FAILED)))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    private Condition sessionWindow(String appCode, String clientCode, String sessionId, LocalDateTime since) {
        return tenant(appCode, clientCode)
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BRIDGE_SESSION_ID.eq(sessionId))
                .and(orderKey().ge(since))
                .and(this.isActiveTrue());
    }

    /**
     * Rows that actually have something for a reader to look at.
     *
     * <p><b>The bubble containing nothing but a timestamp comes from here.</b> Every write path
     * upserts on the provider's message id, and a delivery receipt for a message this service has
     * never seen creates the row rather than being dropped, so the status is not lost if the receipt
     * merely raced the message it belongs to. That is right for a race measured in milliseconds. It is
     * wrong when the message is never coming, and re-linking a number produces exactly that by the
     * handful: WhatsApp sends receipts for the backlog it accumulated while no device was attached,
     * we never held those messages, and each receipt manufactures a permanent empty bubble dated to
     * whenever the customer wrote it.
     *
     * <p>So the rows stay - a receipt is still a fact, and a message that turns up later fills its
     * row in and becomes visible on its own - and the thread simply does not draw the ones that say
     * nothing. Anything with text, an attachment, a preview, a declared media type, a reason its
     * attachment is not coming, or a provider payload counts as something. A media message still
     * waiting for its bytes has a declared type, so it keeps rendering as pending rather than
     * vanishing and reappearing.
     *
     * <p>{@code JSON_EXTRACT} rather than a null check on the column, because a failed download used
     * to store {@code {}} rather than nothing, and an empty object is not an attachment. Those rows
     * are still in the table.
     */
    private Condition hasSomethingToShow() {
        return ENTITY_PROCESSOR_WHATSAPP_MESSAGES
                .BODY_TEXT
                .isNotNull()
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BODY_TEXT.ne(DSL.inline("")))
                .or(jsonHasKeys(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_FILE_DETAIL))
                .or(jsonHasKeys(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_THUMBNAIL_FILE_DETAIL))
                .or(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_MIME_TYPE.isNotNull())
                .or(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_ERROR.isNotNull())
                // A location, a shared contact or an interactive reply can carry its whole content in
                // the payload with no body text at all, so the payload has to count.
                .or(jsonHasKeys(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.IN_MESSAGE))
                .or(jsonHasKeys(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE))
                // And an attachment always counts, even one that never arrived.
                //
                // This is the difference between hiding noise and hiding history. A row typed IMAGE
                // or VIDEO records that the customer sent something, which is worth showing even
                // when the bytes are gone: the thread says the attachment is unavailable and a person
                // can ask them to resend. A receipt stub records nothing at all and is typed TEXT or
                // SYSTEM by default, because a receipt carries no type for parseType to read. So the
                // type is what separates them, and dropping this clause would quietly delete twenty
                // real attachments from the visible history on local alone.
                .or(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MESSAGE_TYPE.in(
                        WhatsappMessageType.IMAGE,
                        WhatsappMessageType.VIDEO,
                        WhatsappMessageType.AUDIO,
                        WhatsappMessageType.DOCUMENT,
                        WhatsappMessageType.STICKER));
    }

    /**
     * Non-null and not an empty object or array. {@code {}} is stored in places null was meant.
     *
     * <p>{@code COALESCE} is load-bearing, not decoration. {@code JSON_LENGTH(NULL)} is NULL and
     * {@code NULL > 0} is NULL, so without it this contributes NULL rather than false to the chain of
     * ORs above. That happens to give the right answer today, because an OR is only true when some
     * operand is true, but it makes the whole predicate unsafe to negate or nest later, and the reason
     * would not be obvious to whoever did it.
     */
    private static Condition jsonHasKeys(Field<?> column) {
        return DSL.condition("COALESCE(JSON_LENGTH({0}), 0) > 0", column);
    }

    private Condition tenant(String appCode, String clientCode) {
        return ENTITY_PROCESSOR_WHATSAPP_MESSAGES
                .APP_CODE
                .eq(appCode)
                .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.CLIENT_CODE.eq(clientCode));
    }

    /**
     * SENT_TIME is null on rows that never got a send timestamp, so fall back to CREATED_AT rather
     * than letting those sort to the bottom of a thread regardless of when they happened.
     */
    private Field<LocalDateTime> orderKey() {
        return orderKeyOf(this.table);
    }

    /**
     * The same ordering key, resolved against a specific table instance.
     *
     * <p>Needed because {@link #orderKey()} is a {@code coalesce} expression rather than a column, so
     * it has no name to look up on an alias. Asking an aliased table for a field by that expression's
     * generated name returns null, and the null only surfaces when jOOQ tries to build the
     * comparison: every send and every health read failed with a NullPointerException out of the
     * first-contact subquery, nowhere near the line at fault.
     */
    private static Field<LocalDateTime> orderKeyOf(Table<?> t) {
        return DSL.coalesce(
                t.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.SENT_TIME),
                t.field(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.CREATED_AT));
    }

    /**
     * FULLTEXT where the term is long enough for the index, LIKE otherwise. MySQL's
     * innodb_ft_min_token_size defaults to 3, so a shorter term matches nothing in boolean mode and
     * would silently return an empty result rather than the obvious matches.
     */
    private Condition bodyMatches(String search) {
        String term = search.trim();
        if (term.length() < 3)
            return ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BODY_TEXT.likeIgnoreCase("%" + term + "%");

        return DSL.condition(
                "MATCH({0}) AGAINST ({1} IN BOOLEAN MODE)",
                ENTITY_PROCESSOR_WHATSAPP_MESSAGES.BODY_TEXT, DSL.inline(term + "*"));
    }

    /** Per-deal rollup for the conversation list. */
    public record ThreadSummary(LocalDateTime lastMessageAt, int unreadCount) {}

    /**
     * Marks messages whose attachment has been collected, so the thread can say so.
     *
     * <p>The files service deletes the bytes on its own schedule, driven by the lifetime stamped on
     * each file at upload. Nothing tells this service when that happens, so without this a bubble
     * keeps pointing at an object that is no longer there and renders as a broken frame. Saying "the
     * attachment expired" is the honest version of the same fact.
     *
     * <p><b>Only files this conversation owns.</b> Two thirds of the media rows point at product
     * brochures and other shared assets that are sent through threads but stored elsewhere, are
     * never given a lifetime, and are therefore never deleted. Marking those expired would tell a
     * reader an attachment is gone while it sits there perfectly intact. The prefix test mirrors
     * exactly what {@code BridgeMediaService} and the outgoing upload write, so the two cannot
     * disagree about which files are conversation attachments.
     *
     * <p>Note where the risk sits: getting this filter wrong shows a wrong message, where getting
     * the deletion filter wrong destroys a file. That asymmetry is why deletion is decided per-file
     * at upload and this is allowed to be a query.
     *
     * @param cutoff messages created before this are considered collected. Deliberately older than
     *     the retention window, so this never claims a file is gone while it still exists.
     * @param notBefore the earliest message this may touch: the day files began carrying lifetimes.
     *     Anything older was stored without one, is never deleted, and must never be marked gone.
     */
    public Mono<Integer> stampExpiredMedia(LocalDateTime cutoff, LocalDateTime notBefore, int limit) {

        Field<String> storedPath = DSL.field(
                "json_unquote(json_extract({0}, '$.filePath'))",
                String.class,
                ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_FILE_DETAIL);

        return Mono.from(this.dslContext
                        .update(ENTITY_PROCESSOR_WHATSAPP_MESSAGES)
                        .set(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_EXPIRED_AT, LocalDateTime.now(ZoneOffset.UTC))
                        // Cleared together with the stamp. They point at objects that no longer
                        // exist, and leaving them is what makes the bubble render a broken image
                        // instead of the sentence explaining why there is nothing to show.
                        .setNull(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_FILE_DETAIL)
                        .setNull(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_THUMBNAIL_FILE_DETAIL)
                        .where(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.MEDIA_EXPIRED_AT.isNull())
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.CREATED_AT.lt(cutoff))
                        // Never older than the day files started carrying lifetimes. Anything
                        // before it was stored without one and can therefore never be deleted, so
                        // marking it expired would tell a reader an attachment is gone while it is
                        // still there. Running this without the floor stamped fifteen legacy rows;
                        // they happened to point at bytes that were already missing, which was luck
                        // rather than the query being right.
                        .and(ENTITY_PROCESSOR_WHATSAPP_MESSAGES.CREATED_AT.ge(notBefore))
                        .and(storedPath.like("/whatsapp/%"))
                        .limit(limit))
                .defaultIfEmpty(0);
    }
}
