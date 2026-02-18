package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.SoxLogDAO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SoxLogServiceTest {

	@Mock
	private SoxLogDAO dao;

	@InjectMocks
	private SoxLogService service;

	private static final ULong OBJECT_ID = ULong.valueOf(42);

	@BeforeEach
	void setUp() {
		// Inject the mocked DAO via reflection since AbstractJOOQDataService stores
		// dao in a superclass field.
		// SoxLogService -> AbstractJOOQDataService (has dao) -> 1 getSuperclass()
		try {
			var daoField = service.getClass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}
	}

	// =========================================================================
	// createLog
	// =========================================================================

	@Nested
	@DisplayName("createLog")
	class CreateLogTests {

		@Test
		void createLog_CallsDaoCreate() {
			SoxLog created = new SoxLog()
					.setObjectId(OBJECT_ID)
					.setActionName(SecuritySoxLogActionName.CREATE)
					.setObjectName(SecuritySoxLogObjectName.CLIENT)
					.setDescription("Client created");

			when(dao.create(any(SoxLog.class))).thenReturn(Mono.just(created));

			service.createLog(OBJECT_ID, SecuritySoxLogActionName.CREATE,
					SecuritySoxLogObjectName.CLIENT, "Client created");

			ArgumentCaptor<SoxLog> captor = ArgumentCaptor.forClass(SoxLog.class);
			verify(dao).create(captor.capture());

			SoxLog captured = captor.getValue();
			assertEquals(OBJECT_ID, captured.getObjectId());
			assertEquals(SecuritySoxLogActionName.CREATE, captured.getActionName());
			assertEquals(SecuritySoxLogObjectName.CLIENT, captured.getObjectName());
			assertEquals("Client created", captured.getDescription());
		}

		@Test
		void createLog_NonBlocking_Subscribe() {
			SoxLog created = new SoxLog()
					.setObjectId(OBJECT_ID)
					.setActionName(SecuritySoxLogActionName.UPDATE)
					.setObjectName(SecuritySoxLogObjectName.USER)
					.setDescription("User updated");

			when(dao.create(any(SoxLog.class))).thenReturn(Mono.just(created));

			// createLog calls subscribe() which is fire-and-forget (non-blocking)
			// It should not throw, and dao.create should be invoked
			assertDoesNotThrow(() -> service.createLog(OBJECT_ID,
					SecuritySoxLogActionName.UPDATE, SecuritySoxLogObjectName.USER, "User updated"));

			verify(dao).create(any(SoxLog.class));
		}

		@Test
		void createLog_AllActionTypes() {
			when(dao.create(any(SoxLog.class))).thenAnswer(invocation -> {
				SoxLog arg = invocation.getArgument(0);
				return Mono.just(arg);
			});

			// Test CREATE action
			service.createLog(OBJECT_ID, SecuritySoxLogActionName.CREATE,
					SecuritySoxLogObjectName.CLIENT, "Created client");

			// Test UPDATE action
			service.createLog(OBJECT_ID, SecuritySoxLogActionName.UPDATE,
					SecuritySoxLogObjectName.USER, "Updated user");

			// Test DELETE action
			service.createLog(OBJECT_ID, SecuritySoxLogActionName.DELETE,
					SecuritySoxLogObjectName.ROLE, "Deleted role");

			// Test LOGIN action
			service.createLog(OBJECT_ID, SecuritySoxLogActionName.LOGIN,
					SecuritySoxLogObjectName.USER, "User logged in");

			// Verify dao.create was called 4 times, once per action type
			verify(dao, times(4)).create(any(SoxLog.class));

			// Verify individual calls with argument captors
			ArgumentCaptor<SoxLog> captor = ArgumentCaptor.forClass(SoxLog.class);
			verify(dao, times(4)).create(captor.capture());

			var allCaptured = captor.getAllValues();
			assertEquals(SecuritySoxLogActionName.CREATE, allCaptured.get(0).getActionName());
			assertEquals(SecuritySoxLogActionName.UPDATE, allCaptured.get(1).getActionName());
			assertEquals(SecuritySoxLogActionName.DELETE, allCaptured.get(2).getActionName());
			assertEquals(SecuritySoxLogActionName.LOGIN, allCaptured.get(3).getActionName());
		}
	}
}
