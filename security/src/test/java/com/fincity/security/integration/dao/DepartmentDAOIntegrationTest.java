package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.DepartmentDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

class DepartmentDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private DepartmentDAO departmentDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_department WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_department WHERE CLIENT_ID = 1 AND NAME NOT LIKE 'SYS%'").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper Methods ---

	private Mono<ULong> insertTestDepartment(ULong clientId, String name, String description) {

		var spec = databaseClient.sql(
				"INSERT INTO security_department (CLIENT_ID, NAME, DESCRIPTION) VALUES (:clientId, :name, :description)")
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		spec = description != null ? spec.bind("description", description)
				: spec.bindNull("description", String.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// --- Test Classes ---

	@Nested
	@DisplayName("checkSameClient()")
	class CheckSameClientTests {

		@Test
		@DisplayName("matching client and department returns true")
		void matchingClientAndDepartment_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CSC_Match_" + ts, "matching dept")
							.flatMap(deptId -> departmentDAO.checkSameClient(SYSTEM_CLIENT_ID, deptId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-matching client returns false")
		void nonMatchingClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "NM" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "NonMatchClient", "BUS")
							.flatMap(otherClientId -> insertTestDepartment(otherClientId,
									"CSC_NoMatch_" + ts, "other client dept")
									.flatMap(deptId -> departmentDAO.checkSameClient(
											SYSTEM_CLIENT_ID, deptId))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent department returns false")
		void nonExistentDepartment_ReturnsFalse() {
			StepVerifier.create(
					departmentDAO.checkSameClient(SYSTEM_CLIENT_ID, ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("department belongs to same client with business client")
		void businessClientDepartment_MatchesCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "BC" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "BusClient", "BUS")
							.flatMap(busClientId -> insertTestDepartment(busClientId,
									"CSC_Bus_" + ts, "bus dept")
									.flatMap(deptId -> departmentDAO.checkSameClient(busClientId, deptId)
											.map(matchesSame -> new Object[] { matchesSame, deptId, busClientId })
									))
							.flatMap(arr -> {
								boolean matchesSame = (boolean) arr[0];
								ULong deptId = (ULong) arr[1];
								assertTrue(matchesSame);
								// Also verify it does NOT match system client
								return departmentDAO.checkSameClient(SYSTEM_CLIENT_ID, deptId);
							}))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		@DisplayName("existing department returns it without security context")
		void existingDepartment_Returns() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "ReadInt_" + ts, "internal read test")
							.flatMap(id -> departmentDAO.readInternal(id)))
					.assertNext(department -> {
						assertNotNull(department);
						assertEquals("ReadInt_" + ts, department.getName());
						assertEquals("internal read test", department.getDescription());
						assertEquals(SYSTEM_CLIENT_ID, department.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent department returns empty")
		void nonExistentDepartment_ReturnsEmpty() {
			StepVerifier.create(departmentDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}

		@Test
		@DisplayName("department with null description returns correctly")
		void departmentWithNullDescription_Returns() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "ReadNullDesc_" + ts, null)
							.flatMap(id -> departmentDAO.readInternal(id)))
					.assertNext(department -> {
						assertNotNull(department);
						assertEquals("ReadNullDesc_" + ts, department.getName());
						assertNull(department.getDescription());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("CRUD basics")
	class CrudTests {

		@Test
		@DisplayName("create via DAO and read back")
		void createAndReadBack() {
			String ts = String.valueOf(System.currentTimeMillis());

			Department dept = new Department();
			dept.setClientId(SYSTEM_CLIENT_ID);
			dept.setName("CRUD_Create_" + ts);
			dept.setDescription("Created via DAO");

			StepVerifier.create(departmentDAO.create(dept))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(SYSTEM_CLIENT_ID, created.getClientId());
						assertEquals("CRUD_Create_" + ts, created.getName());
						assertEquals("Created via DAO", created.getDescription());
						assertNotNull(created.getCreatedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create and then readInternal returns same data")
		void createThenReadInternal() {
			String ts = String.valueOf(System.currentTimeMillis());

			Department dept = new Department();
			dept.setClientId(SYSTEM_CLIENT_ID);
			dept.setName("CRUD_Read_" + ts);
			dept.setDescription("Read back test");

			StepVerifier.create(
					departmentDAO.create(dept)
							.flatMap(created -> departmentDAO.readInternal(created.getId())))
					.assertNext(readBack -> {
						assertNotNull(readBack);
						assertEquals("CRUD_Read_" + ts, readBack.getName());
						assertEquals("Read back test", readBack.getDescription());
						assertEquals(SYSTEM_CLIENT_ID, readBack.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create with null description succeeds")
		void createWithNullDescription() {
			String ts = String.valueOf(System.currentTimeMillis());

			Department dept = new Department();
			dept.setClientId(SYSTEM_CLIENT_ID);
			dept.setName("CRUD_NoDesc_" + ts);

			StepVerifier.create(departmentDAO.create(dept))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals("CRUD_NoDesc_" + ts, created.getName());
						assertNull(created.getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readById with system auth returns department")
		void readByIdWithSystemAuth() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CRUD_ReadById_" + ts, "readById test")
							.flatMap(id -> departmentDAO.readById(id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(department -> {
						assertNotNull(department);
						assertEquals("CRUD_ReadById_" + ts, department.getName());
						assertEquals("readById test", department.getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readById non-existent throws error")
		void readByIdNonExistent_ThrowsError() {
			StepVerifier.create(
					departmentDAO.readById(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder
									.withAuthentication(systemAuth)))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("delete existing department returns 1")
		void deleteExisting_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CRUD_Del_" + ts, "to delete")
							.flatMap(id -> departmentDAO.delete(id)))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete non-existent department returns 0")
		void deleteNonExistent_ReturnsZero() {
			StepVerifier.create(departmentDAO.delete(ULong.valueOf(999999)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("deleted department is not readable via readInternal")
		void deletedDepartment_NotReadable() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CRUD_DelR_" + ts, "delete then read")
							.flatMap(id -> departmentDAO.delete(id)
									.then(departmentDAO.readInternal(id))))
					.verifyComplete();
		}

		@Test
		@DisplayName("update name and description via DTO")
		void updateNameAndDescription() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CRUD_UpdOrig_" + ts, "original")
							.flatMap(id -> departmentDAO.readInternal(id))
							.flatMap(original -> {
								original.setName("CRUD_UpdNew_" + ts);
								original.setDescription("updated description");
								return departmentDAO.update(original)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}))
					.assertNext(updated -> {
						assertEquals("CRUD_UpdNew_" + ts, updated.getName());
						assertEquals("updated description", updated.getDescription());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Unique constraint tests")
	class UniqueConstraintTests {

		@Test
		@DisplayName("duplicate name same client causes error")
		void duplicateNameSameClient_Error() {
			String ts = String.valueOf(System.currentTimeMillis());
			String name = "UniqDept_" + ts;

			Department d1 = new Department();
			d1.setClientId(SYSTEM_CLIENT_ID);
			d1.setName(name);
			d1.setDescription("First");

			Department d2 = new Department();
			d2.setClientId(SYSTEM_CLIENT_ID);
			d2.setName(name);
			d2.setDescription("Duplicate");

			StepVerifier.create(
					departmentDAO.create(d1)
							.then(departmentDAO.create(d2)))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("same name different clients succeeds")
		void sameNameDifferentClients_Success() {
			String ts = String.valueOf(System.currentTimeMillis());
			String name = "SharedDpt_" + ts;
			String code1 = "D1" + ts.substring(ts.length() - 6);
			String code2 = "D2" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code1, "DeptClient1", "BUS")
							.flatMap(client1Id -> insertTestClient(code2, "DeptClient2", "BUS")
									.flatMap(client2Id -> {
										Department d1 = new Department();
										d1.setClientId(client1Id);
										d1.setName(name);
										d1.setDescription("Client 1");

										Department d2 = new Department();
										d2.setClientId(client2Id);
										d2.setName(name);
										d2.setDescription("Client 2");

										return departmentDAO.create(d1)
												.then(departmentDAO.create(d2));
									})))
					.assertNext(created -> {
						assertNotNull(created);
						assertEquals(name, created.getName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canBeUpdated()")
	class CanBeUpdatedTests {

		@Test
		@DisplayName("existing department with system auth returns true")
		void existingWithSystemAuth_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CanUpd_" + ts, "desc")
							.flatMap(id -> departmentDAO.canBeUpdated(id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent department returns false")
		void nonExistent_ReturnsFalse() {
			StepVerifier.create(
					departmentDAO.canBeUpdated(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder
									.withAuthentication(systemAuth)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("business client department can be updated by business client auth")
		void businessClientDepartment_CanBeUpdatedByBusinessAuth() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CU" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "CanUpdBusCl", "BUS")
							.flatMap(busClientId -> insertClientHierarchy(busClientId,
									SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestDepartment(busClientId,
											"CanUpd_Bus_" + ts, "bus dept"))
									.flatMap(id -> {
										ContextAuthentication busAuth = TestDataFactory
												.createBusinessAuth(busClientId, code,
														java.util.List.of("Authorities.Logged_IN",
																"Authorities.Client_UPDATE"));
										return departmentDAO.canBeUpdated(id)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(busAuth));
									})))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("deleted department cannot be updated")
		void deletedDepartment_CannotBeUpdated() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "CanUpd_Del_" + ts, "will delete")
							.flatMap(id -> departmentDAO.delete(id).thenReturn(id))
							.flatMap(id -> departmentDAO.canBeUpdated(id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createForRegistration()")
	class CreateForRegistrationTests {

		@Test
		@DisplayName("creates departments from AppRegistrationDepartment list")
		void createsDepartmentsFromList() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CR" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "RegClient", "BUS")
							.flatMap(clientId -> {
								Client client = new Client();
								client.setId(clientId);
								client.setCode(code);
								client.setName("RegClient");
								client.setTypeCode("BUS");

								AppRegistrationDepartment dept1 = new AppRegistrationDepartment();
								dept1.setId(ULong.valueOf(901));
								dept1.setName("CFR_Sales_" + ts);
								dept1.setDescription("Sales dept");

								AppRegistrationDepartment dept2 = new AppRegistrationDepartment();
								dept2.setId(ULong.valueOf(902));
								dept2.setName("CFR_Marketing_" + ts);
								dept2.setDescription("Marketing dept");

								return departmentDAO.createForRegistration(client,
										List.of(dept1, dept2));
							}))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2, result.size());

						// Verify both original IDs are keys
						assertTrue(result.containsKey(ULong.valueOf(901)));
						assertTrue(result.containsKey(ULong.valueOf(902)));

						// Verify actual Department objects were created with real IDs
						Tuple2<AppRegistrationDepartment, Department> entry1 = result
								.get(ULong.valueOf(901));
						assertNotNull(entry1.getT2().getId());
						assertTrue(entry1.getT2().getName().startsWith("CFR_Sales_"));

						Tuple2<AppRegistrationDepartment, Department> entry2 = result
								.get(ULong.valueOf(902));
						assertNotNull(entry2.getT2().getId());
						assertTrue(entry2.getT2().getName().startsWith("CFR_Marketing_"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("creates departments with parent-child relationships")
		void createsDepartmentsWithParentChild() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CP" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "ParentRegCl", "BUS")
							.flatMap(clientId -> {
								Client client = new Client();
								client.setId(clientId);
								client.setCode(code);
								client.setName("ParentRegCl");
								client.setTypeCode("BUS");

								// Parent department with id 801
								AppRegistrationDepartment parent = new AppRegistrationDepartment();
								parent.setId(ULong.valueOf(801));
								parent.setName("CFR_Parent_" + ts);
								parent.setDescription("Parent dept");

								// Child department with parentDepartmentId = 801
								AppRegistrationDepartment child = new AppRegistrationDepartment();
								child.setId(ULong.valueOf(802));
								child.setName("CFR_Child_" + ts);
								child.setDescription("Child dept");
								child.setParentDepartmentId(ULong.valueOf(801));

								return departmentDAO.createForRegistration(client,
										List.of(parent, child));
							}))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2, result.size());

						Tuple2<AppRegistrationDepartment, Department> parentEntry = result
								.get(ULong.valueOf(801));
						Tuple2<AppRegistrationDepartment, Department> childEntry = result
								.get(ULong.valueOf(802));

						assertNotNull(parentEntry);
						assertNotNull(childEntry);

						// Verify the child's parent was set in the DB
						Department childDept = childEntry.getT2();
						assertNotNull(childDept.getId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("verifies parent-child relationship was persisted via readInternal")
		void parentChildRelationship_PersistedInDB() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "PV" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "VerifyParent", "BUS")
							.flatMap(clientId -> {
								Client client = new Client();
								client.setId(clientId);
								client.setCode(code);
								client.setName("VerifyParent");
								client.setTypeCode("BUS");

								AppRegistrationDepartment parent = new AppRegistrationDepartment();
								parent.setId(ULong.valueOf(701));
								parent.setName("CFR_VParent_" + ts);

								AppRegistrationDepartment child = new AppRegistrationDepartment();
								child.setId(ULong.valueOf(702));
								child.setName("CFR_VChild_" + ts);
								child.setParentDepartmentId(ULong.valueOf(701));

								return departmentDAO
										.createForRegistration(client, List.of(parent, child));
							})
							.flatMap(result -> {
								ULong childId = result.get(ULong.valueOf(702)).getT2().getId();
								ULong parentId = result.get(ULong.valueOf(701)).getT2().getId();
								return departmentDAO.readInternal(childId)
										.map(dept -> new Object[] { dept, parentId });
							}))
					.assertNext(arr -> {
						Department childDept = (Department) arr[0];
						ULong expectedParentId = (ULong) arr[1];
						assertEquals(expectedParentId, childDept.getParentDepartmentId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("no parent reference skips createRelations")
		void noParentReference_SkipsRelations() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "NP" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "NoParentCl", "BUS")
							.flatMap(clientId -> {
								Client client = new Client();
								client.setId(clientId);
								client.setCode(code);
								client.setName("NoParentCl");
								client.setTypeCode("BUS");

								AppRegistrationDepartment dept = new AppRegistrationDepartment();
								dept.setId(ULong.valueOf(601));
								dept.setName("CFR_NoPar_" + ts);
								// No parentDepartmentId set

								return departmentDAO.createForRegistration(client, List.of(dept));
							}))
					.assertNext(result -> {
						assertEquals(1, result.size());
						Tuple2<AppRegistrationDepartment, Department> entry = result
								.get(ULong.valueOf(601));
						assertNotNull(entry);
						assertNull(entry.getT2().getParentDepartmentId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("parent references non-existent dept skips relation")
		void parentReferencesNonExistentDept_SkipsRelation() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "NE" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "NonExParCl", "BUS")
							.flatMap(clientId -> {
								Client client = new Client();
								client.setId(clientId);
								client.setCode(code);
								client.setName("NonExParCl");
								client.setTypeCode("BUS");

								AppRegistrationDepartment dept = new AppRegistrationDepartment();
								dept.setId(ULong.valueOf(501));
								dept.setName("CFR_NexPar_" + ts);
								// parentDepartmentId points to a non-existent id in the batch
								dept.setParentDepartmentId(ULong.valueOf(999));

								return departmentDAO.createForRegistration(client, List.of(dept));
							}))
					.assertNext(result -> {
						assertEquals(1, result.size());
						Tuple2<AppRegistrationDepartment, Department> entry = result
								.get(ULong.valueOf(501));
						assertNotNull(entry);
						// Parent was not in the batch, so relation was not created
						assertNull(entry.getT2().getParentDepartmentId());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readPageFilter()")
	class ReadPageFilterTests {

		@Test
		@DisplayName("null condition returns all departments for client")
		void nullCondition_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_All1_" + ts, "first")
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_All2_" + ts, "second"))
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 100), null)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("filter by name EQUALS returns matching department")
		void filterByNameEquals_ReturnsMatching() {
			String ts = String.valueOf(System.currentTimeMillis());
			String exactName = "RPF_Exact_" + ts;

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, exactName, "exact match")
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Other_" + ts,
									"other dept"))
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 10),
									FilterCondition.make("name", exactName))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
						assertEquals(exactName, page.getContent().get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("filter by name LIKE returns matching departments")
		void filterByNameLike_ReturnsMatching() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Like_A_" + ts, "A")
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Like_B_" + ts, "B"))
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_NoMatch_" + ts, "C"))
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 10),
									FilterCondition.of("name", "RPF_Like_%_" + ts,
											FilterConditionOperator.STRING_LOOSE_EQUAL))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("filter by description returns matching departments")
		void filterByDescription_ReturnsMatching() {
			String ts = String.valueOf(System.currentTimeMillis());
			String desc = "unique_desc_" + ts;

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Desc_" + ts, desc)
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 10),
									FilterCondition.make("description", desc))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
						assertEquals(desc, page.getContent().get(0).getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("complex AND condition filters correctly")
		void complexAndCondition_FiltersCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String name = "RPF_Complex_" + ts;
			String desc = "complex_desc_" + ts;

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, name, desc)
							.then(insertTestDepartment(SYSTEM_CLIENT_ID,
									"RPF_Complex_Other_" + ts, "other desc"))
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 10),
									new ComplexCondition()
											.setConditions(List.of(
													FilterCondition.make("name", name),
													FilterCondition.make("description", desc)))
											.setOperator(ComplexConditionOperator.AND))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
						assertEquals(name, page.getContent().get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty result set returns empty page")
		void noMatchingData_ReturnsEmptyPage() {
			StepVerifier.create(
					departmentDAO.readPageFilter(PageRequest.of(0, 10),
							FilterCondition.make("name", "NONEXISTENT_DEPT_xyz12345"))
							.contextWrite(ReactiveSecurityContextHolder
									.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(0, page.getTotalElements());
						assertTrue(page.getContent().isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("pagination returns correct page size")
		void pagination_ReturnsCorrectPageSize() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Pg1_" + ts, "p1")
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Pg2_" + ts, "p2"))
							.then(insertTestDepartment(SYSTEM_CLIENT_ID, "RPF_Pg3_" + ts, "p3"))
							.then(departmentDAO.readPageFilter(PageRequest.of(0, 2),
									FilterCondition.of("name", "RPF_Pg%_" + ts,
											FilterConditionOperator.STRING_LOOSE_EQUAL))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 3);
						assertEquals(2, page.getContent().size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Parent department operations")
	class ParentDepartmentTests {

		@Test
		@DisplayName("create department with parent and verify via readInternal")
		void createWithParent_VerifyRelationship() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "Par_Parent_" + ts, "parent dept")
							.flatMap(parentId -> {
								Department child = new Department();
								child.setClientId(SYSTEM_CLIENT_ID);
								child.setName("Par_Child_" + ts);
								child.setDescription("child dept");
								child.setParentDepartmentId(parentId);
								return departmentDAO.create(child)
										.map(created -> new Object[] { created, parentId });
							})
							.flatMap(arr -> {
								Department created = (Department) arr[0];
								ULong parentId = (ULong) arr[1];
								return departmentDAO.readInternal(created.getId())
										.map(readBack -> new Object[] { readBack, parentId });
							}))
					.assertNext(arr -> {
						Department readBack = (Department) arr[0];
						ULong expectedParentId = (ULong) arr[1];
						assertEquals(expectedParentId, readBack.getParentDepartmentId());
						assertTrue(readBack.getName().startsWith("Par_Child_"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update department to set parent")
		void updateDepartment_SetParent() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "Par_UpdPar_" + ts, "will be parent")
							.flatMap(parentId -> insertTestDepartment(SYSTEM_CLIENT_ID,
									"Par_UpdChi_" + ts, "will be child")
									.flatMap(childId -> departmentDAO.readInternal(childId))
									.flatMap(child -> {
										child.setParentDepartmentId(parentId);
										return departmentDAO.update(child)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(systemAuth));
									})
									.flatMap(updated -> departmentDAO
											.readInternal(updated.getId())
											.map(readBack -> new Object[] { readBack,
													parentId }))))
					.assertNext(arr -> {
						Department readBack = (Department) arr[0];
						ULong expectedParentId = (ULong) arr[1];
						assertEquals(expectedParentId, readBack.getParentDepartmentId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update department to remove parent")
		void updateDepartment_RemoveParent() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "Par_RmPar_" + ts, "parent")
							.flatMap(parentId -> {
								Department child = new Department();
								child.setClientId(SYSTEM_CLIENT_ID);
								child.setName("Par_RmChi_" + ts);
								child.setDescription("child with parent");
								child.setParentDepartmentId(parentId);
								return departmentDAO.create(child);
							})
							.flatMap(created -> departmentDAO.readInternal(created.getId()))
							.flatMap(withParent -> {
								assertNotNull(withParent.getParentDepartmentId());
								withParent.setParentDepartmentId(null);
								return departmentDAO.update(withParent)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							})
							.flatMap(updated -> departmentDAO.readInternal(updated.getId())))
					.assertNext(readBack -> assertNull(readBack.getParentDepartmentId()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Business client isolation")
	class BusinessClientIsolationTests {

		@Test
		@DisplayName("business client can only see own departments via readPageFilter")
		void businessClient_OnlySeesOwnDepartments() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "BI" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "IsolClient", "BUS")
							.flatMap(busClientId -> insertClientHierarchy(busClientId,
									SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestDepartment(busClientId,
											"Isol_Own_" + ts, "own dept"))
									.then(insertTestDepartment(SYSTEM_CLIENT_ID,
											"Isol_Sys_" + ts, "sys dept"))
									.then(Mono.defer(() -> {
										ContextAuthentication busAuth = TestDataFactory
												.createBusinessAuth(busClientId, code,
														java.util.List.of("Authorities.Logged_IN",
																"Authorities.Client_CREATE",
																"Authorities.ROLE_Owner"));
										return departmentDAO
												.readPageFilter(PageRequest.of(0, 100),
														FilterCondition.of("name",
																"Isol_%_" + ts,
																FilterConditionOperator.STRING_LOOSE_EQUAL))
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(busAuth));
									}))))
					.assertNext(pageObj -> {
						@SuppressWarnings("unchecked")
						Page<Department> page = (Page<Department>) pageObj;
						assertNotNull(page);
						// Bus client should only see its own departments
						assertTrue(page.getTotalElements() >= 1);
						page.getContent().forEach(dept -> assertTrue(
								dept.getName().startsWith("Isol_Own_"),
								"Business client should only see own departments, but found: "
										+ dept.getName()));
					})
					.verifyComplete();
		}
	}
}
