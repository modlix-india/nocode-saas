package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.SSLCertificateDAO;
import com.fincity.security.dao.SSLChallengeDAO;
import com.fincity.security.dao.SSLRequestDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.dto.SSLChallenge;
import com.fincity.security.dto.SSLRequest;
import com.fincity.security.model.SSLCertificateConfiguration;
import com.fincity.security.model.SSLCertificateOrderRequest;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SSLCertificateServiceTest extends AbstractServiceUnitTest {

	@Mock
	private SecurityMessageResourceService msgService;

	@Mock
	private SSLCertificateDAO certificateDao;

	@Mock
	private SSLRequestDAO requestDao;

	@Mock
	private SSLChallengeDAO challengeDao;

	@Mock
	private ClientUrlService clientUrlService;

	@Mock
	private CacheService cacheService;

	@InjectMocks
	private SSLCertificateService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong URL_ID = ULong.valueOf(50);
	private static final ULong CERT_ID = ULong.valueOf(100);
	private static final ULong REQUEST_ID = ULong.valueOf(200);
	private static final ULong CHALLENGE_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		setupMessageResourceService(msgService);
		setupCacheService(cacheService);
		setupEvictionMocks();
		injectValueFields();
	}

	private void setupEvictionMocks() {
		lenient().when(cacheService.evictAll(anyString())).thenReturn(Mono.just(true));
		lenient().when(cacheService.evictAllFunction(anyString()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunction(anyString(), any(Object[].class)))
				.thenReturn(Mono::just);
	}

	private void injectValueFields() {
		try {
			var sessionField = SSLCertificateService.class.getDeclaredField("sessionURL");
			sessionField.setAccessible(true);
			sessionField.set(service, "https://acme-staging-v02.api.letsencrypt.org/directory");

			var accountField = SSLCertificateService.class.getDeclaredField("accountURL");
			accountField.setAccessible(true);
			accountField.set(service, "https://acme-staging-v02.api.letsencrypt.org/acme/acct/12345");

			var keyField = SSLCertificateService.class.getDeclaredField("accountKey");
			keyField.setAccessible(true);
			keyField.set(service, "testkey");
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject @Value fields", e);
		}
	}

	private ClientUrl createClientUrl(ULong id, ULong clientId, String urlPattern) {
		ClientUrl cu = new ClientUrl();
		cu.setId(id);
		cu.setClientId(clientId);
		cu.setUrlPattern(urlPattern);
		cu.setAppCode("testapp");
		return cu;
	}

	private SSLCertificate createSSLCertificate(ULong id, ULong urlId) {
		SSLCertificate cert = new SSLCertificate();
		cert.setId(id);
		cert.setUrlId(urlId);
		cert.setDomains("example.com");
		cert.setOrganization("Test Org");
		cert.setCurrent(true);
		return cert;
	}

	private SSLRequest createSSLRequest(ULong id, ULong urlId) {
		SSLRequest req = new SSLRequest();
		req.setId(id);
		req.setUrlId(urlId);
		req.setDomains("example.com");
		req.setOrganization("Test Org");
		req.setCrtKey("testkey");
		req.setCsr("testcsr");
		req.setValidity(12);
		return req;
	}

	private SSLCertificateOrderRequest createOrderRequest(ULong urlId, List<String> domains) {
		SSLCertificateOrderRequest req = new SSLCertificateOrderRequest();
		req.setUrlId(urlId);
		req.setDomainNames(domains);
		req.setOrganizationName("Test Org");
		req.setValidityInMonths(12);
		return req;
	}

	// =========================================================================
	// getAllCertificates
	// =========================================================================

	@Nested
	@DisplayName("getAllCertificates")
	class GetAllCertificatesTests {

		@Test
		void getAllCertificates_DelegatesToCacheService() {
			List<SSLCertificateConfiguration> configs = List.of(
					new SSLCertificateConfiguration()
							.setUrl("example.com")
							.setClientCode("CLIENT1")
							.setAppCode("app1")
							.setPrivateKey("key")
							.setCertificate("cert"));

			when(certificateDao.readAllCertificates()).thenReturn(Mono.just(configs));

			StepVerifier.create(service.getAllCertificates())
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals("example.com", result.get(0).getUrl());
						assertEquals("CLIENT1", result.get(0).getClientCode());
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(
					eq(SSLCertificateService.CACHE_NAME_CERTIFICATE),
					any(),
					eq("certificates"));
		}
	}

	// =========================================================================
	// getLastUpdated
	// =========================================================================

	@Nested
	@DisplayName("getLastUpdated")
	class GetLastUpdatedTests {

		@Test
		void getLastUpdated_DelegatesToCacheService() {
			when(certificateDao.getLastUpdated()).thenReturn(Mono.just("1700000000"));

			StepVerifier.create(service.getLastUpdated())
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals("1700000000", result);
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(
					eq(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT),
					any(),
					eq("certificatesLastUpdated"));
		}
	}

	// =========================================================================
	// deleteCertificate
	// =========================================================================

	@Nested
	@DisplayName("deleteCertificate")
	class DeleteCertificateTests {

		@Test
		void deleteCertificate_HappyPath_DeletesAndEvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			SSLCertificate cert = createSSLCertificate(CERT_ID, URL_ID);
			ClientUrl clientUrl = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://example.com");

			when(certificateDao.readById(CERT_ID)).thenReturn(Mono.just(cert));
			when(clientUrlService.read(URL_ID)).thenReturn(Mono.just(clientUrl));
			when(certificateDao.delete(CERT_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.deleteCertificate(CERT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(certificateDao).readById(CERT_ID);
			verify(clientUrlService).read(URL_ID);
			verify(certificateDao).delete(CERT_ID);
			verify(cacheService).evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT);
		}

		@Test
		void deleteCertificate_CertNotFound_CompletesEmpty() {
			when(certificateDao.readById(CERT_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.deleteCertificate(CERT_ID))
					.verifyComplete();

			verify(certificateDao, never()).delete(any());
		}

		@Test
		void deleteCertificate_UrlNotFound_CompletesEmpty() {
			SSLCertificate cert = createSSLCertificate(CERT_ID, URL_ID);

			when(certificateDao.readById(CERT_ID)).thenReturn(Mono.just(cert));
			when(clientUrlService.read(URL_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.deleteCertificate(CERT_ID))
					.verifyComplete();

			verify(certificateDao, never()).delete(any());
		}
	}

	// =========================================================================
	// createCertificateRequest
	// =========================================================================

	@Nested
	@DisplayName("createCertificateRequest")
	class CreateCertificateRequestTests {

		@Test
		void createCertificateRequest_NullUrlId_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = createOrderRequest(null, List.of("example.com"));

			StepVerifier.create(service.createCertificateRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createCertificateRequest_EmptyDomainNames_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = createOrderRequest(URL_ID, List.of());

			StepVerifier.create(service.createCertificateRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createCertificateRequest_BlankDomainName_ThrowsBadRequest() {
			SSLCertificateOrderRequest request = createOrderRequest(URL_ID, List.of("example.com", "  "));

			StepVerifier.create(service.createCertificateRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createCertificateRequest_ExistingRequest_ThrowsConflict() {
			SSLCertificateOrderRequest request = createOrderRequest(URL_ID, List.of("example.com"));

			when(requestDao.checkIfRequestExistOnURL(URL_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.createCertificateRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}
	}

	// =========================================================================
	// createExternallyIssuedCertificate
	// =========================================================================

	@Nested
	@DisplayName("createExternallyIssuedCertificate")
	class CreateExternallyIssuedCertificateTests {

		@Test
		void createExternallyIssuedCertificate_MissingKey_ThrowsBadRequest() {
			SSLCertificate certificate = createSSLCertificate(null, URL_ID);
			certificate.setCrtKey(null);
			certificate.setCrt("-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----");

			StepVerifier.create(service.createExternallyIssuedCertificate(certificate))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createExternallyIssuedCertificate_BlankKey_ThrowsBadRequest() {
			SSLCertificate certificate = createSSLCertificate(null, URL_ID);
			certificate.setCrtKey("   ");
			certificate.setCrt("-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----");

			StepVerifier.create(service.createExternallyIssuedCertificate(certificate))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createExternallyIssuedCertificate_MissingCrt_ThrowsBadRequest() {
			SSLCertificate certificate = createSSLCertificate(null, URL_ID);
			certificate.setCrtKey("-----BEGIN RSA PRIVATE KEY-----\ntest\n-----END RSA PRIVATE KEY-----");
			certificate.setCrt(null);

			StepVerifier.create(service.createExternallyIssuedCertificate(certificate))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createExternallyIssuedCertificate_BlankCrt_ThrowsBadRequest() {
			SSLCertificate certificate = createSSLCertificate(null, URL_ID);
			certificate.setCrtKey("-----BEGIN RSA PRIVATE KEY-----\ntest\n-----END RSA PRIVATE KEY-----");
			certificate.setCrt("");

			StepVerifier.create(service.createExternallyIssuedCertificate(certificate))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// getToken
	// =========================================================================

	@Nested
	@DisplayName("getToken")
	class GetTokenTests {

		@Test
		void getToken_DelegatesToChallengeDao() {
			when(challengeDao.getToken("test-token")).thenReturn(Mono.just("auth-value"));

			StepVerifier.create(service.getToken("test-token"))
					.assertNext(result -> assertEquals("auth-value", result))
					.verifyComplete();

			verify(challengeDao).getToken("test-token");
		}

		@Test
		void getToken_NotFound_ReturnsEmpty() {
			when(challengeDao.getToken("unknown-token")).thenReturn(Mono.just(""));

			StepVerifier.create(service.getToken("unknown-token"))
					.assertNext(result -> assertEquals("", result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// deleteRequestByURLId
	// =========================================================================

	@Nested
	@DisplayName("deleteRequestByURLId")
	class DeleteRequestByURLIdTests {

		@Test
		void deleteRequestByURLId_HappyPath_DeletesAndEvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl clientUrl = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://example.com");

			when(clientUrlService.read(URL_ID)).thenReturn(Mono.just(clientUrl));
			when(requestDao.deleteByURLId(URL_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteRequestByURLId(URL_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(clientUrlService).read(URL_ID);
			verify(requestDao).deleteByURLId(URL_ID);
			verify(cacheService).evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT);
		}

		@Test
		void deleteRequestByURLId_UrlNotFound_CompletesEmpty() {
			when(clientUrlService.read(URL_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.deleteRequestByURLId(URL_ID))
					.verifyComplete();

			verify(requestDao, never()).deleteByURLId(any());
		}
	}

	// =========================================================================
	// readRequestByURLId
	// =========================================================================

	@Nested
	@DisplayName("readRequestByURLId")
	class ReadRequestByURLIdTests {

		@Test
		void readRequestByURLId_HappyPath_ReturnsOrder() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl clientUrl = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://example.com");
			SSLRequest sslRequest = createSSLRequest(REQUEST_ID, URL_ID);

			SSLChallenge challenge = new SSLChallenge();
			challenge.setId(CHALLENGE_ID);
			challenge.setRequestId(REQUEST_ID);
			challenge.setDomain("example.com");
			challenge.setStatus("pending");

			when(clientUrlService.read(URL_ID)).thenReturn(Mono.just(clientUrl));
			when(requestDao.readByURLId(URL_ID)).thenReturn(Mono.just(sslRequest));
			when(challengeDao.readChallengesByRequestId(REQUEST_ID))
					.thenReturn(Mono.just(List.of(challenge)));

			StepVerifier.create(service.readRequestByURLId(URL_ID))
					.assertNext(result -> {
						assertNotNull(result);
						assertNotNull(result.getRequest());
						assertEquals(REQUEST_ID, result.getRequest().getId());
						assertNotNull(result.getChallenges());
						assertEquals(1, result.getChallenges().size());
						assertEquals("example.com", result.getChallenges().get(0).getDomain());
					})
					.verifyComplete();
		}

		@Test
		void readRequestByURLId_UrlNotFound_CompletesEmpty() {
			when(clientUrlService.read(URL_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.readRequestByURLId(URL_ID))
					.verifyComplete();

			verify(requestDao, never()).readByURLId(any());
		}

		@Test
		void readRequestByURLId_NoRequestForUrl_CompletesEmpty() {
			ClientUrl clientUrl = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://example.com");

			when(clientUrlService.read(URL_ID)).thenReturn(Mono.just(clientUrl));
			when(requestDao.readByURLId(URL_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.readRequestByURLId(URL_ID))
					.verifyComplete();

			verify(challengeDao, never()).readChallengesByRequestId(any());
		}
	}
}
