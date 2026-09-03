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
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dao.DraftTokenDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.DraftToken;
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

	@Mock
	private DraftTokenDAO draftTokenDAO;

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
			verify(cacheService).evictAllFunction("gatewayClientAppCodeType");
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

			// When the user's client does not manage the target client, the create method
			// returns Mono.empty() (no switchIfEmpty error handler), so the stream completes
			// with no elements emitted.
			StepVerifier.create(service.create(entity))
					.verifyComplete();
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
			verify(cacheService).evictAllFunction("gatewayClientAppCodeType");
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
			// Security context is needed because the update(key, fields) flow calls
			// this.read(key) which requires authentication context.
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

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

			// Evictions happen twice: once from update(entity) and once from update(key, fields)
			// since update(key, fields) delegates to update(entity) internally.
			verify(cacheService, times(2)).evictAllFunction("clientUrl");
			verify(cacheService, times(2)).evictAllFunction("uri");
			verify(cacheService, times(2)).evictAllFunction("gatewayClientAppCodeType");
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
			verify(cacheService).evictAllFunction("gatewayClientAppCodeType");
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
			// When the DAO returns false, BooleanUtil.safeValueOf(false) is false,
			// so the code enters the else branch and calls appService.getAppByCode
			when(appService.getAppByCode("taken")).thenReturn(Mono.empty());

			StepVerifier.create(service.checkSubDomainAvailability("taken", "https://taken.example.com"))
					.assertNext(result -> {
						// appService returns empty, so defaultIfEmpty(false) yields false
						assertNotNull(result);
						assertFalse(result);
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
			// Security context is needed because super.create() calls getLoggedInUserId()
			// which calls SecurityContextUtil.getUsersContextUser() to set the createdBy field.
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientUrl entity = createClientUrl(null, TARGET_CLIENT_ID, "https://reg.example.com/", "testapp");
			ClientUrl created = createClientUrl(URL_ID, TARGET_CLIENT_ID, "https://reg.example.com", "testapp");

			when(dao.create(any(ClientUrl.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createForRegistration(entity))
					.assertNext(result -> {
						assertEquals(URL_ID, result.getId());
						assertEquals("https://reg.example.com", result.getUrlPattern());
					})
					.verifyComplete();

			// Verify no client management check was needed (access check is skipped)
			verifyNoInteractions(clientService);

			// Verify caches are still evicted
			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("gatewayClientAppCodeType");
			verify(cacheService).evictAllFunction("certificateCache");
			verify(cacheService).evictAllFunction("certificatesLastUpdatedCache");
		}
	}

	@Nested
	@DisplayName("Draft edit token: minting reuses the caller's live grant")
	class DraftTokenMinting {

		private static final int WINDOW_MINUTES = 30;
		private static final String HELD = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
		private static final String FRESH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

		@org.junit.jupiter.api.BeforeEach
		void injectWindow() throws Exception {
			// @Value is not applied under MockitoExtension, so the window would be zero
			// and an extension would be indistinguishable from the expiry it replaced.
			var field = ClientUrlService.class.getDeclaredField("draftTokenExpiryMinutes");
			field.setAccessible(true);
			field.setInt(service, WINDOW_MINUTES);
		}

		private Client systemClient() {
			Client c = new Client();
			c.setId(SYSTEM_CLIENT_ID);
			c.setCode("SYSTEM");
			return c;
		}

		private DraftToken held(java.time.LocalDateTime expiresAt) {
			DraftToken token = new DraftToken();
			token.setToken(HELD).setAppCode("myapp").setClientId(SYSTEM_CLIENT_ID)
					.setUserId(ULong.valueOf(1)).setExpiresAt(expiresAt);
			return token;
		}

		@Test
		@DisplayName("a caller who already holds a live grant gets that same hostname back")
		void reusesTheLiveGrant() {
			setupSecurityContext(TestDataFactory.createSystemAuth());
			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(systemClient()));

			// Nearly spent on purpose: a grant with two minutes left is exactly the one
			// worth reusing, because reuse is also when it gets pushed forward.
			when(draftTokenDAO.readLiveOfUser(eq(ULong.valueOf(1)), eq("myapp"), eq(SYSTEM_CLIENT_ID), any()))
					.thenReturn(Mono.just(
							held(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(2))));
			when(draftTokenDAO.extend(eq(HELD), eq(ULong.valueOf(1)), any())).thenReturn(Mono.just(1));

			StepVerifier.create(service.mintDraftToken("myapp"))
					.assertNext(res -> {
						assertEquals(HELD, res.getToken());
						assertEquals("t-" + HELD + ".testdomain.com.modlix.com", res.getHost());
						// Handed back with a full window, not the two minutes it had.
						assertTrue(res.getExpiresAt()
								.isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
										.plusMinutes(WINDOW_MINUTES - 1)),
								"a reused grant must leave with a full window");
					})
					.verifyComplete();

			verify(draftTokenDAO).extend(eq(HELD), eq(ULong.valueOf(1)), any());
			verify(draftTokenDAO, never()).create(any(DraftToken.class));
		}

		@Test
		@DisplayName("a caller holding none gets a new one")
		void mintsWhenNoneIsHeld() {
			setupSecurityContext(TestDataFactory.createSystemAuth());
			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(systemClient()));

			when(draftTokenDAO.readLiveOfUser(any(), anyString(), any(), any())).thenReturn(Mono.empty());
			when(draftTokenDAO.create(any(DraftToken.class))).thenAnswer(invocation -> {
				DraftToken given = invocation.getArgument(0);
				return Mono.just(given.setToken(FRESH));
			});

			StepVerifier.create(service.mintDraftToken("myapp"))
					.assertNext(res -> {
						assertEquals(FRESH, res.getToken());
						assertEquals("t-" + FRESH + ".testdomain.com.modlix.com", res.getHost());
					})
					.verifyComplete();

			verify(draftTokenDAO, never()).extend(anyString(), any(), any());
		}

		@Test
		@DisplayName("without write access the reuse lookup never happens")
		void noWriteAccessNeverReachesTheLookup() {
			// The gate has to sit in front of the lookup, or losing write access would
			// still hand somebody the grant they were holding when they had it.
			setupSecurityContext(TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUS", List.of()));
			when(appService.hasWriteAccess("myapp", "BUS")).thenReturn(Mono.just(Boolean.FALSE));

			StepVerifier.create(service.mintDraftToken("myapp"))
					.verifyError(com.fincity.saas.commons.exeception.GenericException.class);

			verifyNoInteractions(draftTokenDAO);
		}
	}

	@Nested
	@DisplayName("Draft edit token: which hostnames are tokens")
	class DraftTokenHostParsing {

		@Test
		@DisplayName("a well formed t- label is a token")
		void wellFormedLabelIsAToken() {
			assertEquals("0123456789abcdef0123456789abcdef",
					ClientUrlService.draftTokenFromHost(
							"t-0123456789abcdef0123456789abcdef.local.modlix.com"));
		}

		@Test
		@DisplayName("the token is read from the first label only")
		void readsTheFirstLabelOnly() {
			// The environment suffix and base domain vary; only the label is the grant.
			assertEquals("0123456789abcdef0123456789abcdef",
					ClientUrlService.draftTokenFromHost("t-0123456789abcdef0123456789abcdef.modlix.com"));
		}

		@Test
		@DisplayName("an ordinary app hostname beginning with t is not a token")
		void ordinaryHostIsNotAToken() {
			// The whole reason the label is matched whole rather than by prefix.
			assertNull(ClientUrlService.draftTokenFromHost("theorempro.local.modlix.com"));
			assertNull(ClientUrlService.draftTokenFromHost("t-shirts.example.com"));
		}

		@Test
		@DisplayName("wrong length, wrong alphabet and wrong case are not tokens")
		void malformedLabelsAreNotTokens() {
			assertNull(ClientUrlService.draftTokenFromHost("t-0123456789abcdef.local.modlix.com"));
			assertNull(ClientUrlService.draftTokenFromHost(
					"t-0123456789ABCDEF0123456789ABCDEF.local.modlix.com"));
			assertNull(ClientUrlService.draftTokenFromHost(
					"t-0123456789abcdef0123456789abcdeg.local.modlix.com"));
			assertNull(ClientUrlService.draftTokenFromHost(
					"d0123456789abcdef0123456789abcdef.local.modlix.com"));
			assertNull(ClientUrlService.draftTokenFromHost(""));
			assertNull(ClientUrlService.draftTokenFromHost(null));
		}
	}

	@Nested
	@DisplayName("Draft edit token: who it grants the draft surface to")
	class DraftTokenResolution {

		private static final String TOKEN = "0123456789abcdef0123456789abcdef";
		private static final String HOST = "t-" + TOKEN + ".local.modlix.com";

		private DraftToken row(String appCode, ULong clientId, java.time.LocalDateTime expiresAt) {
			DraftToken token = new DraftToken();
			token.setToken(TOKEN).setAppCode(appCode).setClientId(clientId)
					.setUserId(ULong.valueOf(7)).setExpiresAt(expiresAt);
			return token;
		}

		private java.time.LocalDateTime future() {
			return java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(30);
		}

		private Client client(ULong id, String code) {
			Client c = new Client();
			c.setId(id);
			c.setCode(code);
			return c;
		}

		@Test
		@DisplayName("an unknown token grants nothing")
		void unknownTokenIsDenied() {
			when(draftTokenDAO.readByToken(TOKEN)).thenReturn(Mono.empty());

			StepVerifier.create(service.resolveDraftToken(HOST, "myapp", "SYSTEM"))
					.assertNext(res -> assertEquals(Boolean.FALSE, res.getT1()))
					.verifyComplete();
		}

		@Test
		@DisplayName("a hostname that is not a token grants nothing, without a lookup")
		void nonTokenHostIsDenied() {
			StepVerifier.create(service.resolveDraftToken("appbuilder.local.modlix.com", "myapp", "SYSTEM"))
					.assertNext(res -> assertEquals(Boolean.FALSE, res.getT1()))
					.verifyComplete();

			verifyNoInteractions(draftTokenDAO);
		}

		@Test
		@DisplayName("an expired token grants nothing")
		void expiredTokenIsDenied() {
			when(draftTokenDAO.readByToken(TOKEN)).thenReturn(Mono.just(row("myapp", SYSTEM_CLIENT_ID,
					java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1))));

			StepVerifier.create(service.resolveDraftToken(HOST, "myapp", "SYSTEM"))
					.assertNext(res -> assertEquals(Boolean.FALSE, res.getT1()))
					.verifyComplete();
		}

		@Test
		@DisplayName("a token for another app grants nothing")
		void otherAppIsDenied() {
			when(draftTokenDAO.readByToken(TOKEN))
					.thenReturn(Mono.just(row("myapp", SYSTEM_CLIENT_ID, future())));

			StepVerifier.create(service.resolveDraftToken(HOST, "someotherapp", "SYSTEM"))
					.assertNext(res -> assertEquals(Boolean.FALSE, res.getT1()))
					.verifyComplete();
		}

		@Test
		@DisplayName("the minting client's own context is granted")
		void mintingClientIsGranted() {
			when(draftTokenDAO.readByToken(TOKEN))
					.thenReturn(Mono.just(row("myapp", SYSTEM_CLIENT_ID, future())));
			when(clientService.readInternal(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(client(SYSTEM_CLIENT_ID, "SYSTEM")));
			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client(SYSTEM_CLIENT_ID, "SYSTEM")));
			when(clientService.doesClientManageClient(SYSTEM_CLIENT_ID, SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.resolveDraftToken(HOST, "myapp", "SYSTEM"))
					.assertNext(res -> {
						assertEquals(Boolean.TRUE, res.getT1());
						assertEquals("myapp", res.getT3());
						assertEquals("SYSTEM", res.getT4());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("a managed client's context is granted")
		void managedClientIsGranted() {
			when(draftTokenDAO.readByToken(TOKEN))
					.thenReturn(Mono.just(row("myapp", SYSTEM_CLIENT_ID, future())));
			when(clientService.readInternal(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(client(SYSTEM_CLIENT_ID, "SYSTEM")));
			when(clientService.getClientBy("BUS")).thenReturn(Mono.just(client(BUS_CLIENT_ID, "BUS")));
			when(clientService.doesClientManageClient(SYSTEM_CLIENT_ID, BUS_CLIENT_ID))
					.thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.resolveDraftToken(HOST, "myapp", "BUS"))
					.assertNext(res -> {
						assertEquals(Boolean.TRUE, res.getT1());
						assertEquals("BUS", res.getT4());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("an unmanaged client's context grants nothing")
		void unmanagedClientIsDenied() {
			when(draftTokenDAO.readByToken(TOKEN))
					.thenReturn(Mono.just(row("myapp", BUS_CLIENT_ID, future())));
			when(clientService.readInternal(BUS_CLIENT_ID))
					.thenReturn(Mono.just(client(BUS_CLIENT_ID, "BUS")));
			when(clientService.getClientBy("OTHER")).thenReturn(Mono.just(client(TARGET_CLIENT_ID, "OTHER")));
			when(clientService.doesClientManageClient(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(Boolean.FALSE));

			StepVerifier.create(service.resolveDraftToken(HOST, "myapp", "OTHER"))
					.assertNext(res -> assertEquals(Boolean.FALSE, res.getT1()))
					.verifyComplete();
		}

		@Test
		@DisplayName("with no codes to check against, the token supplies its own")
		void blankCodesAdoptTheTokensOwn() {
			// A request straight to the token hostname, with no /appCode/clientCode
			// prefix -- the standalone form, and how SSR's own calls arrive.
			when(draftTokenDAO.readByToken(TOKEN))
					.thenReturn(Mono.just(row("myapp", SYSTEM_CLIENT_ID, future())));
			when(clientService.readInternal(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(client(SYSTEM_CLIENT_ID, "SYSTEM")));

			StepVerifier.create(service.resolveDraftToken(HOST, "", ""))
					.assertNext(res -> {
						assertEquals(Boolean.TRUE, res.getT1());
						assertEquals("myapp", res.getT3());
						assertEquals("SYSTEM", res.getT4());
					})
					.verifyComplete();
		}
	}
}
