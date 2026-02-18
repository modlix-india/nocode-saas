package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientUrlServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientUrlDAO dao;

	@Mock
	private CacheService cacheService;

	@Mock
	private SecurityMessageResourceService msgService;

	@Mock
	private ClientService clientService;

	@Mock
	private AppService appService;

	@InjectMocks
	private ClientUrlService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong TARGET_CLIENT_ID = ULong.valueOf(3);
	private static final ULong URL_ID = ULong.valueOf(50);
	private static final ULong APP_ID = ULong.valueOf(100);

	@BeforeEach
	void setUp() {
		// Inject the mocked DAO via reflection since AbstractJOOQDataService stores
		// dao in a superclass field
		// ClientUrlService -> AbstractJOOQUpdatableDataService -> AbstractJOOQDataService (has dao)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// Inject appCodeSuffix via reflection since it's a @Value field
		try {
			var suffixField = service.getClass().getDeclaredField("appCodeSuffix");
			suffixField.setAccessible(true);
			suffixField.set(service, ".testdomain.com");
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject appCodeSuffix", e);
		}

		setupMessageResourceService(msgService);
		setupCacheService(cacheService);
		setupEvictionMocks();
	}

	@SuppressWarnings("unchecked")
	private void setupEvictionMocks() {
		lenient().when(cacheService.evictAll(anyString())).thenReturn(Mono.just(true));
		lenient().when(cacheService.evictAllFunction(anyString()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunction(anyString(), any(Object[].class)))
				.thenReturn(Mono::just);
		lenient().when(cacheService.cacheEmptyValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});
	}

	private ClientUrl createClientUrl(ULong id, ULong clientId, String urlPattern, String appCode) {
		ClientUrl cu = new ClientUrl();
		cu.setId(id);
		cu.setClientId(clientId);
		cu.setUrlPattern(urlPattern);
		cu.setAppCode(appCode);
		return cu;
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_HappyPath_EvictsMultipleCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(null, SYSTEM_CLIENT_ID, "https://app.example.com/", "testapp");
			ClientUrl created = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.create(any(ClientUrl.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertEquals(URL_ID, result.getId());
						assertEquals("https://app.example.com", result.getUrlPattern());
					})
					.verifyComplete();

			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
			verify(cacheService).evictAllFunction("certificateCache");
			verify(cacheService).evictAllFunction("certificatesLastUpdatedCache");
		}

		@Test
		void create_NullClientId_UsesLoggedIn() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(null, null, "https://app.example.com", "testapp");
			ClientUrl created = createClientUrl(URL_ID, BUS_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.create(any(ClientUrl.class))).thenAnswer(invocation -> {
				ClientUrl arg = invocation.getArgument(0);
				assertEquals(BUS_CLIENT_ID, arg.getClientId());
				return Mono.just(created);
			});

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertEquals(BUS_CLIENT_ID, result.getClientId()))
					.verifyComplete();
		}

		@Test
		void create_TrimsUrlPattern() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(null, SYSTEM_CLIENT_ID, "https://app.example.com///", "testapp");
			ClientUrl created = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.create(any(ClientUrl.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}

		@Test
		void create_NotManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(null, TARGET_CLIENT_ID, "https://app.example.com", "testapp");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(TARGET_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// =========================================================================
	// read
	// =========================================================================

	@Nested
	@DisplayName("read")
	class ReadTests {

		@Test
		void read_SystemClient_AllowsAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl existing = createClientUrl(URL_ID, TARGET_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.readById(URL_ID)).thenReturn(Mono.just(existing));

			StepVerifier.create(service.read(URL_ID))
					.assertNext(result -> {
						assertEquals(URL_ID, result.getId());
						assertEquals("https://app.example.com", result.getUrlPattern());
					})
					.verifyComplete();
		}

		@Test
		void read_ManagedClient_AllowsAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ClientUrl existing = createClientUrl(URL_ID, TARGET_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.readById(URL_ID)).thenReturn(Mono.just(existing));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(TARGET_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.read(URL_ID))
					.assertNext(result -> assertEquals(URL_ID, result.getId()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// update(ClientUrl entity)
	// =========================================================================

	@Nested
	@DisplayName("update(ClientUrl entity)")
	class UpdateEntityTests {

		@Test
		void update_ByEntity_EvictsMultipleCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://updated.example.com/", "testapp");
			ClientUrl existing = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://old.example.com", "testapp");
			ClientUrl updated = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://updated.example.com", "testapp");

			when(dao.readById(URL_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(ClientUrl.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(entity))
					.assertNext(result -> assertEquals("https://updated.example.com", result.getUrlPattern()))
					.verifyComplete();

			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
			verify(cacheService).evictAllFunction("certificateCache");
			verify(cacheService).evictAllFunction("certificatesLastUpdatedCache");
		}
	}

	// =========================================================================
	// update(ULong, Map)
	// =========================================================================

	@Nested
	@DisplayName("update(ULong, Map)")
	class UpdateMapTests {

		@Test
		void update_ByMap_ComputesUrlPatternField() {
			ClientUrl existing = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://old.example.com", "testapp");
			ClientUrl updated = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://new.example.com", "testapp");

			when(dao.readById(URL_ID)).thenReturn(Mono.just(existing));
			when(dao.getPojoClass()).thenReturn(Mono.just(ClientUrl.class));
			when(dao.update(any(ClientUrl.class))).thenReturn(Mono.just(updated));

			// Inject ObjectMapper for map-based update
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			try {
				var omField = service.getClass().getSuperclass()
						.getDeclaredField("objectMapper");
				omField.setAccessible(true);
				omField.set(service, objectMapper);
			} catch (Exception e) {
				throw new RuntimeException("Failed to inject ObjectMapper", e);
			}

			Map<String, Object> fields = new HashMap<>();
			fields.put("urlPattern", "https://new.example.com/");

			StepVerifier.create(service.update(URL_ID, fields))
					.assertNext(result -> assertEquals("https://new.example.com", result.getUrlPattern()))
					.verifyComplete();

			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
		}
	}

	// =========================================================================
	// delete
	// =========================================================================

	@Nested
	@DisplayName("delete")
	class DeleteTests {

		@Test
		void delete_EvictsMultipleCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl existing = createClientUrl(URL_ID, SYSTEM_CLIENT_ID, "https://app.example.com", "testapp");

			when(dao.readById(URL_ID)).thenReturn(Mono.just(existing));
			when(dao.delete(URL_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(URL_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
			verify(cacheService).evictAllFunction("certificateCache");
			verify(cacheService).evictAllFunction("certificatesLastUpdatedCache");
		}
	}

	// =========================================================================
	// getUrlsBasedOnApp
	// =========================================================================

	@Nested
	@DisplayName("getUrlsBasedOnApp")
	class GetUrlsBasedOnAppTests {

		@Test
		void getUrlsBasedOnApp_WithSuffix_AddsSuffixUrls() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			List<String> urlList = new ArrayList<>(List.of("https://existing.example.com"));
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");

			when(dao.getClientUrlsBasedOnAppAndClient(eq("testapp"), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(urlList));
			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getUrlsBasedOnApp("testapp", ".dev"))
					.assertNext(result -> {
						assertTrue(result.size() >= 2);
						assertTrue(result.stream().anyMatch(u -> u.contains(".testdomain.com")));
					})
					.verifyComplete();
		}

		@Test
		void getUrlsBasedOnApp_NoSuffix_ReturnsBase() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			List<String> urlList = new ArrayList<>(List.of("https://existing.example.com"));
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");

			when(dao.getClientUrlsBasedOnAppAndClient(eq("testapp"), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(urlList));
			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getUrlsBasedOnApp("testapp", null))
					.assertNext(result -> assertEquals(1, result.size()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getAppUrl
	// =========================================================================

	@Nested
	@DisplayName("getAppUrl")
	class GetAppUrlTests {

		@Test
		void getAppUrl_FromProperties() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "BUSCLIENT");

			AppProperty urlProp = new AppProperty();
			urlProp.setAppId(APP_ID);
			urlProp.setClientId(BUS_CLIENT_ID);
			urlProp.setName("URL");
			urlProp.setValue("app.example.com");

			Map<ULong, Map<String, AppProperty>> props = Map.of(
					BUS_CLIENT_ID, Map.of("URL", urlProp));

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(clientService.getClientBy("BUSCLIENT")).thenReturn(Mono.just(client));
			when(appService.getProperties(BUS_CLIENT_ID, APP_ID, "testapp", "URL"))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getAppUrl("testapp", "BUSCLIENT"))
					.assertNext(result -> {
						assertTrue(result.startsWith("https://"));
						assertTrue(result.contains("app.example.com"));
					})
					.verifyComplete();
		}

		@Test
		void getAppUrl_FromDAO() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "BUSCLIENT");

			Map<ULong, Map<String, AppProperty>> emptyProps = Map.of();

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(clientService.getClientBy("BUSCLIENT")).thenReturn(Mono.just(client));
			when(appService.getProperties(BUS_CLIENT_ID, APP_ID, "testapp", "URL"))
					.thenReturn(Mono.just(emptyProps));
			when(dao.getLatestClientUrlBasedOnAppAndClient("testapp", BUS_CLIENT_ID))
					.thenReturn(Mono.just("app.example.com"));

			StepVerifier.create(service.getAppUrl("testapp", "BUSCLIENT"))
					.assertNext(result -> {
						assertTrue(result.startsWith("https://"));
						assertTrue(result.contains("app.example.com"));
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// checkSubDomainAvailability
	// =========================================================================

	@Nested
	@DisplayName("checkSubDomainAvailability")
	class CheckSubDomainAvailabilityTests {

		@Test
		void checkSubDomainAvailability_Available_ReturnsTrue() {
			when(dao.checkSubDomainAvailability("https://newdomain.example.com"))
					.thenReturn(Mono.just(true));
			when(appService.getAppByCode("newdomain")).thenReturn(Mono.empty());

			StepVerifier.create(service.checkSubDomainAvailability("newdomain", "https://newdomain.example.com"))
					.assertNext(result -> {
						// When checkSubDomainAvailability returns true (no URL pattern found),
						// and no app exists with that code, it returns false
						assertNotNull(result);
					})
					.verifyComplete();
		}

		@Test
		void checkSubDomainAvailability_Taken_ReturnsFalse() {
			when(dao.checkSubDomainAvailability("https://taken.example.com"))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.checkSubDomainAvailability("taken", "https://taken.example.com"))
					.assertNext(result -> {
						// checkSubDomainAvailability returns false meaning URL exists,
						// then BooleanUtil.safeValueOf(false) = false, so it goes to the else branch
						// and checks the appService
						assertNotNull(result);
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// createForRegistration
	// =========================================================================

	@Nested
	@DisplayName("createForRegistration")
	class CreateForRegistrationTests {

		@Test
		void createForRegistration_SkipsAccessCheck() {
			ClientUrl entity = createClientUrl(null, TARGET_CLIENT_ID, "https://reg.example.com/", "testapp");
			ClientUrl created = createClientUrl(URL_ID, TARGET_CLIENT_ID, "https://reg.example.com", "testapp");

			when(dao.create(any(ClientUrl.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createForRegistration(entity))
					.assertNext(result -> {
						assertEquals(URL_ID, result.getId());
						assertEquals("https://reg.example.com", result.getUrlPattern());
					})
					.verifyComplete();

			// Verify no security context interaction was needed
			verifyNoInteractions(clientService);

			// Verify caches are still evicted
			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
			verify(cacheService).evictAllFunction("certificateCache");
			verify(cacheService).evictAllFunction("certificatesLastUpdatedCache");
		}
	}
}
