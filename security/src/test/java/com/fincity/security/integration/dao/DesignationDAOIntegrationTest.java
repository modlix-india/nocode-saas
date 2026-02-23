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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.DesignationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class DesignationDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private DesignationDAO designationDAO;

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
				.then(databaseClient.sql("DELETE FROM security_designation WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_designation WHERE CLIENT_ID = 1 AND NAME NOT LIKE 'SYS%'").then())
				.then(databaseClient.sql("DELETE FROM security_department WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_department WHERE CLIENT_ID = 1 AND NAME NOT LIKE 'SYS%'").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper Methods ---

	private Mono<ULong> insertTestDesignation(ULong clientId, String name, String description,
			ULong departmentId, ULong parentDesigId, ULong nextDesigId) {

		var spec = databaseClient.sql(
				"INSERT INTO security_designation (CLIENT_ID, NAME, DESCRIPTION, DEPARTMENT_ID, PARENT_DESIGNATION_ID, NEXT_DESIGNATION_ID) "
						+ "VALUES (:clientId, :name, :description, :departmentId, :parentDesigId, :nextDesigId)")
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		spec = description != null ? spec.bind("description", description)
				: spec.bindNull("description", String.class);
		spec = departmentId != null ? spec.bind("departmentId", departmentId.longValue())
				: spec.bindNull("departmentId", Long.class);
		spec = parentDesigId != null ? spec.bind("parentDesigId", parentDesigId.longValue())
				: spec.bindNull("parentDesigId", Long.class);
		spec = nextDesigId != null ? spec.bind("nextDesigId", nextDesigId.longValue())
				: spec.bindNull("nextDesigId", Long.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

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
	@DisplayName("create()")
	class CreateTests {

		@Test
		@DisplayName("basic creation with name and clientId")
		void basicCreation_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			Designation designation = new Designation();
			designation.setClientId(SYSTEM_CLIENT_ID);
			designation.setName("Desig_" + ts);
			designation.setDescription("Test designation");

			StepVerifier.create(designationDAO.create(designation))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(SYSTEM_CLIENT_ID, created.getClientId());
						assertEquals("Desig_" + ts, created.getName());
						assertEquals("Test designation", created.getDescription());
						assertNull(created.getDepartmentId());
						assertNull(created.getParentDesignationId());
						assertNull(created.getNextDesignationId());
						assertNotNull(created.getCreatedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("creation with department reference")
		void withDepartment_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "Dept_" + ts, "Test dept")
							.flatMap(deptId -> {
								Designation designation = new Designation();
								designation.setClientId(SYSTEM_CLIENT_ID);
								designation.setName("DesigDept_" + ts);
								designation.setDescription("With department");
								designation.setDepartmentId(deptId);
								return designationDAO.create(designation)
										.map(created -> Tuples.of(created, deptId));
							}))
					.assertNext(tuple -> {
						Designation created = tuple.getT1();
						ULong deptId = tuple.getT2();
						assertNotNull(created.getId());
						assertEquals(deptId, created.getDepartmentId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("creation with parent designation")
		void withParentDesignation_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "Parent_" + ts, "Parent", null, null, null)
							.flatMap(parentId -> {
								Designation designation = new Designation();
								designation.setClientId(SYSTEM_CLIENT_ID);
								designation.setName("Child_" + ts);
								designation.setDescription("Child designation");
								designation.setParentDesignationId(parentId);
								return designationDAO.create(designation)
										.map(created -> Tuples.of(created, parentId));
							}))
					.assertNext(tuple -> {
						Designation created = tuple.getT1();
						ULong parentId = tuple.getT2();
						assertNotNull(created.getId());
						assertEquals(parentId, created.getParentDesignationId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("creation with all relationships set")
		void withAllRelationships_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "AllDpt_" + ts, "dept")
							.flatMap(deptId -> insertTestDesignation(SYSTEM_CLIENT_ID, "AllPar_" + ts,
									"p", null, null, null)
									.flatMap(parentId -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"AllNxt_" + ts, "n", null, null, null)
											.flatMap(nextId -> {
												Designation d = new Designation();
												d.setClientId(SYSTEM_CLIENT_ID);
												d.setName("AllRel_" + ts);
												d.setDescription("All rels");
												d.setDepartmentId(deptId);
												d.setParentDesignationId(parentId);
												d.setNextDesignationId(nextId);
												return designationDAO.create(d)
														.map(created -> new Object[] { created,
																deptId, parentId, nextId });
											}))))
					.assertNext(arr -> {
						Designation created = (Designation) arr[0];
						ULong deptId = (ULong) arr[1];
						ULong parentId = (ULong) arr[2];
						ULong nextId = (ULong) arr[3];
						assertNotNull(created.getId());
						assertEquals(deptId, created.getDepartmentId());
						assertEquals(parentId, created.getParentDesignationId());
						assertEquals(nextId, created.getNextDesignationId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("creation with null description succeeds")
		void withNullDescription_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			Designation designation = new Designation();
			designation.setClientId(SYSTEM_CLIENT_ID);
			designation.setName("NoDesc_" + ts);

			StepVerifier.create(designationDAO.create(designation))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals("NoDesc_" + ts, created.getName());
						assertNull(created.getDescription());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		@DisplayName("existing designation returns it without security context")
		void existingDesignation_Returns() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "ReadInt_" + ts, "internal", null, null, null)
							.flatMap(id -> designationDAO.readInternal(id)))
					.assertNext(designation -> {
						assertNotNull(designation);
						assertEquals("ReadInt_" + ts, designation.getName());
						assertEquals("internal", designation.getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent designation returns empty")
		void nonExistentDesignation_ReturnsEmpty() {
			StepVerifier.create(designationDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}

		@Test
		@DisplayName("designation with all relationship fields populated")
		void withAllRelationshipFields_Returns() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RIDpt_" + ts, "dept")
							.flatMap(deptId -> insertTestDesignation(SYSTEM_CLIENT_ID,
									"RIPar_" + ts, "p", null, null, null)
									.flatMap(parentId -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"RINxt_" + ts, "n", null, null, null)
											.flatMap(nextId -> insertTestDesignation(
													SYSTEM_CLIENT_ID, "RIAll_" + ts, "all",
													deptId, parentId, nextId)
													.flatMap(designationDAO::readInternal)
													.map(d -> new Object[] { d, deptId,
															parentId, nextId })))))
					.assertNext(arr -> {
						Designation d = (Designation) arr[0];
						ULong deptId = (ULong) arr[1];
						ULong parentId = (ULong) arr[2];
						ULong nextId = (ULong) arr[3];
						assertEquals("RIAll_" + ts, d.getName());
						assertEquals(deptId, d.getDepartmentId());
						assertEquals(parentId, d.getParentDesignationId());
						assertEquals(nextId, d.getNextDesignationId());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readById()")
	class ReadByIdTests {

		@Test
		@DisplayName("existing designation with system auth returns designation")
		void existingDesignation_WithSystemAuth_Returns() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "ReadBy_" + ts, "desc", null, null, null)
							.flatMap(id -> designationDAO.readById(id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(designation -> {
						assertNotNull(designation);
						assertEquals("ReadBy_" + ts, designation.getName());
						assertEquals("desc", designation.getDescription());
						assertEquals(SYSTEM_CLIENT_ID, designation.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent designation with system auth throws error")
		void nonExistentDesignation_ThrowsError() {
			StepVerifier.create(
					designationDAO.readById(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder
									.withAuthentication(systemAuth)))
					.expectError()
					.verify();
		}
	}

	@Nested
	@DisplayName("update()")
	class UpdateTests {

		@Test
		@DisplayName("update name and description")
		void updateNameAndDescription_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "UpdOrig_" + ts, "original", null, null, null)
							.flatMap(id -> designationDAO.readInternal(id))
							.flatMap(original -> {
								original.setName("UpdNew_" + ts);
								original.setDescription("updated description");
								return designationDAO.update(original)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}))
					.assertNext(updated -> {
						assertEquals("UpdNew_" + ts, updated.getName());
						assertEquals("updated description", updated.getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update department reassignment")
		void updateDepartmentReassignment_Success() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "UDp1_" + ts, "dept1")
							.flatMap(dept1Id -> insertTestDepartment(SYSTEM_CLIENT_ID, "UDp2_" + ts,
									"dept2")
									.flatMap(dept2Id -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"UDptD_" + ts, "d", dept1Id, null, null)
											.flatMap(desigId -> designationDAO.readInternal(desigId))
											.flatMap(d -> {
												d.setDepartmentId(dept2Id);
												return designationDAO.update(d)
														.contextWrite(
																ReactiveSecurityContextHolder
																		.withAuthentication(
																				systemAuth));
											})
											.map(updated -> Tuples.of(updated, dept2Id)))))
					.assertNext(tuple -> {
						assertEquals(tuple.getT2(), tuple.getT1().getDepartmentId());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		@DisplayName("successful delete returns 1")
		void successfulDelete_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "Del_" + ts, "to delete", null, null, null)
							.flatMap(id -> designationDAO.delete(id)))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent designation returns 0")
		void nonExistentDesignation_ReturnsZero() {
			StepVerifier.create(designationDAO.delete(ULong.valueOf(999999)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("deleted record is not readable via readInternal")
		void deletedRecord_NotReadable() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "DelR_" + ts, "d", null, null, null)
							.flatMap(id -> designationDAO.delete(id)
									.then(designationDAO.readInternal(id))))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkSameClient()")
	class CheckSameClientTests {

		@Test
		@DisplayName("all null IDs returns true")
		void allNulls_ReturnsTrue() {
			StepVerifier.create(designationDAO.checkSameClient(SYSTEM_CLIENT_ID, null, null, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("same client for parent designation returns true")
		void sameClientParent_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "SCP_" + ts, "p", null, null, null)
							.flatMap(parentId -> designationDAO.checkSameClient(SYSTEM_CLIENT_ID,
									parentId, null, null)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("same client for department returns true")
		void sameClientDepartment_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "SCD_" + ts, "d")
							.flatMap(deptId -> designationDAO.checkSameClient(SYSTEM_CLIENT_ID,
									null, null, deptId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("same client for all three returns true")
		void sameClientAll_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "SCADp_" + ts, "d")
							.flatMap(deptId -> insertTestDesignation(SYSTEM_CLIENT_ID,
									"SCAP_" + ts, "p", null, null, null)
									.flatMap(parentId -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"SCAN_" + ts, "n", null, null, null)
											.flatMap(nextId -> designationDAO.checkSameClient(
													SYSTEM_CLIENT_ID, parentId, nextId,
													deptId)))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("different client for parent returns false")
		void differentClientParent_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "DC" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "DiffClientP", "BUS")
							.flatMap(otherClientId -> insertTestDesignation(otherClientId,
									"DCP_" + ts, "p", null, null, null)
									.flatMap(parentId -> designationDAO.checkSameClient(
											SYSTEM_CLIENT_ID, parentId, null, null))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent ID returns false")
		void nonExistentId_ReturnsFalse() {
			StepVerifier.create(
					designationDAO.checkSameClient(SYSTEM_CLIENT_ID, ULong.valueOf(999999), null, null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("mixed client IDs - one same, one different returns false")
		void mixedClientIds_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MX" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "MixedClient", "BUS")
							.flatMap(otherClientId -> insertTestDesignation(SYSTEM_CLIENT_ID,
									"MXP_" + ts, "same", null, null, null)
									.flatMap(sameParentId -> insertTestDesignation(otherClientId,
											"MXN_" + ts, "diff", null, null, null)
											.flatMap(diffNextId -> designationDAO.checkSameClient(
													SYSTEM_CLIENT_ID, sameParentId,
													diffNextId, null)))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canAssignDesignation()")
	class CanAssignDesignationTests {

		@Test
		@DisplayName("same client returns true")
		void sameClient_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "Assign_" + ts, "desc", null, null, null)
							.flatMap(id -> designationDAO.canAssignDesignation(SYSTEM_CLIENT_ID, id)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("different client returns false")
		void differentClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CA" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "DiffAssign", "BUS")
							.flatMap(otherClientId -> insertTestDesignation(otherClientId,
									"AsnD_" + ts, "d", null, null, null)
									.flatMap(id -> designationDAO.canAssignDesignation(
											SYSTEM_CLIENT_ID, id))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent designation returns false")
		void nonExistent_ReturnsFalse() {
			StepVerifier.create(designationDAO.canAssignDesignation(SYSTEM_CLIENT_ID,
					ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canBeUpdated()")
	class CanBeUpdatedTests {

		@Test
		@DisplayName("existing designation with system auth returns true")
		void existingWithSystemAuth_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "CanUpd_" + ts, "desc", null, null, null)
							.flatMap(id -> designationDAO.canBeUpdated(id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent designation returns false")
		void nonExistent_ReturnsFalse() {
			StepVerifier.create(
					designationDAO.canBeUpdated(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder
									.withAuthentication(systemAuth)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createForRegistration()")
	class CreateForRegistrationTests {

		@Test
		@DisplayName("without relationships creates designations")
		void withoutRelationships_CreatesDesignations() {
			String ts = String.valueOf(System.currentTimeMillis());

			Client client = TestDataFactory.createSystemClient();

			AppRegistrationDesignation ard1 = new AppRegistrationDesignation();
			ard1.setId(ULong.valueOf(100));
			ard1.setName("RegDesig1_" + ts);
			ard1.setDescription("First");

			AppRegistrationDesignation ard2 = new AppRegistrationDesignation();
			ard2.setId(ULong.valueOf(101));
			ard2.setName("RegDesig2_" + ts);
			ard2.setDescription("Second");

			List<AppRegistrationDesignation> designations = List.of(ard1, ard2);
			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> deptIndex = Map.of();

			StepVerifier.create(designationDAO.createForRegistration(client, designations, deptIndex))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2, result.size());
						assertTrue(result.containsKey(ULong.valueOf(100)));
						assertTrue(result.containsKey(ULong.valueOf(101)));

						Designation d1 = result.get(ULong.valueOf(100)).getT2();
						assertEquals("RegDesig1_" + ts, d1.getName());
						assertEquals("First", d1.getDescription());
						assertNotNull(d1.getId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with parent relationship sets parent after creation")
		void withParentRelationship_SetsParent() {
			String ts = String.valueOf(System.currentTimeMillis());

			Client client = TestDataFactory.createSystemClient();

			AppRegistrationDesignation parent = new AppRegistrationDesignation();
			parent.setId(ULong.valueOf(200));
			parent.setName("RegPar_" + ts);
			parent.setDescription("Parent");

			AppRegistrationDesignation child = new AppRegistrationDesignation();
			child.setId(ULong.valueOf(201));
			child.setName("RegChd_" + ts);
			child.setDescription("Child");
			child.setParentDesignationId(ULong.valueOf(200));

			List<AppRegistrationDesignation> designations = List.of(parent, child);
			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> deptIndex = Map.of();

			StepVerifier.create(
					designationDAO.createForRegistration(client, designations, deptIndex)
							.flatMap(result -> {
								ULong childDbId = result.get(ULong.valueOf(201)).getT2().getId();
								return designationDAO.readInternal(childDbId)
										.map(d -> Tuples.of(d, result));
							}))
					.assertNext(tuple -> {
						Designation childFromDb = tuple.getT1();
						var result = tuple.getT2();
						ULong parentDbId = result.get(ULong.valueOf(200)).getT2().getId();
						assertEquals(parentDbId, childFromDb.getParentDesignationId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with next relationship sets next after creation")
		void withNextRelationship_SetsNext() {
			String ts = String.valueOf(System.currentTimeMillis());

			Client client = TestDataFactory.createSystemClient();

			AppRegistrationDesignation first = new AppRegistrationDesignation();
			first.setId(ULong.valueOf(300));
			first.setName("RegFst_" + ts);
			first.setDescription("First");
			first.setNextDesignationId(ULong.valueOf(301));

			AppRegistrationDesignation second = new AppRegistrationDesignation();
			second.setId(ULong.valueOf(301));
			second.setName("RegSnd_" + ts);
			second.setDescription("Second");

			List<AppRegistrationDesignation> designations = List.of(first, second);
			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> deptIndex = Map.of();

			StepVerifier.create(
					designationDAO.createForRegistration(client, designations, deptIndex)
							.flatMap(result -> {
								ULong firstDbId = result.get(ULong.valueOf(300)).getT2().getId();
								return designationDAO.readInternal(firstDbId)
										.map(d -> Tuples.of(d, result));
							}))
					.assertNext(tuple -> {
						Designation firstFromDb = tuple.getT1();
						var result = tuple.getT2();
						ULong secondDbId = result.get(ULong.valueOf(301)).getT2().getId();
						assertEquals(secondDbId, firstFromDb.getNextDesignationId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with department mapping uses department index")
		void withDepartmentMapping_UsesDeptIndex() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDepartment(SYSTEM_CLIENT_ID, "RegDpt_" + ts, "test dept")
							.flatMap(realDeptId -> {
								Client client = TestDataFactory.createSystemClient();

								Department dept = new Department();
								dept.setId(realDeptId);
								dept.setClientId(SYSTEM_CLIENT_ID);
								dept.setName("RegDpt_" + ts);

								AppRegistrationDepartment ard = new AppRegistrationDepartment();
								ard.setId(ULong.valueOf(50));
								ard.setName("RegDpt_" + ts);

								Map<ULong, Tuple2<AppRegistrationDepartment, Department>> deptIndex = Map
										.of(ULong.valueOf(50), Tuples.of(ard, dept));

								AppRegistrationDesignation desig = new AppRegistrationDesignation();
								desig.setId(ULong.valueOf(400));
								desig.setName("RegDptD_" + ts);
								desig.setDescription("With dept");
								desig.setDepartmentId(ULong.valueOf(50));

								List<AppRegistrationDesignation> designations = List.of(desig);

								return designationDAO
										.createForRegistration(client, designations, deptIndex)
										.map(result -> Tuples.of(result, realDeptId));
							}))
					.assertNext(tuple -> {
						var result = tuple.getT1();
						ULong realDeptId = tuple.getT2();
						Designation created = result.get(ULong.valueOf(400)).getT2();
						assertEquals(realDeptId, created.getDepartmentId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty list returns empty map")
		void emptyList_ReturnsEmptyMap() {
			Client client = TestDataFactory.createSystemClient();

			StepVerifier.create(
					designationDAO.createForRegistration(client, List.of(), Map.of()))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("business client registration creates designations for that client")
		void businessClient_CreatesForClient() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "BR" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "BusReg", "BUS")
							.flatMap(busClientId -> {
								Client client = TestDataFactory.createBusinessClient(busClientId, code);

								AppRegistrationDesignation desig = new AppRegistrationDesignation();
								desig.setId(ULong.valueOf(600));
								desig.setName("BusDesig_" + ts);
								desig.setDescription("Business");

								return designationDAO
										.createForRegistration(client, List.of(desig), Map.of())
										.map(result -> Tuples.of(result, busClientId));
							}))
					.assertNext(tuple -> {
						var result = tuple.getT1();
						ULong busClientId = tuple.getT2();
						Designation created = result.get(ULong.valueOf(600)).getT2();
						assertEquals(busClientId, created.getClientId());
						assertEquals("BusDesig_" + ts, created.getName());
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
			String name = "UniqD_" + ts;

			Designation d1 = new Designation();
			d1.setClientId(SYSTEM_CLIENT_ID);
			d1.setName(name);
			d1.setDescription("First");

			Designation d2 = new Designation();
			d2.setClientId(SYSTEM_CLIENT_ID);
			d2.setName(name);
			d2.setDescription("Duplicate");

			StepVerifier.create(
					designationDAO.create(d1)
							.then(designationDAO.create(d2)))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("same name different clients succeeds")
		void sameNameDifferentClients_Success() {
			String ts = String.valueOf(System.currentTimeMillis());
			String name = "SharedD_" + ts;
			String code1 = "U1" + ts.substring(ts.length() - 6);
			String code2 = "U2" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code1, "UniqueClient1", "BUS")
							.flatMap(client1Id -> insertTestClient(code2, "UniqueClient2", "BUS")
									.flatMap(client2Id -> {
										Designation d1 = new Designation();
										d1.setClientId(client1Id);
										d1.setName(name);
										d1.setDescription("Client 1");

										Designation d2 = new Designation();
										d2.setClientId(client2Id);
										d2.setName(name);
										d2.setDescription("Client 2");

										return designationDAO.create(d1)
												.then(designationDAO.create(d2));
									})))
					.assertNext(created -> {
						assertNotNull(created);
						assertEquals(name, created.getName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Hierarchical relationship tests")
	class HierarchicalRelationshipTests {

		@Test
		@DisplayName("multi-level parent chain")
		void multiLevelParentChain() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "L1_" + ts, "Level 1", null, null, null)
							.flatMap(l1Id -> insertTestDesignation(SYSTEM_CLIENT_ID, "L2_" + ts,
									"Level 2", null, l1Id, null)
									.flatMap(l2Id -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"L3_" + ts, "Level 3", null, l2Id, null)
											.flatMap(l3Id -> designationDAO.readInternal(l3Id)
													.flatMap(l3 -> designationDAO
															.readInternal(l2Id)
															.map(l2 -> new Object[] { l3,
																	l2, l1Id, l2Id }))))))
					.assertNext(arr -> {
						Designation l3 = (Designation) arr[0];
						Designation l2 = (Designation) arr[1];
						ULong l1Id = (ULong) arr[2];
						ULong l2Id = (ULong) arr[3];

						assertEquals(l2Id, l3.getParentDesignationId());
						assertEquals(l1Id, l2.getParentDesignationId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("linked list via next designation")
		void linkedListViaNextDesignation() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestDesignation(SYSTEM_CLIENT_ID, "LL3_" + ts, "Third", null, null, null)
							.flatMap(thirdId -> insertTestDesignation(SYSTEM_CLIENT_ID,
									"LL2_" + ts, "Second", null, null, thirdId)
									.flatMap(secondId -> insertTestDesignation(SYSTEM_CLIENT_ID,
											"LL1_" + ts, "First", null, null, secondId)
											.flatMap(firstId -> designationDAO
													.readInternal(firstId)
													.flatMap(first -> designationDAO
															.readInternal(secondId)
															.map(second -> new Object[] {
																	first,
																	second,
																	secondId,
																	thirdId }))))))
					.assertNext(arr -> {
						Designation first = (Designation) arr[0];
						Designation second = (Designation) arr[1];
						ULong secondId = (ULong) arr[2];
						ULong thirdId = (ULong) arr[3];

						assertEquals(secondId, first.getNextDesignationId());
						assertEquals(thirdId, second.getNextDesignationId());
					})
					.verifyComplete();
		}
	}
}