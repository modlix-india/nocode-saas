package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBERS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WhatsappPhoneNumberDAO extends BaseProviderDAO<MessageWhatsappPhoneNumbersRecord, WhatsappPhoneNumber> {

    protected WhatsappPhoneNumberDAO() {
        super(
                WhatsappPhoneNumber.class,
                MESSAGE_WHATSAPP_PHONE_NUMBERS,
                MESSAGE_WHATSAPP_PHONE_NUMBERS.ID,
                MESSAGE_WHATSAPP_PHONE_NUMBERS.PHONE_NUMBER_ID);
    }

    /**
     * The owning row for a Meta phone number id, with no tenant filter, because the caller is
     * asking precisely so it can learn the tenant.
     *
     * <p>Safe to leave unscoped: {@code PHONE_NUMBER_ID} carries a unique key of its own
     * ({@code UK2_WHATSAPP_PHONE_NUMBER_PHONE_NUMBER_ID}, on that column alone rather than scoped
     * by app and client), so this can match at most one row. The id is a Meta object id, which is
     * globally unique on their side too.
     *
     * <p>Used only by the inbound webhook, which arrives on one URL for the whole platform and has
     * nothing but the payload to tell it whose message this is. Every other read goes through the
     * access-scoped overload; this one must not become a general-purpose lookup.
     */
    public Mono<WhatsappPhoneNumber> getByPhoneNumberIdInternal(String phoneNumberId) {

        if (phoneNumberId == null || phoneNumberId.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.PHONE_NUMBER_ID.eq(phoneNumberId)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<WhatsappPhoneNumber> getByPhoneNumberId(MessageAccess messageAccess, String phoneNumberId) {

        if (phoneNumberId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.phoneNumberId, phoneNumberId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    // getByAccountAndPhoneNumberId and getDefaultPhoneNumber were both scoped by WhatsApp Business
    // Account, and retired with it. A linked-device session has no business account: the number is
    // the customer's own, resolved by product or by PHONE_NUMBER_ID, and "default" is now a property
    // of the tenant rather than of an account within it.
    /**
     * The tenant's default number.
     *
     * <p>Unambiguous now in a way it was not before. Under the Cloud API "default" was scoped to a
     * business account, so a tenant with two WABAs had two defaults and this unscoped read answered
     * from whichever row the database reached first. A linked-device tenant has numbers and one
     * default among them, so there is nothing left to disambiguate.
     *
     * <p>Prefer {@link #getPlacedByProduct} when a product is in hand: a deal should send from its
     * product's number, and falling back to the default is the last resort rather than the norm.
     */
    public Mono<WhatsappPhoneNumber> getDefaultPhoneNumber(MessageAccess messageAccess) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.isDefault, Boolean.TRUE)
                                .setOperator(FilterConditionOperator.IS_TRUE),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    /**
     * The session row for a bridge session id, unscoped for the same reason as {@link
     * #getByPhoneNumberIdInternal(String)}: the caller is asking precisely so it can learn the
     * tenant.
     *
     * <p>The session id is this row's {@code CODE}, which already carries a unique key, so this
     * matches at most one row. No separate session identifier was introduced: {@code CODE} is
     * already unique, already generated on insert, and already the value that is safe to hand to a
     * caller.
     */
    public Mono<WhatsappPhoneNumber> getBySessionIdInternal(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.CODE.eq(sessionId)))
                .map(e -> e.into(this.pojoClass));
    }

    /** Every session a tenant has, placed or not, for the integration page's list. */
    public Mono<List<WhatsappPhoneNumber>> listForTenant(String appCode, String clientCode) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.APP_CODE.eq(appCode))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.CLIENT_CODE.eq(clientCode))
                        .orderBy(MESSAGE_WHATSAPP_PHONE_NUMBERS.ID.desc()))
                .map(e -> e.into(this.pojoClass))
                .collectList();
    }

    /**
     * A product's live session.
     *
     * <p>Placed sessions only. An unplaced row is either a Cloud API leftover or a link that was
     * retired, and handing either to a sender would fail at the point of sending rather than at the
     * point of resolution, which is much harder to read in a log.
     */
    public Mono<WhatsappPhoneNumber> getPlacedByProduct(String appCode, String clientCode, ULong productId) {

        if (productId == null) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.APP_CODE.eq(appCode))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.PRODUCT_ID.eq(productId))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID.isNotNull())
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    /** The tenant's default session, for products that have no number of their own. */
    public Mono<WhatsappPhoneNumber> getPlacedDefault(String appCode, String clientCode) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.APP_CODE.eq(appCode))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID.isNotNull())
                        // Prefer the marked default, but fall back to whichever placed session
                        // exists: a tenant with one number has usually never marked anything.
                        .orderBy(
                                MESSAGE_WHATSAPP_PHONE_NUMBERS.IS_DEFAULT.desc(),
                                MESSAGE_WHATSAPP_PHONE_NUMBERS.ID.asc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    /** Every session this service believes an instance holds. One half of the reconciliation diff. */
    public Mono<List<WhatsappPhoneNumber>> listByInstance(String instanceId) {

        if (instanceId == null || instanceId.isBlank()) return Mono.just(List.of());

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID.eq(instanceId)))
                .map(e -> e.into(this.pojoClass))
                .collectList();
    }

    /**
     * Applies the state a bridge reports for one session.
     *
     * <p>{@code STATE_SINCE} is written from the bridge's own value rather than from the clock here,
     * because the bridge is where the transition happened and a heartbeat that merely re-reports an
     * unchanged state must not push the timestamp forward. Doing that would let a dead session reset
     * its retirement clock every fifteen seconds and never be reaped.
     */
    public Mono<Integer> applySessionState(
            String sessionId,
            WhatsappSessionState state,
            String reason,
            String country,
            LocalDateTime linkedAt,
            LocalDateTime stateSince) {

        return Mono.from(this.dslContext
                .update(this.table)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_STATE, state)
                .set(
                        MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_REASON,
                        reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)))
                // COALESCE, not overwrite. The bridge omits country and linkedAt until a session has
                // actually paired, and a null on a later heartbeat must not erase what pairing
                // established.
                .set(
                        MESSAGE_WHATSAPP_PHONE_NUMBERS.COUNTRY,
                        DSL.coalesce(DSL.val(country), MESSAGE_WHATSAPP_PHONE_NUMBERS.COUNTRY))
                .set(
                        MESSAGE_WHATSAPP_PHONE_NUMBERS.LINKED_AT,
                        DSL.coalesce(DSL.val(linkedAt), MESSAGE_WHATSAPP_PHONE_NUMBERS.LINKED_AT))
                .set(
                        MESSAGE_WHATSAPP_PHONE_NUMBERS.STATE_SINCE,
                        DSL.coalesce(DSL.val(stateSince), MESSAGE_WHATSAPP_PHONE_NUMBERS.STATE_SINCE))
                .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.CODE.eq(sessionId)));
    }

    /**
     * Places a session on an instance.
     *
     * <p>Guarded so it can only claim an unassigned row. A placement that silently moved a session
     * already living somewhere else would produce two device stores for one number, which is the
     * failure the whole design is arranged to prevent, so the update simply matches nothing and the
     * caller sees zero rows rather than a quiet reassignment.
     */
    public Mono<Integer> assign(String sessionId, String instanceId, String country, LocalDateTime now) {
        return Mono.from(this.dslContext
                .update(this.table)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID, instanceId)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.COUNTRY, country)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_STATE, WhatsappSessionState.PAIRING)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_REASON, "waiting for the QR code to be scanned")
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.STATE_SINCE, now)
                .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.CODE.eq(sessionId))
                .and(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID.isNull()));
    }

    /**
     * Releases a retired session's assignment.
     *
     * <p>Clearing {@code BRIDGE_INSTANCE_ID} is the entire point and is not optional bookkeeping.
     * The generated unique key on the linked number only ignores rows with a null instance, so a
     * retired row that kept its instance would block the same customer from ever linking again.
     * Clearing it is also what lets them come back on a different instance rather than being pinned
     * to the one that just discarded them, which is the whole reason retirement exists.
     */
    public Mono<Integer> releaseAssignment(String sessionId, WhatsappSessionState finalState, String reason,
            LocalDateTime now) {
        return Mono.from(this.dslContext
                .update(this.table)
                .setNull(MESSAGE_WHATSAPP_PHONE_NUMBERS.BRIDGE_INSTANCE_ID)
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_STATE, finalState)
                .set(
                        MESSAGE_WHATSAPP_PHONE_NUMBERS.SESSION_REASON,
                        reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)))
                .set(MESSAGE_WHATSAPP_PHONE_NUMBERS.STATE_SINCE, now)
                .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.CODE.eq(sessionId)));
    }

    public Mono<WhatsappPhoneNumber> getByProductId(MessageAccess messageAccess, ULong productId) {

        if (productId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.productId, productId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }
}
