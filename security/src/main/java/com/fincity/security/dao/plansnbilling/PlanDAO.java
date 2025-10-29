package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientPlan.SECURITY_CLIENT_PLAN;
import static com.fincity.security.jooq.tables.SecurityPlan.SECURITY_PLAN;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record11;
import org.jooq.Record14;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Record9;
import org.jooq.SelectSeekStep3;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AbstractClientCheckDAO;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.jooq.tables.records.SecurityPlanRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class PlanDAO extends AbstractClientCheckDAO<SecurityPlanRecord, ULong, Plan> {

    public PlanDAO() {
        super(Plan.class, SECURITY_PLAN, SECURITY_PLAN.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_PLAN.CLIENT_ID;
    }

    @Override
    public Mono<Integer> delete(ULong id) {

        return Mono.from(this.dslContext.update(SECURITY_PLAN).set(SECURITY_PLAN.STATUS, SecurityPlanStatus.DELETED)
                .where(SECURITY_PLAN.ID.eq(id)));
    }

    public Mono<Boolean> removeClientFromPlan(ULong clientId, ULong planId) {
        return Mono.from(this.dslContext.update(SECURITY_CLIENT_PLAN)
                .set(SECURITY_CLIENT_PLAN.END_DATE, LocalDateTime.now()).where(
                        SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId).and(SECURITY_CLIENT_PLAN.PLAN_ID.eq(planId))))
                .map(e -> e == 1);
    }

    public Mono<Boolean> addClientToPlan(ULong clientId, ULong planId, ULong cycleId, LocalDateTime endDate) {

        return Mono.from(this.dslContext.insertInto(SECURITY_CLIENT_PLAN)
                .set(SECURITY_CLIENT_PLAN.CLIENT_ID, clientId)
                .set(SECURITY_CLIENT_PLAN.PLAN_ID, planId)
                .set(SECURITY_CLIENT_PLAN.CYCLE_ID, cycleId)
                .set(SECURITY_CLIENT_PLAN.END_DATE, endDate)).map(e -> e == 1);
    }

    public Mono<Boolean> findConflictPlans(ULong clientId, String urlClientCode, ULong planId) {

        return FlatMapUtil.flatMapMono(

                () -> Flux
                        .from(this.dslContext.select(SECURITY_PLAN.APP_ID).from(SECURITY_PLAN)
                                .where(SECURITY_PLAN.ID.eq(planId)))
                        .map(Record1::value1).collect(Collectors.toSet()),

                planApps -> Flux.from(this.dslContext.select(SECURITY_PLAN.APP_ID).from(SECURITY_CLIENT_PLAN)
                        .join(SECURITY_PLAN).on(SECURITY_CLIENT_PLAN.PLAN_ID.eq(SECURITY_PLAN.ID))
                        .join(SECURITY_CLIENT).on(SECURITY_PLAN.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                        .where(DSL.and(SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId),
                                SECURITY_CLIENT.CODE.eq(urlClientCode),
                                SECURITY_CLIENT_PLAN.END_DATE.gt(LocalDateTime.now()))))
                        .map(Record1::value1).filter(appId -> !planApps.contains(appId)).collectList()
                        .map(apps -> !apps.isEmpty()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.findConflictPlans"));
    }

    public Mono<List<ULong>> readRegistrationPlans(String urlClientCode, String urlAppCode,
            boolean includeMultiAppPlans) {

        return FlatMapUtil.flatMapMono(

                () -> Flux
                        .from(this.dslContext.select(SECURITY_PLAN.ID, SECURITY_PLAN.APP_ID)
                                .from(SECURITY_PLAN)
                                .join(SECURITY_CLIENT).on(SECURITY_PLAN.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                                .join(SECURITY_APP).on(SECURITY_PLAN.APP_ID.eq(SECURITY_APP.ID))
                                .where(DSL.and(
                                        SECURITY_PLAN.FOR_REGISTRATION.eq(ByteUtil.ONE),
                                        SECURITY_CLIENT.CODE.eq(urlClientCode),
                                        SECURITY_APP.APP_CODE.eq(urlAppCode),
                                        SECURITY_PLAN.STATUS.eq(SecurityPlanStatus.ACTIVE))))
                        .collect(Collectors.groupingBy(Record2::value1,
                                Collectors.mapping(Record2::value2, Collectors.toList()))),

                planApps -> Mono.just(planApps.entrySet().stream()
                        .filter(entry -> includeMultiAppPlans || entry.getValue().size() == 1)
                        .map(Map.Entry::getKey)
                        .toList()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.readRegistrationPlans"));
    }

    public Mono<ULong> getDefaultPlanId(ULong appId) {
        return Mono.from(this.dslContext.select(SECURITY_PLAN.ID).from(SECURITY_PLAN)
                .where(SECURITY_PLAN.APP_ID.eq(appId).and(SECURITY_PLAN.DEFAULT_PLAN.eq(ByteUtil.ONE)))
                .orderBy(SECURITY_PLAN.UPDATED_AT.desc())
                .limit(1))
                .map(Record1::value1);
    }

    public Mono<ClientPlan> getClientPlan(ULong appId, ULong clientId) {

        Condition appIdCondition = appId == null ? SECURITY_PLAN.APP_ID.isNull() : SECURITY_PLAN.APP_ID.eq(appId);
        return Mono.from(this.dslContext.select(SECURITY_CLIENT_PLAN.fields()).from(SECURITY_CLIENT_PLAN)
                .join(SECURITY_PLAN).on(SECURITY_CLIENT_PLAN.PLAN_ID.eq(SECURITY_PLAN.ID))
                .where(SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId).and(appIdCondition)).limit(1))
                .map(rec -> rec.into(ClientPlan.class));
    }

    public Flux<ULong> getClientsForPlan(ULong planId) {
        return Flux.from(this.dslContext.select(SECURITY_CLIENT_PLAN.CLIENT_ID).from(SECURITY_CLIENT_PLAN)
                .where(SECURITY_CLIENT_PLAN.PLAN_ID.eq(planId)))
                .map(Record1::value1);
    }

    // Need to select all the client plans those are eligible for invoice
    // generation.
    // 1. If the ClientPlan start date is in the past, generate current billing
    // period and next billing periods.
    // 2. If an invoice is already created for that billing period, skip it.
    // 3. If an invoice is not created for that billing period, return the client
    // plan.
    // 4. It doesn't matter if the CYCLE is not active or deleted, we need to
    // generate the invoice for the billing period.
    // 5. If the Plan is prepaid, we need to generate for the next billing period if
    // the current date + PAYMENT_TERMS_DAYS is in the next billing period .
    // 6. If the Plan is not prepaid, we need to generate for the current billing
    // period.

    public Flux<ClientPlan> getClientPlansForInvoiceGeneration() {

        return Flux.from(eligibleClientPlans(this.dslContext))
                .map(rec -> rec.into(ClientPlan.class));
    }

    // WITH
    // base

    // AS (
    // SELECT
    // cp.ID AS client_plan_id,
    // cp.CLIENT_ID AS client_id,
    // cp.PLAN_ID AS plan_id,
    // cp.CYCLE_ID AS cycle_id,
    // cp.START_DATE AS start_date,
    // cp.END_DATE AS end_date,
    // p.PREPAID AS prepaid,
    // pc.PAYMENT_TERMS_DAYS AS payment_terms_days,
    // pc.INTERVAL_TYPE AS interval_type
    // FROM `security`.`security_client_plan` cp
    // JOIN `security`.`security_plan` p ON p.ID = cp.PLAN_ID
    // JOIN `security`.`security_plan_cycle` pc ON pc.ID = cp.CYCLE_ID
    // WHERE cp.START_DATE <= CURRENT_TIMESTAMP
    // ),

    // idx AS (
    // SELECT
    // b.*,
    // CASE b.interval_type
    // WHEN 'WEEK'

    // THEN TIMESTAMPDIFF(WEEK , b.start_date, CURRENT_TIMESTAMP)
    // WHEN 'MONTH'

    // THEN TIMESTAMPDIFF(MONTH, b.start_date, CURRENT_TIMESTAMP)
    // WHEN 'QUARTER'

    // THEN FLOOR(TIMESTAMPDIFF(MONTH, b.start_date, CURRENT_TIMESTAMP) / 3)
    // WHEN 'ANNUAL'

    // THEN TIMESTAMPDIFF(YEAR , b.start_date, CURRENT_TIMESTAMP)
    // END AS current_idx,
    // CASE b.interval_type
    // WHEN 'WEEK'

    // THEN TIMESTAMPDIFF(WEEK , b.start_date, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL
    // b.payment_terms_days DAY))
    // WHEN 'MONTH'

    // THEN TIMESTAMPDIFF(MONTH, b.start_date, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL
    // b.payment_terms_days DAY))
    // WHEN 'QUARTER'

    // THEN FLOOR(TIMESTAMPDIFF(MONTH, b.start_date, DATE_ADD(CURRENT_TIMESTAMP,
    // INTERVAL b.payment_terms_days DAY)) / 3)
    // WHEN 'ANNUAL'

    // THEN TIMESTAMPDIFF(YEAR , b.start_date, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL
    // b.payment_terms_days DAY))
    // END AS lead_idx
    // FROM base b
    // ),

    // target AS (
    // SELECT
    // i.*,
    // CASE WHEN i.prepaid = 1 THEN i.lead_idx ELSE i.current_idx END AS target_idx,

    // /* Start of the current period (for rule 7) */
    // CASE i.interval_type
    // WHEN 'WEEK'

    // THEN DATE_ADD(i.start_date, INTERVAL i.current_idx WEEK)
    // WHEN 'MONTH'

    // THEN DATE_ADD(i.start_date, INTERVAL i.current_idx MONTH)
    // WHEN 'QUARTER'

    // THEN DATE_ADD(i.start_date, INTERVAL (i.current_idx*3) MONTH)
    // WHEN 'ANNUAL'

    // THEN DATE_ADD(i.start_date, INTERVAL i.current_idx YEAR)
    // END AS current_start,

    // /* Start of the lead-lookahead period (for prepaid-next) */
    // CASE i.interval_type
    // WHEN 'WEEK'

    // THEN DATE_ADD(i.start_date, INTERVAL i.lead_idx WEEK)
    // WHEN 'MONTH'

    // THEN DATE_ADD(i.start_date, INTERVAL i.lead_idx MONTH)
    // WHEN 'QUARTER'

    // THEN DATE_ADD(i.start_date, INTERVAL (i.lead_idx*3) MONTH)
    // WHEN 'ANNUAL'

    // THEN DATE_ADD(i.start_date, INTERVAL i.lead_idx YEAR)
    // END AS lead_start
    // FROM idx i
    // ),

    // inv AS (
    // /* Last already-created period index (PERIOD_START preferred; fallback to
    // INVOICE_DATE) */
    // SELECT
    // i.CLIENT_ID,
    // i.PLAN_ID,
    // i.CYCLE_ID,
    // MAX(
    // CASE pc.INTERVAL_TYPE
    // WHEN 'WEEK'

    // THEN TIMESTAMPDIFF(WEEK , cp.START_DATE, COALESCE(i.PERIOD_START,
    // i.INVOICE_DATE))
    // WHEN 'MONTH'

    // THEN TIMESTAMPDIFF(MONTH, cp.START_DATE, COALESCE(i.PERIOD_START,
    // i.INVOICE_DATE))
    // WHEN 'QUARTER'

    // THEN FLOOR(TIMESTAMPDIFF(MONTH, cp.START_DATE, COALESCE(i.PERIOD_START,
    // i.INVOICE_DATE)) / 3)
    // WHEN 'ANNUAL'

    // THEN TIMESTAMPDIFF(YEAR , cp.START_DATE, COALESCE(i.PERIOD_START,
    // i.INVOICE_DATE))
    // END
    // ) AS last_invoiced_index
    // FROM `security`.`security_invoice` i
    // JOIN `security`.`security_client_plan` cp ON cp.CLIENT_ID = i.CLIENT_ID
    // AND cp.PLAN_ID = i.PLAN_ID
    // AND cp.CYCLE_ID = i.CYCLE_ID
    // JOIN `security`.`security_plan_cycle` pc ON pc.ID = cp.CYCLE_ID
    // GROUP BY i.CLIENT_ID, i.PLAN_ID, i.CYCLE_ID
    // )
    // SELECT
    // t.client_plan_id,
    // t.client_id,
    // t.plan_id,
    // t.cycle_id,
    // t.prepaid,
    // t.interval_type,
    // t.payment_terms_days,

    // COALESCE(v.last_invoiced_index, -1) AS last_invoiced_index,
    // t.current_idx,
    // t.lead_idx,
    // t.target_idx,

    // (COALESCE(v.last_invoiced_index, -1) + 1) AS first_uninvoiced_index,

    // /* First-uninvoiced period start */
    // CASE t.interval_type
    // WHEN 'WEEK'

    // THEN DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) +
    // 1) WEEK)
    // WHEN 'MONTH'

    // THEN DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) +
    // 1) MONTH)
    // WHEN 'QUARTER'

    // THEN DATE_ADD(t.start_date, INTERVAL ((COALESCE(v.last_invoiced_index, -1) +
    // 1)*3) MONTH)
    // WHEN 'ANNUAL'

    // THEN DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) +
    // 1) YEAR)
    // END AS first_uninvoiced_start,

    // /* First-uninvoiced period end */
    // CASE t.interval_type
    // WHEN 'WEEK'

    // THEN DATE_ADD(
    // DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) + 1)
    // WEEK),
    // INTERVAL 1 WEEK)
    // WHEN 'MONTH'

    // THEN DATE_ADD(
    // DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) + 1)
    // MONTH),
    // INTERVAL 1 MONTH)
    // WHEN 'QUARTER'

    // THEN DATE_ADD(
    // DATE_ADD(t.start_date, INTERVAL ((COALESCE(v.last_invoiced_index, -1) + 1)*3)
    // MONTH),
    // INTERVAL 3 MONTH)
    // WHEN 'ANNUAL'

    // THEN DATE_ADD(
    // DATE_ADD(t.start_date, INTERVAL (COALESCE(v.last_invoiced_index, -1) + 1)
    // YEAR),
    // INTERVAL 1 YEAR)
    // END AS first_uninvoiced_end

    // FROM target t
    // LEFT JOIN inv v
    // ON v.CLIENT_ID = t.client_id
    // AND v.PLAN_ID = t.plan_id
    // AND v.CYCLE_ID = t.cycle_id
    // WHERE
    // /* (7) skip if subscription ended before the current billing period starts */
    // t.end_date > t.current_start
    // /* (2)(3)(5)(6) at least one period to bill up to target_idx */
    // AND t.target_idx > COALESCE(v.last_invoiced_index, -1)
    // /* (5) for prepaid-next, ensure the next period exists before END_DATE */
    // AND (t.prepaid = 0 OR t.end_date > t.lead_start)
    // ORDER BY
    // t.client_id ASC,
    // t.plan_id ASC,
    // t.cycle_id ASC;

    // Period i (0-based): the i-th billing period since the subscription started.

    // Monthly: i=0 is [START → START+1 month), i=1 is [START+1 → START+2 months),
    // etc.

    // Weekly/Quarterly/Annual: same idea with weeks/quarters/years.

    // last_invoiced_index: the highest i we’ve already invoiced.
    // (We compute it from your invoice’s PERIOD_START vs the plan’s START_DATE.)

    // current_idx: the i that contains “now”.
    // (If today is inside Sep 30 → Oct 30, then current_idx = 1.)

    // lead_idx: the i that contains “now + PAYMENT_TERMS_DAYS”.
    // (For prepaid with, say, 7-day lead, if “now+7” falls in the next period,
    // lead_idx jumps ahead.)

    // target_idx: what we plan to bill up to right now.

    // Prepaid → target_idx =

    // lead_idx (look ahead).

    // Postpaid → target_idx = current_idx (bill the period you’re in).

    // first_uninvoiced_index: the next i you haven’t billed yet, i.e.
    // last_invoiced_index + 1.

    public static @NotNull SelectSeekStep3<Record1<ULong>, ULong, ULong, ULong> eligibleClientPlans(
            DSLContext ctx) {

        // --- Physical tables (swap for generated tables if you use jOOQ codegen) ---
        Table<?> CP = table(name("security", "security_client_plan"));
        Table<?> P = table(name("security", "security_plan"));
        Table<?> PC = table(name("security", "security_plan_cycle"));
        Table<?> INV = table(name("security", "security_invoice"));

        // client_plan fields
        Field<ULong> CP_ID = field(name("security", "security_client_plan", "ID"), ULong.class);
        Field<ULong> CP_CLIENT_ID = field(name("security", "security_client_plan", "CLIENT_ID"), ULong.class);
        Field<ULong> CP_PLAN_ID = field(name("security", "security_client_plan", "PLAN_ID"), ULong.class);
        Field<ULong> CP_CYCLE_ID = field(name("security", "security_client_plan", "CYCLE_ID"), ULong.class);
        Field<LocalDateTime> CP_START = field(name("security", "security_client_plan", "START_DATE"),
                LocalDateTime.class);
        Field<LocalDateTime> CP_END = field(name("security", "security_client_plan", "END_DATE"), LocalDateTime.class);

        // plan fields
        Field<Byte> P_PREPAID = field(name("security", "security_plan", "PREPAID"), Byte.class);

        // cycle fields
        Field<Integer> PC_TERMS = field(name("security", "security_plan_cycle", "PAYMENT_TERMS_DAYS"), Integer.class);
        Field<String> PC_INTERVAL = field(name("security", "security_plan_cycle", "INTERVAL_TYPE"), String.class);

        // invoice fields (now prefer PERIOD_START; fallback to INVOICE_DATE)
        Field<LocalDateTime> I_DATE = field(name("security", "security_invoice", "INVOICE_DATE"), LocalDateTime.class);
        Field<LocalDateTime> I_PERIOD_START = field(name("security", "security_invoice", "PERIOD_START"),
                LocalDateTime.class);
        Field<ULong> I_CLIENTID = field(name("security", "security_invoice", "CLIENT_ID"), ULong.class);
        Field<ULong> I_PLANID = field(name("security", "security_invoice", "PLAN_ID"), ULong.class);
        Field<ULong> I_CYCLEID = field(name("security", "security_invoice", "CYCLE_ID"), ULong.class);

        /* -------------------- CTE: base -------------------- */
        CommonTableExpression<Record9<ULong, ULong, ULong, ULong, LocalDateTime, LocalDateTime, Byte, Integer, String>> BASE = name(
                "base").as(
                        select(
                                CP_ID.as("client_plan_id"),
                                CP_CLIENT_ID.as("client_id"),
                                CP_PLAN_ID.as("plan_id"),
                                CP_CYCLE_ID.as("cycle_id"),
                                CP_START.as("start_date"),
                                CP_END.as("end_date"),
                                P_PREPAID.as("prepaid"),
                                PC_TERMS.as("payment_terms_days"),
                                PC_INTERVAL.as("interval_type"))
                                .from(CP)
                                .join(P).on(field(name("security", "security_plan", "ID"), ULong.class).eq(CP_PLAN_ID)) // (4)
                                                                                                                        // no
                                                                                                                        // status
                                                                                                                        // filter
                                .join(PC)
                                .on(field(name("security", "security_plan_cycle", "ID"), ULong.class).eq(CP_CYCLE_ID))
                                .where(CP_START.le(currentLocalDateTime())) // (1) started in the past
        );

        // Handy refs to BASE columns
        Field<ULong> BASE_CP_ID = field(name("base", "client_plan_id"), ULong.class);
        Field<ULong> BASE_CLIENT = field(name("base", "client_id"), ULong.class);
        Field<ULong> BASE_PLAN = field(name("base", "plan_id"), ULong.class);
        Field<ULong> BASE_CYCLE = field(name("base", "cycle_id"), ULong.class);
        Field<LocalDateTime> BASE_START = field(name("base", "start_date"), LocalDateTime.class);
        Field<LocalDateTime> BASE_END = field(name("base", "end_date"), LocalDateTime.class);
        Field<Byte> BASE_PREPAID = field(name("base", "prepaid"), Byte.class);
        Field<Integer> BASE_TERMS = field(name("base", "payment_terms_days"), Integer.class);
        Field<String> BASE_ITYPE = field(name("base", "interval_type"), String.class);

        /* -------------------- CTE: idx -------------------- */
        Field<Integer> CURRENT_IDX = when(BASE_ITYPE.eq(inline("WEEK")),
                field("TIMESTAMPDIFF(WEEK, {0}, {1})", Integer.class, BASE_START, currentLocalDateTime()))
                .when(BASE_ITYPE.eq(inline("MONTH")),
                        field("TIMESTAMPDIFF(MONTH, {0}, {1})", Integer.class, BASE_START, currentLocalDateTime()))
                .when(BASE_ITYPE.eq(inline("QUARTER")),
                        field("FLOOR(TIMESTAMPDIFF(MONTH, {0}, {1})/3)", Integer.class, BASE_START,
                                currentLocalDateTime()))
                .when(BASE_ITYPE.eq(inline("ANNUAL")),
                        field("TIMESTAMPDIFF(YEAR, {0}, {1})", Integer.class, BASE_START, currentLocalDateTime()))
                .otherwise(inline(0));

        Field<Integer> LEAD_IDX = when(BASE_ITYPE.eq(inline("WEEK")),
                field("TIMESTAMPDIFF(WEEK, {0}, DATE_ADD({1}, INTERVAL {2} DAY))", Integer.class, BASE_START,
                        currentLocalDateTime(), BASE_TERMS))
                .when(BASE_ITYPE.eq(inline("MONTH")),
                        field("TIMESTAMPDIFF(MONTH, {0}, DATE_ADD({1}, INTERVAL {2} DAY))", Integer.class, BASE_START,
                                currentLocalDateTime(), BASE_TERMS))
                .when(BASE_ITYPE.eq(inline("QUARTER")),
                        field("FLOOR(TIMESTAMPDIFF(MONTH, {0}, DATE_ADD({1}, INTERVAL {2} DAY))/3)", Integer.class,
                                BASE_START, currentLocalDateTime(), BASE_TERMS))
                .when(BASE_ITYPE.eq(inline("ANNUAL")),
                        field("TIMESTAMPDIFF(YEAR, {0}, DATE_ADD({1}, INTERVAL {2} DAY))", Integer.class, BASE_START,
                                currentLocalDateTime(), BASE_TERMS))
                .otherwise(inline(0));

        CommonTableExpression<Record11<ULong, ULong, ULong, ULong, LocalDateTime, LocalDateTime, Byte, Integer, String, Integer, Integer>> IDX = name(
                "idx").as(
                        select(
                                BASE_CP_ID.as("client_plan_id"),
                                BASE_CLIENT.as("client_id"),
                                BASE_PLAN.as("plan_id"),
                                BASE_CYCLE.as("cycle_id"),
                                BASE_START.as("start_date"),
                                BASE_END.as("end_date"),
                                BASE_PREPAID.as("prepaid"),
                                BASE_TERMS.as("payment_terms_days"),
                                BASE_ITYPE.as("interval_type"),
                                CURRENT_IDX.as("current_idx"),
                                LEAD_IDX.as("lead_idx")).from(BASE));

        Field<Integer> IDX_CURR = field(name("idx", "current_idx"), Integer.class);
        Field<Integer> IDX_LEAD = field(name("idx", "lead_idx"), Integer.class);

        /* -------------------- CTE: target -------------------- */
        Field<Integer> TARGET_IDX = when(field(name("idx", "prepaid"), Byte.class).eq(inline((byte) 1)), IDX_LEAD)
                .otherwise(IDX_CURR)
                .as("target_idx");

        Field<LocalDateTime> CURR_START = when(BASE_ITYPE.eq(inline("WEEK")),
                field("DATE_ADD({0}, INTERVAL {1} WEEK)", LocalDateTime.class, BASE_START, IDX_CURR))
                .when(BASE_ITYPE.eq(inline("MONTH")),
                        field("DATE_ADD({0}, INTERVAL {1} MONTH)", LocalDateTime.class, BASE_START, IDX_CURR))
                .when(BASE_ITYPE.eq(inline("QUARTER")),
                        field("DATE_ADD({0}, INTERVAL ({1}*3) MONTH)", LocalDateTime.class, BASE_START, IDX_CURR))
                .when(BASE_ITYPE.eq(inline("ANNUAL")),
                        field("DATE_ADD({0}, INTERVAL {1} YEAR)", LocalDateTime.class, BASE_START, IDX_CURR))
                .otherwise(BASE_START)
                .as("current_start");

        Field<LocalDateTime> LEAD_START = when(BASE_ITYPE.eq(inline("WEEK")),
                field("DATE_ADD({0}, INTERVAL {1} WEEK)", LocalDateTime.class, BASE_START, IDX_LEAD))
                .when(BASE_ITYPE.eq(inline("MONTH")),
                        field("DATE_ADD({0}, INTERVAL {1} MONTH)", LocalDateTime.class, BASE_START, IDX_LEAD))
                .when(BASE_ITYPE.eq(inline("QUARTER")),
                        field("DATE_ADD({0}, INTERVAL ({1}*3) MONTH)", LocalDateTime.class, BASE_START, IDX_LEAD))
                .when(BASE_ITYPE.eq(inline("ANNUAL")),
                        field("DATE_ADD({0}, INTERVAL {1} YEAR)", LocalDateTime.class, BASE_START, IDX_LEAD))
                .otherwise(BASE_START)
                .as("lead_start");

        CommonTableExpression<Record14<ULong, ULong, ULong, ULong, LocalDateTime, LocalDateTime, Byte, Integer, String, Integer, Integer, Integer, LocalDateTime, LocalDateTime>> TARGET = name(
                "target").as(
                        select(
                                field(name("idx", "client_plan_id"), ULong.class).as("client_plan_id"),
                                field(name("idx", "client_id"), ULong.class).as("client_id"),
                                field(name("idx", "plan_id"), ULong.class).as("plan_id"),
                                field(name("idx", "cycle_id"), ULong.class).as("cycle_id"),
                                field(name("idx", "start_date"), LocalDateTime.class).as("start_date"),
                                field(name("idx", "end_date"), LocalDateTime.class).as("end_date"),
                                field(name("idx", "prepaid"), Byte.class).as("prepaid"),
                                field(name("idx", "payment_terms_days"), Integer.class).as("payment_terms_days"),
                                field(name("idx", "interval_type"), String.class).as("interval_type"),
                                IDX_CURR.as("current_idx"),
                                IDX_LEAD.as("lead_idx"),
                                TARGET_IDX,
                                CURR_START,
                                LEAD_START).from(IDX));

        // target refs
        Field<LocalDateTime> T_END_DATE = field(name("target", "end_date"), LocalDateTime.class);
        Field<LocalDateTime> T_CURR_START = field(name("target", "current_start"), LocalDateTime.class);
        Field<LocalDateTime> T_LEAD_START = field(name("target", "lead_start"), LocalDateTime.class);
        Field<Integer> T_TARGET_IDX = field(name("target", "target_idx"), Integer.class);
        Field<Byte> T_PREPAID = field(name("target", "prepaid"), Byte.class);
        Field<String> T_ITYPE = field(name("target", "interval_type"), String.class);
        Field<LocalDateTime> T_START_DATE = field(name("target", "start_date"), LocalDateTime.class);

        /*
         * -------------------- CTE: inv (last invoiced period index)
         * --------------------
         */
        // Use PERIOD_START if present; else INVOICE_DATE for legacy rows
        Field<LocalDateTime> I_PERIOD = coalesce(I_PERIOD_START, I_DATE);

        Field<Integer> INV_INDEX_EXPR = when(PC_INTERVAL.eq(inline("WEEK")),
                field("TIMESTAMPDIFF(WEEK, {0}, {1})", Integer.class, CP_START, I_PERIOD))
                .when(PC_INTERVAL.eq(inline("MONTH")),
                        field("TIMESTAMPDIFF(MONTH, {0}, {1})", Integer.class, CP_START, I_PERIOD))
                .when(PC_INTERVAL.eq(inline("QUARTER")),
                        field("FLOOR(TIMESTAMPDIFF(MONTH, {0}, {1})/3)", Integer.class, CP_START, I_PERIOD))
                .when(PC_INTERVAL.eq(inline("ANNUAL")),
                        field("TIMESTAMPDIFF(YEAR, {0}, {1})", Integer.class, CP_START, I_PERIOD))
                .otherwise(inline(0));

        CommonTableExpression<Record4<ULong, ULong, ULong, Integer>> INVCTE = name("inv").as(
                select(
                        I_CLIENTID.as("client_id"),
                        I_PLANID.as("plan_id"),
                        I_CYCLEID.as("cycle_id"),
                        max(INV_INDEX_EXPR).as("last_invoiced_index"))
                        .from(INV)
                        .join(CP).on(CP_CLIENT_ID.eq(I_CLIENTID)
                                .and(CP_PLAN_ID.eq(I_PLANID))
                                .and(CP_CYCLE_ID.eq(I_CYCLEID)))
                        .join(PC).on(field(name("security", "security_plan_cycle", "ID"), ULong.class).eq(CP_CYCLE_ID))
                        .groupBy(I_CLIENTID, I_PLANID, I_CYCLEID));

        Field<Integer> LAST_INVOICED = coalesce(field(name("inv", "last_invoiced_index"), Integer.class), inline(-1));
        Field<Integer> FIRST_UNINV = LAST_INVOICED.add(inline(1)).as("first_uninvoiced_index");

        // first_uninvoiced_start / end (timestamps as per method signature)
        Field<Timestamp> FIRST_UNINV_START = when(T_ITYPE.eq(inline("WEEK")),
                field("DATE_ADD({0}, INTERVAL ({1}) WEEK)", Timestamp.class, T_START_DATE,
                        LAST_INVOICED.add(inline(1))))
                .when(T_ITYPE.eq(inline("MONTH")),
                        field("DATE_ADD({0}, INTERVAL ({1}) MONTH)", Timestamp.class, T_START_DATE,
                                LAST_INVOICED.add(inline(1))))
                .when(T_ITYPE.eq(inline("QUARTER")),
                        field("DATE_ADD({0}, INTERVAL (({1})*3) MONTH)", Timestamp.class, T_START_DATE,
                                LAST_INVOICED.add(inline(1))))
                .when(T_ITYPE.eq(inline("ANNUAL")),
                        field("DATE_ADD({0}, INTERVAL ({1}) YEAR)", Timestamp.class, T_START_DATE,
                                LAST_INVOICED.add(inline(1))))
                .otherwise(T_START_DATE.cast(Timestamp.class))
                .as("first_uninvoiced_start");

        Field<Timestamp> FIRST_UNINV_END = when(T_ITYPE.eq(inline("WEEK")),
                field("DATE_ADD({0}, INTERVAL 1 WEEK)", Timestamp.class, FIRST_UNINV_START))
                .when(T_ITYPE.eq(inline("MONTH")),
                        field("DATE_ADD({0}, INTERVAL 1 MONTH)", Timestamp.class, FIRST_UNINV_START))
                .when(T_ITYPE.eq(inline("QUARTER")),
                        field("DATE_ADD({0}, INTERVAL 3 MONTH)", Timestamp.class, FIRST_UNINV_START))
                .when(T_ITYPE.eq(inline("ANNUAL")),
                        field("DATE_ADD({0}, INTERVAL 1 YEAR)", Timestamp.class, FIRST_UNINV_START))
                .otherwise(FIRST_UNINV_START)
                .as("first_uninvoiced_end");

        // field(name("target", "client_id"), ULong.class).as("client_id"),
        // field(name("target", "plan_id"), ULong.class).as("plan_id"),
        // field(name("target", "cycle_id"), ULong.class).as("cycle_id"),
        // field(name("target", "prepaid"), Byte.class).as("prepaid"),
        // field(name("target", "interval_type"), String.class).as("interval_type"),
        // field(name("target", "payment_terms_days"),
        // Integer.class).as("payment_terms_days"),

        // LAST_INVOICED.as("last_invoiced_index"),
        // field(name("target", "current_idx"), Integer.class).as("current_idx"),
        // field(name("target", "lead_idx"), Integer.class).as("lead_idx"),
        // field(name("target", "target_idx"), Integer.class).as("target_idx"),

        // FIRST_UNINV,
        // FIRST_UNINV_START,
        // FIRST_UNINV_END

        // Final query
        return ctx
                .with(BASE)
                .with(IDX)
                .with(TARGET)
                .with(INVCTE)
                .select(
                        field(name("target", "client_plan_id"), ULong.class).as("client_plan_id"))
                .from(table(name("target")))
                .leftJoin(table(name("inv")))
                .on(field(name("inv", "client_id"), ULong.class).eq(field(name("target", "client_id"), ULong.class))
                        .and(field(name("inv", "plan_id"), ULong.class)
                                .eq(field(name("target", "plan_id"), ULong.class)))
                        .and(field(name("inv", "cycle_id"), ULong.class)
                                .eq(field(name("target", "cycle_id"), ULong.class))))
                // (7) skip if subscription ended before the current billing period starts
                .where(T_END_DATE.gt(T_CURR_START)
                        // (2)(3)(5)(6) at least one period to bill up to target_idx
                        .and(T_TARGET_IDX.gt(LAST_INVOICED))
                        // (5) for prepaid-next, ensure next period exists before END_DATE
                        .and(T_PREPAID.eq(inline((byte) 0)).or(T_END_DATE.gt(T_LEAD_START))))
                .orderBy(
                        field(name("target", "client_id"), ULong.class).asc(),
                        field(name("target", "plan_id"), ULong.class).asc(),
                        field(name("target", "cycle_id"), ULong.class).asc());
    }

}
