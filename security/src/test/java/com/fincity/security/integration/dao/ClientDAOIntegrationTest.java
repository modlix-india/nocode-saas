package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

class ClientDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientDAO clientDAO;

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@Nested
	@DisplayName("getSystemClientId()")
	class GetSystemClientIdTests {

		@Test
		void returnsSystemClientId() {
			StepVerifier.create(clientDAO.getSystemClientId())
					.assertNext(id -> {
						assertNotNull(id);
						assertEquals(ULong.valueOf(1), id);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientTypeNCode()")
	class GetClientTypeNCodeTests {

		@Test
		void systemClient_ReturnsTypeAndCode() {
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(1)))
					.assertNext(tuple -> {
						assertNotNull(tuple);
						assertEquals("SYS", tuple.getT1());
						assertNotNull(tuple.getT2());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmpty() {
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		void existingClient_ReturnsClient() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1)))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(ULong.valueOf(1), client.getId());
						assertNotNull(client.getCode());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmpty() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientBy() - by code")
	class GetClientByCodeTests {

		@Test
		void existingCode_ReturnsClient() {
			// System client always exists
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(1))
					.flatMap(tuple -> clientDAO.getClientBy(tuple.getT2())))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(ULong.valueOf(1), client.getId());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentCode_ReturnsEmpty() {
			StepVerifier.create(clientDAO.getClientBy("NONEXISTENT_CODE_XYZ"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientsBy() - by IDs")
	class GetClientsByIdsTests {

		@Test
		void existingIds_ReturnsClients() {
			StepVerifier.create(clientDAO.getClientsBy(List.of(ULong.valueOf(1))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
						assertEquals(1, clients.size());
					})
					.verifyComplete();
		}

		@Test
		void mixOfExistingAndNonExisting_ReturnsOnlyExisting() {
			StepVerifier.create(clientDAO.getClientsBy(List.of(ULong.valueOf(1), ULong.valueOf(999999))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertEquals(1, clients.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isClientActive()")
	class IsClientActiveTests {

		@Test
		void activeClient_ReturnsTrue() {
			StepVerifier.create(clientDAO.isClientActive(List.of(ULong.valueOf(1))))
					.assertNext(active -> assertTrue(active))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.isClientActive(List.of(ULong.valueOf(999999))))
					.assertNext(active -> assertFalse(active))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getValidClientCode()")
	class GetValidClientCodeTests {

		@Test
		void validName_GeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("TestCompany"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("TESTC"));
					})
					.verifyComplete();
		}

		@Test
		void shortName_GeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("AB"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("AB"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("makeClientActiveIfInActive()")
	class MakeClientActiveTests {

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.makeClientActiveIfInActive(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("makeClientInActive()")
	class MakeClientInActiveTests {

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.makeClientInActive(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readClientPatterns()")
	class ReadClientPatternsTests {

		@Test
		void returnsPatterns() {
			StepVerifier.create(clientDAO.readClientPatterns().collectList())
					.assertNext(patterns -> assertNotNull(patterns))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("fillUserCounts()")
	class FillUserCountsTests {

		@Test
		void withSystemClient_FillsCounts() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1))
					.flatMap(client -> {
						Map<ULong, Client> map = new HashMap<>();
						map.put(client.getId(), client);
						return clientDAO.fillUserCounts(map, null, null);
					}))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void withAppCode_FillsCounts() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1))
					.flatMap(client -> {
						Map<ULong, Client> map = new HashMap<>();
						map.put(client.getId(), client);
						return clientDAO.fillUserCounts(map, "appx", null);
					}))
					.assertNext(clients -> assertNotNull(clients))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readClientURLs()")
	class ReadClientURLsTests {

		@Test
		void nonExistentCode_ReturnsEmptyMap() {
			StepVerifier.create(clientDAO.readClientURLs("NONEXISTENT", List.of(ULong.valueOf(1))))
					.assertNext(urls -> assertTrue(urls.isEmpty()))
					.verifyComplete();
		}
	}
}
