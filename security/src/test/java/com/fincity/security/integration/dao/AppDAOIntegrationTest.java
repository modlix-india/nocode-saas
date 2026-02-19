package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.AppDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

class AppDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppDAO appDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@Nested
	@DisplayName("hasReadAccess() - by IDs")
	class HasReadAccessByIdTests {

		@Test
		void ownerClient_HasReadAccess() {
			// Create a test app and check access
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testracc_" + System.currentTimeMillis(), "Test Read Access App")
							.flatMap(appId -> appDAO.hasReadAccess(appId, SYSTEM_CLIENT_ID)))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentApp_NoAccess() {
			StepVerifier.create(appDAO.hasReadAccess(ULong.valueOf(999999), SYSTEM_CLIENT_ID))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_NoAccess() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testrac2_" + System.currentTimeMillis(), "Test App")
							.flatMap(appId -> appDAO.hasReadAccess(appId, ULong.valueOf(999999))))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasWriteAccess() - by IDs")
	class HasWriteAccessByIdTests {

		@Test
		void ownerClient_HasWriteAccess() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testwracc_" + System.currentTimeMillis(), "Test Write App")
							.flatMap(appId -> appDAO.hasWriteAccess(appId, SYSTEM_CLIENT_ID)))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentApp_NoWriteAccess() {
			StepVerifier.create(appDAO.hasWriteAccess(ULong.valueOf(999999), SYSTEM_CLIENT_ID))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasReadAccess() - by codes")
	class HasReadAccessByCodeTests {

		@Test
		void nonExistentAppCode_NoAccess() {
			StepVerifier.create(appDAO.hasReadAccess("NONEXISTENT_APP_CODE", "SYSTEM"))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasWriteAccess() - by codes")
	class HasWriteAccessByCodeTests {

		@Test
		void nonExistentAppCode_NoAccess() {
			StepVerifier.create(appDAO.hasWriteAccess("NONEXISTENT_APP_CODE", "SYSTEM"))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAppsIDsPerClient()")
	class GetAppsIDsPerClientTests {

		@Test
		void systemClient_ReturnsApps() {
			StepVerifier.create(appDAO.getAppsIDsPerClient(List.of(SYSTEM_CLIENT_ID)))
					.assertNext(appsMap -> assertNotNull(appsMap))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmptyMap() {
			StepVerifier.create(appDAO.getAppsIDsPerClient(List.of(ULong.valueOf(999999))))
					.assertNext(appsMap -> {
						assertNotNull(appsMap);
						assertTrue(appsMap.isEmpty() || !appsMap.containsKey(ULong.valueOf(999999)));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isNoneUsingTheAppOtherThan()")
	class IsNoneUsingTests {

		@Test
		void nonExistentApp_ReturnsTrue() {
			StepVerifier.create(appDAO.isNoneUsingTheAppOtherThan(ULong.valueOf(999999), BigInteger.ONE))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}
}
