package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.UserDAO;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.model.AuthenticationPasswordType;

import reactor.test.StepVerifier;

class UserDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserDAO userDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@Nested
	@DisplayName("getUserClientId()")
	class GetUserClientIdTests {

		@Test
		void existingUser_ReturnsClientId() {
			// User ID 1 (system admin) should exist
			StepVerifier.create(userDAO.getUserClientId(ULong.valueOf(1)))
					.assertNext(clientId -> {
						assertNotNull(clientId);
						assertEquals(SYSTEM_CLIENT_ID, clientId);
					})
					.verifyComplete();
		}

		@Test
		void nonExistentUser_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUserClientId(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		void existingUser_ReturnsUser() {
			StepVerifier.create(userDAO.readInternal(ULong.valueOf(1)))
					.assertNext(user -> {
						assertNotNull(user);
						assertEquals(ULong.valueOf(1), user.getId());
						assertNotNull(user.getUserName());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentUser_ReturnsEmpty() {
			StepVerifier.create(userDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExistsForInvite()")
	class CheckUserExistsForInviteTests {

		@Test
		void existingUserName_ReturnsTrue() {
			StepVerifier.create(userDAO.readInternal(ULong.valueOf(1))
					.flatMap(user -> userDAO.checkUserExistsForInvite(
							SYSTEM_CLIENT_ID, user.getUserName(), null, null)))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		void nonExistentUserName_ReturnsFalse() {
			StepVerifier.create(userDAO.checkUserExistsForInvite(
					SYSTEM_CLIENT_ID, "nonexistent_user_xyz_123", null, null))
					.assertNext(exists -> assertFalse(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserForInvite()")
	class GetUserForInviteTests {

		@Test
		void existingUser_ReturnsUser() {
			StepVerifier.create(userDAO.readInternal(ULong.valueOf(1))
					.flatMap(user -> userDAO.getUserForInvite(
							SYSTEM_CLIENT_ID, user.getUserName(), null, null)))
					.assertNext(user -> {
						assertNotNull(user);
						assertEquals(ULong.valueOf(1), user.getId());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentUser_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUserForInvite(
					SYSTEM_CLIENT_ID, "nonexistent_user_xyz", null, null))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("increaseFailedAttempt()")
	class IncreaseFailedAttemptTests {

		@Test
		void passwordType_IncreasesCount() {
			// Create a test user, then increase failed attempts
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "failtest_" + System.currentTimeMillis(),
							"failtest@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PASSWORD)))
					.assertNext(count -> {
						assertNotNull(count);
						assertTrue(count >= 1);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("resetFailedAttempt()")
	class ResetFailedAttemptTests {

		@Test
		void resetsToZero() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "resettest_" + ts,
							"resettest_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PASSWORD)
									.then(userDAO.resetFailedAttempt(userId,
											AuthenticationPasswordType.PASSWORD))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getOwners()")
	class GetOwnersTests {

		@Test
		void systemClient_ReturnsOwners() {
			StepVerifier.create(userDAO.getOwners(SYSTEM_CLIENT_ID))
					.assertNext(owners -> assertNotNull(owners))
					.verifyComplete();
		}
	}
}
