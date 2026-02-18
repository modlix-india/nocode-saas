package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.dto.User;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientManagerServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientManagerDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ClientService clientService;

	@Mock
	private UserService userService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	private ClientManagerService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong TARGET_CLIENT_ID = ULong.valueOf(3);

	@BeforeEach
	void setUp() {
		service = new ClientManagerService(messageResourceService, cacheService,
				clientService, userService, clientHierarchyService);

		// Inject the mocked DAO using reflection since it's set by the parent class
		try {
			var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		lenient().when(dao.getPojoClass()).thenReturn(Mono.just(ClientManager.class));

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
	}

	// ===== create() tests =====

	@Test
	void create_SystemClient_HasAccess_CreatesMapping() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(clientHierarchyService.isClientBeingManagedBy(SYSTEM_CLIENT_ID, BUS_CLIENT_ID))
				.thenReturn(Mono.just(true));

		when(dao.createIfNotExists(eq(TARGET_CLIENT_ID), eq(USER_ID), any(ULong.class)))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void create_OwnerRole_SameClient_HasAccess() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.ROLE_Owner", "Authorities.Client_UPDATE", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(dao.createIfNotExists(eq(TARGET_CLIENT_ID), eq(USER_ID), any(ULong.class)))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void create_ManagedClient_HasAccess() {

		ULong contextClientId = ULong.valueOf(5);
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(contextClientId, "MANAGED",
				List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(clientHierarchyService.isClientBeingManagedBy(contextClientId, BUS_CLIENT_ID))
				.thenReturn(Mono.just(true));

		when(dao.createIfNotExists(eq(TARGET_CLIENT_ID), eq(USER_ID), any(ULong.class)))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void create_SameClientAsUser_ThrowsBadRequest() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, TARGET_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
				.verify();
	}

	@Test
	void create_NotHasAccess_ThrowsForbidden() {

		ULong contextClientId = ULong.valueOf(5);
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(contextClientId, "NOACCESS",
				List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(clientHierarchyService.isClientBeingManagedBy(contextClientId, BUS_CLIENT_ID))
				.thenReturn(Mono.just(false));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
				.verify();
	}

	@Test
	void create_EvictsCacheForUserAndClient() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(dao.createIfNotExists(eq(TARGET_CLIENT_ID), eq(USER_ID), any(ULong.class)))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();

		verify(cacheService).evict(eq("clientManager"), eq(USER_ID), eq(TARGET_CLIENT_ID));
	}

	// ===== getClientsOfUser() tests =====

	@Test
	void getClientsOfUser_SystemClient_ReturnsPage() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		Pageable pageable = PageRequest.of(0, 10);
		Page<ULong> clientIdPage = new PageImpl<>(List.of(TARGET_CLIENT_ID), pageable, 1);
		when(dao.getClientsOfManager(USER_ID, pageable)).thenReturn(Mono.just(clientIdPage));

		Client targetClient = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "TARGET");
		when(clientService.readInternal(TARGET_CLIENT_ID)).thenReturn(Mono.just(targetClient));

		StepVerifier.create(service.getClientsOfUser(USER_ID, pageable))
				.assertNext(page -> {
					assertEquals(1, page.getTotalElements());
					assertEquals("TARGET", page.getContent().get(0).getCode());
				})
				.verifyComplete();
	}

	@Test
	void getClientsOfUser_OwnerOfSameClient_ReturnsPage() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.ROLE_Owner", "Authorities.Client_READ", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		Pageable pageable = PageRequest.of(0, 10);
		Page<ULong> clientIdPage = new PageImpl<>(List.of(TARGET_CLIENT_ID), pageable, 1);
		when(dao.getClientsOfManager(USER_ID, pageable)).thenReturn(Mono.just(clientIdPage));

		Client targetClient = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "TARGET");
		when(clientService.readInternal(TARGET_CLIENT_ID)).thenReturn(Mono.just(targetClient));

		StepVerifier.create(service.getClientsOfUser(USER_ID, pageable))
				.assertNext(page -> {
					assertEquals(1, page.getTotalElements());
				})
				.verifyComplete();
	}

	@Test
	void getClientsOfUser_NoAccess_ThrowsForbidden() {

		ULong contextClientId = ULong.valueOf(5);
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(contextClientId, "NOACCESS",
				List.of("Authorities.Client_READ", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(clientHierarchyService.isClientBeingManagedBy(contextClientId, BUS_CLIENT_ID))
				.thenReturn(Mono.just(false));

		Pageable pageable = PageRequest.of(0, 10);

		StepVerifier.create(service.getClientsOfUser(USER_ID, pageable))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
				.verify();
	}

	@Test
	void getClientsOfUser_EmptyPage_ReturnsEmptyPage() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		Pageable pageable = PageRequest.of(0, 10);
		Page<ULong> emptyPage = new PageImpl<>(List.of(), pageable, 0);
		when(dao.getClientsOfManager(USER_ID, pageable)).thenReturn(Mono.just(emptyPage));

		StepVerifier.create(service.getClientsOfUser(USER_ID, pageable))
				.assertNext(page -> {
					assertEquals(0, page.getTotalElements());
					assertTrue(page.getContent().isEmpty());
				})
				.verifyComplete();
	}

	// ===== delete() tests =====

	@Test
	void delete_HasAccess_DeletesMapping() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(dao.deleteByClientIdAndManagerId(TARGET_CLIENT_ID, USER_ID))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.delete(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void delete_NoAccess_ThrowsForbidden() {

		ULong contextClientId = ULong.valueOf(5);
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(contextClientId, "NOACCESS",
				List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(clientHierarchyService.isClientBeingManagedBy(contextClientId, BUS_CLIENT_ID))
				.thenReturn(Mono.just(false));

		StepVerifier.create(service.delete(USER_ID, TARGET_CLIENT_ID))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
				.verify();
	}

	@Test
	void delete_EvictsCacheForUserAndClient() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		setupSecurityContext(ca);

		User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
		when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

		when(dao.deleteByClientIdAndManagerId(TARGET_CLIENT_ID, USER_ID))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.delete(USER_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();

		verify(cacheService).evict(eq("clientManager"), eq(USER_ID), eq(TARGET_CLIENT_ID));
	}

	// ===== isUserClientManager(ContextAuthentication, ULong) tests =====

	@Test
	void isUserClientManager_ByCA_SameClient_OwnerRole_ReturnsTrue() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN"));

		StepVerifier.create(service.isUserClientManager(ca, BUS_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_ByCA_SameClient_NotOwner_ReturnsFalse() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.Client_READ", "Authorities.Logged_IN"));

		StepVerifier.create(service.isUserClientManager(ca, BUS_CLIENT_ID))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_ByCA_DifferentClient_IsManager_ReturnsTrue() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.Client_READ", "Authorities.Logged_IN"));

		when(dao.isManagerForClient(ULong.valueOf(10), TARGET_CLIENT_ID))
				.thenReturn(Mono.just(true));

		StepVerifier.create(service.isUserClientManager(ca, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_ByCA_DifferentClient_NotManager_ReturnsFalse() {

		ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
				List.of("Authorities.Client_READ", "Authorities.Logged_IN"));

		when(dao.isManagerForClient(ULong.valueOf(10), TARGET_CLIENT_ID))
				.thenReturn(Mono.just(false));

		StepVerifier.create(service.isUserClientManager(ca, TARGET_CLIENT_ID))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	// ===== isUserClientManager(String, ULong, ULong, ULong) tests =====

	@Test
	void isUserClientManager_ByAppCode_SameClient_ChecksAuthority() {

		when(userService.getUserAuthorities(eq("appCode"), eq(BUS_CLIENT_ID), eq(USER_ID)))
				.thenReturn(Mono.just(List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN")));

		StepVerifier.create(service.isUserClientManager("appCode", USER_ID, BUS_CLIENT_ID, BUS_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_ByAppCode_SameClient_NotOwner_ReturnsFalse() {

		when(userService.getUserAuthorities(eq("appCode"), eq(BUS_CLIENT_ID), eq(USER_ID)))
				.thenReturn(Mono.just(List.of("Authorities.Client_READ", "Authorities.Logged_IN")));

		StepVerifier.create(service.isUserClientManager("appCode", USER_ID, BUS_CLIENT_ID, BUS_CLIENT_ID))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_ByAppCode_DifferentClient_IsManager_ReturnsTrue() {

		when(dao.isManagerForClient(USER_ID, TARGET_CLIENT_ID))
				.thenReturn(Mono.just(true));

		StepVerifier.create(service.isUserClientManager("appCode", USER_ID, BUS_CLIENT_ID, TARGET_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}
}
