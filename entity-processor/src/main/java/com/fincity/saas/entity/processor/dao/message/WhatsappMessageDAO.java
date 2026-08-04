package com.fincity.saas.entity.processor.dao.message;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorWhatsappMessages.ENTITY_PROCESSOR_WHATSAPP_MESSAGES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorWhatsappMessagesRecord;
import java.time.LocalDateTime;
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
                .and(this.isActiveTrue());

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
                .and(this.isActiveTrue());

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
        return DSL.coalesce(
                ENTITY_PROCESSOR_WHATSAPP_MESSAGES.SENT_TIME, ENTITY_PROCESSOR_WHATSAPP_MESSAGES.CREATED_AT);
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
}
