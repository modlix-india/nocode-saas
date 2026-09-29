package com.fincity.saas.entity.processor.analytics.dao.base;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess.UserInheritanceInfo;

/**
 * The analytics access scope is "assigned to someone in my sub-org OR belonging to a client I
 * manage". That OR cannot use either of the two indexes covering its branches, so it is resolved
 * through a UNION and replaced with an equality on the primary key.
 *
 * The substitution itself is exact by construction - the id set IS the set the OR matched - so
 * what needs guarding is not the happy path but every way the resolution can fail to happen. A
 * failure must always fall back to the original OR. Falling back costs performance; anything else
 * would change what a user is allowed to see, in the one piece of code where that must not drift.
 *
 * These tests leave the DSLContext unset, which makes the resolution throw. That is both the
 * worst case and the one most likely to be reached in production by something unrelated - a
 * connection failure, a pool timeout - so it is the one worth pinning down.
 */
@DisplayName("Analytics access scope")
class BaseAnalyticsVisibilityTest {

    private static final ULong USER_A = ULong.valueOf(1096);
    private static final ULong USER_B = ULong.valueOf(1051);
    private static final ULong CLIENT_A = ULong.valueOf(1858);

    /**
     * The smallest real subclass: the same table, id field and field mappings TicketBucketDAO
     * declares for the columns this code touches. No Spring, no database.
     */
    private static final class TestAnalyticsDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

        private TestAnalyticsDAO() {
            super(
                    Ticket.class,
                    EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS,
                    EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.ID);
        }

        @Override
        protected Map<String, String> getBucketFilterFieldMappings() {
            return Map.of(
                    BaseFilter.Fields.createdByIds, AbstractDTO.Fields.createdBy,
                    BaseFilter.Fields.assignedUserIds, Ticket.Fields.assignedUserId,
                    BaseFilter.Fields.clientIds, BaseProcessorDto.Fields.clientId,
                    BaseFilter.Fields.startDate, AbstractDTO.Fields.createdAt,
                    BaseFilter.Fields.endDate, AbstractDTO.Fields.createdAt);
        }
    }

    /**
     * UserInheritanceInfo declares subOrg and managingClientIds as List&lt;ULong&gt; but its only
     * setters are private and take List&lt;BigInteger&gt;, converting as they go. Reflection is
     * the way in from a test without reaching for a BigInteger round trip that proves nothing.
     */
    private static void set(UserInheritanceInfo info, String fieldName, List<ULong> value) {
        try {
            Field field = UserInheritanceInfo.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(info, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "UserInheritanceInfo." + fieldName + " has moved; this test needs updating", e);
        }
    }

    private static ProcessorAccess access(List<ULong> subOrg, List<ULong> managingClients, boolean bpAccess) {

        UserInheritanceInfo info = new UserInheritanceInfo();
        set(info, "subOrg", subOrg);
        set(info, "managingClientIds", managingClients);

        return new ProcessorAccess()
                .setAppCode("leadzump")
                .setClientCode("FIN")
                .setHasAccessFlag(true)
                .setHasBpAccess(bpAccess)
                .setUserInherit(info);
    }

    /** Walks the condition tree looking for a filter on the primary key. */
    private static boolean containsIdFilter(AbstractCondition condition) {

        if (condition instanceof FilterCondition fc) return AbstractDTO.Fields.id.equals(fc.getField());

        if (condition instanceof ComplexCondition cc && cc.getConditions() != null)
            return cc.getConditions().stream().anyMatch(BaseAnalyticsVisibilityTest::containsIdFilter);

        return false;
    }

    /** Walks the tree looking for an OR that holds filters on all of the given fields. */
    private static boolean containsOrOver(AbstractCondition condition, String... fields) {

        if (!(condition instanceof ComplexCondition cc) || cc.getConditions() == null) return false;

        if (cc.getOperator() == ComplexConditionOperator.OR) {
            List<String> present = cc.getConditions().stream()
                    .filter(FilterCondition.class::isInstance)
                    .map(c -> ((FilterCondition) c).getField())
                    .toList();
            if (List.of(fields).stream().allMatch(present::contains)) return true;
        }

        return cc.getConditions().stream().anyMatch(c -> containsOrOver(c, fields));
    }

    @Test
    @DisplayName("a failed resolution falls back to the original OR, never to a wider scope")
    void resolutionFailureFallsBackToTheOr() {

        // dslContext is never set, so building the union throws. Mono.defer turns that into an
        // error signal, and onErrorReturn has to hand back the OR rather than propagate it.
        AbstractCondition condition = new TestAnalyticsDAO()
                .createBucketConditions(
                        access(List.of(USER_A, USER_B), List.of(CLIENT_A), true), new TicketBucketFilter())
                .block();

        assertNotNull(condition, "a failed resolution must still produce a condition");
        assertTrue(
                containsOrOver(condition, Ticket.Fields.assignedUserId, BaseProcessorDto.Fields.clientId),
                "the original OR across assignedUserId and clientId must survive a failed resolution");
        assertFalse(containsIdFilter(condition), "a failed resolution must not leave an id filter behind");
    }

    @Test
    @DisplayName("a user without BP access keeps the single indexed condition")
    void withoutBpAccessNothingIsResolved() {

        // No managing clients, so getClientIdCondition is empty and the OR has a single branch
        // that the index already serves. Nothing should be resolved.
        AbstractCondition condition = new TestAnalyticsDAO()
                .createBucketConditions(access(List.of(USER_A), List.of(), false), new TicketBucketFilter())
                .block();

        assertNotNull(condition);
        assertFalse(containsIdFilter(condition), "nothing to resolve, so no id filter should appear");
    }

    @Test
    @DisplayName("the fields the resolution needs actually resolve")
    void fieldMappingsResolve() {

        // Two jobs. It keeps the fallback test above honest: unless all four of these resolve,
        // visibilityCondition returns the OR before it ever touches the DSLContext, and that
        // test would pass without exercising anything. It also guards the silent case - a
        // renamed column would not fail anything, the optimisation would just quietly stop
        // applying and the scans would come back.
        TestAnalyticsDAO dao = new TestAnalyticsDAO();

        assertNotNull(dao.getField(Ticket.Fields.assignedUserId), "assignedUserId must map to a column");
        assertNotNull(dao.getField(BaseProcessorDto.Fields.clientId), "clientId must map to a column");
        assertNotNull(dao.getField(AbstractFlowUpdatableDTO.Fields.appCode), "appCode must map to a column");
        assertNotNull(dao.getField(AbstractFlowUpdatableDTO.Fields.clientCode), "clientCode must map to a column");
    }

    @Test
    @DisplayName("the id-resolution union is bounded by a LIMIT")
    void unionIsBounded() {

        // This is the test the 2026-09-28 incident is owed. Without the LIMIT, a scope with a
        // few assignees but several hundred managed client ids makes MySQL abandon the index on
        // the client branch and scan the table - 160,624 rows and 260ms per analytics request,
        // for a result that was then discarded for exceeding the cap. Bounded, the same query
        // reads 10,100 rows in 17ms. A future edit that drops the limit fails here rather than
        // on production.
        TestAnalyticsDAO dao = new TestAnalyticsDAO();
        DSLContext ctx = DSL.using(SQLDialect.MYSQL);

        String sql = dao.visibleIdQuery(
                        ctx,
                        DSL.field("APP_CODE", String.class)
                                .eq("leadzump")
                                .and(DSL.field("CLIENT_CODE", String.class).eq("FIN")),
                        EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                        EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.CLIENT_ID,
                        List.of(USER_A, USER_B),
                        List.of(CLIENT_A))
                .getSQL();

        assertTrue(sql.toLowerCase().contains("union"), "both branches must still be a union");
        assertTrue(
                sql.toLowerCase().trim().matches("(?s).*limit\\s+\\??\\s*$"),
                "the union must end in a LIMIT, otherwise an unselective branch scans the table: " + sql);
    }

    @Test
    @DisplayName("an empty sub-org is left alone rather than resolved into an empty id set")
    void emptySubOrgIsNotResolved() {

        AbstractCondition condition = new TestAnalyticsDAO()
                .createBucketConditions(access(List.of(), List.of(CLIENT_A), true), new TicketBucketFilter())
                .block();

        assertNotNull(condition);
        assertFalse(containsIdFilter(condition), "an empty branch means there is no two-column OR to fix");
    }
}
