package com.fincity.security.service.plansnbilling;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dao.plansnbilling.PlanCycleDAO;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.InvoiceItem;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.dto.plansnbilling.PlanCycle;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityPlanCycleIntervalType;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;
import com.fincity.security.service.AbstractSecurityUpdatableDataService;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class InvoiceService
        extends AbstractSecurityUpdatableDataService<SecurityInvoiceRecord, ULong, Invoice, InvoiceDAO> {

    private final PlanDAO planDAO;
    private final PlanCycleDAO planCycleDAO;
    private final ClientService clientService;
    private final AppService appService;
    private final ClientUrlService clientUrlService;
    private final SecurityMessageResourceService messageResourceService;

    private final EventCreationService eventCreationService;

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

    public InvoiceService(PlanCycleDAO planCycleDAO, PlanDAO planDAO,
            ClientService clientService, SecurityMessageResourceService messageResourceService,
            EventCreationService eventCreationService, AppService appService, ClientUrlService clientUrlService) {
        this.planCycleDAO = planCycleDAO;
        this.planDAO = planDAO;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
        this.eventCreationService = eventCreationService;
        this.appService = appService;
        this.clientUrlService = clientUrlService;
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.INVOICE;
    }

    public Mono<LocalDateTime> getNextInvoiceDate(ULong planId, ULong cycleId, LocalDateTime startDate,
            LocalDateTime latestInvoiceData) {
        return FlatMapUtil.flatMapMono(

                () -> this.planDAO.readById(planId),

                plan -> this.planCycleDAO.readById(cycleId),

                (plan, cycle) -> {

                    if (!cycle.getPlanId().equals(planId))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.PLAN_CYCLE_NOT_FOUND);

                    LocalDateTime nextInvoiceDate;
                    if (plan.isPrepaid()) {

                        if (latestInvoiceData == null)
                            nextInvoiceDate = startDate;
                        else
                            nextInvoiceDate = this.calculateNextInvoiceDate(startDate, latestInvoiceData, cycle);
                    } else {
                        nextInvoiceDate = this.calculateNextInvoiceDate(startDate,
                                latestInvoiceData == null ? startDate : latestInvoiceData, cycle);
                    }

                    return Mono.just(nextInvoiceDate);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.getNextInvoiceDate"));
    }

    private LocalDateTime calculateNextInvoiceDate(LocalDateTime startDate, LocalDateTime latestInvoiceData,
            PlanCycle cycle) {

        LocalDateTime nextInvoiceDate = switch (cycle.getIntervalType()) {
            case WEEK -> latestInvoiceData.plusWeeks(1);
            case MONTH -> latestInvoiceData.plusMonths(1);
            case QUARTER -> latestInvoiceData.plusMonths(3);
            case ANNUAL -> latestInvoiceData.plusYears(1);
        };

        if (nextInvoiceDate.getDayOfMonth() == startDate.getDayOfMonth())
            return nextInvoiceDate;

        int i = 0;
        while (true) {
            try {
                return nextInvoiceDate.withDayOfMonth(startDate.getDayOfMonth() - i);
            } catch (DateTimeException e) {
                i++;
            }
        }
    }

    public Mono<Void> generateInvoices() {

        return this.planDAO.querySubscriptionsNeedingInvoices()
                .flatMap(this::generateInvoice)
                .then();
    }

    /*
     * Invoices :
     *
     * '5001', '3', '1001', '2001', 'INV-20250831-0001', '2025-08-31 10:00:00',
     * '2025-08-31 10:00:00', '1178.82', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Renewal: 2025-08-31 → 2025-09-30', '2025-08-31
     * 10:00:00', '2025-09-30 10:00:00'
     * '5002', '3', '1001', '2001', 'INV-20250930-0001', '2025-09-30 10:00:00',
     * '2025-09-30 10:00:00', '1178.82', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Renewal: 2025-09-30 → 2025-10-31', '2025-09-30
     * 10:00:00', '2025-10-30 10:00:00'
     * '5003', '3', '1002', '2003', 'INV-20251010-PRORATE', '2025-10-10 15:00:00',
     * '2025-10-10 15:00:00', '1582.85', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:19:06', 'Proration: Upgrade to Pro', '2025-10-10 15:00:00',
     * '2025-11-10 15:00:00'
     * '5004', '2', '1002', '2004', 'INV-20251001-Q1', '2025-10-01 00:00:00',
     * '2025-10-16 00:00:00', '10028.82', 'PENDING', NULL, '2025-10-29 08:09:15',
     * NULL, '2025-10-29 15:19:06', 'Quarterly postpaid charges: 2025-07-01 →
     * 2025-10-01', '2025-10-01 00:00:00', '2026-01-01 00:00:00'
     * '5005', '4', '1003', '2006', 'INV-20251020-W1', '2025-10-20 09:00:00',
     * '2025-10-20 09:00:00', '199.00', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Weekly renewal: 2025-10-20 → 2025-10-27', '2025-10-20
     * 09:00:00', '2025-10-27 09:00:00'
     * '5006', '4', '1003', '2006', 'INV-20251027-W2', '2025-10-27 09:00:00',
     * '2025-10-27 09:00:00', '199.00', 'FAILED', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Weekly renewal: 2025-10-27 → 2025-11-03', '2025-10-27
     * 09:00:00', '2025-11-03 09:00:00'
     * '5007', '4', '1001', '2002', 'INV-20240229-AN1', '2024-02-29 00:00:00',
     * '2024-02-29 00:00:00', '11798.82', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Annual renewal: 2024-02-29 → 2025-02-28', '2024-02-29
     * 00:00:00', '2025-02-28 00:00:00'
     * '5008', '4', '1001', '2002', 'INV-20250228-AN2', '2025-02-28 00:00:00',
     * '2025-02-28 00:00:00', '11798.82', 'PAID', NULL, '2025-10-29 08:09:15', NULL,
     * '2025-10-29 15:18:58', 'Annual renewal: 2025-02-28 → 2026-02-28', '2025-02-28
     * 00:00:00', '2026-02-28 00:00:00'
     *
     * Invoice Items :
     *
     * '1', '5001', 'Starter Web — 2025-08-31 to 2025-09-30', 'Recurring
     * subscription charge', '999.00', '179.82', NULL, NULL, NULL, NULL
     * '2', '5002', 'Starter Web — 2025-09-30 to 2025-10-31', 'Recurring
     * subscription charge', '999.00', '179.82', NULL, NULL, NULL, NULL
     * '3', '5003', 'Prorated credit — Starter', 'Credit for unused period from
     * 2025-10-10 15:00 to 2025-10-31 10:00', '-670.03', '-120.61', NULL, NULL,
     * NULL, NULL
     * '4', '5003', 'Prorated charge — Pro', 'Charge for remaining period from
     * 2025-10-10 15:00 to 2025-10-31 10:00', '2011.43', '362.06', NULL, NULL, NULL,
     * NULL
     * '5', '5004', 'Pro Mobile — 2025-07-01 to 2025-10-01', 'Quarterly postpaid
     * charges', '8499.00', '1529.82', NULL, NULL, NULL, NULL
     * '6', '5005', 'Enterprise Weekly — 2025-10-20 to 2025-10-27', 'Weekly prepaid
     * charge', '199.00', NULL, NULL, NULL, NULL, NULL
     * '7', '5006', 'Enterprise Weekly — 2025-10-27 to 2025-11-03', 'Weekly prepaid
     * charge', '199.00', NULL, NULL, NULL, NULL, NULL
     * '8', '5007', 'Starter Annual — 2024-02-29 to 2025-02-28', 'Annual prepaid
     * charge', '9999.00', '1799.82', NULL, NULL, NULL, NULL
     * '9', '5008', 'Starter Annual — 2025-02-28 to 2026-02-28', 'Annual prepaid
     * charge', '9999.00', '1799.82', NULL, NULL, NULL, NULL
     *
     */

    private Mono<Boolean> generateInvoice(ClientPlan clientPlan) {
        return FlatMapUtil.flatMapMono(

                () -> this.planDAO.readById(clientPlan.getPlanId()),

                plan -> this.planCycleDAO.readById(clientPlan.getCycleId()),

                (plan, cycle) -> this.dao.getInvoiceCount(clientPlan.getClientId(), plan.getAppId()),

                (plan, cycle, count) -> plan.isPrepaid() ? this.generatePrepaidInvoiceItems(clientPlan, plan, cycle)
                        : this.generatePostpaidInvoiceItems(clientPlan, plan, cycle),

                (plan, cycle, count, invoiceItems) -> {

                    if (invoiceItems.isEmpty()) {

                        logger.error("Unable to generate invoice items for client plan: {}", clientPlan);
                        return Mono.just(false);
                    }

                    Invoice invoice = new Invoice();
                    invoice.setClientId(clientPlan.getClientId());
                    invoice.setPlanId(plan.getId());
                    invoice.setCycleId(cycle.getId());
                    invoice.setInvoiceNumber(
                            this.generateInvoiceNumber(clientPlan.getNextInvoiceDate(), cycle.getIntervalType(),
                                    count));
                    invoice.setInvoiceDate(clientPlan.getNextInvoiceDate());
                    invoice.setInvoiceDueDate(clientPlan.getNextInvoiceDate().plusDays(cycle.getPaymentTermsDays()));
                    invoice.setInvoiceAmount(invoiceItems.stream().map(InvoiceItem::getItemTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    invoice.setInvoiceStatus(SecurityInvoiceInvoiceStatus.PENDING);

                    LocalDateTime nextInvoiceDate = this.calculateNextInvoiceDate(clientPlan.getStartDate(),
                            clientPlan.getNextInvoiceDate(), cycle);

                    if (plan.isPrepaid())
                        invoice.setPeriodStart(clientPlan.getNextInvoiceDate())
                                .setPeriodEnd(nextInvoiceDate);
                    else
                        invoice
                                .setPeriodStart(clientPlan.getNextInvoiceDate())
                                .setPeriodEnd(clientPlan.getNextInvoiceDate());

                    return this.dao.create(invoice)
                            .flatMap(x -> this.dao.createInvoiceItems(x.getId(), invoiceItems).map(x::setItems))
                            .flatMap(x -> this.createEvent(x, plan, cycle, clientPlan))
                            .flatMap(x -> this.planDAO.updateNextInvoiceDate(clientPlan.getCycleId(),
                                    nextInvoiceDate))
                            .thenReturn(true);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.generateInvoice"));
    }

    private String generateInvoiceNumber(LocalDateTime nextInvoiceDate, SecurityPlanCycleIntervalType type, int count) {
        return String.format("INV-%s-%s%05d", nextInvoiceDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                type.name().substring(0, 1), count);
    }

    private Mono<Boolean> createEvent(Invoice invoice, Plan plan, PlanCycle cycle, ClientPlan clientPlan) {

        return FlatMapUtil.flatMapMono(

                () -> this.clientService.getClientInfoById(plan.getClientId()),

                urlClient -> this.appService.getAppById(plan.getAppId()),

                (urlClient, app) -> this.clientService.getOwnersEmails(invoice.getClientId(), null, app.getId()),

                (urlClient, app, owners) -> this.clientUrlService.getAppUrl(urlClient.getCode(), app.getAppCode()),

                (urlClient, app, owners, url) -> {

                    EventQueObject event = new EventQueObject()
                            .setAppCode(app.getAppCode())
                            .setClientCode(urlClient.getCode())
                            .setEventName(EventNames.INVOICE_CREATED)
                            .setData(Map.of("invoice", invoice, "ownerEmails", owners, "app", app, "urlClient",
                                    urlClient, "url", url, "cycle", cycle, "clientPlan", clientPlan, "plan", plan));

                    return this.eventCreationService.createEvent(event);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.createEvent"));
    }

    private Mono<List<InvoiceItem>> generatePrepaidInvoiceItems(ClientPlan clientPlan, Plan plan, PlanCycle cycle) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> clientPlan.getCycleNumber() > 1 ? Mono.<ClientPlan>empty()
                        : this.planDAO.getPreviousPlan(plan.getAppId(), clientPlan.getClientId(), clientPlan.getId()),

                pcp -> {
                    if (pcp == null)
                        return Mono.empty();

                    return this.dao.getLastPaidInvoice(pcp.getPlanId(), pcp.getCycleId(), pcp.getClientId());
                },

                (pcp, pInvoice) -> pInvoice != null
                        ? this.planDAO.readById(pcp.getPlanId())
                                .flatMap(p -> this.planCycleDAO.getCycles(pcp.getPlanId()).map(p::setCycles))
                        : Mono.<Plan>empty(),

                (pcp, pInvoice, pPlan) -> {

                    List<InvoiceItem> invoiceItems = new ArrayList<>();

                    if (pPlan != null && pPlan.getCycles() != null && !pPlan.getCycles().isEmpty()) {

                        int factor = pPlan.isPrepaid() ? -1 : 1;
                        // Calculating prorated credit
                        PlanCycle previousCycle = pPlan.getCycles().stream()
                                .filter(c -> c.getId().equals(pcp.getCycleId())).findFirst().orElse(null);
                        if (previousCycle != null) {
                            var start = pInvoice.getPeriodStart();
                            var end = pInvoice.getPeriodEnd();
                            var totalDays = start.until(end, ChronoUnit.DAYS);
                            var unUsedDays = clientPlan.getStartDate().until(end, ChronoUnit.DAYS);
                            var proratedAmount = factor * previousCycle.getCost().doubleValue() * unUsedDays
                                    / totalDays;
                            var proratedTax1 = previousCycle.getTax1() != null
                                    ? factor * proratedAmount * previousCycle.getTax1().doubleValue() / 100
                                    : 0;
                            var proratedTax2 = previousCycle.getTax2() != null
                                    ? factor * proratedAmount * previousCycle.getTax2().doubleValue() / 100
                                    : 0;
                            var proratedTax3 = previousCycle.getTax3() != null
                                    ? factor * proratedAmount * previousCycle.getTax3().doubleValue() / 100
                                    : 0;
                            var proratedTax4 = previousCycle.getTax4() != null
                                    ? factor * proratedAmount * previousCycle.getTax4().doubleValue() / 100
                                    : 0;
                            var proratedTax5 = previousCycle.getTax5() != null
                                    ? factor * proratedAmount * previousCycle.getTax5().doubleValue() / 100
                                    : 0;
                            invoiceItems.add(new InvoiceItem()
                                    .setItemName("Prorated credit - " + previousCycle.getName())
                                    .setItemAmount(BigDecimal.valueOf(proratedAmount))
                                    .setItemTax1(BigDecimal.valueOf(proratedTax1))
                                    .setItemTax2(BigDecimal.valueOf(proratedTax2))
                                    .setItemTax3(BigDecimal.valueOf(proratedTax3))
                                    .setItemTax4(BigDecimal.valueOf(proratedTax4))
                                    .setItemTax5(BigDecimal.valueOf(proratedTax5))
                                    .setCycleId(previousCycle.getId()));
                        }
                    }

                    var amount = cycle.getCost().doubleValue();
                    var tax1 = cycle.getTax1() != null ? amount * cycle.getTax1().doubleValue() / 100 : 0;
                    var tax2 = cycle.getTax2() != null ? amount * cycle.getTax2().doubleValue() / 100 : 0;
                    var tax3 = cycle.getTax3() != null ? amount * cycle.getTax3().doubleValue() / 100 : 0;
                    var tax4 = cycle.getTax4() != null ? amount * cycle.getTax4().doubleValue() / 100 : 0;
                    var tax5 = cycle.getTax5() != null ? amount * cycle.getTax5().doubleValue() / 100 : 0;
                    invoiceItems.add(new InvoiceItem()
                            .setItemName(cycle.getName())
                            .setItemAmount(BigDecimal.valueOf(amount))
                            .setItemTax1(BigDecimal.valueOf(tax1))
                            .setItemTax2(BigDecimal.valueOf(tax2))
                            .setItemTax3(BigDecimal.valueOf(tax3))
                            .setItemTax4(BigDecimal.valueOf(tax4))
                            .setItemTax5(BigDecimal.valueOf(tax5))
                            .setCycleId(cycle.getId()));

                    return Mono.just(invoiceItems);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.generatePrepaidInvoiceItems"));
    }

    private Mono<List<InvoiceItem>> generatePostpaidInvoiceItems(ClientPlan clientPlan, Plan plan, PlanCycle cycle) {
        return FlatMapUtil.flatMapMonoWithNull(

                () -> clientPlan.getCycleNumber() > 1 ? Mono.<ClientPlan>empty()
                        : this.planDAO.getPreviousPlan(plan.getAppId(), clientPlan.getClientId(), clientPlan.getId()),

                pcp -> {
                    if (pcp == null)
                        return Mono.empty();

                    return this.dao.getLastPaidInvoice(pcp.getPlanId(), pcp.getCycleId(), pcp.getClientId());
                },

                (pcp, pInvoice) -> pInvoice != null
                        ? this.planDAO.readById(pcp.getPlanId())
                                .flatMap(p -> this.planCycleDAO.getCycles(pcp.getPlanId()).map(p::setCycles))
                        : Mono.<Plan>empty(),

                (pcp, pInvoice, pPlan) -> {

                    List<InvoiceItem> invoiceItems = new ArrayList<>();

                    if (pPlan != null && pPlan.getCycles() != null && !pPlan.getCycles().isEmpty()) {

                        int factor = pPlan.isPrepaid() ? -1 : 1;
                        // Calculating prorated credit
                        PlanCycle previousCycle = pPlan.getCycles().stream()
                                .filter(c -> c.getId().equals(pcp.getCycleId())).findFirst().orElse(null);
                        if (previousCycle != null) {
                            var start = pInvoice.getPeriodStart();
                            var end = pInvoice.getPeriodEnd();
                            var totalDays = start.until(end, ChronoUnit.DAYS);
                            var unUsedDays = clientPlan.getStartDate().until(end, ChronoUnit.DAYS);
                            var proratedAmount = factor * previousCycle.getCost().doubleValue() * unUsedDays
                                    / totalDays;
                            var proratedTax1 = previousCycle.getTax1() != null
                                    ? factor * proratedAmount * previousCycle.getTax1().doubleValue() / 100
                                    : 0;
                            var proratedTax2 = previousCycle.getTax2() != null
                                    ? factor * proratedAmount * previousCycle.getTax2().doubleValue() / 100
                                    : 0;
                            var proratedTax3 = previousCycle.getTax3() != null
                                    ? factor * proratedAmount * previousCycle.getTax3().doubleValue() / 100
                                    : 0;
                            var proratedTax4 = previousCycle.getTax4() != null
                                    ? factor * proratedAmount * previousCycle.getTax4().doubleValue() / 100
                                    : 0;
                            var proratedTax5 = previousCycle.getTax5() != null
                                    ? factor * proratedAmount * previousCycle.getTax5().doubleValue() / 100
                                    : 0;
                            invoiceItems.add(new InvoiceItem()
                                    .setItemName("Prorated credit - " + previousCycle.getName())
                                    .setItemAmount(BigDecimal.valueOf(proratedAmount))
                                    .setItemTax1(BigDecimal.valueOf(proratedTax1))
                                    .setItemTax2(BigDecimal.valueOf(proratedTax2))
                                    .setItemTax3(BigDecimal.valueOf(proratedTax3))
                                    .setItemTax4(BigDecimal.valueOf(proratedTax4))
                                    .setItemTax5(BigDecimal.valueOf(proratedTax5))
                                    .setCycleId(previousCycle.getId()));
                        }
                    }

                    var amount = cycle.getCost().doubleValue();
                    var tax1 = cycle.getTax1() != null ? amount * cycle.getTax1().doubleValue() / 100 : 0;
                    var tax2 = cycle.getTax2() != null ? amount * cycle.getTax2().doubleValue() / 100 : 0;
                    var tax3 = cycle.getTax3() != null ? amount * cycle.getTax3().doubleValue() / 100 : 0;
                    var tax4 = cycle.getTax4() != null ? amount * cycle.getTax4().doubleValue() / 100 : 0;
                    var tax5 = cycle.getTax5() != null ? amount * cycle.getTax5().doubleValue() / 100 : 0;
                    invoiceItems.add(new InvoiceItem()
                            .setItemName(cycle.getName())
                            .setItemAmount(BigDecimal.valueOf(amount))
                            .setItemTax1(BigDecimal.valueOf(tax1))
                            .setItemTax2(BigDecimal.valueOf(tax2))
                            .setItemTax3(BigDecimal.valueOf(tax3))
                            .setItemTax4(BigDecimal.valueOf(tax4))
                            .setItemTax5(BigDecimal.valueOf(tax5))
                            .setCycleId(cycle.getId()));

                    return Mono.just(invoiceItems);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.generatePostpaidInvoiceItems"));
    }
}
