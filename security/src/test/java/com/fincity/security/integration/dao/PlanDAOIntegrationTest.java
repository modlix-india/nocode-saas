package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlanDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private PlanDAO planDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ULong testClientId;
	private ULong testAppId;
	private String testClientCode;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		String ts = String.valueOf(System.currentTimeMillis());
		testClientCode = "PL" + ts.substring(ts.length() - 6);

		testClientId = insertTestClient(testClientCode, "Plan Test Client " + ts, "BUS")
				.block();
		testAppId = insertTestApp(SYSTEM_CLIENT_ID, "planapp", "Plan Test App")
				.block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_plan WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_plan_limit WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_plan_cycle WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_plan WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1 OR APP_CODE = 'planapp' OR APP_CODE = 'planap2'").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper methods ---

	private Mono<ULong> insertPlan(ULong clientId, ULong appId, String name, String planCode,
			String status, boolean forRegistration, boolean defaultPlan) {
		var spec = databaseClient.sql(
				"INSERT INTO security_plan (CLIENT_ID, APP_ID, NAME, PLAN_CODE, STATUS, FOR_REGISTRATION, DEFAULT_PLAN) "
						+ "VALUES (:clientId, :appId, :name, :planCode, :status, :forReg, :defPlan)")
				.bind("clientId", clientId.longValue())
				.bind("name", name)
				.bind("planCode", planCode)
				.bind("status", status)
				.bind("forReg", forRegistration ? 1 : 0)
				.bind("defPlan", defaultPlan ? 1 : 0);

		spec = appId != null ? spec.bind("appId", appId.longValue()) : spec.bindNull("appId", Long.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertClientPlan(ULong clientId, ULong planId, ULong cycleId,
			LocalDateTime startDate, LocalDateTime endDate, LocalDateTime nextInvoiceDate) {
		var spec = databaseClient.sql(
				"INSERT INTO security_client_plan (CLIENT_ID, PLAN_ID, CYCLE_ID, START_DATE, END_DATE, NEXT_INVOICE_DATE) "
						+ "VALUES (:clientId, :planId, :cycleId, :startDate, :endDate, :nextInvoiceDate)")
				.bind("clientId", clientId.longValue())
				.bind("planId", planId.longValue());

		spec = cycleId != null ? spec.bind("cycleId", cycleId.longValue()) : spec.bindNull("cycleId", Long.class);
		spec = startDate != null ? spec.bind("startDate", startDate) : spec.bindNull("startDate", LocalDateTime.class);
		spec = endDate != null ? spec.bind("endDate", endDate) : spec.bindNull("endDate", LocalDateTime.class);
		spec = nextInvoiceDate != null ? spec.bind("nextInvoiceDate", nextInvoiceDate)
				: spec.bindNull("nextInvoiceDate", LocalDateTime.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertPlanCycle(ULong planId, String name, String intervalType,
			String cost, String currency) {
		return databaseClient.sql(
				"INSERT INTO security_plan_cycle (PLAN_ID, NAME, COST, CURRENCY, INTERVAL_TYPE, STATUS) "
						+ "VALUES (:planId, :name, :cost, :currency, :intervalType, 'ACTIVE')")
				.bind("planId", planId.longValue())
				.bind("name", name)
				.bind("cost", cost)
				.bind("currency", currency)
				.bind("intervalType", intervalType)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		void delete_SetsStatusToDeleted() {
			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Delete Test Plan", "DELPLAN1", "ACTIVE", false, false)
							.flatMap(planId -> planDAO.delete(planId)
									.then(databaseClient.sql(
											"SELECT STATUS FROM security_plan WHERE ID = :id")
											.bind("id", planId.longValue())
											.map(row -> row.get("STATUS", String.class))
											.one())))
					.assertNext(status -> assertEquals("DELETED", status))
					.verifyComplete();
		}

		@Test
		void delete_ReturnsUpdateCount() {
			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Delete Count Plan", "DELCNT1", "ACTIVE", false, false)
							.flatMap(planId -> planDAO.delete(planId)))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Client Plan operations")
	class ClientPlanTests {

		@Test
		void addClientToPlan_Successfully() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime end = now.plusMonths(1);
			LocalDateTime nextInvoice = now.plusDays(30);

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Add Client Plan", "ADDCP01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "9.99", "USD")
									.flatMap(cycleId -> planDAO.addClientToPlan(
											testClientId, planId, cycleId, now, end, nextInvoice))))
					.assertNext(Assertions::assertTrue)
					.verifyComplete();
		}

		@Test
		void removeClientFromPlan_SetsEndDate() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime end = now.plusMonths(1);
			LocalDateTime nextInvoice = now.plusDays(30);

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Remove Client Plan", "RMCP001", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "9.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											now, end, nextInvoice))
									.thenReturn(planId))
							.flatMap(planId -> planDAO.removeClientFromPlan(testClientId, planId)
									.then(databaseClient.sql(
											"SELECT END_DATE, NEXT_INVOICE_DATE FROM security_client_plan WHERE CLIENT_ID = :clientId AND PLAN_ID = :planId")
											.bind("clientId", testClientId.longValue())
											.bind("planId", planId.longValue())
											.map(row -> {
												LocalDateTime endDate = row.get("END_DATE", LocalDateTime.class);
												LocalDateTime nextInv = row.get("NEXT_INVOICE_DATE",
														LocalDateTime.class);
												return new LocalDateTime[] { endDate, nextInv };
											})
											.one())))
					.assertNext(dates -> {
						assertNotNull(dates[0], "END_DATE should be set");
						assertNotNull(dates[1], "NEXT_INVOICE_DATE should be set");
						assertEquals(2035, dates[1].getYear(), "NEXT_INVOICE_DATE year should be 2035");
					})
					.verifyComplete();
		}

		@Test
		void getClientPlan_ReturnsCorrectPlan() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime end = now.plusMonths(1);

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Get Client Plan", "GETCP01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "19.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											now, end, now.plusDays(30)))
									.thenReturn(planId))
							.flatMap(planId -> planDAO.getClientPlan(testAppId, testClientId)))
					.assertNext(clientPlan -> {
						assertNotNull(clientPlan);
						assertEquals(testClientId, clientPlan.getClientId());
						assertNotNull(clientPlan.getPlanId());
						assertNotNull(clientPlan.getCycleId());
					})
					.verifyComplete();
		}

		@Test
		void getClientPlan_WithNullAppId_UsesIsNull() {
			LocalDateTime now = LocalDateTime.now();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, null, "Null App Plan", "NULAP01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "9.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											now, now.plusMonths(1), now.plusDays(30)))
									.thenReturn(planId))
							.flatMap(planId -> planDAO.getClientPlan(null, testClientId)))
					.assertNext(clientPlan -> {
						assertNotNull(clientPlan);
						assertEquals(testClientId, clientPlan.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void getClientsForPlan_ReturnsClientIds() {
			LocalDateTime now = LocalDateTime.now();
			String ts = String.valueOf(System.currentTimeMillis());
			String code2 = "P2" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code2, "Second Plan Client " + ts, "BUS")
							.flatMap(clientId2 -> insertPlan(SYSTEM_CLIENT_ID, testAppId, "Multi Client Plan",
									"MCLPL01", "ACTIVE", false, false)
									.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "9.99", "USD")
											.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
													now, now.plusMonths(1), now.plusDays(30))
													.then(insertClientPlan(clientId2, planId, cycleId,
															now, now.plusMonths(1), now.plusDays(30))))
											.thenReturn(planId))
									.flatMap(planId -> planDAO.getClientsForPlan(planId).collectList())))
					.assertNext(clientIds -> {
						assertNotNull(clientIds);
						assertEquals(2, clientIds.size());
						assertTrue(clientIds.contains(testClientId));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("findConflictPlans()")
	class FindConflictPlansTests {

		private String systemClientCode;

		@BeforeEach
		void setUpConflictTests() {
			// findConflictPlans joins SECURITY_CLIENT via SECURITY_PLAN.CLIENT_ID (plan owner),
			// so urlClientCode must be the plan owner's code, not the subscribing client's code.
			systemClientCode = databaseClient.sql("SELECT CODE FROM security_client WHERE ID = 1")
					.map(row -> row.get("CODE", String.class))
					.one()
					.block();
		}

		@Test
		void findConflictPlans_NoConflict_ReturnsTrue() {
			// When no overlapping apps exist in client's current plans, returns true (no conflict)
			LocalDateTime now = LocalDateTime.now();

			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "planap2", "Plan Test App 2")
							.flatMap(appId2 -> insertPlan(SYSTEM_CLIENT_ID, testAppId, "Existing Plan",
									"EXIST01", "ACTIVE", false, false)
									.flatMap(existingPlanId -> insertPlanCycle(existingPlanId, "Monthly", "MONTH",
											"9.99", "USD")
											.flatMap(cycleId -> insertClientPlan(testClientId, existingPlanId, cycleId,
													now, now.plusMonths(1), now.plusDays(30)))
											.thenReturn(existingPlanId))
									.then(insertPlan(SYSTEM_CLIENT_ID, appId2, "New Plan",
											"NEWPL01", "ACTIVE", false, false))
									.flatMap(newPlanId -> planDAO.findConflictPlans(testClientId,
											systemClientCode, newPlanId))))
					.assertNext(noConflict -> assertTrue(noConflict,
							"Should return true (no conflict) when apps don't overlap"))
					.verifyComplete();
		}

		@Test
		void findConflictPlans_WithConflict_ReturnsFalse() {
			// When same apps exist in client's current plan, returns false (conflict exists)
			LocalDateTime now = LocalDateTime.now();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Existing Conflict Plan", "EXCON01", "ACTIVE", false,
							false)
							.flatMap(existingPlanId -> insertPlanCycle(existingPlanId, "Monthly", "MONTH", "9.99",
									"USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, existingPlanId, cycleId,
											now, now.plusYears(1), now.plusDays(30)))
									.thenReturn(existingPlanId))
							.then(insertPlan(SYSTEM_CLIENT_ID, testAppId, "Conflicting Plan", "CNFPL01", "ACTIVE",
									false, false))
							.flatMap(newPlanId -> planDAO.findConflictPlans(testClientId, systemClientCode,
									newPlanId)))
					.assertNext(noConflict -> assertFalse(noConflict,
							"Should return false (conflict) when same app exists in current plan"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readRegistrationPlans()")
	class RegistrationPlanTests {

		@Test
		void readRegistrationPlans_ReturnsRegistrationPlansForAppCode() {

			String sysCode = databaseClient.sql("SELECT CODE FROM security_client WHERE ID = 1")
					.map(row -> row.get("CODE", String.class))
					.one()
					.block();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Reg Plan", "REGPL01", "ACTIVE", true, false)
							.flatMap(planId -> planDAO.readRegistrationPlans(sysCode, "planapp", true)))
					.assertNext(planIds -> {
						assertNotNull(planIds);
						assertFalse(planIds.isEmpty(), "Should find at least one registration plan");
					})
					.verifyComplete();
		}

		@Test
		void readRegistrationPlans_ExcludesNonRegistrationPlans() {

			String sysCode = databaseClient.sql("SELECT CODE FROM security_client WHERE ID = 1")
					.map(row -> row.get("CODE", String.class))
					.one()
					.block();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Non Reg Plan", "NRGPL01", "ACTIVE", false, false)
							.flatMap(planId -> planDAO.readRegistrationPlans(sysCode, "planapp", true)))
					.assertNext(planIds -> {
						assertNotNull(planIds);
						assertTrue(planIds.isEmpty(), "Should not find non-registration plans");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getDefaultPlanId()")
	class DefaultPlanTests {

		@Test
		void getDefaultPlanId_ReturnsId() {
			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Default Plan", "DEFPL01", "ACTIVE", false, true)
							.flatMap(planId -> planDAO.getDefaultPlanId(testAppId)))
					.assertNext(id -> assertNotNull(id))
					.verifyComplete();
		}

		@Test
		void getDefaultPlanId_ReturnsEmptyForNonExistent() {
			StepVerifier.create(planDAO.getDefaultPlanId(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Invoice query operations")
	class InvoiceQueryTests {

		@Test
		void querySubscriptionsNeedingInvoices_FindsPastDue() {
			LocalDateTime pastDate = LocalDateTime.now().minusDays(5);

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Invoice Plan", "INVPL01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly Invoice", "MONTH", "29.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											pastDate.minusMonths(1), pastDate.plusMonths(1), pastDate)))
							.then(planDAO.querySubscriptionsNeedingInvoices().collectList()))
					.assertNext(clientPlans -> {
						assertNotNull(clientPlans);
						assertFalse(clientPlans.isEmpty(),
								"Should find subscriptions needing invoices");
						boolean found = clientPlans.stream()
								.anyMatch(cp -> cp.getClientId().equals(testClientId));
						assertTrue(found, "Should find the test client's subscription");
					})
					.verifyComplete();
		}

		@Test
		void updateNextInvoiceDate_IncrementsCycleNumber() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime nextInvoice = now.plusMonths(1);

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Update Invoice Plan", "UPDIV01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly Update", "MONTH", "19.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											now, now.plusMonths(1), now.plusDays(30))
											.thenReturn(cycleId)))
							.flatMap(cycleId -> planDAO.updateNextInvoiceDate(cycleId, nextInvoice)
									.then(databaseClient.sql(
											"SELECT CYCLE_NUMBER, NEXT_INVOICE_DATE FROM security_client_plan WHERE CYCLE_ID = :cycleId")
											.bind("cycleId", cycleId.longValue())
											.map(row -> {
												int cycleNumber = row.get("CYCLE_NUMBER", Integer.class);
												LocalDateTime nextInv = row.get("NEXT_INVOICE_DATE",
														LocalDateTime.class);
												return new Object[] { cycleNumber, nextInv };
											})
											.one())))
					.assertNext(result -> {
						assertEquals(2, result[0], "Cycle number should be incremented from 1 to 2");
						assertNotNull(result[1], "Next invoice date should be set");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getPreviousPlan()")
	class GetPreviousPlanTests {

		@Test
		void getPreviousPlan_ReturnsPriorPlan() {
			LocalDateTime now = LocalDateTime.now();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Previous Plan", "PRVPL01", "ACTIVE", false, false)
							.flatMap(plan1Id -> insertPlanCycle(plan1Id, "Monthly Prev", "MONTH", "9.99", "USD")
									.flatMap(cycleId1 -> insertClientPlan(testClientId, plan1Id, cycleId1,
											now.minusMonths(2), now.minusMonths(1), now.minusMonths(1)))
									.thenReturn(plan1Id))
							.flatMap(plan1Id -> insertPlan(SYSTEM_CLIENT_ID, testAppId, "Current Plan", "CURPL01",
									"ACTIVE", false, false)
									.flatMap(plan2Id -> insertPlanCycle(plan2Id, "Monthly Cur", "MONTH", "19.99",
											"USD")
											.flatMap(cycleId2 -> insertClientPlan(testClientId, plan2Id, cycleId2,
													now.minusDays(1), now.plusMonths(1), now.plusDays(30)))
											.flatMap(cpId -> planDAO.getPreviousPlan(testAppId,
													testClientId, cpId)))))
					.assertNext(previousPlan -> {
						assertNotNull(previousPlan);
						assertEquals(testClientId, previousPlan.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void getPreviousPlan_ReturnsEmptyWhenNoPrior() {
			LocalDateTime now = LocalDateTime.now();

			StepVerifier.create(
					insertPlan(SYSTEM_CLIENT_ID, testAppId, "Only Plan", "ONLPL01", "ACTIVE", false, false)
							.flatMap(planId -> insertPlanCycle(planId, "Monthly", "MONTH", "9.99", "USD")
									.flatMap(cycleId -> insertClientPlan(testClientId, planId, cycleId,
											now.minusDays(1), now.plusMonths(1), now.plusDays(30))))
							.flatMap(cpId -> planDAO.getPreviousPlan(testAppId, testClientId, cpId)))
					.verifyComplete();
		}
	}
}
