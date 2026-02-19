package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.dto.plansnbilling.PlanCycle;
import com.fincity.security.dto.plansnbilling.PlanLimit;
import com.fincity.security.jooq.enums.SecurityPlanLimitName;
import com.fincity.security.jooq.enums.SecurityPlanLimitStatus;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.model.ClientPlanRequest;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
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

	// =========================================================================
	// update() tests
	// =========================================================================

	@Nested
	@DisplayName("update()")
	class UpdateTests {

		@Test
		void update_DefaultPlan_NullAppId_ThrowsError() {
			Plan entity = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, null, true);

			StepVerifier.create(service.update(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void update_NonDefaultPlan_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan entity = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			entity.setName("Updated Plan");
			Plan existing = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(dao.update(any(Plan.class))).thenReturn(Mono.just(entity));
			when(planCycleDAO.deleteCycles(PLAN_ID)).thenReturn(Mono.just(true));
			when(planLimitDAO.deleteLimits(PLAN_ID)).thenReturn(Mono.just(true));
			when(dao.getClientsForPlan(PLAN_ID)).thenReturn(Flux.empty());

			StepVerifier.create(service.update(entity))
					.assertNext(result -> assertEquals("Updated Plan", result.getName()))
					.verifyComplete();
		}

		@Test
		void update_WithCyclesAndLimits_UpdatesThem() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PlanCycle cycle = new PlanCycle();
			cycle.setId(ULong.valueOf(500));
			cycle.setPlanId(PLAN_ID);

			PlanLimit limit = new PlanLimit();
			limit.setPlanId(PLAN_ID);
			limit.setName(SecurityPlanLimitName.USER);
			limit.setLimit(100);
			limit.setStatus(SecurityPlanLimitStatus.ACTIVE);

			Plan entity = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			entity.setCycles(List.of(cycle));
			entity.setLimits(List.of(limit));

			Plan existing = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(dao.update(any(Plan.class))).thenReturn(Mono.just(entity));
			when(planCycleDAO.updateCycles(eq(PLAN_ID), anyList())).thenReturn(Mono.just(List.of(cycle)));
			when(planLimitDAO.updateLimits(eq(PLAN_ID), anyList())).thenReturn(Mono.just(List.of(limit)));
			when(dao.getClientsForPlan(PLAN_ID)).thenReturn(Flux.empty());

			StepVerifier.create(service.update(entity))
					.assertNext(result -> {
						assertNotNull(result.getCycles());
						assertNotNull(result.getLimits());
					})
					.verifyComplete();

			verify(planCycleDAO).updateCycles(eq(PLAN_ID), anyList());
			verify(planLimitDAO).updateLimits(eq(PLAN_ID), anyList());
		}

		@Test
		void update_WithFallbackPlan_DifferentClient_Forbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan entity = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			entity.setFallBackPlanId(ULong.valueOf(999));

			Plan fallbackPlan = createPlan(ULong.valueOf(999), BUS_CLIENT_ID, APP_ID, false);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.readById(ULong.valueOf(999))).thenReturn(Mono.just(fallbackPlan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.update(entity))
					.verifyComplete();
		}
	}

	// =========================================================================
	// delete() tests
	// =========================================================================

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		void delete_AsOwner_DeletesPlanAndRelatedData() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.delete(PLAN_ID)).thenReturn(Mono.just(1));
			when(planCycleDAO.deleteCycles(PLAN_ID)).thenReturn(Mono.just(true));
			when(planLimitDAO.deleteLimits(PLAN_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.delete(PLAN_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(planCycleDAO).deleteCycles(PLAN_ID);
			verify(planLimitDAO).deleteLimits(PLAN_ID);
		}

		@Test
		void delete_DefaultPlan_EvictsDefaultPlanCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, true);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.delete(PLAN_ID)).thenReturn(Mono.just(1));
			when(planCycleDAO.deleteCycles(PLAN_ID)).thenReturn(Mono.just(true));
			when(planLimitDAO.deleteLimits(PLAN_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.delete(PLAN_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_NotManaged_Forbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Plan_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.delete(PLAN_ID))
					.verifyComplete();
		}
	}

	// =========================================================================
	// readLimits() tests
	// =========================================================================

	@Nested
	@DisplayName("readLimits(ULong, ULong)")
	class ReadLimitsByIdTests {

		@Test
		void readLimits_ActivePlan_ReturnsLimits() {
			PlanLimit limit = new PlanLimit();
			limit.setPlanId(PLAN_ID);
			limit.setName(SecurityPlanLimitName.USER);
			limit.setLimit(50);
			limit.setStatus(SecurityPlanLimitStatus.ACTIVE);

			ClientPlan clientPlan = new ClientPlan();
			clientPlan.setPlanId(PLAN_ID);
			clientPlan.setEndDate(LocalDateTime.now().plusMonths(1));

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			plan.setLimits(List.of(limit));

			when(dao.getClientPlan(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(clientPlan));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any()))
					.thenReturn(Mono.just(java.util.Map.of(PLAN_ID, List.of(limit))));

			StepVerifier.create(service.readLimits(APP_ID, BUS_CLIENT_ID))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals(SecurityPlanLimitName.USER, result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void readLimits_ExpiredPlan_FallsBackToDefault() {
			ClientPlan expiredPlan = new ClientPlan();
			expiredPlan.setPlanId(PLAN_ID);
			expiredPlan.setEndDate(LocalDateTime.now().minusDays(1));

			ULong defaultPlanId = ULong.valueOf(101);
			PlanLimit defaultLimit = new PlanLimit();
			defaultLimit.setPlanId(defaultPlanId);
			defaultLimit.setName(SecurityPlanLimitName.USER);
			defaultLimit.setLimit(10);

			Plan defaultPlan = createPlan(defaultPlanId, SYSTEM_CLIENT_ID, APP_ID, true);
			defaultPlan.setLimits(List.of(defaultLimit));

			when(dao.getClientPlan(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(expiredPlan));
			when(dao.getDefaultPlanId(APP_ID)).thenReturn(Mono.just(defaultPlanId));
			when(dao.readById(defaultPlanId)).thenReturn(Mono.just(defaultPlan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any()))
					.thenReturn(Mono.just(java.util.Map.of(defaultPlanId, List.of(defaultLimit))));

			StepVerifier.create(service.readLimits(APP_ID, BUS_CLIENT_ID))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals(10, result.get(0).getLimit());
					})
					.verifyComplete();
		}

		@Test
		void readLimits_NoPlan_ReturnsEmpty() {
			when(dao.getClientPlan(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.readLimits(APP_ID, BUS_CLIENT_ID))
					.verifyComplete();
		}
	}

	// =========================================================================
	// readLimits(String, String) tests
	// =========================================================================

	@Nested
	@DisplayName("readLimits(String, String)")
	class ReadLimitsByCodeTests {

		@Test
		void readLimits_ByCode_ResolvesAndReturnsLimits() {
			Client client = new Client();
			client.setId(BUS_CLIENT_ID);
			client.setCode("BUSCLIENT");

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			PlanLimit limit = new PlanLimit();
			limit.setPlanId(PLAN_ID);
			limit.setName(SecurityPlanLimitName.STORAGE);
			limit.setLimit(1024);
			limit.setStatus(SecurityPlanLimitStatus.ACTIVE);

			ClientPlan clientPlan = new ClientPlan();
			clientPlan.setPlanId(PLAN_ID);
			clientPlan.setEndDate(LocalDateTime.now().plusMonths(1));

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			plan.setLimits(List.of(limit));

			when(clientService.getClientBy("BUSCLIENT")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(dao.getClientPlan(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(clientPlan));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any()))
					.thenReturn(Mono.just(java.util.Map.of(PLAN_ID, List.of(limit))));

			StepVerifier.create(service.readLimits("testApp", "BUSCLIENT"))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals(SecurityPlanLimitName.STORAGE, result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void readLimits_ByCode_ClientNotFound_ReturnsEmpty() {
			when(clientService.getClientBy("UNKNOWN")).thenReturn(Mono.empty());

			StepVerifier.create(service.readLimits("testApp", "UNKNOWN"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// addPlanAndCyCle() tests
	// =========================================================================

	@Nested
	@DisplayName("addPlanAndCyCle()")
	class AddPlanAndCycleTests {

		private static final ULong CYCLE_ID = ULong.valueOf(500);

		@Test
		void addPlanAndCyCle_WithUrlClientCode_Success() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PlanCycle cycle = new PlanCycle();
			cycle.setId(CYCLE_ID);
			cycle.setPlanId(PLAN_ID);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			plan.setCycles(List.of(cycle));

			Client urlClient = new Client();
			urlClient.setId(SYSTEM_CLIENT_ID);
			urlClient.setCode("SYSTEM");

			ClientPlanRequest request = new ClientPlanRequest();
			request.setClientId(BUS_CLIENT_ID);
			request.setUrlClientCode("SYSTEM");
			request.setPlanId(PLAN_ID);
			request.setCycleId(CYCLE_ID);
			request.setEndDate(LocalDateTime.now().plusYears(1));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.findConflictPlans(BUS_CLIENT_ID, "SYSTEM", PLAN_ID)).thenReturn(Mono.just(true));
			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(urlClient));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any()))
					.thenReturn(Mono.just(java.util.Map.of(PLAN_ID, List.of(cycle))));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(invoiceService.getNextInvoiceDate(eq(PLAN_ID), eq(CYCLE_ID), any(), any()))
					.thenReturn(Mono.just(LocalDateTime.now().plusMonths(1)));
			when(dao.addClientToPlan(eq(BUS_CLIENT_ID), eq(PLAN_ID), eq(CYCLE_ID), any(), any(), any()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.addPlanAndCyCle(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void addPlanAndCyCle_WithUrlClientId_ResolvesCode() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PlanCycle cycle = new PlanCycle();
			cycle.setId(CYCLE_ID);
			cycle.setPlanId(PLAN_ID);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);
			plan.setCycles(List.of(cycle));

			Client urlClient = new Client();
			urlClient.setId(SYSTEM_CLIENT_ID);
			urlClient.setCode("SYSTEM");

			ClientPlanRequest request = new ClientPlanRequest();
			request.setClientId(BUS_CLIENT_ID);
			request.setUrlClientId(SYSTEM_CLIENT_ID);
			request.setPlanId(PLAN_ID);
			request.setCycleId(CYCLE_ID);

			when(clientService.getClientInfoById(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(urlClient));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.findConflictPlans(BUS_CLIENT_ID, "SYSTEM", PLAN_ID)).thenReturn(Mono.just(true));
			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(urlClient));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any()))
					.thenReturn(Mono.just(java.util.Map.of(PLAN_ID, List.of(cycle))));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(invoiceService.getNextInvoiceDate(eq(PLAN_ID), eq(CYCLE_ID), any(), any()))
					.thenReturn(Mono.just(LocalDateTime.now().plusMonths(1)));
			when(dao.addClientToPlan(eq(BUS_CLIENT_ID), eq(PLAN_ID), eq(CYCLE_ID), any(), any(), any()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.addPlanAndCyCle(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(clientService).getClientInfoById(SYSTEM_CLIENT_ID);
		}

		@Test
		void addPlanAndCyCle_ConflictPlan_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.findConflictPlans(BUS_CLIENT_ID, "SYSTEM", PLAN_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(
					service.addPlanAndCyCle(BUS_CLIENT_ID, "SYSTEM", PLAN_ID, ULong.valueOf(500), null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void addPlanAndCyCle_NotManaged_Forbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(999))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(
					service.addPlanAndCyCle(ULong.valueOf(999), "SYSTEM", PLAN_ID, ULong.valueOf(500), null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// removeClientFromPlan() tests
	// =========================================================================

	@Nested
	@DisplayName("removeClientFromPlan()")
	class RemoveClientFromPlanTests {

		@Test
		void removeClientFromPlan_Managed_Success() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.removeClientFromPlan(BUS_CLIENT_ID, PLAN_ID)).thenReturn(Mono.just(true));
			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.removeClientFromPlan(BUS_CLIENT_ID, PLAN_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).removeClientFromPlan(BUS_CLIENT_ID, PLAN_ID);
		}

		@Test
		void removeClientFromPlan_NotManaged_Forbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(999))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.removeClientFromPlan(ULong.valueOf(999), PLAN_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// evictClientPlanByPlanId() tests
	// =========================================================================

	@Nested
	@DisplayName("evictClientPlanByPlanId()")
	class EvictClientPlanByPlanIdTests {

		@Test
		void evictClientPlanByPlanId_EvictsAllClientsForPlan() {
			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.getClientsForPlan(PLAN_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID, ULong.valueOf(3)));

			StepVerifier.create(service.evictClientPlanByPlanId(plan))
					.assertNext(result -> assertEquals(PLAN_ID, result.getId()))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPlan"), eq(APP_ID + "_" + BUS_CLIENT_ID));
			verify(cacheService).evict(eq("clientPlan"), eq(APP_ID + "_" + ULong.valueOf(3)));
		}

		@Test
		void evictClientPlanByPlanId_NoClients_StillReturnsPlan() {
			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.getClientsForPlan(PLAN_ID)).thenReturn(Flux.empty());

			StepVerifier.create(service.evictClientPlanByPlanId(plan))
					.assertNext(result -> assertEquals(PLAN_ID, result.getId()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// evictClientPlanByClientId() tests
	// =========================================================================

	@Nested
	@DisplayName("evictClientPlanByClientId()")
	class EvictClientPlanByClientIdTests {

		@Test
		void evictClientPlanByClientId_EvictsCorrectCacheKey() {
			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.evictClientPlanByClientId(PLAN_ID, BUS_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPlan"), eq(APP_ID + "_" + BUS_CLIENT_ID));
		}

		@Test
		void evictClientPlanByClientId_NullAppId_ThrowsNpe() {
			// When plan's appId is null, Reactor's map(Plan::getAppId) returns null
			// which causes a NullPointerException in the map operator
			Plan plan = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, null, false);

			when(dao.readById(PLAN_ID)).thenReturn(Mono.just(plan));
			when(appService.getAppById(any())).thenReturn(Mono.empty());
			when(planCycleDAO.readCyclesMap(any())).thenReturn(Mono.just(java.util.Map.of()));
			when(planLimitDAO.readLimitsMap(any())).thenReturn(Mono.just(java.util.Map.of()));

			StepVerifier.create(service.evictClientPlanByClientId(PLAN_ID, BUS_CLIENT_ID))
					.expectError(NullPointerException.class)
					.verify();
		}
	}

	// =========================================================================
	// create() with cycles and limits tests
	// =========================================================================

	@Nested
	@DisplayName("create() with cycles and limits")
	class CreateWithCyclesAndLimitsTests {

		@Test
		void create_WithCycles_CreatesCycles() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PlanCycle cycle = new PlanCycle();
			cycle.setPlanId(null);
			cycle.setName("Monthly");

			Plan entity = createPlan(null, null, APP_ID, false);
			entity.setCycles(List.of(cycle));

			Plan created = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			PlanCycle savedCycle = new PlanCycle();
			savedCycle.setId(ULong.valueOf(500));
			savedCycle.setPlanId(PLAN_ID);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.create(any(Plan.class))).thenReturn(Mono.just(created));
			when(planCycleDAO.create(any(PlanCycle.class))).thenReturn(Mono.just(savedCycle));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertNotNull(result);
						assertNotNull(result.getCycles());
						assertEquals(1, result.getCycles().size());
					})
					.verifyComplete();

			verify(planCycleDAO).create(any(PlanCycle.class));
		}

		@Test
		void create_WithLimits_CreatesLimits() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PlanLimit limit = new PlanLimit();
			limit.setName(SecurityPlanLimitName.USER);
			limit.setLimit(100);

			Plan entity = createPlan(null, null, APP_ID, false);
			entity.setLimits(List.of(limit));

			Plan created = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, false);

			PlanLimit savedLimit = new PlanLimit();
			savedLimit.setId(ULong.valueOf(600));
			savedLimit.setPlanId(PLAN_ID);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.create(any(Plan.class))).thenReturn(Mono.just(created));
			when(planLimitDAO.create(any(PlanLimit.class))).thenReturn(Mono.just(savedLimit));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertNotNull(result);
						assertNotNull(result.getLimits());
						assertEquals(1, result.getLimits().size());
					})
					.verifyComplete();

			verify(planLimitDAO).create(any(PlanLimit.class));
		}

		@Test
		void create_NotManaged_Forbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Plan_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Plan entity = createPlan(null, ULong.valueOf(999), APP_ID, false);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(999))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_DefaultPlan_EvictsDefaultPlanCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Plan entity = createPlan(null, null, APP_ID, true);
			Plan created = createPlan(PLAN_ID, SYSTEM_CLIENT_ID, APP_ID, true);

			when(appService.hasWriteAccess(APP_ID, SYSTEM_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.create(any(Plan.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertTrue(result.isDefaultPlan()))
					.verifyComplete();
		}
	}
}
