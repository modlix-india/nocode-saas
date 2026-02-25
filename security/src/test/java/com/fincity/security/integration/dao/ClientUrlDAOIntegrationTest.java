package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientUrlDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientUrlDAO clientUrlDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ULong testClientId;
	private String testClientCode;
	private ULong testAppId;
	private String testAppCode;

	@BeforeEach
	void setUp() {
		setupMockBeans();

		String ts = String.valueOf(System.currentTimeMillis());
		testClientCode = "CU" + ts.substring(ts.length() - 6);

		testClientId = insertTestClient(testClientCode, "ClientUrl Test Client " + ts, "BUS").block();

		testAppId = insertTestApp(SYSTEM_CLIENT_ID, "cu" + ts.substring(ts.length() - 8), "ClientUrl Test App")
				.block();
		testAppCode = databaseClient.sql("SELECT APP_CODE FROM security_app WHERE ID = :appId")
				.bind("appId", testAppId.longValue())
				.map(row -> row.get("APP_CODE", String.class))
				.one()
				.block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_url WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_department WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_app WHERE ID = :appId")
						.bind("appId", testAppId.longValue()).then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper Methods ---

	private Mono<ULong> insertClientUrl(ULong clientId, String urlPattern, String appCode) {
		return databaseClient.sql(
				"INSERT INTO security_client_url (CLIENT_ID, URL_PATTERN, APP_CODE) VALUES (:clientId, :urlPattern, :appCode)")
				.bind("clientId", clientId.longValue())
				.bind("urlPattern", urlPattern)
				.bind("appCode", appCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// --- Test Classes ---

	@Nested
	@DisplayName("getClientUrlsBasedOnAppAndClient()")
	class GetClientUrlsBasedOnAppAndClientTests {

		@Test
		@DisplayName("returns matching URLs for appCode and clientId")
		void matchingUrls_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());

			insertClientUrl(testClientId, "https://url1-" + ts + ".example.com", testAppCode).block();
			insertClientUrl(testClientId, "https://url2-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrlsBasedOnAppAndClient(testAppCode, testClientId))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertEquals(2, urls.size());
						assertTrue(urls.stream().anyMatch(u -> u.contains("url1-" + ts)));
						assertTrue(urls.stream().anyMatch(u -> u.contains("url2-" + ts)));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty list when no matching URLs")
		void noMatches_ReturnsEmptyList() {
			StepVerifier.create(
					clientUrlDAO.getClientUrlsBasedOnAppAndClient("nonexistentAppCode", testClientId))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns only URLs for specified client, not other clients")
		void onlySpecifiedClient_FiltersCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String otherCode = "OC" + ts.substring(ts.length() - 6);

			ULong otherClientId = insertTestClient(otherCode, "Other Client", "BUS").block();

			insertClientUrl(testClientId, "https://mine-" + ts + ".example.com", testAppCode).block();
			insertClientUrl(otherClientId, "https://other-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrlsBasedOnAppAndClient(testAppCode, testClientId))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertEquals(1, urls.size());
						assertTrue(urls.get(0).contains("mine-" + ts));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns all URLs for appCode when clientId is null")
		void nullClientId_ReturnsAllForAppCode() {
			String ts = String.valueOf(System.currentTimeMillis());
			String otherCode = "NC" + ts.substring(ts.length() - 6);

			ULong otherClientId = insertTestClient(otherCode, "Null Client Test", "BUS").block();

			insertClientUrl(testClientId, "https://null1-" + ts + ".example.com", testAppCode).block();
			insertClientUrl(otherClientId, "https://null2-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrlsBasedOnAppAndClient(testAppCode, null))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.size() >= 2);
						assertTrue(urls.stream().anyMatch(u -> u.contains("null1-" + ts)));
						assertTrue(urls.stream().anyMatch(u -> u.contains("null2-" + ts)));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLatestClientUrlBasedOnAppAndClient()")
	class GetLatestClientUrlBasedOnAppAndClientTests {

		@Test
		@DisplayName("single URL returns that URL")
		void singleUrl_ReturnsThatUrl() {
			String ts = String.valueOf(System.currentTimeMillis());

			insertClientUrl(testClientId, "https://single-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(
					clientUrlDAO.getLatestClientUrlBasedOnAppAndClient(testAppCode, testClientId))
					.assertNext(url -> {
						assertNotNull(url);
						assertTrue(url.contains("single-" + ts));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple URLs returns the latest (most recently updated)")
		void multipleUrls_ReturnsLatest() {
			String ts = String.valueOf(System.currentTimeMillis());

			// Insert first URL with an explicitly earlier UPDATED_AT
			ULong olderId = insertClientUrl(testClientId, "https://older-" + ts + ".example.com", testAppCode).block();
			databaseClient.sql("UPDATE security_client_url SET UPDATED_AT = '2020-01-01 00:00:00' WHERE ID = :id")
					.bind("id", olderId.longValue())
					.then()
					.block();

			// Insert second URL (will have the current UPDATED_AT, which is later)
			insertClientUrl(testClientId, "https://newer-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(
					clientUrlDAO.getLatestClientUrlBasedOnAppAndClient(testAppCode, testClientId))
					.assertNext(url -> {
						assertNotNull(url);
						assertTrue(url.contains("newer-" + ts),
								"Expected the latest URL but got: " + url);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("no matching URLs returns empty")
		void noMatches_ReturnsEmpty() {
			StepVerifier.create(
					clientUrlDAO.getLatestClientUrlBasedOnAppAndClient("nonexistentAppCode", testClientId))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkSubDomainAvailability()")
	class CheckSubDomainAvailabilityTests {

		@Test
		@DisplayName("available subdomain returns true")
		void availableSubdomain_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					clientUrlDAO.checkSubDomainAvailability("available-" + ts + ".example.com"))
					.assertNext(result -> assertTrue(result, "Subdomain should be available"))
					.verifyComplete();
		}

		@Test
		@DisplayName("taken subdomain returns false")
		void takenSubdomain_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String urlPattern = "taken-" + ts + ".example.com";

			insertClientUrl(testClientId, urlPattern, testAppCode).block();

			StepVerifier.create(clientUrlDAO.checkSubDomainAvailability(urlPattern))
					.assertNext(result -> assertFalse(result, "Subdomain should be taken"))
					.verifyComplete();
		}

		@Test
		@DisplayName("similar but different subdomain is available")
		void similarButDifferent_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String urlPattern = "exact-" + ts + ".example.com";

			insertClientUrl(testClientId, urlPattern, testAppCode).block();

			// A similar but not identical URL should be available
			StepVerifier.create(
					clientUrlDAO.checkSubDomainAvailability("exact-" + ts + "-other.example.com"))
					.assertNext(result -> assertTrue(result, "Similar but different subdomain should be available"))
					.verifyComplete();
		}

		@Test
		@DisplayName("after deleting a URL the subdomain becomes available again")
		void afterDeletion_BecomesAvailable() {
			String ts = String.valueOf(System.currentTimeMillis());
			String urlPattern = "delcheck-" + ts + ".example.com";

			ULong urlId = insertClientUrl(testClientId, urlPattern, testAppCode).block();
			assertNotNull(urlId);

			// Subdomain should be taken
			StepVerifier.create(clientUrlDAO.checkSubDomainAvailability(urlPattern))
					.assertNext(result -> assertFalse(result, "Subdomain should be taken before deletion"))
					.verifyComplete();

			// Delete the URL
			databaseClient.sql("DELETE FROM security_client_url WHERE ID = :id")
					.bind("id", urlId.longValue())
					.then()
					.block();

			// Now it should be available
			StepVerifier.create(clientUrlDAO.checkSubDomainAvailability(urlPattern))
					.assertNext(result -> assertTrue(result, "Subdomain should be available after deletion"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientUrls()")
	class GetClientUrlsTests {

		@Test
		@DisplayName("returns URLs by appCode and clientCode")
		void byAppCodeAndClientCode_ReturnsUrls() {
			String ts = String.valueOf(System.currentTimeMillis());

			insertClientUrl(testClientId, "https://gcurl1-" + ts + ".example.com", testAppCode).block();
			insertClientUrl(testClientId, "https://gcurl2-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrls(testAppCode, testClientCode))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertEquals(2, urls.size());
						for (ClientUrl url : urls) {
							assertEquals(testClientId, url.getClientId());
							assertEquals(testAppCode.trim(), url.getAppCode().trim());
							assertNotNull(url.getUrlPattern());
						}
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty list for non-existent clientCode")
		void nonExistentClientCode_ReturnsEmptyList() {
			StepVerifier.create(clientUrlDAO.getClientUrls(testAppCode, "NOEXIST"))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty list for non-existent appCode")
		void nonExistentAppCode_ReturnsEmptyList() {
			String ts = String.valueOf(System.currentTimeMillis());
			insertClientUrl(testClientId, "https://noapp-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrls("nonExistentApp", testClientCode))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("does not return URLs from a different app")
		void differentApp_DoesNotReturn() {
			String ts = String.valueOf(System.currentTimeMillis());

			ULong otherAppId = insertTestApp(SYSTEM_CLIENT_ID, "ot" + ts.substring(ts.length() - 8),
					"Other App").block();
			String otherAppCode = databaseClient.sql("SELECT APP_CODE FROM security_app WHERE ID = :appId")
					.bind("appId", otherAppId.longValue())
					.map(row -> row.get("APP_CODE", String.class))
					.one()
					.block();

			insertClientUrl(testClientId, "https://diffapp-" + ts + ".example.com", otherAppCode).block();

			// Query with the original testAppCode should not return URLs from otherAppCode
			StepVerifier.create(clientUrlDAO.getClientUrls(testAppCode, testClientCode))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.stream().noneMatch(u -> u.getUrlPattern().contains("diffapp-" + ts)));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("does not return URLs from a different client")
		void differentClient_DoesNotReturn() {
			String ts = String.valueOf(System.currentTimeMillis());
			String otherCode = "DC" + ts.substring(ts.length() - 6);

			ULong otherClientId = insertTestClient(otherCode, "Diff Client", "BUS").block();
			insertClientUrl(otherClientId, "https://diffcli-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrls(testAppCode, testClientCode))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.stream().noneMatch(u -> u.getUrlPattern().contains("diffcli-" + ts)));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returned ClientUrl objects have all expected fields populated")
		void returnedObjects_HaveAllFields() {
			String ts = String.valueOf(System.currentTimeMillis());

			insertClientUrl(testClientId, "https://fields-" + ts + ".example.com", testAppCode).block();

			StepVerifier.create(clientUrlDAO.getClientUrls(testAppCode, testClientCode))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertEquals(1, urls.size());

						ClientUrl url = urls.get(0);
						assertNotNull(url.getId());
						assertEquals(testClientId, url.getClientId());
						assertEquals("https://fields-" + ts + ".example.com", url.getUrlPattern());
						assertNotNull(url.getCreatedAt());
						assertNotNull(url.getUpdatedAt());
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

			ClientUrl clientUrl = new ClientUrl();
			clientUrl.setClientId(testClientId);
			clientUrl.setUrlPattern("https://crud-" + ts + ".example.com");
			clientUrl.setAppCode(testAppCode);

			StepVerifier.create(clientUrlDAO.create(clientUrl))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(testClientId, created.getClientId());
						assertEquals("https://crud-" + ts + ".example.com", created.getUrlPattern());
						assertNotNull(created.getCreatedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("delete existing URL returns 1")
		void deleteExisting_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			ULong urlId = insertClientUrl(testClientId, "https://del-" + ts + ".example.com", testAppCode).block();
			assertNotNull(urlId);

			StepVerifier.create(clientUrlDAO.delete(urlId))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete non-existent URL returns 0")
		void deleteNonExistent_ReturnsZero() {
			StepVerifier.create(clientUrlDAO.delete(ULong.valueOf(999999)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}
}
