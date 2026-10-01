package com.fincity.saas.message.dao.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.util.SpringContextAccessor;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffect;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffectTest;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiWebhook;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.RowCountQuery;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * The guards that make webhook writes safe in any order, pinned in the SQL they render to.
 *
 * <p>The behaviour itself was replayed against MySQL: every statement of a full call (and of a
 * declined call, and of an agent who never answered) in 60 random interleavings each, ending in the
 * same row every time with exactly one outcome write. These tests keep the guards that result
 * rests on from being edited away.
 */
class TelecmiDAOStatementsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ULong ID = ULong.valueOf(1);
    private static final DSLContext DSL_MYSQL =
            DSL.using(SQLDialect.MYSQL, new Settings().withStatementType(StatementType.STATIC_STATEMENT));

    @BeforeAll
    static void jsonConverterNeedsAnObjectMapper() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(ObjectMapper.class, () -> MAPPER);
        context.refresh();
        new SpringContextAccessor().setApplicationContext(context);
    }

    private static TelecmiWebhookEffect effect(String json) throws Exception {
        return TelecmiWebhookEffect.of(
                new TelecmiCall().setIsOutbound(Boolean.TRUE),
                TelecmiWebhook.of(MAPPER.readTree(json)),
                LocalDateTime.of(2026, 9, 28, 16, 17, 44));
    }

    private static String sql(List<RowCountQuery> queries) {
        return queries.stream().map(RowCountQuery::getSQL).collect(Collectors.joining("\n"));
    }

    @Test
    void anEventWritesOnlyItsOwnLegAndNeverOverAnyCdrStatus() throws Exception {

        String sql = sql(TelecmiDAO.statementsBeforeOutcome(
                DSL_MYSQL, ID, effect(TelecmiWebhookEffectTest.outboundEvent("a", "hangup"))));

        assertTrue(sql.contains("`LEG1_STATUS` = case when `message`.`message_telecmi_calls`.`LEG1_CDR` is null"));
        assertFalse(sql.contains("LEG2_"));
        assertTrue(sql.contains("`PROVIDER_CALL_ID` = coalesce("));
        assertTrue(sql.contains("`START_TIME` = least(coalesce("));
    }

    @Test
    void progressNeverOverwritesAnOutcome() throws Exception {

        String sql = sql(TelecmiDAO.statementsBeforeOutcome(
                DSL_MYSQL, ID, effect(TelecmiWebhookEffectTest.outboundEvent("b", "answered"))));

        assertTrue(sql.contains("`CALL_STATUS` = 'ORIGINATE'"));
        assertTrue(sql.contains("not in ("));
    }

    @Test
    void theOutcomeIsWrittenOnceAndTheEndOnlyMovesEarlier() throws Exception {

        TelecmiWebhookEffect cdr = effect(TelecmiWebhookEffectTest.outboundCdr("b", "answered", "sent_bye", 9, true));

        String before = sql(TelecmiDAO.statementsBeforeOutcome(DSL_MYSQL, ID, cdr));
        String outcome = TelecmiDAO.outcomeStatement(DSL_MYSQL, ID, cdr).getSQL();
        String recording = TelecmiDAO.recordingStatement(DSL_MYSQL, ID, cdr).getSQL();

        assertTrue(before.contains("`END_TIME` = least(coalesce("));
        assertTrue(before.contains("`LEG2_CDR` = '{"));
        assertTrue(outcome.contains("`CALL_STATUS` = 'CALL_COMPLETE'"));
        assertTrue(outcome.contains("`CALL_STATUS` not in ("));
        assertFalse(outcome.contains("LEG2_CMIUUID"));
        assertTrue(recording.contains("`RECORDING_FILE` is null"));
    }

    @Test
    void anAgentWhoNeverAnsweredDecidesTheCallOnlyWhileTheCustomerNeverRang() throws Exception {

        TelecmiWebhookEffect missed =
                effect(TelecmiWebhookEffectTest.outboundCdr("a", "missed", "recv_cancel", null, false));

        String before = sql(TelecmiDAO.statementsBeforeOutcome(DSL_MYSQL, ID, missed));
        String outcome = TelecmiDAO.outcomeStatement(DSL_MYSQL, ID, missed).getSQL();

        assertFalse(before.contains("`END_TIME`"));
        assertTrue(outcome.contains("`LEG2_CMIUUID` is null"));
        assertTrue(outcome.contains("`END_TIME` = least(coalesce("));
    }

    @Test
    void aWebhookThatDecidesNothingWritesNoOutcome() throws Exception {
        TelecmiWebhookEffect started = effect(TelecmiWebhookEffectTest.outboundEvent("a", "started"));
        assertNull(TelecmiDAO.outcomeStatement(DSL_MYSQL, ID, started));
        assertNull(TelecmiDAO.recordingStatement(DSL_MYSQL, ID, started));
        assertEquals(
                2, TelecmiDAO.statementsBeforeOutcome(DSL_MYSQL, ID, started).size());
    }

    @Test
    void placementWritesOnlyTheRequestIdAndTheResponseAndKeepsAnIdAWebhookStamped() {

        String sql = TelecmiDAO.placementStatement(DSL_MYSQL, ID, "REQ-1", java.util.Map.of("code", 200))
                .getSQL();

        assertTrue(sql.contains("`PROVIDER_CALL_ID` = coalesce("));
        assertTrue(sql.contains("`TELECMI_CALL_RESPONSE` = '{"));
        assertFalse(sql.contains("LEG1_"));
        assertFalse(sql.contains("CALL_STATUS"));
        assertFalse(sql.contains("START_TIME"));
    }

    @Test
    void aFailedPlacementNeverOverwritesACallAWebhookShowedRinging() {

        String sql = TelecmiDAO.placementFailedStatement(DSL_MYSQL, ID, null).getSQL();

        assertTrue(sql.contains("`CALL_STATUS` = 'FAILED'"));
        assertTrue(sql.contains("`CALL_STATUS` = 'QUEUED'"));
        assertFalse(sql.contains("END_TIME"));
        assertFalse(sql.contains("TELECMI_CALL_RESPONSE"));
    }
}
