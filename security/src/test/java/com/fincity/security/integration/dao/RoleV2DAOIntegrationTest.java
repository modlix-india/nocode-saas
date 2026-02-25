package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RoleV2DAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private RoleV2DAO roleV2DAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_v2_role_permission WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_user_role WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role_role WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1) OR SUB_ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_permission WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertTestRole(ULong clientId, String name, ULong appId) {
		var spec = databaseClient.sql(
				"INSERT INTO security_v2_role (CLIENT_ID, NAME, APP_ID) VALUES (:clientId, :name, :appId)")
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		spec = appId != null ? spec.bind("appId", appId.longValue()) : spec.bindNull("appId", Long.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<Void> insertSubRole(ULong roleId, ULong subRoleId) {
		return databaseClient.sql(
				"INSERT INTO security_v2_role_role (ROLE_ID, SUB_ROLE_ID) VALUES (:roleId, :subRoleId)")
				.bind("roleId", roleId.longValue())
				.bind("subRoleId", subRoleId.longValue())
				.then();
	}

	private Mono<Void> assignRoleToUser(ULong userId, ULong roleId) {
		return databaseClient.sql(
				"INSERT INTO security_v2_user_role (USER_ID, ROLE_ID) VALUES (:userId, :roleId)")
				.bind("userId", userId.longValue())
				.bind("roleId", roleId.longValue())
				.then();
	}

	private Mono<ULong> insertPermission(ULong clientId, String name, ULong appId) {
		var spec = databaseClient.sql(
				"INSERT INTO security_permission (CLIENT_ID, NAME, APP_ID) VALUES (:clientId, :name, :appId)")
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		spec = appId != null ? spec.bind("appId", appId.longValue()) : spec.bindNull("appId", Long.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<Void> assignPermissionToRole(ULong roleId, ULong permissionId) {
		return databaseClient.sql(
				"INSERT INTO security_v2_role_permission (ROLE_ID, PERMISSION_ID) VALUES (:roleId, :permissionId)")
				.bind("roleId", roleId.longValue())
				.bind("permissionId", permissionId.longValue())
				.then();
	}

	@Nested
	@DisplayName("getRoles()")
	class GetRolesTests {

		@Test
		void singleRoleNoSubRoles_ReturnsRole() {
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "TestRole_" + System.currentTimeMillis(), null)
							.flatMap(roleId -> roleV2DAO.getRoles(List.of(roleId))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertEquals(1, roles.size());
						assertTrue(roles.get(0).getName().startsWith("TestRole_"));
					})
					.verifyComplete();
		}

		@Test
		void roleWithSubRoles_ReturnsRoleWithSubRoles() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "ParentRole_" + ts, null)
							.flatMap(parentId -> insertTestRole(SYSTEM_CLIENT_ID, "SubRole1_" + ts, null)
									.flatMap(sub1Id -> insertTestRole(SYSTEM_CLIENT_ID, "SubRole2_" + ts, null)
											.flatMap(sub2Id -> insertSubRole(parentId, sub1Id)
													.then(insertSubRole(parentId, sub2Id))
													.then(roleV2DAO.getRoles(List.of(parentId)))))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertEquals(1, roles.size());
						RoleV2 parent = roles.get(0);
						assertTrue(parent.getName().startsWith("ParentRole_"));
						assertNotNull(parent.getSubRoles());
						assertEquals(2, parent.getSubRoles().size());
					})
					.verifyComplete();
		}

		@Test
		void multipleRoles_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "RoleA_" + ts, null)
							.flatMap(roleAId -> insertTestRole(SYSTEM_CLIENT_ID, "RoleB_" + ts, null)
									.flatMap(roleBId -> roleV2DAO.getRoles(List.of(roleAId, roleBId)))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertEquals(2, roles.size());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentRoleIds_ReturnsEmpty() {
			StepVerifier.create(roleV2DAO.getRoles(List.of(ULong.valueOf(999999))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertTrue(roles.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getRoleAuthoritiesPerApp()")
	class GetRoleAuthoritiesPerAppTests {

		@Test
		void userWithRolesAndPermissions_ReturnsAuthorities() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "authapp_" + ts, "Auth App")
							.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, "AuthRole_" + ts, appId)
									.flatMap(roleId -> insertPermission(SYSTEM_CLIENT_ID, "AuthPerm_" + ts, appId)
											.flatMap(permId -> assignPermissionToRole(roleId, permId)
													.then(insertTestUser(SYSTEM_CLIENT_ID,
															"authuser_" + ts,
															"authuser_" + ts + "@test.com",
															"password123"))
													.flatMap(userId -> assignRoleToUser(userId, roleId)
															.then(roleV2DAO
																	.getRoleAuthoritiesPerApp(userId)))))))
					.assertNext(authMap -> {
						assertNotNull(authMap);
						assertFalse(authMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void userWithNoRoles_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "norole_" + ts, "norole_" + ts + "@test.com",
							"password123")
							.flatMap(userId -> roleV2DAO.getRoleAuthoritiesPerApp(userId)))
					.assertNext(authMap -> {
						assertNotNull(authMap);
						assertTrue(authMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void userWithRoleHavingSubRoles_IncludesSubRoleAuthorities() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "subapp_" + ts, "Sub App")
							.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, "ParentAuthRole_" + ts, appId)
									.flatMap(parentRoleId -> insertTestRole(SYSTEM_CLIENT_ID,
											"SubAuthRole_" + ts, appId)
											.flatMap(subRoleId -> insertSubRole(parentRoleId, subRoleId)
													.then(insertTestUser(SYSTEM_CLIENT_ID,
															"subuser_" + ts,
															"subuser_" + ts + "@test.com",
															"password123"))
													.flatMap(userId -> assignRoleToUser(userId, parentRoleId)
															.then(roleV2DAO
																	.getRoleAuthoritiesPerApp(userId)))))))
					.assertNext(authMap -> {
						assertNotNull(authMap);
						assertFalse(authMap.isEmpty());
						// Should contain both parent and sub role authorities
						List<String> appAuthorities = authMap.values().stream()
								.flatMap(List::stream)
								.toList();
						assertTrue(appAuthorities.size() >= 2);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getRolesForAssignmentInApp()")
	class GetRolesForAssignmentInAppTests {

		@Test
		void appWithRoles_ReturnsRoles() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestClient("RLBUS", "Role Business", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null,
									null)
									.then(insertTestApp(clientId, "rlapp_" + ts, "Role App"))
									.flatMap(appId -> insertTestRole(clientId, "AppRole_" + ts, appId)
											.then(databaseClient.sql(
													"SELECT * FROM security_client_hierarchy WHERE CLIENT_ID = :id")
													.bind("id", clientId.longValue())
													.map(row -> {
														com.fincity.security.dto.ClientHierarchy ch = new com.fincity.security.dto.ClientHierarchy();
														ch.setClientId(clientId);
														return ch;
													})
													.one())
											.flatMap(hierarchy -> roleV2DAO.getRolesForAssignmentInApp(
													"rlapp_" + ts, hierarchy)))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertFalse(roles.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void nothingAppCode_ReturnsAllClientRoles() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestClient("RLAL", "Role All", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null,
									null)
									.then(insertTestRole(clientId, "AllRole_" + ts, null))
									.then(Mono.defer(() -> {
										com.fincity.security.dto.ClientHierarchy ch = new com.fincity.security.dto.ClientHierarchy();
										ch.setClientId(clientId);
										return roleV2DAO.getRolesForAssignmentInApp("nothing", ch);
									}))))
					.assertNext(roles -> {
						assertNotNull(roles);
						assertFalse(roles.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("fetchSubRoles()")
	class FetchSubRolesTests {

		@Test
		void roleWithSubRoles_ReturnsMappedSubRoles() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "FetchParent_" + ts, null)
							.flatMap(parentId -> insertTestRole(SYSTEM_CLIENT_ID, "FetchSub_" + ts, null)
									.flatMap(subId -> insertSubRole(parentId, subId)
											.then(roleV2DAO.fetchSubRoles(List.of(parentId))))))
					.assertNext(subRolesMap -> {
						assertNotNull(subRolesMap);
						assertFalse(subRolesMap.isEmpty());
						assertEquals(1, subRolesMap.size());
						List<RoleV2> subs = subRolesMap.values().iterator().next();
						assertEquals(1, subs.size());
						assertTrue(subs.get(0).getName().startsWith("FetchSub_"));
					})
					.verifyComplete();
		}

		@Test
		void roleWithNoSubRoles_ReturnsEmptyMap() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "NoSub_" + ts, null)
							.flatMap(roleId -> roleV2DAO.fetchSubRoles(List.of(roleId))))
					.assertNext(subRolesMap -> {
						assertNotNull(subRolesMap);
						assertTrue(subRolesMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void multipleRolesWithSubRoles_ReturnsMappedCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestRole(SYSTEM_CLIENT_ID, "P1_" + ts, null)
							.flatMap(p1Id -> insertTestRole(SYSTEM_CLIENT_ID, "P2_" + ts, null)
									.flatMap(p2Id -> insertTestRole(SYSTEM_CLIENT_ID, "S1_" + ts, null)
											.flatMap(s1Id -> insertTestRole(SYSTEM_CLIENT_ID, "S2_" + ts, null)
													.flatMap(s2Id -> insertSubRole(p1Id, s1Id)
															.then(insertSubRole(p2Id, s2Id))
															.then(roleV2DAO
																	.fetchSubRoles(
																			List.of(p1Id, p2Id))))))))
					.assertNext(subRolesMap -> {
						assertNotNull(subRolesMap);
						assertEquals(2, subRolesMap.size());
					})
					.verifyComplete();
		}
	}
}
