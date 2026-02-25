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
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientHierarchyDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientHierarchyServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientHierarchyDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ClientService clientService;

	private ClientHierarchyService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong PARENT_CLIENT_ID = ULong.valueOf(2);
	private static final ULong CHILD_CLIENT_ID = ULong.valueOf(3);


	@BeforeEach
	void setUp() {
		service = new ClientHierarchyService(messageResourceService, cacheService);
		service.setClientService(clientService);

		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
	}

	// ===== create() tests =====

	@Test
	void create_HappyPath_CreatesHierarchyAndCaches() {

		ClientHierarchy parentHierarchy = TestDataFactory.createSystemHierarchy(PARENT_CLIENT_ID);

		when(dao.getClientHierarchy(PARENT_CLIENT_ID)).thenReturn(Mono.just(parentHierarchy));
		when(dao.create(any(ClientHierarchy.class))).thenAnswer(invocation -> {
			ClientHierarchy ch = invocation.getArgument(0);
			ch.setId(ULong.valueOf(100));
			return Mono.just(ch);
		});

		StepVerifier.create(service.create(PARENT_CLIENT_ID, CHILD_CLIENT_ID))
				.assertNext(result -> {
					assertNotNull(result);
					assertEquals(CHILD_CLIENT_ID, result.getClientId());
					assertEquals(PARENT_CLIENT_ID, result.getManageClientLevel0());
				})
				.verifyComplete();

		verify(cacheService).put(eq("clientHierarchy"), any(ClientHierarchy.class), eq(CHILD_CLIENT_ID));
	}

	@Test
	void create_SameIds_ThrowsError() {

		StepVerifier.create(service.create(CHILD_CLIENT_ID, CHILD_CLIENT_ID))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
				.verify();
	}

	@Test
	void create_MaxLevelReached_ThrowsError() {

		ClientHierarchy fullHierarchy = TestDataFactory.createClientHierarchy(
				PARENT_CLIENT_ID,
				ULong.valueOf(10),
				ULong.valueOf(11),
				ULong.valueOf(12),
				ULong.valueOf(13));

		when(dao.getClientHierarchy(PARENT_CLIENT_ID)).thenReturn(Mono.just(fullHierarchy));

		StepVerifier.create(service.create(PARENT_CLIENT_ID, CHILD_CLIENT_ID))
				.expectErrorMatches(e -> e instanceof GenericException
						&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
				.verify();
	}

	@Test
	void create_PutsInCache() {

		ClientHierarchy parentHierarchy = TestDataFactory.createSystemHierarchy(PARENT_CLIENT_ID);

		when(dao.getClientHierarchy(PARENT_CLIENT_ID)).thenReturn(Mono.just(parentHierarchy));
		when(dao.create(any(ClientHierarchy.class))).thenAnswer(invocation -> {
			ClientHierarchy ch = invocation.getArgument(0);
			ch.setId(ULong.valueOf(100));
			return Mono.just(ch);
		});

		StepVerifier.create(service.create(PARENT_CLIENT_ID, CHILD_CLIENT_ID))
				.expectNextCount(1)
				.verifyComplete();

		verify(cacheService).put(eq("clientHierarchy"), any(ClientHierarchy.class), eq(CHILD_CLIENT_ID));
	}

	// ===== getManagingClientIds() tests =====

	@Test
	void getManagingClientIds_DelegatesToDAO() {

		List<ULong> ids = List.of(PARENT_CLIENT_ID, CHILD_CLIENT_ID);
		when(dao.getManagingClientIds(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(ids));

		StepVerifier.create(service.getManagingClientIds(SYSTEM_CLIENT_ID))
				.assertNext(result -> assertEquals(2, result.size()))
				.verifyComplete();
	}

	// ===== getClientHierarchy() tests =====

	@Test
	void getClientHierarchy_CachedResult_ReturnsCached() {

		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getClientHierarchy(CHILD_CLIENT_ID))
				.assertNext(result -> {
					assertEquals(CHILD_CLIENT_ID, result.getClientId());
					assertEquals(PARENT_CLIENT_ID, result.getManageClientLevel0());
				})
				.verifyComplete();

		verify(cacheService).cacheValueOrGet(eq("clientHierarchy"), any(), eq(CHILD_CLIENT_ID));
	}

	// ===== getUserClientHierarchy() tests =====

	@Test
	void getUserClientHierarchy_UsesUserHierarchyCache() {

		ULong userId = ULong.valueOf(10);
		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getUserClientHierarchy(userId)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getUserClientHierarchy(userId))
				.assertNext(result -> assertEquals(CHILD_CLIENT_ID, result.getClientId()))
				.verifyComplete();

		verify(cacheService).cacheValueOrGet(eq("userClientHierarchy"), any(), eq(userId));
	}

	// ===== getClientHierarchyIds() tests =====

	@Test
	void getClientHierarchyIds_ReturnsFluxOfIds() {

		ClientHierarchy hierarchy = TestDataFactory.createClientHierarchy(
				CHILD_CLIENT_ID, PARENT_CLIENT_ID, SYSTEM_CLIENT_ID, null, null);

		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getClientHierarchyIds(CHILD_CLIENT_ID))
				.expectNext(CHILD_CLIENT_ID)
				.expectNext(PARENT_CLIENT_ID)
				.expectNext(SYSTEM_CLIENT_ID)
				.verifyComplete();
	}

	// ===== getClientHierarchyIdInOrder() tests =====

	@Test
	void getClientHierarchyIdInOrder_ReturnsOrderedList() {

		ClientHierarchy hierarchy = TestDataFactory.createClientHierarchy(
				CHILD_CLIENT_ID, PARENT_CLIENT_ID, SYSTEM_CLIENT_ID, null, null);

		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getClientHierarchyIdInOrder(CHILD_CLIENT_ID))
				.assertNext(result -> {
					assertEquals(3, result.size());
					assertEquals(CHILD_CLIENT_ID, result.get(0));
					assertEquals(PARENT_CLIENT_ID, result.get(1));
					assertEquals(SYSTEM_CLIENT_ID, result.get(2));
				})
				.verifyComplete();
	}

	// ===== isClientBeingManagedBy() tests =====

	@Test
	void isClientBeingManagedBy_SameClient_ReturnsTrue() {

		StepVerifier.create(service.isClientBeingManagedBy(CHILD_CLIENT_ID, CHILD_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();

		verifyNoInteractions(dao);
	}

	@Test
	void isClientBeingManagedBy_InHierarchy_ReturnsTrue() {

		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.isClientBeingManagedBy(PARENT_CLIENT_ID, CHILD_CLIENT_ID))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isClientBeingManagedBy_NotInHierarchy_ReturnsFalse() {

		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		ULong unrelatedClientId = ULong.valueOf(99);

		StepVerifier.create(service.isClientBeingManagedBy(unrelatedClientId, CHILD_CLIENT_ID))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	@Test
	void isClientBeingManagedBy_DefaultIfEmpty_ReturnsFalse() {

		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.empty());

		StepVerifier.create(service.isClientBeingManagedBy(PARENT_CLIENT_ID, CHILD_CLIENT_ID))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	// ===== getManagingClient() tests =====

	@Test
	void getManagingClient_SystemLevel_ReturnsSystemClientId() {

		when(clientService.getSystemClientId()).thenReturn(Mono.just(SYSTEM_CLIENT_ID));

		StepVerifier.create(service.getManagingClient(CHILD_CLIENT_ID, ClientHierarchy.Level.SYSTEM))
				.assertNext(result -> assertEquals(SYSTEM_CLIENT_ID, result))
				.verifyComplete();

		verifyNoInteractions(dao);
	}

	@Test
	void getManagingClient_Level0_ReturnsImmediateParent() {

		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getManagingClient(CHILD_CLIENT_ID, ClientHierarchy.Level.ZERO))
				.assertNext(result -> assertEquals(PARENT_CLIENT_ID, result))
				.verifyComplete();
	}

	@Test
	void getManagingClient_LevelNotSet_ReturnsEmpty() {

		ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(CHILD_CLIENT_ID, PARENT_CLIENT_ID);
		when(dao.getClientHierarchy(CHILD_CLIENT_ID)).thenReturn(Mono.just(hierarchy));

		StepVerifier.create(service.getManagingClient(CHILD_CLIENT_ID, ClientHierarchy.Level.TWO))
				.verifyComplete();
	}
}
