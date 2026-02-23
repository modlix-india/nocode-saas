package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.service.ClientService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientDAO clientDAO;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE CLIENT_ID > 1)")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Nested
	@DisplayName("isUserClientManageClient()")
	class IsUserClientManageClientTests {

		@Test
		@DisplayName("system client manages any client - should return true")
		void systemClient_ManagesAnyClient() {
			Mono<Boolean> result = insertTestClient("MGTONE", "Managed Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.isUserClientManageClient(systemAuth, clientId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(manages -> assertThat(manages).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("business client does not manage unrelated client - should return false")
		void businessClient_DoesNotManageUnrelatedClient() {
			Mono<Boolean> result = insertTestClient("BUSONE", "Business One", "BUS")
					.flatMap(bus1Id -> insertClientHierarchy(bus1Id, ULong.valueOf(1), null, null, null)
							.then(insertTestClient("BUSTWO", "Business Two", "BUS"))
							.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
									.thenReturn(bus2Id))
							.flatMap(bus2Id -> {
								ContextAuthentication bus1Auth = TestDataFactory.createBusinessAuth(
										bus1Id, "BUSONE",
										List.of("Authorities.Client_READ", "Authorities.Logged_IN"));
								return clientService.isUserClientManageClient(bus1Auth, bus2Id);
							}));

			StepVerifier.create(result)
					.assertNext(manages -> assertThat(manages).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientDAO.getValidClientCode()")
	class GetValidClientCodeTests {

		@Test
		@DisplayName("unique name should generate valid code")
		void uniqueName_GeneratesCode() {
			Mono<String> result = clientDAO.getValidClientCode("TestCompany");

			StepVerifier.create(result)
					.assertNext(code -> {
						assertThat(code).isNotNull();
						assertThat(code).hasSizeLessThanOrEqualTo(8);
						assertThat(code).isUpperCase();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("name with special characters is cleaned")
		void specialChars_Cleaned() {
			Mono<String> result = clientDAO.getValidClientCode("My@Company!#");

			StepVerifier.create(result)
					.assertNext(code -> {
						assertThat(code).isNotNull();
						assertThat(code).isUpperCase();
						assertThat(code).doesNotContain("@", "!", "#");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientDAO.getSystemClientId()")
	class GetSystemClientIdTests {

		@Test
		@DisplayName("should return system client ID (1)")
		void returnsSystemClientId() {
			StepVerifier.create(clientDAO.getSystemClientId())
					.assertNext(id -> assertThat(id).isEqualTo(ULong.valueOf(1)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientDAO.isClientActive()")
	class IsClientActiveTests {

		@Test
		@DisplayName("active clients return true")
		void activeClients_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("ACTONE", "Active Client", "BUS")
					.flatMap(clientId -> clientDAO.isClientActive(List.of(ULong.valueOf(1), clientId)));

			StepVerifier.create(result)
					.assertNext(active -> assertThat(active).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent client returns false")
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.isClientActive(List.of(ULong.valueOf(999999))))
					.assertNext(active -> assertThat(active).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientDAO.readClientPatterns()")
	class ReadClientPatternsTests {

		@Test
		@DisplayName("returns URL patterns (may be empty in test DB)")
		void returnsPatterns() {
			StepVerifier.create(clientDAO.readClientPatterns().collectList())
					.assertNext(patterns -> assertThat(patterns).isNotNull())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getClientBy()")
	class GetClientByTests {

		@Test
		@DisplayName("existing client code returns client")
		void existingCode_ReturnsClient() {
			Mono<Client> result = insertTestClient("GETCLI", "Get Client", "BUS")
					.flatMap(clientId -> clientService.getClientBy("GETCLI"));

			StepVerifier.create(result)
					.assertNext(client -> {
						assertThat(client).isNotNull();
						assertThat(client.getCode()).isEqualTo("GETCLI");
						assertThat(client.getName()).isEqualTo("Get Client");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent code returns empty")
		void nonExistentCode_ReturnsEmpty() {
			StepVerifier.create(clientService.getClientBy("NOTEXIST"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getActiveClient()")
	class GetActiveClientTests {

		@Test
		@DisplayName("active client returns client")
		void activeClient_ReturnsClient() {
			Mono<Client> result = insertTestClient("ACTTWO", "Active Client Two", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getActiveClient(clientId));

			StepVerifier.create(result)
					.assertNext(client -> {
						assertThat(client).isNotNull();
						assertThat(client.getName()).isEqualTo("Active Client Two");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getClientsBy()")
	class GetClientsByTests {

		@Test
		@DisplayName("returns multiple clients by IDs")
		void multipleIds_ReturnsAllClients() {
			Mono<List<Client>> result = insertTestClient("MLONE", "Multi One", "BUS")
					.flatMap(id1 -> insertTestClient("MLTWO", "Multi Two", "BUS")
							.flatMap(id2 -> clientService.getClientsBy(List.of(id1, id2))));

			StepVerifier.create(result)
					.assertNext(clients -> {
						assertThat(clients).hasSize(2);
						assertThat(clients).extracting(Client::getName)
								.containsExactlyInAnyOrder("Multi One", "Multi Two");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty list returns empty result")
		void emptyList_ReturnsEmpty() {
			StepVerifier.create(clientService.getClientsBy(List.of()))
					.assertNext(clients -> assertThat(clients).isEmpty())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.doesClientManageClient()")
	class DoesClientManageClientTests {

		@Test
		@DisplayName("system manages child client")
		void systemManagesChild_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("DCMONE", "DCM Client One", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.doesClientManageClient(ULong.valueOf(1), clientId));

			StepVerifier.create(result)
					.assertNext(manages -> assertThat(manages).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("child does not manage system")
		void childDoesNotManageSystem_ReturnsFalse() {
			Mono<Boolean> result = insertTestClient("DCMTWO", "DCM Client Two", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.doesClientManageClient(clientId, ULong.valueOf(1)));

			StepVerifier.create(result)
					.assertNext(manages -> assertThat(manages).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getClientId()")
	class GetClientIdTests {

		@Test
		@DisplayName("returns ID for existing client code")
		void existingCode_ReturnsId() {
			Mono<ULong> result = insertTestClient("GCIDTS", "Get ID Client", "BUS")
					.flatMap(clientId -> clientService.getClientId("GCIDTS")
							.map(resolvedId -> {
								assertThat(resolvedId).isEqualTo(clientId);
								return resolvedId;
							}));

			StepVerifier.create(result)
					.assertNext(id -> assertThat(id).isNotNull())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getSystemClientId()")
	class GetSystemClientIdServiceTests {

		@Test
		@DisplayName("returns system client ID = 1")
		void returnsSystemId() {
			StepVerifier.create(clientService.getSystemClientId())
					.assertNext(id -> assertThat(id).isEqualTo(ULong.valueOf(1)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getClientInfoById()")
	class GetClientInfoByIdTests {

		@Test
		@DisplayName("returns client info for existing ID")
		void existingId_ReturnsClient() {
			Mono<Client> result = insertTestClient("INFONE", "Info Client", "BUS")
					.flatMap(clientId -> clientService.getClientInfoById(clientId));

			StepVerifier.create(result)
					.assertNext(client -> {
						assertThat(client).isNotNull();
						assertThat(client.getName()).isEqualTo("Info Client");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.makeClientActiveIfInActive() / makeClientInActive()")
	class ActivateDeactivateTests {

		@Test
		@DisplayName("deactivate then reactivate client")
		void deactivateThenReactivate() {
			Mono<Boolean> result = insertTestClient("TOGONE", "Toggle Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.makeClientInActive(clientId)
							.then(clientDAO.isClientActive(List.of(clientId)))
							.flatMap(isActive -> {
								assertThat(isActive).isFalse();
								return clientService.makeClientActiveIfInActive(clientId);
							})
							.then(clientDAO.isClientActive(List.of(clientId))))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(isActive -> assertThat(isActive).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getClientHierarchy()")
	class GetClientHierarchyTests {

		@Test
		@DisplayName("returns hierarchy for child client")
		void childClient_ReturnsHierarchy() {
			Mono<List<ULong>> result = insertTestClient("HRONE", "Hierarchy Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getClientHierarchy(clientId));

			StepVerifier.create(result)
					.assertNext(hierarchy -> {
						assertThat(hierarchy).isNotNull();
						assertThat(hierarchy).isNotEmpty();
						assertThat(hierarchy).contains(ULong.valueOf(1));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getManagingClientIds()")
	class GetManagingClientIdsTests {

		@Test
		@DisplayName("system client manages itself and children")
		void systemClient_IncludesChildren() {
			Mono<List<ULong>> result = insertTestClient("MGONE", "Managed One", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getManagingClientIds(ULong.valueOf(1))
							.map(ids -> {
								assertThat(ids).contains(ULong.valueOf(1));
								assertThat(ids).contains(clientId);
								return ids;
							}));

			StepVerifier.create(result)
					.assertNext(ids -> assertThat(ids).hasSizeGreaterThanOrEqualTo(2))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.isUserPartOfHierarchy()")
	class IsUserPartOfHierarchyTests {

		@Test
		@DisplayName("user in child client is part of parent hierarchy")
		void userInChild_IsPartOfParent() {
			Mono<Boolean> result = insertTestClient("UPHONE", "UPH Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "uphuser", "uphuser@test.com", "fincity@123"))
							.flatMap(userId -> clientService.isUserPartOfHierarchy("SYSTEM", userId)));

			StepVerifier.create(result)
					.assertNext(isPart -> assertThat(isPart).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("user not in hierarchy returns false")
		void userNotInHierarchy_ReturnsFalse() {
			Mono<Boolean> result = insertTestClient("UPHTWO", "UPH Two", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> insertTestClient("UPHTHR", "UPH Three", "BUS")
							.flatMap(otherId -> insertClientHierarchy(otherId, ULong.valueOf(1), null, null, null)
									.then(insertTestUser(otherId, "uphuser2", "uphuser2@test.com", "fincity@123"))))
					.flatMap(userId -> clientService.isUserPartOfHierarchy("UPHTWO", userId));

			StepVerifier.create(result)
					.assertNext(isPart -> assertThat(isPart).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.getManagedClientOfClientById()")
	class GetManagedClientTests {

		@Test
		@DisplayName("child client returns its managing parent")
		void childClient_ReturnsParent() {
			Mono<Client> result = insertTestClient("MGCONE", "MGC Child", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getManagedClientOfClientById(clientId));

			StepVerifier.create(result)
					.assertNext(parent -> assertThat(parent).isNotNull())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.isClientActive()")
	class IsClientActiveServiceTests {

		@Test
		@DisplayName("active client with hierarchy returns true")
		void activeWithHierarchy_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("ICAONE", "ICA Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.isClientActive(clientId));

			StepVerifier.create(result)
					.assertNext(active -> assertThat(active).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.isUserClientManageClient() by clientCode")
	class IsUserClientManageClientByCodeTests {

		@Test
		@DisplayName("system client manages by code")
		void systemManagesByCode_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("IMCONE", "IMC Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.isUserClientManageClient(systemAuth, "IMCONE"));

			StepVerifier.create(result)
					.assertNext(manages -> assertThat(manages).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent code returns false")
		void nonExistentCode_ReturnsFalse() {
			StepVerifier.create(clientService.isUserClientManageClient(systemAuth, "NXCODE"))
					.assertNext(manages -> assertThat(manages).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.fillManagingClientDetails()")
	class FillManagingClientDetailsTests {

		@Test
		@DisplayName("fills managing client for child")
		void childClient_FillsParent() {
			Mono<Client> result = insertTestClient("FMCONE", "FMC Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getClientInfoById(clientId))
					.flatMap(client -> clientService.fillManagingClientDetails(client));

			StepVerifier.create(result)
					.assertNext(client -> {
						assertThat(client).isNotNull();
						assertThat(client.getName()).isEqualTo("FMC Client");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("ClientService.fillDetails()")
	class FillDetailsTests {

		@Test
		@DisplayName("fillDetails with fetchManagingClient flag")
		void fetchManagingClient_FillsParent() {
			Mono<List<Client>> result = insertTestClient("FDONE", "FD Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> clientService.getClientInfoById(clientId)
							.flatMap(client -> {
								org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
								params.add("fetchManagingClient", "true");
								return clientService.fillDetails(List.of(client), params);
							}));

			StepVerifier.create(result)
					.assertNext(clients -> {
						assertThat(clients).hasSize(1);
						assertThat(clients.get(0).getName()).isEqualTo("FD Client");
					})
					.verifyComplete();
		}
	}
}
