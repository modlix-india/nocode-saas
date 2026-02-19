package com.fincity.security.service.appregistration;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.AppRegistrationIntegrationDAO;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AppRegistrationIntegrationServiceTest extends AbstractServiceUnitTest {

	@Mock
	private AppRegistrationIntegrationDAO dao;

	@Mock
	private AppService appService;

	@Mock
	private CacheService cacheService;

	@Mock
	private AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

	@Mock
	private SecurityMessageResourceService securityMessageResourceService;

	@InjectMocks
	private AppRegistrationIntegrationService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong INTEGRATION_ID = ULong.valueOf(50);

	@BeforeEach
	void setUp() {
		// Inject the mocked DAO via reflection since AbstractJOOQDataService stores
		// dao in a superclass field
		// AppRegistrationIntegrationService -> AbstractJOOQUpdatableDataService ->
		// AbstractJOOQDataService (has dao)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		setupMessageResourceService(securityMessageResourceService);
		setupCacheService(cacheService);
		setupEvictionMocks();
	}

	private void setupEvictionMocks() {
		lenient().when(cacheService.evictAll(anyString())).thenReturn(Mono.just(true));
		lenient().when(cacheService.evictAllFunction(anyString()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunction(anyString(), any(Object[].class)))
				.thenReturn(Mono::just);
	}

	private AppRegistrationIntegration createIntegration(ULong id, ULong clientId, ULong appId,
			SecurityAppRegIntegrationPlatform platform) {
		AppRegistrationIntegration integration = new AppRegistrationIntegration();
		integration.setId(id);
		integration.setClientId(clientId);
		integration.setAppId(appId);
		integration.setPlatform(platform);
		integration.setIntgId("test-intg-id");
		integration.setIntgSecret("test-intg-secret");
		integration.setLoginUri("https://login.example.com");
		return integration;
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_DelegatesToSuperCreate() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppRegistrationIntegration entity = createIntegration(null, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);

			AppRegistrationIntegration created = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);

			when(dao.create(any(AppRegistrationIntegration.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertEquals(INTEGRATION_ID, result.getId());
						assertEquals(SYSTEM_CLIENT_ID, result.getClientId());
						assertEquals(APP_ID, result.getAppId());
						assertEquals(SecurityAppRegIntegrationPlatform.GOOGLE, result.getPlatform());
					})
					.verifyComplete();

			verify(dao).create(any(AppRegistrationIntegration.class));
		}
	}

	// =========================================================================
	// update
	// =========================================================================

	@Nested
	@DisplayName("update")
	class UpdateTests {

		@Test
		void update_EvictsCachAfterUpdate() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppRegistrationIntegration entity = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);
			entity.setIntgId("updated-intg-id");

			AppRegistrationIntegration existing = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);

			AppRegistrationIntegration updated = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);
			updated.setIntgId("updated-intg-id");

			when(dao.readById(INTEGRATION_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(AppRegistrationIntegration.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(entity))
					.assertNext(result -> {
						assertEquals(INTEGRATION_ID, result.getId());
						assertEquals("updated-intg-id", result.getIntgId());
					})
					.verifyComplete();

			verify(cacheService).evict(eq("integrationPlatform"), anyString());
		}
	}

	// =========================================================================
	// delete
	// =========================================================================

	@Nested
	@DisplayName("delete")
	class DeleteTests {

		@Test
		void delete_EvictsCacheAfterDelete() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppRegistrationIntegration existing = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);

			when(dao.readById(INTEGRATION_ID)).thenReturn(Mono.just(existing));
			when(dao.delete(INTEGRATION_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(INTEGRATION_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evictFunction(eq("integrationPlatform"), any(Object[].class));
		}
	}

	// =========================================================================
	// readPageFilter
	// =========================================================================

	@Nested
	@DisplayName("readPageFilter")
	class ReadPageFilterTests {

		@Test
		void readPageFilter_DelegatesToSuperReadPageFilter() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Pageable pageable = PageRequest.of(0, 10);

			AppRegistrationIntegration integration = createIntegration(INTEGRATION_ID, SYSTEM_CLIENT_ID, APP_ID,
					SecurityAppRegIntegrationPlatform.GOOGLE);
			Page<AppRegistrationIntegration> page = new PageImpl<>(List.of(integration), pageable, 1);

			when(dao.readPageFilter(eq(pageable), isNull())).thenReturn(Mono.just(page));

			StepVerifier.create(service.readPageFilter(pageable, null))
					.assertNext(result -> {
						assertEquals(1, result.getTotalElements());
						assertEquals(INTEGRATION_ID, result.getContent().get(0).getId());
					})
					.verifyComplete();
		}
	}
}
