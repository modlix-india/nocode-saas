package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.SSLCertificateDAO;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.model.SSLCertificateConfiguration;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SSLCertificateDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private SSLCertificateDAO sslCertificateDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private static final String DUMMY_CRT = "-----BEGIN CERTIFICATE-----\nMIIBkTCB+wIJALRiMLAh0ESYMA0G\n-----END CERTIFICATE-----";
	private static final String DUMMY_CRT_CHAIN = "-----BEGIN CERTIFICATE-----\nMIIBkTCB+wIJALRiMLAhCHAIN\n-----END CERTIFICATE-----";
	private static final String DUMMY_CRT_KEY = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0B\n-----END PRIVATE KEY-----";
	private static final String DUMMY_CSR = "-----BEGIN CERTIFICATE REQUEST-----\nMIIBkTCB+wIJCSR\n-----END CERTIFICATE REQUEST-----";

	private ULong testClientId;
	private ULong testUrlId;
	private String testAppCode;

	@BeforeEach
	void setUp() {
		setupMockBeans();

		String ts = String.valueOf(System.currentTimeMillis());
		String clientCode = "SC" + ts.substring(ts.length() - 6);

		testClientId = insertTestClient(clientCode, "SSL Test Client " + ts, "BUS").block();

		ULong appId = insertTestApp(SYSTEM_CLIENT_ID, "ssl" + ts.substring(ts.length() - 8), "SSL Test App").block();
		testAppCode = databaseClient.sql("SELECT APP_CODE FROM security_app WHERE ID = :appId")
				.bind("appId", appId.longValue())
				.map(row -> row.get("APP_CODE", String.class))
				.one()
				.block();

		testUrlId = insertClientUrl(testClientId, "https://ssl-test-" + ts + ".example.com", testAppCode).block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient
						.sql("DELETE FROM security_ssl_certificate WHERE URL_ID IN (SELECT ID FROM security_client_url WHERE CLIENT_ID = :clientId)")
						.bind("clientId", testClientId.longValue()).then())
				.then(databaseClient.sql("DELETE FROM security_client_url WHERE CLIENT_ID = :clientId")
						.bind("clientId", testClientId.longValue()).then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID = :clientId")
						.bind("clientId", testClientId.longValue()).then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper methods ---

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

	private Mono<ULong> insertSSLCert(ULong urlId, String domains, boolean current) {
		return insertSSLCertFull(urlId, DUMMY_CRT, DUMMY_CRT_CHAIN, DUMMY_CRT_KEY, DUMMY_CSR,
				domains, "Test Org", LocalDateTime.now().plusDays(90), "Lets Encrypt",
				current, LocalDateTime.now().plusDays(365));
	}

	private Mono<ULong> insertSSLCertFull(ULong urlId, String crt, String crtChain, String crtKey,
			String csr, String domains, String organization, LocalDateTime expiryDate, String issuer,
			boolean current, LocalDateTime autoRenewTill) {

		var spec = databaseClient.sql(
				"INSERT INTO security_ssl_certificate (URL_ID, CRT, CRT_CHAIN, CRT_KEY, CSR, DOMAINS, ORGANIZATION, EXPIRY_DATE, ISSUER, `CURRENT`, AUTO_RENEW_TILL) "
						+ "VALUES (:urlId, :crt, :crtChain, :crtKey, :csr, :domains, :org, :expiryDate, :issuer, :current, :autoRenewTill)")
				.bind("urlId", urlId.longValue())
				.bind("crt", crt)
				.bind("crtChain", crtChain)
				.bind("crtKey", crtKey)
				.bind("csr", csr)
				.bind("domains", domains)
				.bind("org", organization)
				.bind("expiryDate", expiryDate)
				.bind("issuer", issuer)
				.bind("current", current ? 1 : 0);

		spec = autoRenewTill != null ? spec.bind("autoRenewTill", autoRenewTill)
				: spec.bindNull("autoRenewTill", LocalDateTime.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// --- Test Classes ---

	@Nested
	@DisplayName("create() - DTO-based creation")
	class CreateTests {

		@Test
		@DisplayName("Should create SSL certificate with all fields")
		void createWithAllFields() {
			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("test-create.example.com")
					.setOrganization("Test Org Create")
					.setExpiryDate(LocalDateTime.now().plusDays(90))
					.setIssuer("Lets Encrypt")
					.setCurrent(true)
					.setAutoRenewTill(LocalDateTime.now().plusDays(365));

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(testUrlId, created.getUrlId());
						assertEquals(DUMMY_CRT, created.getCrt());
						assertEquals(DUMMY_CRT_CHAIN, created.getCrtChain());
						assertEquals(DUMMY_CRT_KEY, created.getCrtKey());
						assertEquals(DUMMY_CSR, created.getCsr());
						assertEquals("test-create.example.com", created.getDomains());
						assertEquals("Test Org Create", created.getOrganization());
						assertEquals("Lets Encrypt", created.getIssuer());
						assertTrue(created.getCurrent());
						assertNotNull(created.getExpiryDate());
						assertNotNull(created.getAutoRenewTill());
						assertNotNull(created.getCreatedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should create SSL certificate without auto renew till")
		void createWithoutAutoRenewTill() {
			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("test-no-renew.example.com")
					.setOrganization("Test Org No Renew")
					.setExpiryDate(LocalDateTime.now().plusDays(30))
					.setIssuer("Custom Issuer")
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals("test-no-renew.example.com", created.getDomains());
						assertEquals("Custom Issuer", created.getIssuer());
						assertNull(created.getAutoRenewTill());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should create non-current certificate")
		void createNonCurrentCertificate() {
			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("test-noncurrent.example.com")
					.setOrganization("Test Org")
					.setExpiryDate(LocalDateTime.now().plusDays(90))
					.setIssuer("Lets Encrypt")
					.setCurrent(false);

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertFalse(created.getCurrent());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should create multiple certificates for same URL")
		void createMultipleForSameUrl() {
			SSLCertificate cert1 = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("multi1.example.com")
					.setOrganization("Org 1")
					.setExpiryDate(LocalDateTime.now().plusDays(90))
					.setIssuer("Issuer 1")
					.setCurrent(false);

			SSLCertificate cert2 = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("multi2.example.com")
					.setOrganization("Org 2")
					.setExpiryDate(LocalDateTime.now().plusDays(180))
					.setIssuer("Issuer 2")
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert1).then(sslCertificateDAO.create(cert2)))
					.assertNext(created -> {
						assertEquals("multi2.example.com", created.getDomains());
						assertTrue(created.getCurrent());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readById()")
	class ReadByIdTests {

		@Test
		@DisplayName("Should read existing certificate by ID")
		void readExistingById() {
			ULong certId = insertSSLCert(testUrlId, "readbyid.example.com", true).block();
			assertNotNull(certId);

			StepVerifier.create(sslCertificateDAO.readById(certId))
					.assertNext(cert -> {
						assertEquals(certId, cert.getId());
						assertEquals(testUrlId, cert.getUrlId());
						assertEquals(DUMMY_CRT, cert.getCrt());
						assertEquals(DUMMY_CRT_CHAIN, cert.getCrtChain());
						assertEquals(DUMMY_CRT_KEY, cert.getCrtKey());
						assertEquals(DUMMY_CSR, cert.getCsr());
						assertEquals("readbyid.example.com", cert.getDomains());
						assertEquals("Test Org", cert.getOrganization());
						assertEquals("Lets Encrypt", cert.getIssuer());
						assertTrue(cert.getCurrent());
						assertNotNull(cert.getExpiryDate());
						assertNotNull(cert.getAutoRenewTill());
						assertNotNull(cert.getCreatedAt());
						assertNotNull(cert.getUpdatedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should throw error for non-existent ID")
		void readNonExistentById() {
			StepVerifier.create(sslCertificateDAO.readById(ULong.valueOf(999999)))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("Should read non-current certificate by ID")
		void readNonCurrentById() {
			ULong certId = insertSSLCert(testUrlId, "readnoncurrent.example.com", false).block();
			assertNotNull(certId);

			StepVerifier.create(sslCertificateDAO.readById(certId))
					.assertNext(cert -> {
						assertEquals(certId, cert.getId());
						assertFalse(cert.getCurrent());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		@DisplayName("Should delete existing certificate")
		void deleteExisting() {
			ULong certId = insertSSLCert(testUrlId, "delete-test.example.com", true).block();
			assertNotNull(certId);

			StepVerifier.create(sslCertificateDAO.delete(certId))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();

			// readById throws error for non-existent (deleted) records
			StepVerifier.create(sslCertificateDAO.readById(certId))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("Should return 0 when deleting non-existent certificate")
		void deleteNonExistent() {
			StepVerifier.create(sslCertificateDAO.delete(ULong.valueOf(999999)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("Should only delete specified certificate, not others for same URL")
		void deleteOnlySpecified() {
			ULong certId1 = insertSSLCert(testUrlId, "delete-keep.example.com", false).block();
			ULong certId2 = insertSSLCert(testUrlId, "delete-remove.example.com", true).block();
			assertNotNull(certId1);
			assertNotNull(certId2);

			StepVerifier.create(sslCertificateDAO.delete(certId2))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();

			StepVerifier.create(sslCertificateDAO.readById(certId1))
					.assertNext(cert -> assertEquals(certId1, cert.getId()))
					.verifyComplete();

			// readById throws error for non-existent (deleted) records
			StepVerifier.create(sslCertificateDAO.readById(certId2))
					.expectError()
					.verify();
		}
	}

	@Nested
	@DisplayName("readAllCertificates()")
	class ReadAllCertificatesTests {

		@Test
		@DisplayName("Should return current certificate with URL and client info")
		void readAllReturnsCurrentCerts() {
			insertSSLCert(testUrlId, "readall-current.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						assertNotNull(configs);
						assertFalse(configs.isEmpty());

						SSLCertificateConfiguration ourConfig = configs.stream()
								.filter(c -> c.getPrivateKey() != null
										&& c.getPrivateKey().equals(DUMMY_CRT_KEY)
										&& c.getUrl() != null
										&& c.getUrl().contains("ssl-test-"))
								.findFirst()
								.orElse(null);

						assertNotNull(ourConfig, "Should find our test certificate in the results");
						assertNotNull(ourConfig.getClientCode());
						assertNotNull(ourConfig.getAppCode());
						assertNotNull(ourConfig.getCertificate());
						assertEquals(DUMMY_CRT_KEY, ourConfig.getPrivateKey());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should include URLs without certificates (left join)")
		void readAllIncludesUrlsWithoutCerts() {
			// testUrlId has no cert inserted - left join should still include the URL row
			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						assertNotNull(configs);
						boolean hasOurUrl = configs.stream()
								.anyMatch(c -> c.getUrl() != null && c.getUrl().contains("ssl-test-"));
						assertTrue(hasOurUrl,
								"URLs without certificates should appear due to left join with IS NULL condition");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should concatenate CRT and CRT_CHAIN in certificate field")
		void readAllConcatenatesCrtAndChain() {
			String crt = "-----BEGIN CERTIFICATE-----\nCERT_DATA\n-----END CERTIFICATE-----";
			String chain = "-----BEGIN CERTIFICATE-----\nCHAIN_DATA\n-----END CERTIFICATE-----";

			insertSSLCertFull(testUrlId, crt, chain, DUMMY_CRT_KEY, DUMMY_CSR,
					"concat.example.com", "Test Org", LocalDateTime.now().plusDays(90),
					"Lets Encrypt", true, null).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						SSLCertificateConfiguration ourConfig = configs.stream()
								.filter(c -> c.getUrl() != null && c.getUrl().contains("ssl-test-")
										&& c.getCertificate() != null
										&& c.getCertificate().contains("CERT_DATA"))
								.findFirst()
								.orElse(null);

						assertNotNull(ourConfig, "Should find test certificate");
						assertTrue(ourConfig.getCertificate().contains("CERT_DATA"));
						assertTrue(ourConfig.getCertificate().contains("CHAIN_DATA"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should strip protocol and port from URL pattern")
		void readAllStripsProtocolAndPort() {
			String ts = String.valueOf(System.currentTimeMillis());
			ULong urlWithPort = insertClientUrl(testClientId,
					"https://porttest-" + ts + ".example.com:8443", testAppCode).block();
			assertNotNull(urlWithPort);

			insertSSLCert(urlWithPort, "porttest.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						SSLCertificateConfiguration ourConfig = configs.stream()
								.filter(c -> c.getUrl() != null && c.getUrl().contains("porttest-"))
								.findFirst()
								.orElse(null);

						assertNotNull(ourConfig, "Should find port test certificate");
						assertFalse(ourConfig.getUrl().contains("https://"),
								"Protocol should be stripped from URL");
						assertFalse(ourConfig.getUrl().contains(":8443"),
								"Port should be stripped from URL");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should populate client code and app code in result")
		void readAllPopulatesClientAndAppCode() {
			insertSSLCert(testUrlId, "codes-test.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						SSLCertificateConfiguration ourConfig = configs.stream()
								.filter(c -> c.getUrl() != null && c.getUrl().contains("ssl-test-")
										&& c.getPrivateKey() != null
										&& c.getPrivateKey().equals(DUMMY_CRT_KEY))
								.findFirst()
								.orElse(null);

						assertNotNull(ourConfig, "Should find our test certificate");
						assertNotNull(ourConfig.getClientCode(), "Client code should not be null");
						assertTrue(ourConfig.getClientCode().startsWith("SC"),
								"Client code should match our test client code prefix");
						assertNotNull(ourConfig.getAppCode(), "App code should not be null");
						assertEquals(testAppCode, ourConfig.getAppCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLastUpdated()")
	class GetLastUpdatedTests {

		@Test
		@DisplayName("Should return non-empty string when certificates exist")
		void getLastUpdatedWithCerts() {
			insertSSLCert(testUrlId, "lastupdated.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.getLastUpdated())
					.assertNext(lastUpdated -> {
						assertNotNull(lastUpdated);
						assertFalse(lastUpdated.isEmpty());
						assertDoesNotThrow(() -> Long.parseLong(lastUpdated),
								"Last updated should be a parseable epoch second");
						long epoch = Long.parseLong(lastUpdated);
						assertTrue(epoch > 0, "Epoch should be positive");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should return timestamp even when only URLs exist (no certs)")
		void getLastUpdatedUrlOnly() {
			// We have testUrlId from setup but no certificates
			StepVerifier.create(sslCertificateDAO.getLastUpdated())
					.assertNext(lastUpdated -> {
						assertNotNull(lastUpdated);
						assertFalse(lastUpdated.isEmpty(),
								"Should return URL timestamp when URLs exist but no certificates");
						assertDoesNotThrow(() -> Long.parseLong(lastUpdated));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should return recent timestamp")
		void getLastUpdatedReturnsRecentTimestamp() {
			insertSSLCert(testUrlId, "maxts.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.getLastUpdated())
					.assertNext(lastUpdated -> {
						assertNotNull(lastUpdated);
						long epoch = Long.parseLong(lastUpdated);
						long now = java.time.Instant.now().getEpochSecond();
						assertTrue(Math.abs(now - epoch) < 300,
								"Last updated timestamp should be within 5 minutes of now");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("update() via DTO-based inherited AbstractUpdatableDAO")
	class UpdateTests {

		@Test
		@DisplayName("Should update certificate fields via DTO-based update")
		void updateCertificateFields() {
			ULong certId = insertSSLCert(testUrlId, "update-test.example.com", true).block();
			assertNotNull(certId);

			// Read, modify, then update via DTO
			SSLCertificate existing = sslCertificateDAO.readById(certId).block();
			assertNotNull(existing);
			existing.setDomains("updated-domain.example.com");
			existing.setOrganization("Updated Organization");

			StepVerifier.create(sslCertificateDAO.update(existing))
					.assertNext(updated -> {
						assertEquals(certId, updated.getId());
						assertEquals("updated-domain.example.com", updated.getDomains());
						assertEquals("Updated Organization", updated.getOrganization());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should update CRT content via DTO")
		void updateCrtContent() {
			ULong certId = insertSSLCert(testUrlId, "update-crt.example.com", true).block();
			assertNotNull(certId);

			String newCrt = "-----BEGIN CERTIFICATE-----\nUPDATED_CRT_CONTENT\n-----END CERTIFICATE-----";

			SSLCertificate existing = sslCertificateDAO.readById(certId).block();
			assertNotNull(existing);
			existing.setCrt(newCrt);

			StepVerifier.create(sslCertificateDAO.update(existing))
					.assertNext(updated -> {
						assertEquals(certId, updated.getId());
						assertEquals(newCrt, updated.getCrt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should update current flag via DTO")
		void updateCurrentFlag() {
			ULong certId = insertSSLCert(testUrlId, "update-current.example.com", true).block();
			assertNotNull(certId);

			SSLCertificate existing = sslCertificateDAO.readById(certId).block();
			assertNotNull(existing);
			assertTrue(existing.getCurrent());

			existing.setCurrent(false);

			StepVerifier.create(sslCertificateDAO.update(existing))
					.assertNext(updated -> {
						assertEquals(certId, updated.getId());
						assertFalse(updated.getCurrent());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Edge cases and data integrity")
	class EdgeCaseTests {

		@Test
		@DisplayName("Should handle certificate with long domain list")
		void longDomainList() {
			StringBuilder domains = new StringBuilder();
			for (int i = 0; i < 10; i++) {
				if (i > 0) domains.append(",");
				domains.append("subdomain").append(i).append(".example.com");
			}

			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains(domains.toString())
					.setOrganization("Multi Domain Org")
					.setExpiryDate(LocalDateTime.now().plusDays(90))
					.setIssuer("Lets Encrypt")
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(domains.toString(), created.getDomains());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should preserve expiry date precision")
		void expiryDatePrecision() {
			LocalDateTime expiry = LocalDateTime.now().plusDays(90).truncatedTo(ChronoUnit.SECONDS);

			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("expiry-precision.example.com")
					.setOrganization("Test Org")
					.setExpiryDate(expiry)
					.setIssuer("Lets Encrypt")
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getExpiryDate());
						assertEquals(expiry, created.getExpiryDate());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should handle past expiry date")
		void pastExpiryDate() {
			LocalDateTime pastExpiry = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.SECONDS);

			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("expired.example.com")
					.setOrganization("Expired Org")
					.setExpiryDate(pastExpiry)
					.setIssuer("Lets Encrypt")
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert))
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(pastExpiry, created.getExpiryDate());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should create and read back certificate with special characters in fields")
		void specialCharactersInFields() {
			String specialOrg = "Org & Co. <SSL> \"Cert\" Provider's";
			String specialIssuer = "CN=Let's Encrypt Authority X3, O=Let's Encrypt, C=US";

			SSLCertificate cert = new SSLCertificate()
					.setUrlId(testUrlId)
					.setCrt(DUMMY_CRT)
					.setCrtChain(DUMMY_CRT_CHAIN)
					.setCrtKey(DUMMY_CRT_KEY)
					.setCsr(DUMMY_CSR)
					.setDomains("special.example.com")
					.setOrganization(specialOrg)
					.setExpiryDate(LocalDateTime.now().plusDays(90))
					.setIssuer(specialIssuer)
					.setCurrent(true);

			StepVerifier.create(sslCertificateDAO.create(cert)
					.flatMap(created -> sslCertificateDAO.readById(created.getId())))
					.assertNext(readBack -> {
						assertEquals(specialOrg, readBack.getOrganization());
						assertEquals(specialIssuer, readBack.getIssuer());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should cascade delete when client URL is deleted")
		void cascadeDeleteOnUrlRemoval() {
			String ts = String.valueOf(System.currentTimeMillis());
			ULong cascadeUrlId = insertClientUrl(testClientId,
					"https://cascade-" + ts + ".example.com", testAppCode).block();
			assertNotNull(cascadeUrlId);

			ULong certId = insertSSLCert(cascadeUrlId, "cascade.example.com", true).block();
			assertNotNull(certId);

			StepVerifier.create(sslCertificateDAO.readById(certId))
					.assertNext(cert -> assertNotNull(cert.getId()))
					.verifyComplete();

			databaseClient.sql("DELETE FROM security_client_url WHERE ID = :urlId")
					.bind("urlId", cascadeUrlId.longValue())
					.then()
					.block();

			// readById throws error for non-existent (cascade deleted) records
			StepVerifier.create(sslCertificateDAO.readById(certId))
					.expectError()
					.verify();
		}
	}

	@Nested
	@DisplayName("Multiple URLs and certificates")
	class MultiUrlTests {

		@Test
		@DisplayName("Should return certificates from multiple URLs in readAllCertificates")
		void readAllWithMultipleUrls() {
			String ts = String.valueOf(System.currentTimeMillis());

			ULong urlId1 = insertClientUrl(testClientId, "https://multi1-" + ts + ".example.com",
					testAppCode).block();
			ULong urlId2 = insertClientUrl(testClientId, "https://multi2-" + ts + ".example.com",
					testAppCode).block();
			assertNotNull(urlId1);
			assertNotNull(urlId2);

			insertSSLCert(urlId1, "multi1.example.com", true).block();
			insertSSLCert(urlId2, "multi2.example.com", true).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						long testCertCount = configs.stream()
								.filter(c -> c.getUrl() != null
										&& (c.getUrl().contains("multi1-" + ts)
												|| c.getUrl().contains("multi2-" + ts)))
								.count();
						assertEquals(2, testCertCount,
								"Should find certificates for both test URLs");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("Should only return current cert per URL in readAllCertificates")
		void readAllReturnsOnlyCurrentPerUrl() {
			String ts = String.valueOf(System.currentTimeMillis());

			ULong urlId = insertClientUrl(testClientId, "https://onlycurr-" + ts + ".example.com",
					testAppCode).block();
			assertNotNull(urlId);

			insertSSLCert(urlId, "noncurr1.example.com", false).block();
			insertSSLCert(urlId, "noncurr2.example.com", false).block();
			insertSSLCertFull(urlId,
					"-----BEGIN CERTIFICATE-----\nCURRENT_CERT\n-----END CERTIFICATE-----",
					DUMMY_CRT_CHAIN, "CURRENT_KEY", DUMMY_CSR,
					"onlycurrent.example.com", "Test Org",
					LocalDateTime.now().plusDays(90), "Lets Encrypt", true, null).block();

			StepVerifier.create(sslCertificateDAO.readAllCertificates())
					.assertNext(configs -> {
						List<SSLCertificateConfiguration> ourConfigs = configs.stream()
								.filter(c -> c.getUrl() != null && c.getUrl().contains("onlycurr-" + ts)
										&& c.getPrivateKey() != null)
								.toList();

						assertEquals(1, ourConfigs.size(),
								"Only the current certificate should be returned for a URL");
						assertEquals("CURRENT_KEY", ourConfigs.get(0).getPrivateKey());
					})
					.verifyComplete();
		}
	}
}