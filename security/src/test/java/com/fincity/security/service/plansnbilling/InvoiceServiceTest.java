package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
	}
}
