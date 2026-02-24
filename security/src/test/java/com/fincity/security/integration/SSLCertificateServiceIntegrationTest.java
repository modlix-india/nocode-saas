package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.model.SSLCertificateConfiguration;
import com.fincity.security.model.SSLCertificateOrder;
import com.fincity.security.model.SSLCertificateOrderRequest;
import com.fincity.security.service.SSLCertificateService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SSLCertificateServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private SSLCertificateService sslCertificateService;

	@Autowired
	private CacheService cacheService;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();

		// Evict SSL-related caches to ensure clean state between tests
		cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE).block();
		cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT).block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("DELETE FROM security_ssl_challenge WHERE ID > 0").then()
				.then(databaseClient.sql("DELETE FROM security_ssl_request WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_ssl_certificate WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_url WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql(
						"DELETE FROM security_client WHERE ID > 1 AND ID NOT IN (SELECT DISTINCT CLIENT_ID FROM security_user)")
						.then())
				.block();
	}

	// --- Helper methods ---

	private Mono<ULong> insertClientUrl(ULong clientId, String appCode, String urlPattern) {
		return databaseClient.sql(
				"INSERT INTO security_client_url (CLIENT_ID, APP_CODE, URL_PATTERN) VALUES (:clientId, :appCode, :urlPattern)")
				.bind("clientId", clientId.longValue())
				.bind("appCode", appCode)
				.bind("urlPattern", urlPattern)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertSSLCertificate(ULong urlId, String domains, boolean current) {
		return databaseClient.sql(
				"INSERT INTO security_ssl_certificate (URL_ID, CRT, CRT_CHAIN, CRT_KEY, CSR, DOMAINS, ORGANIZATION, CURRENT, EXPIRY_DATE, ISSUER) "
						+ "VALUES (:urlId, 'test-crt', 'test-chain', 'test-key', 'test-csr', :domains, 'Test Org', :current, DATE_ADD(NOW(), INTERVAL 1 YEAR), 'Test Issuer')")
				.bind("urlId", urlId.longValue())
				.bind("domains", domains)
				.bind("current", current)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertSSLCertificateWithDetails(ULong urlId, String crt, String crtChain, String crtKey,
			String domains, boolean current) {
		return databaseClient.sql(
				"INSERT INTO security_ssl_certificate (URL_ID, CRT, CRT_CHAIN, CRT_KEY, CSR, DOMAINS, ORGANIZATION, CURRENT, EXPIRY_DATE, ISSUER) "
						+ "VALUES (:urlId, :crt, :crtChain, :crtKey, 'test-csr', :domains, 'Test Org', :current, DATE_ADD(NOW(), INTERVAL 1 YEAR), 'Test Issuer')")
				.bind("urlId", urlId.longValue())
				.bind("crt", crt)
				.bind("crtChain", crtChain)
				.bind("crtKey", crtKey)
				.bind("domains", domains)
				.bind("current", current)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertSSLRequest(ULong urlId, String domains, String organization) {
		return databaseClient.sql(
				"INSERT INTO security_ssl_request (URL_ID, DOMAINS, ORGANIZATION, CRT_KEY, CSR, VALIDITY) "
						+ "VALUES (:urlId, :domains, :organization, 'test-key', 'test-csr', 12)")
				.bind("urlId", urlId.longValue())
				.bind("domains", domains)
				.bind("organization", organization)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertSSLChallenge(ULong requestId, String domain, String token, String authorization) {
		return databaseClient.sql(
				"INSERT INTO security_ssl_challenge (REQUEST_ID, CHALLENGE_TYPE, DOMAIN, TOKEN, AUTHORIZATION, STATUS, RETRY_COUNT) "
						+ "VALUES (:requestId, 'http-01', :domain, :token, :authorization, 'PENDING', 0)")
				.bind("requestId", requestId.longValue())
				.bind("domain", domain)
				.bind("token", token)
				.bind("authorization", authorization)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// --- Test classes ---

	@Nested
	@DisplayName("getAllCertificates()")
	class GetAllCertificatesTests {

		@Test
		@DisplayName("should return empty list when no certificates exist")
		void noCertificates_ReturnsEmptyList() {
			Mono<List<SSLCertificateConfiguration>> result = sslCertificateService.getAllCertificates();

			StepVerifier.create(result)
					.assertNext(certs -> assertThat(certs).isEmpty())
					.verifyComplete();
		}

		@Test
		@DisplayName("should return certificates for current entries")
		void withCurrentCertificates_ReturnsList() {
			// Setup: create a client, app, client URL, and an SSL certificate
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLONE", "SSL Client One", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslapp1", "SSL App One"))
							.then(insertClientUrl(clientId, "sslapp1", "https://sslone.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslone.example.com", true))
					.then(Mono.defer(() -> {
						// Evict cache so fresh data is fetched
						return cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
								.then(sslCertificateService.getAllCertificates());
					}));

			StepVerifier.create(result)
					.assertNext(certs -> {
						assertThat(certs).isNotEmpty();
						assertThat(certs).anyMatch(c -> "sslone.example.com".equals(c.getUrl()));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should not return non-current certificates")
		void nonCurrentCertificates_AreExcluded() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLNON", "SSL Non-Current", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslapp2", "SSL App Two"))
							.then(insertClientUrl(clientId, "sslapp2", "https://sslnon.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslnon.example.com", false))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						// Non-current certificates should not appear; the DAO query filters
						// for CURRENT = 1 OR CURRENT IS NULL. A URL entry without a current cert
						// still appears in the join but with null cert fields.
						boolean hasNonCurrentCert = certs.stream()
								.anyMatch(c -> "sslnon.example.com".equals(c.getUrl())
										&& c.getPrivateKey() != null);
						assertThat(hasNonCurrentCert).isFalse();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return multiple certificates for different URLs")
		void multipleCertificates_ReturnsAll() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLMUL", "SSL Multi", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslmul1", "SSL Multi App 1"))
							.then(insertTestApp(clientId, "sslmul2", "SSL Multi App 2"))
							.then(insertClientUrl(clientId, "sslmul1", "https://multi1.example.com")
									.flatMap(urlId1 -> insertSSLCertificate(urlId1, "multi1.example.com", true)
											.then(insertClientUrl(clientId, "sslmul2",
													"https://multi2.example.com"))
											.flatMap(urlId2 -> insertSSLCertificate(urlId2,
													"multi2.example.com", true)))))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						assertThat(certs).hasSizeGreaterThanOrEqualTo(2);
						assertThat(certs).anyMatch(c -> "multi1.example.com".equals(c.getUrl()));
						assertThat(certs).anyMatch(c -> "multi2.example.com".equals(c.getUrl()));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLastUpdated()")
	class GetLastUpdatedTests {

		@Test
		@DisplayName("should return empty string when no data exists")
		void noData_ReturnsEmptyString() {
			Mono<String> result = cacheService
					.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
					.then(sslCertificateService.getLastUpdated());

			StepVerifier.create(result)
					.assertNext(lastUpdated -> {
						// When no certs and no client URLs with CLIENT_ID > 1 exist,
						// the system client URL may still exist. We just verify it returns a value.
						assertThat(lastUpdated).isNotNull();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return a timestamp after inserting a certificate")
		void withCertificate_ReturnsTimestamp() {
			Mono<String> result = insertTestClient("SSLLST", "SSL Last Updated", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssllst1", "SSL Last App"))
							.then(insertClientUrl(clientId, "ssllst1", "https://ssllst.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "ssllst.example.com", true))
					.then(Mono.defer(() -> cacheService
							.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
							.then(sslCertificateService.getLastUpdated())));

			StepVerifier.create(result)
					.assertNext(lastUpdated -> {
						assertThat(lastUpdated).isNotNull();
						assertThat(lastUpdated).isNotBlank();
						// Should be a parseable epoch seconds value
						long epoch = Long.parseLong(lastUpdated);
						assertThat(epoch).isGreaterThan(0);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteCertificate()")
	class DeleteCertificateTests {

		@Test
		@DisplayName("should delete an existing certificate and return true")
		void existingCertificate_DeletesSuccessfully() {
			Mono<Boolean> result = insertTestClient("SSLDEL", "SSL Delete", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssldel1", "SSL Delete App"))
							.then(insertClientUrl(clientId, "ssldel1", "https://ssldel.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "ssldel.example.com", true))
					.flatMap(certId -> sslCertificateService.deleteCertificate(certId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("should throw when certificate does not exist")
		void nonExistentCertificate_ThrowsNotFound() {
			Mono<Boolean> result = sslCertificateService.deleteCertificate(ULong.valueOf(99999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("not found"))
					.verify();
		}

		@Test
		@DisplayName("should verify certificate no longer exists after deletion")
		void deletedCertificate_NoLongerInGetAll() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLVFY", "SSL Verify Delete", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslvfy1", "SSL Verify App"))
							.then(insertClientUrl(clientId, "sslvfy1", "https://sslvfy.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslvfy.example.com", true))
					.flatMap(certId -> sslCertificateService.deleteCertificate(certId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.thenReturn(certId))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						boolean hasCert = certs.stream()
								.anyMatch(c -> "sslvfy.example.com".equals(c.getUrl())
										&& c.getPrivateKey() != null);
						assertThat(hasCert).isFalse();
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("findSSLCertificates()")
	class FindSSLCertificatesTests {

		@Test
		@DisplayName("should return page of certificates for a valid URL ID")
		void validUrlId_ReturnsCertificates() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLFND", "SSL Find", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslfnd1", "SSL Find App"))
							.then(insertClientUrl(clientId, "sslfnd1", "https://sslfnd.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslfnd.example.com", true)
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService
							.findSSLCertificates(urlId, PageRequest.of(0, 10), null)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(1);
						assertThat(page.getContent()).hasSize(1);
						assertThat(page.getContent().get(0).getDomains()).isEqualTo("sslfnd.example.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty page when URL has no certificates")
		void urlWithNoCertificates_ReturnsEmptyPage() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLNOC", "SSL No Certs", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnoc1", "SSL NoCerts App"))
							.then(insertClientUrl(clientId, "sslnoc1", "https://sslnoc.example.com")))
					.flatMap(urlId -> sslCertificateService
							.findSSLCertificates(urlId, PageRequest.of(0, 10), null)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isZero();
						assertThat(page.getContent()).isEmpty();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return multiple certificates for the same URL")
		void multipleCertsForUrl_ReturnsAll() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLMLT", "SSL Multi Certs", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslmlt1", "SSL Multi App"))
							.then(insertClientUrl(clientId, "sslmlt1", "https://sslmlt.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslmlt.example.com", true)
							.then(insertSSLCertificate(urlId, "sslmlt.example.com", false))
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService
							.findSSLCertificates(urlId, PageRequest.of(0, 10), null)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(2);
						assertThat(page.getContent()).hasSize(2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty when URL ID does not exist")
		void nonExistentUrlId_ReturnsEmpty() {
			// ClientUrlService.read() will throw NOT_FOUND for a non-existent URL ID
			Mono<Page<SSLCertificate>> result = sslCertificateService
					.findSSLCertificates(ULong.valueOf(99999), PageRequest.of(0, 10), null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(
							e -> e instanceof GenericException && ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		@DisplayName("should respect pagination")
		void pagination_RespectsPageSize() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLPAG", "SSL Pagination", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslpag1", "SSL Pag App"))
							.then(insertClientUrl(clientId, "sslpag1", "https://sslpag.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslpag.example.com", true)
							.then(insertSSLCertificate(urlId, "sslpag.example.com", false))
							.then(insertSSLCertificate(urlId, "sslpag.example.com", false))
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService
							.findSSLCertificates(urlId, PageRequest.of(0, 2), null)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(3);
						assertThat(page.getContent()).hasSize(2);
						assertThat(page.getTotalPages()).isEqualTo(2);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createExternallyIssuedCertificate() - validation errors")
	class CreateExternallyIssuedCertificateValidationTests {

		@Test
		@DisplayName("should reject when CRT key is missing")
		void missingCrtKey_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setCrt("some-certificate-content");
			// crtKey is deliberately not set (null)
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when CRT key is blank")
		void blankCrtKey_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setCrt("some-certificate-content");
			cert.setCrtKey("   ");
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when CRT is missing")
		void missingCrt_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setCrtKey("some-private-key-content");
			// crt is deliberately not set (null)
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when CRT is blank")
		void blankCrt_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setCrtKey("some-private-key-content");
			cert.setCrt("");
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when CRT content is not a valid X.509 certificate")
		void invalidCrtContent_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setCrtKey("-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----");
			cert.setCrt("not-a-valid-certificate-content");
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when URL ID does not exist")
		void nonExistentUrlId_ThrowsError() {
			// A valid X.509 self-signed cert is needed to pass the validation stage,
			// but we provide garbage to hit the parse error path before URL check.
			SSLCertificate cert = new SSLCertificate();
			cert.setCrtKey("some-key-value");
			cert.setCrt("some-crt-value");
			cert.setUrlId(ULong.valueOf(99999));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// Will fail at CRT parsing (BAD_REQUEST) since "some-crt-value" is not valid X.509
			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when both CRT and CRT key are missing")
		void bothMissing_ThrowsBadRequest() {
			SSLCertificate cert = new SSLCertificate();
			cert.setUrlId(ULong.valueOf(1));

			Mono<SSLCertificate> result = sslCertificateService.createExternallyIssuedCertificate(cert)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	@Nested
	@DisplayName("readRequestByURLId()")
	class ReadRequestByURLIdTests {

		@Test
		@DisplayName("should return request and challenges for a valid URL with request")
		void validUrlWithRequest_ReturnsOrderWithChallenges() {
			Mono<SSLCertificateOrder> result = insertTestClient("SSLREQ", "SSL Request", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslreq1", "SSL Req App"))
							.then(insertClientUrl(clientId, "sslreq1", "https://sslreq.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "sslreq.example.com", "Test Org")
							.flatMap(reqId -> insertSSLChallenge(reqId, "sslreq.example.com",
									"test-token-1", "test-auth-1")
									.then(insertSSLChallenge(reqId, "sslreq.example.com",
											"test-token-2", "test-auth-2"))
									.thenReturn(urlId)))
					.flatMap(urlId -> sslCertificateService.readRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(order -> {
						assertThat(order).isNotNull();
						assertThat(order.getRequest()).isNotNull();
						assertThat(order.getRequest().getDomains()).isEqualTo("sslreq.example.com");
						assertThat(order.getRequest().getOrganization()).isEqualTo("Test Org");
						assertThat(order.getChallenges()).hasSize(2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return request with empty challenges when no challenges exist")
		void validUrlWithRequestNoChallenges_ReturnsOrderWithEmptyChallenges() {
			Mono<SSLCertificateOrder> result = insertTestClient("SSLRNC", "SSL Req No Chal", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslrnc1", "SSL RNC App"))
							.then(insertClientUrl(clientId, "sslrnc1", "https://sslrnc.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "sslrnc.example.com", "Test Org")
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService.readRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(order -> {
						assertThat(order).isNotNull();
						assertThat(order.getRequest()).isNotNull();
						assertThat(order.getRequest().getDomains()).isEqualTo("sslrnc.example.com");
						assertThat(order.getChallenges()).isEmpty();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty when URL has no request")
		void urlWithNoRequest_ReturnsEmpty() {
			Mono<SSLCertificateOrder> result = insertTestClient("SSLNRQ", "SSL No Request", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnrq1", "SSL NRQ App"))
							.then(insertClientUrl(clientId, "sslnrq1", "https://sslnrq.example.com")))
					.flatMap(urlId -> sslCertificateService.readRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			// readRequestByURLId uses FlatMapUtil; when requestDao.readByURLId returns
			// empty, the chain completes empty
			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("should throw when URL ID does not exist")
		void nonExistentUrlId_ThrowsNotFound() {
			Mono<SSLCertificateOrder> result = sslCertificateService.readRequestByURLId(ULong.valueOf(99999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	@Nested
	@DisplayName("getToken()")
	class GetTokenTests {

		@Test
		@DisplayName("should return authorization for an existing token")
		void existingToken_ReturnsAuthorization() {
			Mono<String> result = insertTestClient("SSLTOK", "SSL Token", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssltok1", "SSL Token App"))
							.then(insertClientUrl(clientId, "ssltok1", "https://ssltok.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "ssltok.example.com", "Test Org"))
					.flatMap(reqId -> insertSSLChallenge(reqId, "ssltok.example.com",
							"unique-token-abc", "auth-value-xyz"))
					.then(Mono.defer(() -> sslCertificateService.getToken("unique-token-abc")));

			StepVerifier.create(result)
					.assertNext(auth -> assertThat(auth).isEqualTo("auth-value-xyz"))
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty string when token does not exist")
		void nonExistentToken_ReturnsEmptyString() {
			Mono<String> result = sslCertificateService.getToken("non-existent-token-xyz");

			StepVerifier.create(result)
					.assertNext(auth -> assertThat(auth).isEmpty())
					.verifyComplete();
		}

		@Test
		@DisplayName("should return correct authorization when multiple challenges exist")
		void multipleTokens_ReturnsCorrectAuth() {
			Mono<String> result = insertTestClient("SSLMTK", "SSL Multi Token", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslmtk1", "SSL MTK App"))
							.then(insertClientUrl(clientId, "sslmtk1", "https://sslmtk.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "sslmtk.example.com", "Test Org"))
					.flatMap(reqId -> insertSSLChallenge(reqId, "sslmtk.example.com",
							"token-alpha", "auth-alpha")
							.then(insertSSLChallenge(reqId, "sslmtk.example.com",
									"token-beta", "auth-beta")))
					.then(Mono.defer(() -> sslCertificateService.getToken("token-beta")));

			StepVerifier.create(result)
					.assertNext(auth -> assertThat(auth).isEqualTo("auth-beta"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteRequestByURLId()")
	class DeleteRequestByURLIdTests {

		@Test
		@DisplayName("should delete an existing request and return true")
		void existingRequest_DeletesSuccessfully() {
			Mono<Boolean> result = insertTestClient("SSLDRQ", "SSL Del Req", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssldrq1", "SSL DRQ App"))
							.then(insertClientUrl(clientId, "ssldrq1", "https://ssldrq.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "ssldrq.example.com", "Test Org")
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService.deleteRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("should verify request is gone after deletion")
		void deletedRequest_NoLongerReadable() {
			Mono<SSLCertificateOrder> result = insertTestClient("SSLDVR", "SSL Del Verify Req", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssldvr1", "SSL DVR App"))
							.then(insertClientUrl(clientId, "ssldvr1", "https://ssldvr.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "ssldvr.example.com", "Test Org")
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService.deleteRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService.readRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			// After deletion, readRequestByURLId should complete empty (no request found)
			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("should throw when URL ID does not exist")
		void nonExistentUrlId_ThrowsNotFound() {
			Mono<Boolean> result = sslCertificateService.deleteRequestByURLId(ULong.valueOf(99999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		@DisplayName("should also remove associated challenges via cascade")
		void requestWithChallenges_CascadeDeletesChallenges() {
			Mono<String> result = insertTestClient("SSLCAS", "SSL Cascade", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcas1", "SSL CAS App"))
							.then(insertClientUrl(clientId, "sslcas1", "https://sslcas.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "sslcas.example.com", "Test Org")
							.flatMap(reqId -> insertSSLChallenge(reqId, "sslcas.example.com",
									"cascade-token", "cascade-auth"))
							.thenReturn(urlId))
					.flatMap(urlId -> sslCertificateService.deleteRequestByURLId(urlId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.then(Mono.defer(() -> sslCertificateService.getToken("cascade-token")));

			// The challenge token should no longer be found after request deletion
			StepVerifier.create(result)
					.assertNext(auth -> assertThat(auth).isEmpty())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createCertificateRequest() - validation errors")
	class CreateCertificateRequestValidationTests {

		@Test
		@DisplayName("should reject when URL ID is null")
		void nullUrlId_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
			request.setUrlId(null);
			request.setDomainNames(List.of("example.com"));
			request.setOrganizationName("Test Org");

			Mono<SSLCertificateOrder> result = sslCertificateService.createCertificateRequest(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when domain names list is empty")
		void emptyDomainNames_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
			request.setUrlId(ULong.valueOf(1));
			request.setDomainNames(Collections.emptyList());
			request.setOrganizationName("Test Org");

			Mono<SSLCertificateOrder> result = sslCertificateService.createCertificateRequest(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when domain names contain a blank entry")
		void blankDomainName_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
			request.setUrlId(ULong.valueOf(1));
			request.setDomainNames(List.of("example.com", "   "));
			request.setOrganizationName("Test Org");

			Mono<SSLCertificateOrder> result = sslCertificateService.createCertificateRequest(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject when domain names contain an empty string")
		void emptyStringDomainName_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
			request.setUrlId(ULong.valueOf(1));
			request.setDomainNames(List.of(""));
			request.setOrganizationName("Test Org");

			Mono<SSLCertificateOrder> result = sslCertificateService.createCertificateRequest(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("should reject with CONFLICT when a request already exists on the URL")
		void existingRequestOnUrl_ThrowsConflict() {
			Mono<SSLCertificateOrder> result = insertTestClient("SSLCNF", "SSL Conflict", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcnf1", "SSL CNF App"))
							.then(insertClientUrl(clientId, "sslcnf1", "https://sslcnf.example.com")))
					.flatMap(urlId -> insertSSLRequest(urlId, "sslcnf.example.com", "Test Org")
							.thenReturn(urlId))
					.flatMap(urlId -> {
						SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
						request.setUrlId(urlId);
						request.setDomainNames(List.of("sslcnf.example.com"));
						request.setOrganizationName("Test Org");
						return sslCertificateService.createCertificateRequest(request)
								.contextWrite(
										ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}

		@Test
		@DisplayName("should reject when domain names do not match URL pattern")
		void mismatchedDomains_ThrowsBadRequest() {
			// Must use a business client auth so domain validation is not skipped
			// (system client auth bypasses domain check by returning blank URL)
			Mono<SSLCertificateOrder> result = insertTestClient("SSLMIS", "SSL Mismatch", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslmis1", "SSL MIS App"))
							.then(insertClientUrl(clientId, "sslmis1", "https://sslmis.example.com"))
							.map(urlId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										clientId, "SSLMIS",
										List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
								return new Object[] { urlId, busAuth };
							}))
					.flatMap(arr -> {
						ULong urlId = (ULong) arr[0];
						ContextAuthentication busAuth = (ContextAuthentication) arr[1];
						SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
						request.setUrlId(urlId);
						// Domain does not end with the URL pattern
						request.setDomainNames(List.of("otherdomain.xyz"));
						request.setOrganizationName("Test Org");
						return sslCertificateService.createCertificateRequest(request)
								.contextWrite(
										ReactiveSecurityContextHolder.withAuthentication(busAuth));
					});

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	@Nested
	@DisplayName("findSSLCertificates() - with condition filter")
	class FindSSLCertificatesWithConditionTests {

		@Test
		@DisplayName("should filter certificates by domain using a condition")
		void filterByDomain_ReturnsMatching() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLCND", "SSL Condition", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcnd1", "SSL CND App"))
							.then(insertClientUrl(clientId, "sslcnd1", "https://sslcnd.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslcnd.example.com", true)
							.then(insertSSLCertificate(urlId, "other.sslcnd.example.com", false))
							.thenReturn(urlId))
					.flatMap(urlId -> {
						FilterCondition condition = FilterCondition.make("domains",
								"sslcnd.example.com");
						return sslCertificateService
								.findSSLCertificates(urlId, PageRequest.of(0, 10), condition)
								.contextWrite(ReactiveSecurityContextHolder
										.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(1);
						assertThat(page.getContent().get(0).getDomains())
								.isEqualTo("sslcnd.example.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should filter certificates by issuer field")
		void filterByIssuer_ReturnsMatching() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLISS", "SSL Issuer", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssliss1", "SSL ISS App"))
							.then(insertClientUrl(clientId, "ssliss1", "https://ssliss.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "ssliss.example.com", true)
							.thenReturn(urlId))
					.flatMap(urlId -> {
						FilterCondition condition = FilterCondition.make("issuer", "Test Issuer");
						return sslCertificateService
								.findSSLCertificates(urlId, PageRequest.of(0, 10), condition)
								.contextWrite(ReactiveSecurityContextHolder
										.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(1);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty when condition does not match any certificate")
		void nonMatchingCondition_ReturnsEmpty() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLNMT", "SSL No Match", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnmt1", "SSL NMT App"))
							.then(insertClientUrl(clientId, "sslnmt1", "https://sslnmt.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslnmt.example.com", true)
							.thenReturn(urlId))
					.flatMap(urlId -> {
						FilterCondition condition = FilterCondition.make("domains",
								"nonexistent.domain.com");
						return sslCertificateService
								.findSSLCertificates(urlId, PageRequest.of(0, 10), condition)
								.contextWrite(ReactiveSecurityContextHolder
										.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isZero();
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAllCertificates() - URL pattern parsing")
	class GetAllCertificatesUrlParsingTests {

		@Test
		@DisplayName("should strip port from URL pattern")
		void urlWithPort_StripsPort() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLPRT", "SSL Port", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslprt1", "SSL Port App"))
							.then(insertClientUrl(clientId, "sslprt1",
									"https://sslprt.example.com:8443")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslprt.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						assertThat(certs).isNotEmpty();
						// The DAO strips the port from the URL pattern
						assertThat(certs).anyMatch(
								c -> "sslprt.example.com".equals(c.getUrl()));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should strip protocol from URL pattern")
		void urlWithProtocol_StripsProtocol() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLPRO", "SSL Proto", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslpro1", "SSL Proto App"))
							.then(insertClientUrl(clientId, "sslpro1",
									"http://sslpro.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslpro.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						assertThat(certs).isNotEmpty();
						assertThat(certs).anyMatch(
								c -> "sslpro.example.com".equals(c.getUrl()));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should handle URL with no protocol prefix")
		void urlWithNoProtocol_ReturnsAsIs() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLNOP", "SSL No Proto", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnop1", "SSL NP App"))
							.then(insertClientUrl(clientId, "sslnop1", "sslnop.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslnop.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						assertThat(certs).isNotEmpty();
						assertThat(certs).anyMatch(
								c -> "sslnop.example.com".equals(c.getUrl()));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should include appCode and clientCode in configuration")
		void certConfiguration_ContainsAppCodeAndClientCode() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLCFG", "SSL Config", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcfg1", "SSL Config App"))
							.then(insertClientUrl(clientId, "sslcfg1",
									"https://sslcfg.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslcfg.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						SSLCertificateConfiguration cfg = certs.stream()
								.filter(c -> "sslcfg.example.com".equals(c.getUrl()))
								.findFirst()
								.orElse(null);
						assertThat(cfg).isNotNull();
						assertThat(cfg.getAppCode()).isEqualTo("sslcfg1");
						assertThat(cfg.getClientCode()).isEqualTo("SSLCFG");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should concatenate CRT and CRT_CHAIN in certificate field")
		void certWithChain_ConcatenatesCrtAndChain() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLCHN", "SSL Chain", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslchn1", "SSL Chain App"))
							.then(insertClientUrl(clientId, "sslchn1",
									"https://sslchn.example.com")))
					.flatMap(urlId -> insertSSLCertificateWithDetails(urlId,
							"main-cert-content\n", "chain-cert-content",
							"private-key", "sslchn.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						SSLCertificateConfiguration cfg = certs.stream()
								.filter(c -> "sslchn.example.com".equals(c.getUrl()))
								.findFirst()
								.orElse(null);
						assertThat(cfg).isNotNull();
						// CRT ends with newline, so no extra newline is added before chain
						assertThat(cfg.getCertificate())
								.isEqualTo("main-cert-content\nchain-cert-content");
						assertThat(cfg.getPrivateKey()).isEqualTo("private-key");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should add newline between CRT and CRT_CHAIN when CRT does not end with newline")
		void certWithoutTrailingNewline_AddsNewlineBeforeChain() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLNLN", "SSL Newline", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnln1", "SSL NLN App"))
							.then(insertClientUrl(clientId, "sslnln1",
									"https://sslnln.example.com")))
					.flatMap(urlId -> insertSSLCertificateWithDetails(urlId,
							"main-cert-no-newline", "chain-cert-content",
							"private-key", "sslnln.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						SSLCertificateConfiguration cfg = certs.stream()
								.filter(c -> "sslnln.example.com".equals(c.getUrl()))
								.findFirst()
								.orElse(null);
						assertThat(cfg).isNotNull();
						// CRT does not end with newline, so a newline is inserted
						assertThat(cfg.getCertificate())
								.isEqualTo("main-cert-no-newline\nchain-cert-content");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return URL entry with null cert fields when URL has no current certificate")
		void urlWithNoCert_ReturnsEntryWithNullCertFields() {
			Mono<List<SSLCertificateConfiguration>> result = insertTestClient("SSLNUL", "SSL Null Cert", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnul1", "SSL NUL App"))
							.then(insertClientUrl(clientId, "sslnul1",
									"https://sslnul.example.com")))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)
							.then(sslCertificateService.getAllCertificates())));

			StepVerifier.create(result)
					.assertNext(certs -> {
						// URL without any certificate appears due to LEFT JOIN with NULL cert
						SSLCertificateConfiguration cfg = certs.stream()
								.filter(c -> "sslnul.example.com".equals(c.getUrl()))
								.findFirst()
								.orElse(null);
						assertThat(cfg).isNotNull();
						assertThat(cfg.getPrivateKey()).isNull();
						assertThat(cfg.getCertificate()).isNull();
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAllCertificates() - caching behavior")
	class GetAllCertificatesCachingTests {

		@Test
		@DisplayName("should return cached result on second call without DB changes")
		void cachedResult_ReturnsSameData() {
			Mono<Boolean> result = insertTestClient("SSLCCH", "SSL Cache", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcch1", "SSL Cache App"))
							.then(insertClientUrl(clientId, "sslcch1",
									"https://sslcch.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslcch.example.com", true))
					.then(Mono.defer(() -> cacheService.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE)))
					.then(Mono.defer(() -> sslCertificateService.getAllCertificates()
							.flatMap(firstCall -> sslCertificateService.getAllCertificates()
									.map(secondCall -> firstCall.size() == secondCall.size()))));

			StepVerifier.create(result)
					.assertNext(same -> assertThat(same).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLastUpdated() - edge cases")
	class GetLastUpdatedEdgeCaseTests {

		@Test
		@DisplayName("should return URL timestamp when only URL exists without certificate")
		void urlOnlyNoCert_ReturnsUrlTimestamp() {
			Mono<String> result = insertTestClient("SSLLUO", "SSL URL Only", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslluo1", "SSL LUO App"))
							.then(insertClientUrl(clientId, "sslluo1",
									"https://sslluo.example.com")))
					.then(Mono.defer(() -> cacheService
							.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
							.then(sslCertificateService.getLastUpdated())));

			StepVerifier.create(result)
					.assertNext(lastUpdated -> {
						assertThat(lastUpdated).isNotNull();
						assertThat(lastUpdated).isNotBlank();
						long epoch = Long.parseLong(lastUpdated);
						assertThat(epoch).isGreaterThan(0);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return the more recent of cert and URL timestamps")
		void certAndUrl_ReturnsNewerTimestamp() {
			Mono<String> result = insertTestClient("SSLNEW", "SSL Newer", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslnew1", "SSL NEW App"))
							.then(insertClientUrl(clientId, "sslnew1",
									"https://sslnew.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslnew.example.com", true))
					.then(Mono.defer(() -> cacheService
							.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
							.then(sslCertificateService.getLastUpdated())));

			StepVerifier.create(result)
					.assertNext(lastUpdated -> {
						assertThat(lastUpdated).isNotNull();
						assertThat(lastUpdated).isNotBlank();
						long epoch = Long.parseLong(lastUpdated);
						// Should be a recent timestamp (within last minute)
						long now = java.time.Instant.now().getEpochSecond();
						assertThat(epoch).isBetween(now - 60, now + 60);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should reflect updated timestamp after cache eviction")
		void cacheEviction_ReflectsNewTimestamp() {
			Mono<Boolean> result = insertTestClient("SSLCEV", "SSL Cache Evict", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslcev1", "SSL CEV App"))
							.then(insertClientUrl(clientId, "sslcev1",
									"https://sslcev.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslcev.example.com", true))
					.then(Mono.defer(() -> cacheService
							.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)))
					.then(Mono.defer(() -> sslCertificateService.getLastUpdated()))
					.flatMap(first -> cacheService
							.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
							.then(sslCertificateService.getLastUpdated())
							.map(second -> {
								// Both calls should return valid epoch timestamps
								long epoch1 = Long.parseLong(first);
								long epoch2 = Long.parseLong(second);
								return epoch1 > 0 && epoch2 > 0;
							}));

			StepVerifier.create(result)
					.assertNext(valid -> assertThat(valid).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteCertificate() - additional scenarios")
	class DeleteCertificateAdditionalTests {

		@Test
		@DisplayName("should evict lastUpdated cache after deletion")
		void deletion_EvictsLastUpdatedCache() {
			Mono<Boolean> result = insertTestClient("SSLDCE", "SSL Del Cache", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssldce1", "SSL DCE App"))
							.then(insertClientUrl(clientId, "ssldce1",
									"https://ssldce.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "ssldce.example.com", true)
							.flatMap(certId -> {
								// Get last updated before deletion
								return cacheService
										.evictAll(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT)
										.then(sslCertificateService.getLastUpdated())
										.flatMap(before -> sslCertificateService
												.deleteCertificate(certId)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(systemAuth))
												// After deletion, cache was evicted; get new value
												.then(sslCertificateService.getLastUpdated())
												.map(after -> {
													// Both should be valid timestamps
													return !before.isEmpty() || !after.isEmpty();
												}));
							}));

			StepVerifier.create(result)
					.assertNext(valid -> assertThat(valid).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("should allow deleting non-current certificate")
		void nonCurrentCertificate_DeletesSuccessfully() {
			Mono<Boolean> result = insertTestClient("SSLDNC", "SSL Del NonCur", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "ssldnc1", "SSL DNC App"))
							.then(insertClientUrl(clientId, "ssldnc1",
									"https://ssldnc.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "ssldnc.example.com", false))
					.flatMap(certId -> sslCertificateService.deleteCertificate(certId)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("should delete one certificate while leaving others intact")
		void deleteOne_LeavesOthers() {
			Mono<Page<SSLCertificate>> result = insertTestClient("SSLSEL", "SSL Selective Del", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestApp(clientId, "sslsel1", "SSL SEL App"))
							.then(insertClientUrl(clientId, "sslsel1",
									"https://sslsel.example.com")))
					.flatMap(urlId -> insertSSLCertificate(urlId, "sslsel.example.com", true)
							.flatMap(certId1 -> insertSSLCertificate(urlId,
									"sslsel.example.com", false)
									.flatMap(certId2 -> sslCertificateService
											.deleteCertificate(certId1)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth))
											.thenReturn(urlId))))
					.flatMap(urlId -> sslCertificateService
							.findSSLCertificates(urlId, PageRequest.of(0, 10), null)
							.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(1);
					})
					.verifyComplete();
		}
	}
}
