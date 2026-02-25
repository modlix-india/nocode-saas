package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dao.plansnbilling.PlanCycleDAO;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.InvoiceItem;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.dto.plansnbilling.PlanCycle;
import com.fincity.security.jooq.enums.SecurityPlanCycleIntervalType;
import com.fincity.security.jooq.enums.SecurityPlanCycleStatus;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.SoxLogService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest extends AbstractServiceUnitTest {

	@Mock
	private InvoiceDAO dao;

	@Mock
	private PlanCycleDAO planCycleDAO;

	@Mock
	private PlanDAO planDAO;

	@Mock
	private ClientService clientService;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private EventCreationService eventCreationService;

	@Mock
	private AppService appService;

	@Mock
	private ClientUrlService clientUrlService;

	@Mock
	private SoxLogService soxLogService;

	private InvoiceService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong PLAN_ID = ULong.valueOf(100);
	private static final ULong CYCLE_ID = ULong.valueOf(200);
	private static final ULong APP_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new InvoiceService(planCycleDAO, planDAO, clientService,
				messageResourceService, eventCreationService, appService, clientUrlService);

		// Inject the mocked DAO via reflection
		// InvoiceService -> AbstractSecurityUpdatableDataService ->
		// AbstractJOOQUpdatableDataService -> AbstractJOOQDataService (has dao field)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass()
					.getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// Inject soxLogService via reflection since it's @Autowired private in
		// AbstractSecurityUpdatableDataService
		try {
			var soxField = service.getClass().getSuperclass().getDeclaredField("soxLogService");
			soxField.setAccessible(true);
			soxField.set(service, soxLogService);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject SoxLogService", e);
		}

		setupMessageResourceService(messageResourceService);
		setupSoxLogService(soxLogService);

		lenient().when(soxLogService.create(any(com.fincity.security.dto.SoxLog.class)))
				.thenReturn(Mono.just(new com.fincity.security.dto.SoxLog()));
	}

	private Plan createPlan(ULong id, ULong appId, boolean prepaid) {
		Plan plan = new Plan();
		plan.setId(id);
		plan.setClientId(SYSTEM_CLIENT_ID);
		plan.setAppId(appId);
		plan.setName("Test Plan");
		plan.setDescription("A test plan");
		plan.setPrepaid(prepaid);
		plan.setStatus(SecurityPlanStatus.ACTIVE);
		return plan;
	}

	private PlanCycle createPlanCycle(ULong id, ULong planId, SecurityPlanCycleIntervalType intervalType) {
		PlanCycle cycle = new PlanCycle();
		cycle.setId(id);
		cycle.setPlanId(planId);
		cycle.setName("Monthly Cycle");
		cycle.setCost(BigDecimal.valueOf(999));
		cycle.setCurrency("INR");
		cycle.setIntervalType(intervalType);
		cycle.setPaymentTermsDays(15);
		cycle.setStatus(SecurityPlanCycleStatus.ACTIVE);
		cycle.setTax1(BigDecimal.valueOf(18));
		cycle.setTax2(BigDecimal.ZERO);
		cycle.setTax3(BigDecimal.ZERO);
		cycle.setTax4(BigDecimal.ZERO);
		cycle.setTax5(BigDecimal.ZERO);
		return cycle;
	}

	// =========================================================================
	// getSoxObjectName() tests
	// =========================================================================

	@Nested
	@DisplayName("getSoxObjectName()")
	class GetSoxObjectNameTests {

		@Test
		void returnsINVOICE() {
			assertEquals(SecuritySoxLogObjectName.INVOICE, service.getSoxObjectName());
		}
	}

	// =========================================================================
	// getNextInvoiceDate() tests
	// =========================================================================

	@Nested
	@DisplayName("getNextInvoiceDate()")
	class GetNextInvoiceDateTests {

		@Test
		void prepaid_NullLatestInvoice_ReturnsStartDate() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);
			LocalDateTime startDate = LocalDateTime.of(2025, 8, 31, 10, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, null))
					.assertNext(result -> assertEquals(startDate, result))
					.verifyComplete();
		}

		@Test
		void prepaid_WithLatestInvoice_ReturnsCalculatedDate() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);
			LocalDateTime startDate = LocalDateTime.of(2025, 8, 31, 10, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2025, 9, 30, 10, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2025, result.getYear());
						assertEquals(10, result.getMonthValue());
						assertEquals(31, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void postpaid_NullLatestInvoice_ReturnsCalculatedFromStartDate() {
			Plan plan = createPlan(PLAN_ID, APP_ID, false);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);
			LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, null))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2025, result.getYear());
						assertEquals(8, result.getMonthValue());
						assertEquals(1, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void cyclePlanIdMismatch_ThrowsBadRequest() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, ULong.valueOf(999), SecurityPlanCycleIntervalType.MONTH);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID,
					LocalDateTime.of(2025, 8, 31, 10, 0, 0), null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void prepaid_WeekInterval_RealignsToStartDay() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.WEEK);
			LocalDateTime startDate = LocalDateTime.of(2025, 10, 20, 9, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2025, 10, 20, 9, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			// The algorithm adds 1 week (Oct 27), then realigns to start day-of-month (20)
			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(10, result.getMonthValue());
						assertEquals(20, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void prepaid_QuarterInterval_ReturnsThreeMonthsLater() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.QUARTER);
			LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2025, 7, 1, 0, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(10, result.getMonthValue());
						assertEquals(1, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void prepaid_AnnualInterval_ReturnsOneYearLater() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.ANNUAL);
			LocalDateTime startDate = LocalDateTime.of(2024, 2, 29, 0, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2024, 2, 29, 0, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(2, result.getMonthValue());
						// Feb 29 leap year -> Feb 28 non-leap year adjustment
						assertEquals(28, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void postpaid_WithLatestInvoice_ReturnsCalculatedDate() {
			Plan plan = createPlan(PLAN_ID, APP_ID, false);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);
			LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2025, 10, 1, 0, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(11, result.getMonthValue());
						assertEquals(1, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void postpaid_QuarterInterval_ReturnsThreeMonthsFromStart() {
			Plan plan = createPlan(PLAN_ID, APP_ID, false);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.QUARTER);
			LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, null))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(10, result.getMonthValue());
						assertEquals(1, result.getDayOfMonth());
					})
					.verifyComplete();
		}

		@Test
		void prepaid_MonthEnd31_AdjustsToDayOfMonth() {
			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);
			LocalDateTime startDate = LocalDateTime.of(2025, 1, 31, 10, 0, 0);
			LocalDateTime latestInvoice = LocalDateTime.of(2025, 1, 31, 10, 0, 0);

			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));

			// Jan 31 + 1 month = Feb 28, then tries to adjust to day 31
			// but Feb only has 28 days, so adjusts down
			StepVerifier.create(service.getNextInvoiceDate(PLAN_ID, CYCLE_ID, startDate, latestInvoice))
					.assertNext(result -> {
						assertEquals(2025, result.getYear());
						assertEquals(2, result.getMonthValue());
						assertTrue(result.getDayOfMonth() <= 28);
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// generateInvoices() tests
	// =========================================================================

	@Nested
	@DisplayName("generateInvoices()")
	class GenerateInvoicesTests {

		@Test
		void generateInvoices_NoSubscriptions_CompletesEmpty() {
			when(planDAO.querySubscriptionsNeedingInvoices()).thenReturn(Flux.empty());

			StepVerifier.create(service.generateInvoices())
					.verifyComplete();

			verify(planDAO).querySubscriptionsNeedingInvoices();
		}

		@Test
		void generateInvoices_PrepaidSubscription_GeneratesInvoice() {
			ClientPlan clientPlan = new ClientPlan();
			clientPlan.setId(ULong.valueOf(1));
			clientPlan.setClientId(ULong.valueOf(3));
			clientPlan.setPlanId(PLAN_ID);
			clientPlan.setCycleId(CYCLE_ID);
			clientPlan.setStartDate(LocalDateTime.of(2025, 8, 31, 10, 0, 0));
			clientPlan.setNextInvoiceDate(LocalDateTime.of(2025, 10, 31, 10, 0, 0));
			clientPlan.setCycleNumber(3);

			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);

			Invoice invoice = new Invoice();
			invoice.setId(ULong.valueOf(5001));
			invoice.setClientId(ULong.valueOf(3));

			Client urlClient = new Client();
			urlClient.setId(SYSTEM_CLIENT_ID);
			urlClient.setCode("SYSTEM");

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			when(planDAO.querySubscriptionsNeedingInvoices()).thenReturn(Flux.just(clientPlan));
			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));
			when(dao.getInvoiceCount(ULong.valueOf(3), APP_ID)).thenReturn(Mono.just(2));
			when(dao.create(any(Invoice.class))).thenReturn(Mono.just(invoice));
			when(dao.createInvoiceItems(any(), any())).thenReturn(Mono.just(List.of()));
			when(clientService.getClientInfoById(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(urlClient));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.getOwnersEmails(any(), any(), any())).thenReturn(Mono.just(List.of("test@test.com")));
			when(clientUrlService.getAppUrl(anyString(), anyString())).thenReturn(Mono.just("https://test.com"));
			when(eventCreationService.createEvent(any())).thenReturn(Mono.just(true));
			when(planDAO.updateNextInvoiceDate(eq(CYCLE_ID), any())).thenReturn(Mono.just(true));

			StepVerifier.create(service.generateInvoices())
					.verifyComplete();

			verify(dao).create(any(Invoice.class));
			verify(planDAO).updateNextInvoiceDate(eq(CYCLE_ID), any());
		}

		@Test
		void generateInvoices_PostpaidSubscription_GeneratesInvoice() {
			ClientPlan clientPlan = new ClientPlan();
			clientPlan.setId(ULong.valueOf(2));
			clientPlan.setClientId(ULong.valueOf(3));
			clientPlan.setPlanId(PLAN_ID);
			clientPlan.setCycleId(CYCLE_ID);
			clientPlan.setStartDate(LocalDateTime.of(2025, 7, 1, 0, 0, 0));
			clientPlan.setNextInvoiceDate(LocalDateTime.of(2025, 10, 1, 0, 0, 0));
			clientPlan.setCycleNumber(2);

			Plan plan = createPlan(PLAN_ID, APP_ID, false);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.QUARTER);

			Invoice invoice = new Invoice();
			invoice.setId(ULong.valueOf(5004));
			invoice.setClientId(ULong.valueOf(3));

			Client urlClient = new Client();
			urlClient.setId(SYSTEM_CLIENT_ID);
			urlClient.setCode("SYSTEM");

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			when(planDAO.querySubscriptionsNeedingInvoices()).thenReturn(Flux.just(clientPlan));
			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));
			when(dao.getInvoiceCount(ULong.valueOf(3), APP_ID)).thenReturn(Mono.just(1));
			when(dao.create(any(Invoice.class))).thenReturn(Mono.just(invoice));
			when(dao.createInvoiceItems(any(), any())).thenReturn(Mono.just(List.of()));
			when(clientService.getClientInfoById(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(urlClient));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.getOwnersEmails(any(), any(), any())).thenReturn(Mono.just(List.of("test@test.com")));
			when(clientUrlService.getAppUrl(anyString(), anyString())).thenReturn(Mono.just("https://test.com"));
			when(eventCreationService.createEvent(any())).thenReturn(Mono.just(true));
			when(planDAO.updateNextInvoiceDate(eq(CYCLE_ID), any())).thenReturn(Mono.just(true));

			StepVerifier.create(service.generateInvoices())
					.verifyComplete();

			verify(dao).create(any(Invoice.class));
		}

		@Test
		void generateInvoices_FirstCycleWithPreviousPlan_CalculatesProration() {
			ClientPlan clientPlan = new ClientPlan();
			clientPlan.setId(ULong.valueOf(3));
			clientPlan.setClientId(ULong.valueOf(3));
			clientPlan.setPlanId(PLAN_ID);
			clientPlan.setCycleId(CYCLE_ID);
			clientPlan.setStartDate(LocalDateTime.of(2025, 10, 10, 15, 0, 0));
			clientPlan.setNextInvoiceDate(LocalDateTime.of(2025, 10, 10, 15, 0, 0));
			clientPlan.setCycleNumber(1);

			Plan plan = createPlan(PLAN_ID, APP_ID, true);
			PlanCycle cycle = createPlanCycle(CYCLE_ID, PLAN_ID, SecurityPlanCycleIntervalType.MONTH);

			// Previous plan info
			ULong prevPlanId = ULong.valueOf(50);
			ULong prevCycleId = ULong.valueOf(150);
			ClientPlan prevClientPlan = new ClientPlan();
			prevClientPlan.setId(ULong.valueOf(10));
			prevClientPlan.setClientId(ULong.valueOf(3));
			prevClientPlan.setPlanId(prevPlanId);
			prevClientPlan.setCycleId(prevCycleId);

			Plan prevPlan = createPlan(prevPlanId, APP_ID, true);
			PlanCycle prevCycle = createPlanCycle(prevCycleId, prevPlanId, SecurityPlanCycleIntervalType.MONTH);
			prevCycle.setCost(BigDecimal.valueOf(500));
			prevPlan.setCycles(List.of(prevCycle));

			Invoice prevInvoice = new Invoice();
			prevInvoice.setId(ULong.valueOf(4000));
			prevInvoice.setPeriodStart(LocalDateTime.of(2025, 10, 1, 0, 0, 0));
			prevInvoice.setPeriodEnd(LocalDateTime.of(2025, 10, 31, 0, 0, 0));

			Invoice newInvoice = new Invoice();
			newInvoice.setId(ULong.valueOf(5003));
			newInvoice.setClientId(ULong.valueOf(3));

			Client urlClient = new Client();
			urlClient.setId(SYSTEM_CLIENT_ID);
			urlClient.setCode("SYSTEM");

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			when(planDAO.querySubscriptionsNeedingInvoices()).thenReturn(Flux.just(clientPlan));
			when(planDAO.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(planDAO.readById(prevPlanId)).thenReturn(Mono.just(prevPlan));
			when(planCycleDAO.readById(CYCLE_ID)).thenReturn(Mono.just(cycle));
			when(planDAO.getPreviousPlan(APP_ID, ULong.valueOf(3), ULong.valueOf(3)))
					.thenReturn(Mono.just(prevClientPlan));
			when(dao.getLastPaidInvoice(prevPlanId, prevCycleId, ULong.valueOf(3)))
					.thenReturn(Mono.just(prevInvoice));
			when(planCycleDAO.getCycles(prevPlanId)).thenReturn(Mono.just(List.of(prevCycle)));
			when(dao.getInvoiceCount(ULong.valueOf(3), APP_ID)).thenReturn(Mono.just(2));
			when(dao.create(any(Invoice.class))).thenReturn(Mono.just(newInvoice));
			when(dao.createInvoiceItems(any(), any())).thenReturn(Mono.just(List.of()));
			when(clientService.getClientInfoById(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(urlClient));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.getOwnersEmails(any(), any(), any())).thenReturn(Mono.just(List.of("test@test.com")));
			when(clientUrlService.getAppUrl(anyString(), anyString())).thenReturn(Mono.just("https://test.com"));
			when(eventCreationService.createEvent(any())).thenReturn(Mono.just(true));
			when(planDAO.updateNextInvoiceDate(eq(CYCLE_ID), any())).thenReturn(Mono.just(true));

			StepVerifier.create(service.generateInvoices())
					.verifyComplete();

			verify(dao).create(any(Invoice.class));
			// Invoice items should include prorated credit + new charge
			verify(dao).createInvoiceItems(any(), argThat(items -> items.size() == 2));
		}
	}
}
