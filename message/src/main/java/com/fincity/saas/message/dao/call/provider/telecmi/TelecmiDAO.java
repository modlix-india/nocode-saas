package com.fincity.saas.message.dao.call.provider.telecmi;

import static com.fincity.saas.message.jooq.tables.MessageTelecmiCalls.MESSAGE_TELECMI_CALLS;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffect;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.jooq.tables.records.MessageTelecmiCallsRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.RowCountQuery;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** TeleCMI calls, looked up by the per-call key every webhook carries. */
@Component
public class TelecmiDAO extends BaseProviderDAO<MessageTelecmiCallsRecord, TelecmiCall> {

    protected TelecmiDAO() {
        super(
                TelecmiCall.class,
                MESSAGE_TELECMI_CALLS,
                MESSAGE_TELECMI_CALLS.ID,
                MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID);
    }

    /** A call by our code from {@code extra_params}, scoped to the tenant the webhook token proved. */
    public Mono<TelecmiCall> findByCode(String appCode, String clientCode, String code) {
        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_TELECMI_CALLS)
                        .where(MESSAGE_TELECMI_CALLS.APP_CODE.eq(appCode))
                        .and(MESSAGE_TELECMI_CALLS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_TELECMI_CALLS.CODE.eq(code)))
                .map(rec -> rec.into(TelecmiCall.class));
    }

    /**
     * Records only {@code request_id} and the response, not the whole row: the agent leg's first webhooks can
     * land before this runs, and a whole-row write would undo them. An id a webhook already stamped is kept.
     */
    public Mono<TelecmiCall> recordPlacement(ULong id, String requestId, Map<String, Object> response) {
        return Mono.from(placementStatement(this.dslContext, id, requestId, response))
                .then(this.readById(id));
    }

    /**
     * Marks a call {@code FAILED} unless a webhook already showed it ringing. No {@code END_TIME}: one written
     * here would stick, since the end only ever moves earlier.
     */
    public Mono<TelecmiCall> markPlacementFailed(ULong id, Map<String, Object> response) {
        return Mono.from(placementFailedStatement(this.dslContext, id, response))
                .then(this.readById(id));
    }

    static RowCountQuery placementStatement(DSLContext dsl, ULong id, String requestId, Map<String, Object> response) {
        return dsl.update(MESSAGE_TELECMI_CALLS)
                .set(
                        MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID,
                        DSL.coalesce(MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID, DSL.val(requestId)))
                .set(
                        MESSAGE_TELECMI_CALLS.TELECMI_CALL_RESPONSE,
                        DSL.val(response, MESSAGE_TELECMI_CALLS.TELECMI_CALL_RESPONSE))
                .where(MESSAGE_TELECMI_CALLS.ID.eq(id));
    }

    static RowCountQuery placementFailedStatement(DSLContext dsl, ULong id, Map<String, Object> response) {

        Map<Field<?>, Object> columns = new LinkedHashMap<>();
        columns.put(MESSAGE_TELECMI_CALLS.CALL_STATUS, CallStatus.FAILED);
        if (response != null)
            columns.put(
                    MESSAGE_TELECMI_CALLS.TELECMI_CALL_RESPONSE,
                    DSL.val(response, MESSAGE_TELECMI_CALLS.TELECMI_CALL_RESPONSE));

        return dsl.update(MESSAGE_TELECMI_CALLS)
                .set(columns)
                .where(MESSAGE_TELECMI_CALLS.ID.eq(id))
                .and(MESSAGE_TELECMI_CALLS.CALL_STATUS.eq(CallStatus.QUEUED));
    }

    /** {@code finalised}: this webhook decided the outcome, which exactly one webhook per call does. */
    public record Applied(boolean finalised, boolean recordingAdded) {}

    /**
     * Writes one webhook's effect as guarded column updates, never a whole-row write: two legs' webhooks can
     * arrive together. Each guard makes its statement a no-op once settled, so they are safe in any order and
     * repeated. Times only move earlier, a CDR's leg status beats an event's, and the outcome is written once.
     */
    public Mono<Applied> apply(ULong id, TelecmiWebhookEffect effect) {

        List<RowCountQuery> always = statementsBeforeOutcome(this.dslContext, id, effect);
        RowCountQuery outcome = outcomeStatement(this.dslContext, id, effect);
        RowCountQuery recording = recordingStatement(this.dslContext, id, effect);

        return Flux.fromIterable(always)
                .concatMap(Mono::from)
                .then(outcome == null ? Mono.just(0) : Mono.from(outcome))
                .flatMap(finalised -> (recording == null ? Mono.just(0) : Mono.from(recording))
                        .map(added -> new Applied(finalised > 0, added > 0)))
                .flatMap(applied ->
                        Mono.from(durationStatement(this.dslContext, id)).thenReturn(applied));
    }

    static List<RowCountQuery> statementsBeforeOutcome(DSLContext dsl, ULong id, TelecmiWebhookEffect effect) {

        List<RowCountQuery> queries = new ArrayList<>();
        Map<Field<?>, Object> columns = new LinkedHashMap<>();

        if (effect.providerCallId() != null)
            columns.put(
                    MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID,
                    DSL.coalesce(MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID, DSL.val(effect.providerCallId())));

        if (effect.startTime() != null)
            columns.put(
                    MESSAGE_TELECMI_CALLS.START_TIME, earliest(MESSAGE_TELECMI_CALLS.START_TIME, effect.startTime()));

        LegColumns leg = LegColumns.of(effect.leg());

        if (leg != null) {
            if (effect.cmiuuid() != null)
                columns.put(leg.cmiuuid, DSL.coalesce(leg.cmiuuid, DSL.val(effect.cmiuuid())));

            if (effect.legStatus() != null)
                columns.put(
                        leg.status,
                        effect.cdr()
                                ? DSL.val(effect.legStatus())
                                : DSL.when(leg.cdr.isNull(), DSL.val(effect.legStatus()))
                                        .otherwise(leg.status));

            if (effect.hangupReason() != null) columns.put(leg.hangupReason, DSL.val(effect.hangupReason()));

            if (effect.cdrBody() != null) columns.put(leg.cdr, DSL.val(effect.cdrBody(), leg.cdr));
        }

        if (!columns.isEmpty())
            queries.add(dsl.update(MESSAGE_TELECMI_CALLS).set(columns).where(MESSAGE_TELECMI_CALLS.ID.eq(id)));

        if (effect.progressed())
            queries.add(dsl.update(MESSAGE_TELECMI_CALLS)
                    .set(MESSAGE_TELECMI_CALLS.CALL_STATUS, CallStatus.ORIGINATE)
                    .where(MESSAGE_TELECMI_CALLS.ID.eq(id))
                    .and(notDecided())
                    .and(MESSAGE_TELECMI_CALLS.CALL_STATUS.ne(CallStatus.ORIGINATE)));

        // An agent-leg end is written with the outcome instead, under the same guard.
        if (effect.endTime() != null && !effect.onlyIfCustomerNeverRang())
            queries.add(dsl.update(MESSAGE_TELECMI_CALLS)
                    .set(MESSAGE_TELECMI_CALLS.END_TIME, earliest(MESSAGE_TELECMI_CALLS.END_TIME, effect.endTime()))
                    .where(MESSAGE_TELECMI_CALLS.ID.eq(id)));

        return queries;
    }

    static RowCountQuery outcomeStatement(DSLContext dsl, ULong id, TelecmiWebhookEffect effect) {

        if (effect.finalStatus() == null) return null;

        Condition guard = MESSAGE_TELECMI_CALLS.ID.eq(id).and(notDecided());

        // An agent who never answered: final only while TeleCMI has not started dialling the customer.
        if (effect.onlyIfCustomerNeverRang()) guard = guard.and(MESSAGE_TELECMI_CALLS.LEG2_CMIUUID.isNull());

        Map<Field<?>, Object> columns = new LinkedHashMap<>();
        columns.put(MESSAGE_TELECMI_CALLS.CALL_STATUS, effect.finalStatus());
        if (effect.onlyIfCustomerNeverRang() && effect.endTime() != null)
            columns.put(MESSAGE_TELECMI_CALLS.END_TIME, earliest(MESSAGE_TELECMI_CALLS.END_TIME, effect.endTime()));
        if (effect.conversationDuration() != null)
            columns.put(MESSAGE_TELECMI_CALLS.CONVERSATION_DURATION, effect.conversationDuration());

        return dsl.update(MESSAGE_TELECMI_CALLS).set(columns).where(guard);
    }

    static RowCountQuery recordingStatement(DSLContext dsl, ULong id, TelecmiWebhookEffect effect) {

        if (effect.recordingFile() == null) return null;

        return dsl.update(MESSAGE_TELECMI_CALLS)
                .set(MESSAGE_TELECMI_CALLS.RECORDING_FILE, effect.recordingFile())
                .where(MESSAGE_TELECMI_CALLS.ID.eq(id))
                .and(MESSAGE_TELECMI_CALLS.RECORDING_FILE.isNull());
    }

    static RowCountQuery durationStatement(DSLContext dsl, ULong id) {
        return dsl.update(MESSAGE_TELECMI_CALLS)
                .set(
                        MESSAGE_TELECMI_CALLS.DURATION,
                        DSL.field(
                                "TIMESTAMPDIFF(SECOND, {0}, {1})",
                                Long.class, MESSAGE_TELECMI_CALLS.START_TIME, MESSAGE_TELECMI_CALLS.END_TIME))
                .where(MESSAGE_TELECMI_CALLS.ID.eq(id))
                .and(MESSAGE_TELECMI_CALLS.START_TIME.isNotNull())
                .and(MESSAGE_TELECMI_CALLS.END_TIME.ge(MESSAGE_TELECMI_CALLS.START_TIME));
    }

    private static Field<LocalDateTime> earliest(Field<LocalDateTime> column, LocalDateTime value) {
        return DSL.least(DSL.coalesce(column, DSL.val(value)), DSL.val(value));
    }

    private static Condition notDecided() {
        return MESSAGE_TELECMI_CALLS.CALL_STATUS.notIn(TelecmiWebhookEffect.FINAL);
    }

    @SuppressWarnings("rawtypes")
    private record LegColumns(Field<String> cmiuuid, Field<String> status, Field<String> hangupReason, Field<Map> cdr) {

        static LegColumns of(int leg) {
            return switch (leg) {
                case TelecmiWebhookEffect.AGENT_LEG ->
                    new LegColumns(
                            MESSAGE_TELECMI_CALLS.LEG1_CMIUUID,
                            MESSAGE_TELECMI_CALLS.LEG1_STATUS,
                            MESSAGE_TELECMI_CALLS.LEG1_HANGUP_REASON,
                            MESSAGE_TELECMI_CALLS.LEG1_CDR);
                case TelecmiWebhookEffect.CUSTOMER_LEG ->
                    new LegColumns(
                            MESSAGE_TELECMI_CALLS.LEG2_CMIUUID,
                            MESSAGE_TELECMI_CALLS.LEG2_STATUS,
                            MESSAGE_TELECMI_CALLS.LEG2_HANGUP_REASON,
                            MESSAGE_TELECMI_CALLS.LEG2_CDR);
                default -> null;
            };
        }
    }
}
