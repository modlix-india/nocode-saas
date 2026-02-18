package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

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
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.plansnbilling.PlanCycleDAO;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dao.plansnbilling.PlanLimitDAO;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest extends AbstractServiceUnitTest {

	@Mock
	private PlanDAO dao;

	@Mock
	private PlanCycleDAO planCycleDAO;

	@Mock
	private PlanLimitDAO planLimitDAO;

	@Mock
	private InvoiceService invoiceService;

	@Mock
	private ClientService clientService;

	@Mock
	private AppService appService;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	private PlanService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong PLAN_ID = ULong.valueOf(100);
	private static final ULong APP_ID = ULong.valueOf(200);

	@BeforeEach
	void setUp() {
		service = new PlanService(planCycleDAO, planLimitDAO, invoiceService, clientService, appService,
				messageResourceService, cacheService);

		// Inject the mocked DAO via reflection
		// PlanService -> AbstractJOOQUpdatableDataService -> AbstractJOOQDataService
		// (has dao field)
		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupEvictionMocks();
	}

	@SuppressWarnings("unchecked")
	private void setupEvictionMocks() {
		lenient().when(cacheService.evictAll(anyString())).thenReturn(Mono.just(true));
		lenient().when(cacheService.evictAllFunction(anyString()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunction(anyString(), any(Object[].class)))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunctionWithKeyFunction(anyString(), any()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunctionWithSuppliers(anyString(), any()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.cacheEmptyValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});
	}

	private Plan createPlan(ULong id, ULong clientId, ULong appId, boolean isDefault) {
		Plan plan = new Plan();
		plan.setId(id);
		plan.setClientId(clientId);
		plan.setAppId(appId);
		plan.setName("Test Plan");
		plan.setDescription("A test plan");
		plan.setDefaultPlan(isDefault);
		plan.setStatus(SecurityPlanStatus.ACTIVE);
		plan.setPrepaid(true);
		plan.setForRegistration(false);
		plan.setOrderNumber(1);
		return plan;
	}

	// =========================================================================
	// create() tests
	// =========================================================================

	@Nested
	@DisplayName("create()")
	class CreateTests {

		@Test
		void create_DefaultPlan_NullAppId_ThrowsError() {
			Plan entity = createPlan(null, SYSTEM_CLIENT_ID, null, true);

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void create_NonDefaultPlan_Creates() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan entity = createPlan(null, null, APP_ID, false);
			Plan created = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.create(any(Plan.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertEquals(PLAN_ID, result.getId());
						assertFalse(result.isDefaultPlan());
					})
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_ChecksManagement() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Plan_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Plan entity = createPlan(null, BUS_CLIENT_ID, APP_ID, false);
			Plan created = createPlan(PLAN_ID, BUS_CLIENT_ID, APP_ID, false);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.create(any(Plan.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertEquals(PLAN_ID, result.getId()))
					.verifyComplete();

			verify(clientService).isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID));
		}
	}

	// =========================================================================
	// read() tests
	// =========================================================================

	@Nested
	@DisplayName("read()")
	class ReadTests {

		@Test
		void read_DelegatesToSuper() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.read(PLAN_ID))
					.assertNext(result -> {
						assertEquals(PLAN_ID, result.getId());
						assertEquals("Test Plan", result.getName());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// readRegistrationPlans() tests
	// =========================================================================

	@Nested
	@DisplayName("readRegistrationPlans()")
	class ReadRegistrationPlansTests {

		@Test
		void readRegistrationPlans_ReturnsPlans() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setUrlClientCode("SYSTEM");
			ca.setUrlAppCode("testApp");
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			plan.setForRegistration(true);
			plan.setOrderNumber(1);

			when(dao.readRegistrationPlans("SYSTEM", "testApp", false))
					.thenReturn(Mono.just(List.of(PLAN_ID)));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.readRegistrationPlans(false))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals(PLAN_ID, result.get(0).getId());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getDefaultPlan() tests
	// =========================================================================

	@Nested
	@DisplayName("getDefaultPlan()")
	class GetDefaultPlanTests {

		@Test
		void getDefaultPlan_UsesCacheAndReturns() {
			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, true);

			when(dao.getDefaultPlanId(APP_ID)).thenReturn(Mono.just(PLAN_ID));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.getDefaultPlan(APP_ID))
					.assertNext(result -> {
						assertEquals(PLAN_ID, result.getId());
						assertTrue(result.isDefaultPlan());
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("defaultPlanId"), any(), eq(APP_ID.toString()));
		}
	}
}
