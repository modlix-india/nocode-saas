package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Multi-Tenant Visibility & CRUD Restriction Tests")
class MultiTenantVisibilityIntegrationTest extends AbstractIntegrationTest {

	private static final List<String> OWNER_AUTHORITIES = List.of(
			"Authorities.ROLE_Owner",
			"Authorities.User_READ", "Authorities.User_CREATE",
			"Authorities.User_UPDATE", "Authorities.User_DELETE",
			"Authorities.Client_READ", "Authorities.Client_CREATE",
			"Authorities.Client_UPDATE", "Authorities.Client_DELETE",
			"Authorities.Logged_IN");

	private static final List<String> CLIENT_MGR_AUTHORITIES = List.of(
			"Authorities.User_READ", "Authorities.User_CREATE",
			"Authorities.User_UPDATE", "Authorities.User_DELETE",
			"Authorities.Client_READ", "Authorities.Client_UPDATE",
			"Authorities.Logged_IN");

	@Autowired
	private UserService userService;

	@Autowired
	private ClientService clientService;

	// --- Client IDs ---
	private ULong lzclaId, lzclbId;
	private ULong lzacp1Id, lzacp2Id, lzacp3Id;
	private ULong lzbcp1Id, lzbcp2Id, lzbcp3Id;

	// --- User IDs (LZCLA side) ---
	private ULong lzclaOwnerId, lzclaAgent1Id, lzclaAgent2Id;
	private ULong lzclaCpMgrId, lzclaCpMgr12Id, lzclaCpMgr3Id;
	private ULong lzacp1OwnerId, lzacp2OwnerId, lzacp3OwnerId;
	private ULong lzacp2Tm1Id, lzacp2Tm2Id, lzacp3Tm1Id, lzacp3Tm2Id;

	// --- User IDs (LZCLB side) ---
	private ULong lzclbOwnerId, lzclbAgent1Id, lzclbAgent2Id;
	private ULong lzclbCpMgrId, lzclbCpMgr12Id, lzclbCpMgr3Id;
	private ULong lzbcp1OwnerId, lzbcp2OwnerId, lzbcp3OwnerId;
	private ULong lzbcp2Tm1Id, lzbcp2Tm2Id, lzbcp3Tm1Id, lzbcp3Tm2Id;

	@BeforeEach
	void setUp() {
		setupMockBeans();

		ULong systemId = ULong.valueOf(1);

		// Step 1: Create 8 clients
		lzclaId = insertTestClient("LZCLA", "LZClient A", "BUS").block();
		lzclbId = insertTestClient("LZCLB", "LZClient B", "BUS").block();
		lzacp1Id = insertTestClient("LZACP1", "LZClientA CP1", "BUS").block();
		lzacp2Id = insertTestClient("LZACP2", "LZClientA CP2", "BUS").block();
		lzacp3Id = insertTestClient("LZACP3", "LZClientA CP3", "BUS").block();
		lzbcp1Id = insertTestClient("LZBCP1", "LZClientB CP1", "BUS").block();
		lzbcp2Id = insertTestClient("LZBCP2", "LZClientB CP2", "BUS").block();
		lzbcp3Id = insertTestClient("LZBCP3", "LZClientB CP3", "BUS").block();

		// Step 2: Client hierarchy
		insertClientHierarchy(lzclaId, systemId, null, null, null).block();
		insertClientHierarchy(lzclbId, systemId, null, null, null).block();
		insertClientHierarchy(lzacp1Id, lzclaId, systemId, null, null).block();
		insertClientHierarchy(lzacp2Id, lzclaId, systemId, null, null).block();
		insertClientHierarchy(lzacp3Id, lzclaId, systemId, null, null).block();
		insertClientHierarchy(lzbcp1Id, lzclbId, systemId, null, null).block();
		insertClientHierarchy(lzbcp2Id, lzclbId, systemId, null, null).block();
		insertClientHierarchy(lzbcp3Id, lzclbId, systemId, null, null).block();

		// Step 3: Owner users (8)
		lzclaOwnerId = insertTestUser(lzclaId, "lzcla_owner", "lzcla.owner@test.com", "fincity@123").block();
		lzclbOwnerId = insertTestUser(lzclbId, "lzclb_owner", "lzclb.owner@test.com", "fincity@123").block();
		lzacp1OwnerId = insertTestUser(lzacp1Id, "lzacp1_owner", "lzacp1.owner@test.com", "fincity@123").block();
		lzacp2OwnerId = insertTestUser(lzacp2Id, "lzacp2_owner", "lzacp2.owner@test.com", "fincity@123").block();
		lzacp3OwnerId = insertTestUser(lzacp3Id, "lzacp3_owner", "lzacp3.owner@test.com", "fincity@123").block();
		lzbcp1OwnerId = insertTestUser(lzbcp1Id, "lzbcp1_owner", "lzbcp1.owner@test.com", "fincity@123").block();
		lzbcp2OwnerId = insertTestUser(lzbcp2Id, "lzbcp2_owner", "lzbcp2.owner@test.com", "fincity@123").block();
		lzbcp3OwnerId = insertTestUser(lzbcp3Id, "lzbcp3_owner", "lzbcp3.owner@test.com", "fincity@123").block();

		// Step 4: Agent users (4)
		lzclaAgent1Id = insertTestUser(lzclaId, "lzcla_agent1", "lzcla.agent1@test.com", "fincity@123").block();
		lzclaAgent2Id = insertTestUser(lzclaId, "lzcla_agent2", "lzcla.agent2@test.com", "fincity@123").block();
		lzclbAgent1Id = insertTestUser(lzclbId, "lzclb_agent1", "lzclb.agent1@test.com", "fincity@123").block();
		lzclbAgent2Id = insertTestUser(lzclbId, "lzclb_agent2", "lzclb.agent2@test.com", "fincity@123").block();

		// Step 5: CP Manager users (2) — no reporting_to
		lzclaCpMgrId = insertTestUser(lzclaId, "lzcla_cp_mgr", "lzcla.cpmgr@test.com", "fincity@123").block();
		lzclbCpMgrId = insertTestUser(lzclbId, "lzclb_cp_mgr", "lzclb.cpmgr@test.com", "fincity@123").block();

		// Step 6: CP Manager 12 & 3 (4) — reporting_to = cp_mgr of same client
		lzclaCpMgr12Id = insertUserWithReportingTo(lzclaId, "lzcla_cp_mgr12", "lzcla.cpmgr12@test.com",
				lzclaCpMgrId).block();
		lzclaCpMgr3Id = insertUserWithReportingTo(lzclaId, "lzcla_cp_mgr3", "lzcla.cpmgr3@test.com",
				lzclaCpMgrId).block();
		lzclbCpMgr12Id = insertUserWithReportingTo(lzclbId, "lzclb_cp_mgr12", "lzclb.cpmgr12@test.com",
				lzclbCpMgrId).block();
		lzclbCpMgr3Id = insertUserWithReportingTo(lzclbId, "lzclb_cp_mgr3", "lzclb.cpmgr3@test.com",
				lzclbCpMgrId).block();

		// Step 7: Client manager entries
		insertClientManager(lzacp1Id, lzclaCpMgr12Id).block();
		insertClientManager(lzacp2Id, lzclaCpMgr12Id).block();
		insertClientManager(lzacp3Id, lzclaCpMgr3Id).block();
		insertClientManager(lzbcp1Id, lzclbCpMgr12Id).block();
		insertClientManager(lzbcp2Id, lzclbCpMgr12Id).block();
		insertClientManager(lzbcp3Id, lzclbCpMgr3Id).block();

		// Step 8: CP Teammates (8) — reporting_to = owner of respective CP client
		lzacp2Tm1Id = insertUserWithReportingTo(lzacp2Id, "lzacp2_tm1", "lzacp2.tm1@test.com",
				lzacp2OwnerId).block();
		lzacp2Tm2Id = insertUserWithReportingTo(lzacp2Id, "lzacp2_tm2", "lzacp2.tm2@test.com",
				lzacp2OwnerId).block();
		lzacp3Tm1Id = insertUserWithReportingTo(lzacp3Id, "lzacp3_tm1", "lzacp3.tm1@test.com",
				lzacp3OwnerId).block();
		lzacp3Tm2Id = insertUserWithReportingTo(lzacp3Id, "lzacp3_tm2", "lzacp3.tm2@test.com",
				lzacp3OwnerId).block();
		lzbcp2Tm1Id = insertUserWithReportingTo(lzbcp2Id, "lzbcp2_tm1", "lzbcp2.tm1@test.com",
				lzbcp2OwnerId).block();
		lzbcp2Tm2Id = insertUserWithReportingTo(lzbcp2Id, "lzbcp2_tm2", "lzbcp2.tm2@test.com",
				lzbcp2OwnerId).block();
		lzbcp3Tm1Id = insertUserWithReportingTo(lzbcp3Id, "lzbcp3_tm1", "lzbcp3.tm1@test.com",
				lzbcp3OwnerId).block();
		lzbcp3Tm2Id = insertUserWithReportingTo(lzbcp3Id, "lzbcp3_tm2", "lzbcp3.tm2@test.com",
				lzbcp3OwnerId).block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
				.then(databaseClient.sql("UPDATE security_user SET REPORTING_TO = NULL WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// ─── helpers ─────────────────────────────────────────────────────────────

	private Mono<ULong> insertUserWithReportingTo(ULong clientId, String userName, String email, ULong reportingTo) {
		return databaseClient.sql(
				"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, " +
						"PASSWORD, PASSWORD_HASHED, STATUS_CODE, REPORTING_TO) " +
						"VALUES (:clientId, :userName, :email, 'Test', 'User', 'fincity@123', false, 'ACTIVE', :reportingTo)")
				.bind("clientId", clientId.longValue())
				.bind("userName", userName)
				.bind("email", email)
				.bind("reportingTo", reportingTo.longValue())
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private ContextAuthentication ownerAuth(ULong userId, ULong clientId, String clientCode) {
		return TestDataFactory.createBusinessAuth(userId, clientId, clientCode, OWNER_AUTHORITIES);
	}

	private ContextAuthentication clientMgrAuth(ULong userId, ULong clientId, String clientCode) {
		return TestDataFactory.createBusinessAuth(userId, clientId, clientCode, CLIENT_MGR_AUTHORITIES);
	}

	// ─── User Visibility ─────────────────────────────────────────────────────

	@Nested
	@DisplayName("UserVisibility - readPageFilter")
	class UserVisibility {

		@Test
		@DisplayName("LZCLA owner sees all 13 users in hierarchy")
		void lzclaOwner_seesAllHierarchyUsers() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						// LZCLA users: owner, agent1, agent2, cp_mgr, cp_mgr12, cp_mgr3
						assertThat(ids).contains(lzclaOwnerId, lzclaAgent1Id, lzclaAgent2Id,
								lzclaCpMgrId, lzclaCpMgr12Id, lzclaCpMgr3Id);
						// CP users: acp1_owner, acp2_owner+tm1+tm2, acp3_owner+tm1+tm2
						assertThat(ids).contains(lzacp1OwnerId, lzacp2OwnerId, lzacp2Tm1Id, lzacp2Tm2Id,
								lzacp3OwnerId, lzacp3Tm1Id, lzacp3Tm2Id);
						assertThat(ids).hasSize(13);
						// Must NOT see any LZCLB side users
						assertThat(ids).doesNotContain(lzclbOwnerId, lzclbAgent1Id, lzbcp1OwnerId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr sees 10 users (self + sub-org + managed clients)")
		void lzclaCpMgr_seesSubOrgAndManagedClientUsers() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgrId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						// Self + direct reports (cp_mgr12, cp_mgr3)
						assertThat(ids).contains(lzclaCpMgrId, lzclaCpMgr12Id, lzclaCpMgr3Id);
						// Managed clients via sub-org delegation: all CP1/2/3 users
						assertThat(ids).contains(lzacp1OwnerId, lzacp2OwnerId, lzacp2Tm1Id, lzacp2Tm2Id,
								lzacp3OwnerId, lzacp3Tm1Id, lzacp3Tm2Id);
						assertThat(ids).hasSize(10);
						// Must NOT see owner, agents (same client but outside sub-org)
						assertThat(ids).doesNotContain(lzclaOwnerId, lzclaAgent1Id, lzclaAgent2Id);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr12 sees 5 users (self + managed CP1 + managed CP2)")
		void lzclaCpMgr12_seesManagedCp1Cp2Users() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						// Self
						assertThat(ids).contains(lzclaCpMgr12Id);
						// Managed CP1: owner
						assertThat(ids).contains(lzacp1OwnerId);
						// Managed CP2: owner + tm1 + tm2
						assertThat(ids).contains(lzacp2OwnerId, lzacp2Tm1Id, lzacp2Tm2Id);
						assertThat(ids).hasSize(5);
						// Must NOT see CP3 users or other LZCLA users
						assertThat(ids).doesNotContain(lzacp3OwnerId, lzacp3Tm1Id, lzclaCpMgrId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr3 sees 4 users (self + managed CP3)")
		void lzclaCpMgr3_seesManagedCp3Users() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr3Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						assertThat(ids).contains(lzclaCpMgr3Id);
						assertThat(ids).contains(lzacp3OwnerId, lzacp3Tm1Id, lzacp3Tm2Id);
						assertThat(ids).hasSize(4);
						assertThat(ids).doesNotContain(lzacp1OwnerId, lzacp2OwnerId, lzclaCpMgrId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLB owner sees all 13 LZCLB-side users (mirror of LZCLA)")
		void lzclbOwner_seesAllHierarchyUsers() {
			ContextAuthentication auth = ownerAuth(lzclbOwnerId, lzclbId, "LZCLB");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						assertThat(ids).contains(lzclbOwnerId, lzclbAgent1Id, lzclbAgent2Id,
								lzclbCpMgrId, lzclbCpMgr12Id, lzclbCpMgr3Id);
						assertThat(ids).contains(lzbcp1OwnerId, lzbcp2OwnerId, lzbcp2Tm1Id, lzbcp2Tm2Id,
								lzbcp3OwnerId, lzbcp3Tm1Id, lzbcp3Tm2Id);
						assertThat(ids).hasSize(13);
						assertThat(ids).doesNotContain(lzclaOwnerId, lzclaAgent1Id, lzacp1OwnerId);
					})
					.verifyComplete();
		}
	}

	// ─── Client Visibility ───────────────────────────────────────────────────

	@Nested
	@DisplayName("ClientVisibility - readPageFilter")
	class ClientVisibility {

		@Test
		@DisplayName("LZCLA owner sees 4 clients (own + 3 sub-clients)")
		void lzclaOwner_seesOwnAndSubClients() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(Client::getId).collect(Collectors.toSet());
						assertThat(ids).contains(lzclaId, lzacp1Id, lzacp2Id, lzacp3Id);
						assertThat(ids).hasSize(4);
						assertThat(ids).doesNotContain(lzclbId, lzbcp1Id, lzbcp2Id, lzbcp3Id);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr sees 3 clients via sub-org delegation")
		void lzclaCpMgr_seesManagedClientsViaSubOrg() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgrId, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(Client::getId).collect(Collectors.toSet());
						assertThat(ids).contains(lzacp1Id, lzacp2Id, lzacp3Id);
						assertThat(ids).hasSize(3);
						assertThat(ids).doesNotContain(lzclaId, lzclbId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr12 sees 2 clients (CP1, CP2)")
		void lzclaCpMgr12_seesManagedCp1Cp2() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(Client::getId).collect(Collectors.toSet());
						assertThat(ids).contains(lzacp1Id, lzacp2Id);
						assertThat(ids).hasSize(2);
						assertThat(ids).doesNotContain(lzacp3Id, lzclaId, lzclbId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLA cp_mgr3 sees 1 client (CP3)")
		void lzclaCpMgr3_seesManagedCp3() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr3Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(Client::getId).collect(Collectors.toSet());
						assertThat(ids).containsExactly(lzacp3Id);
					})
					.verifyComplete();
		}
	}

	// ─── Client CRUD Restrictions ────────────────────────────────────────────

	@Nested
	@DisplayName("ClientCRUDRestrictions")
	class ClientCRUDRestrictions {

		@Test
		@DisplayName("Owner can read managed sub-client")
		void owner_canReadManagedSubClient() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.read(lzacp1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(client -> assertThat(client.getCode()).isEqualTo("LZACP1"))
					.verifyComplete();
		}

		@Test
		@DisplayName("Owner cannot read unrelated client")
		void owner_cannotReadUnrelatedClient() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.read(lzclbId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("CP Mgr12 can read managed CP1")
		void cpMgr12_canReadManagedCp1() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.read(lzacp1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(client -> assertThat(client.getCode()).isEqualTo("LZACP1"))
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 cannot read unmanaged CP3")
		void cpMgr12_cannotReadUnmanagedCp3() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.read(lzacp3Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("CP Mgr12 can update managed CP2 via map")
		void cpMgr12_canUpdateManagedCp2() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.update(lzacp2Id, java.util.Map.of("name", "Updated CP2"))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(client -> assertThat(client.getName()).isEqualTo("Updated CP2"))
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 cannot update unmanaged CP3")
		void cpMgr12_cannotUpdateUnmanagedCp3() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.update(lzacp3Id, java.util.Map.of("name", "Hacked"))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("Owner can delete managed sub-client (soft delete)")
		void owner_canDeleteManagedSubClient() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.delete(lzacp1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 cannot delete unmanaged CP3")
		void cpMgr12_cannotDeleteUnmanagedCp3() {
			// cp_mgr12 lacks Client_DELETE authority, so @PreAuthorize fires first
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					clientService.delete(lzacp3Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError()
					.verify();
		}
	}

	// ─── User CRUD Restrictions ──────────────────────────────────────────────

	@Nested
	@DisplayName("UserCRUDRestrictions - status operations with checkSubOrgAndRun")
	class UserCRUDRestrictions {

		@Test
		@DisplayName("CP Mgr12 can makeUserActive for user in managed client CP1")
		void cpMgr12_canMakeUserActiveInManagedCp1() {
			// cp_mgr12 has direct client_manager entry for LZACP1
			databaseClient.sql("UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :id")
					.bind("id", lzacp1OwnerId.longValue()).then().block();

			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.makeUserActive(lzacp1OwnerId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 can makeUserActive for user in managed CP2")
		void cpMgr12_canMakeUserActiveInManagedCp2() {
			databaseClient.sql("UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :id")
					.bind("id", lzacp2Tm1Id.longValue()).then().block();

			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.makeUserActive(lzacp2Tm1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 cannot makeUserActive for user in unmanaged CP3")
		void cpMgr12_cannotMakeUserActiveInUnmanagedCp3() {
			databaseClient.sql("UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :id")
					.bind("id", lzacp3Tm1Id.longValue()).then().block();

			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.makeUserActive(lzacp3Tm1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("CP Mgr3 can makeUserInActive for user in managed CP3")
		void cpMgr3_canMakeUserInActiveInManagedCp3() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr3Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.makeUserInActive(lzacp3Tm1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr3 cannot makeUserInActive for user in unmanaged CP2")
		void cpMgr3_cannotMakeUserInActiveInUnmanagedCp2() {
			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr3Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.makeUserInActive(lzacp2Tm1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("CP Mgr can unblock user in sub-org (cp_mgr12 reports to cp_mgr)")
		void cpMgr_canUnblockUserInSubOrg() {
			databaseClient.sql(
					"UPDATE security_user SET STATUS_CODE = 'LOCKED', LOCKED_UNTIL = NOW() + INTERVAL 1 HOUR WHERE ID = :id")
					.bind("id", lzclaCpMgr12Id.longValue()).then().block();

			ContextAuthentication auth = clientMgrAuth(lzclaCpMgrId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.unblockUser(lzclaCpMgr12Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("CP Mgr12 cannot unblock user outside sub-org (agent1)")
		void cpMgr12_cannotUnblockUserOutsideSubOrg() {
			databaseClient.sql(
					"UPDATE security_user SET STATUS_CODE = 'LOCKED', LOCKED_UNTIL = NOW() + INTERVAL 1 HOUR WHERE ID = :id")
					.bind("id", lzclaAgent1Id.longValue()).then().block();

			ContextAuthentication auth = clientMgrAuth(lzclaCpMgr12Id, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.unblockUser(lzclaAgent1Id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}
	}

	// ─── User Update Status Preservation ─────────────────────────────────────

	@Nested
	@DisplayName("UserUpdateStatusPreservation")
	class UserUpdateStatusPreservation {

		@Test
		@DisplayName("update(key, map) preserves ACTIVE status when updating firstName")
		void updateMap_preservesActiveStatus() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.update(lzclaAgent1Id, java.util.Map.of("firstName", "UpdatedName"))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(user -> {
						assertThat(user.getFirstName()).isEqualTo("UpdatedName");
						assertThat(user.getStatusCode())
								.as("Status should remain ACTIVE after map update")
								.isEqualTo(com.fincity.security.jooq.enums.SecurityUserStatusCode.ACTIVE);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update(entity) preserves ACTIVE status when updating firstName")
		void updateEntity_preservesActiveStatus() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readInternal(lzclaAgent2Id)
							.flatMap(user -> {
								user.setFirstName("EntityUpdated");
								return userService.update(user)
										.contextWrite(
												ReactiveSecurityContextHolder.withAuthentication(auth));
							}))
					.assertNext(user -> {
						assertThat(user.getFirstName()).isEqualTo("EntityUpdated");
						assertThat(user.getStatusCode())
								.as("Status should remain ACTIVE after entity update")
								.isEqualTo(com.fincity.security.jooq.enums.SecurityUserStatusCode.ACTIVE);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update(key, map) with only lastName preserves all other fields")
		void updateMap_preservesAllFields() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			// Read before update to capture original values
			User original = userService.readInternal(lzacp2Tm1Id).block();

			StepVerifier.create(
					userService.update(lzacp2Tm1Id, java.util.Map.of("lastName", "NewLast"))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(user -> {
						assertThat(user.getLastName()).isEqualTo("NewLast");
						assertThat(user.getFirstName()).isEqualTo(original.getFirstName());
						assertThat(user.getStatusCode()).isEqualTo(original.getStatusCode());
						assertThat(user.getUserName()).isEqualTo(original.getUserName());
						assertThat(user.getEmailId()).isEqualTo(original.getEmailId());
					})
					.verifyComplete();
		}
	}

	// ─── Cross-Client Isolation ──────────────────────────────────────────────

	@Nested
	@DisplayName("CrossClientIsolation")
	class CrossClientIsolation {

		@Test
		@DisplayName("LZCLA owner sees zero LZCLB users")
		void lzclaOwner_seesNoLzclbUsers() {
			ContextAuthentication auth = ownerAuth(lzclaOwnerId, lzclaId, "LZCLA");

			StepVerifier.create(
					userService.readPageFilter(PageRequest.of(0, 100), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.assertNext(page -> {
						Set<ULong> ids = page.getContent().stream().map(User::getId).collect(Collectors.toSet());
						// Should not contain any LZCLB side users
						assertThat(ids).doesNotContain(
								lzclbOwnerId, lzclbAgent1Id, lzclbAgent2Id,
								lzclbCpMgrId, lzclbCpMgr12Id, lzclbCpMgr3Id,
								lzbcp1OwnerId, lzbcp2OwnerId, lzbcp2Tm1Id, lzbcp2Tm2Id,
								lzbcp3OwnerId, lzbcp3Tm1Id, lzbcp3Tm2Id);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("LZCLB cp_mgr cannot read LZCLA client")
		void lzclbCpMgr_cannotReadLzclaClient() {
			ContextAuthentication auth = clientMgrAuth(lzclbCpMgrId, lzclbId, "LZCLB");

			StepVerifier.create(
					clientService.read(lzclaId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
					.expectError(GenericException.class)
					.verify();
		}
	}
}
