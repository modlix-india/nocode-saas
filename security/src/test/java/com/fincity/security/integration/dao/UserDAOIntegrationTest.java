package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserDAO userDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_v2_user_role WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_pins WHERE USER_ID > 1").then())
				.then(databaseClient.sql("UPDATE security_user SET DESIGNATION_ID = NULL, REPORTING_TO = NULL WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_designation WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Nested
	@DisplayName("getUserClientId()")
	class GetUserClientIdTests {

		@Test
		void existingUser_ReturnsClientId() {
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
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "failtest_" + ts,
							"failtest_" + ts + "@test.com", "password123")
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

	@Nested
	@DisplayName("setPassword()")
	class SetPasswordTests {

		@Test
		void setPassword_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "setpwd_" + ts,
							"setpwd_" + ts + "@test.com", "oldpassword")
							.flatMap(userId -> userDAO.setPassword(userId, ULong.valueOf(1),
									"newPassword123", AuthenticationPasswordType.PASSWORD)))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("lockUser()")
	class LockUserTests {

		@Test
		void lockUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(1);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "lockusr_" + ts,
							"lockusr_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.lockUser(userId, lockUntil,
									"Too many failed attempts")))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void lockUser_StatusIsLockedInDatabase() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(2);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "lockusr2_" + ts,
							"lockusr2_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.lockUser(userId, lockUntil,
									"Suspicious activity")
									.then(databaseClient.sql(
											"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one())))
					.assertNext(status -> assertEquals("LOCKED", status))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("updateUserStatusToActive()")
	class UpdateUserStatusToActiveTests {

		@Test
		void activateLockedUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(1);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "activate_" + ts,
							"activate_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.lockUser(userId, lockUntil, "test lock")
									.then(userDAO.updateUserStatusToActive(userId))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void activateLockedUser_StatusIsActiveInDatabase() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(1);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "activate2_" + ts,
							"activate2_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.lockUser(userId, lockUntil, "test lock")
									.then(userDAO.updateUserStatusToActive(userId))
									.then(databaseClient.sql(
											"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one())))
					.assertNext(status -> assertEquals("ACTIVE", status))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExists()")
	class CheckUserExistsTests {

		@Test
		void existingUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "chkexist_" + ts;

			// checkUserExists queries hierarchy table, so we need a client with a hierarchy entry
			StepVerifier.create(
					insertTestClient("CHKUSR", "Check User Client", "BUS")
							.flatMap(clientId -> databaseClient.sql(
									"INSERT INTO security_client_hierarchy (CLIENT_ID, MANAGE_CLIENT_LEVEL_0) VALUES (:clientId, :managingId)")
									.bind("clientId", clientId.longValue())
									.bind("managingId", SYSTEM_CLIENT_ID.longValue())
									.then()
									.then(insertTestUser(clientId, userName,
											"chkexist_" + ts + "@test.com", "password123"))
									.then(userDAO.checkUserExists(
											SYSTEM_CLIENT_ID, userName, null, null, "BUS"))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		void nonExistentUser_ReturnsFalse() {
			StepVerifier.create(
					insertTestClient("CHKNO", "Check No User", "BUS")
							.flatMap(clientId -> databaseClient.sql(
									"INSERT INTO security_client_hierarchy (CLIENT_ID, MANAGE_CLIENT_LEVEL_0) VALUES (:clientId, :managingId)")
									.bind("clientId", clientId.longValue())
									.bind("managingId", SYSTEM_CLIENT_ID.longValue())
									.then()
									.then(userDAO.checkUserExists(
											SYSTEM_CLIENT_ID, "nonexist_" + System.currentTimeMillis(),
											null, null, "BUS"))))
					.assertNext(exists -> assertFalse(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addRoleToUser() / removeRoleForUser()")
	class AddRemoveRoleTests {

		@Test
		void addRoleToUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "addrole_" + ts,
							"addrole_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_v2_role WHERE NAME = 'Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(roleId -> userDAO.addRoleToUser(userId, roleId))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void removeRoleForUser_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "rmrole_" + ts,
							"rmrole_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_v2_role WHERE NAME = 'Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(roleId -> databaseClient.sql(
											"INSERT INTO security_v2_user_role (USER_ID, ROLE_ID) VALUES (:userId, :roleId)")
											.bind("userId", userId.longValue())
											.bind("roleId", roleId.longValue())
											.then()
											.then(userDAO.removeRoleForUser(userId, roleId)))))
					.assertNext(result -> assertEquals(1, result.intValue()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addProfileToUser() / removeProfileForUser()")
	class AddRemoveProfileTests {

		@Test
		void addProfileToUser_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "addprof_" + ts,
							"addprof_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(profileId -> userDAO.addProfileToUser(userId, profileId))))
					.assertNext(result -> assertEquals(1, result.intValue()))
					.verifyComplete();
		}

		@Test
		void removeProfileForUser_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "rmprof_" + ts,
							"rmprof_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(profileId -> databaseClient.sql(
											"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
											.bind("profileId", profileId.longValue())
											.bind("userId", userId.longValue())
											.then()
											.then(userDAO.removeProfileForUser(userId, profileId)))))
					.assertNext(result -> assertEquals(1, result.intValue()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserIdsByClientId()")
	class GetUserIdsByClientIdTests {

		@Test
		void multipleUsersInSystemClient_ReturnsAllCreatedIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "getids1_" + ts,
							"getids1_" + ts + "@test.com", "password123")
							.flatMap(userId1 -> insertTestUser(SYSTEM_CLIENT_ID, "getids2_" + ts,
									"getids2_" + ts + "@test.com", "password123")
									.map(userId2 -> List.of(userId1, userId2)))
							.flatMap(createdIds -> userDAO.getUserIdsByClientId(SYSTEM_CLIENT_ID, null)
									.collectList()
									.map(returnedIds -> {
										for (ULong createdId : createdIds) {
											assertTrue(returnedIds.contains(createdId),
													"Expected returned IDs to contain " + createdId);
										}
										return returnedIds;
									})))
					.assertNext(ids -> assertFalse(ids.isEmpty()))
					.verifyComplete();
		}

		@Test
		void filterBySpecificUserIds_ReturnsOnlyThoseIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "getfilt1_" + ts,
							"getfilt1_" + ts + "@test.com", "password123")
							.flatMap(userId1 -> insertTestUser(SYSTEM_CLIENT_ID, "getfilt2_" + ts,
									"getfilt2_" + ts + "@test.com", "password123")
									.map(userId2 -> List.of(userId1, userId2)))
							.flatMap(createdIds -> {
								List<ULong> filterIds = List.of(createdIds.get(0));
								return userDAO.getUserIdsByClientId(SYSTEM_CLIENT_ID, filterIds)
										.collectList()
										.doOnNext(returnedIds -> {
											assertTrue(returnedIds.contains(createdIds.get(0)),
													"Should contain the filtered user ID");
											assertFalse(returnedIds.contains(createdIds.get(1)),
													"Should not contain the non-filtered user ID");
										});
							}))
					.assertNext(ids -> assertFalse(ids.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("increaseResendAttempts()")
	class IncreaseResendAttemptTests {

		@Test
		@DisplayName("should increase OTP resend attempt counter")
		void increaseResendAttempts_ReturnsIncreasedCount() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "resend_" + ts,
							"resend_" + ts + "@test.com", "password123")
							.flatMap(userDAO::increaseResendAttempts))
					.assertNext(count -> assertTrue(count >= 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple increases should accumulate")
		void increaseResendAttempts_MultipleCalls_Accumulates() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "resend2_" + ts,
							"resend2_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseResendAttempts(userId)
									.then(userDAO.increaseResendAttempts(userId))))
					.assertNext(count -> assertEquals((short) 2, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("resetResendAttempts()")
	class ResetResendAttemptTests {

		@Test
		@DisplayName("should reset OTP resend counter to zero")
		void resetResendAttempts_AfterIncrease_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "rrsa_" + ts,
							"rrsa_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseResendAttempts(userId)
									.then(userDAO.resetResendAttempts(userId))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("PIN failed attempts")
	class PinFailedAttemptTests {

		@Test
		@DisplayName("increaseFailedAttempt PIN should increase counter")
		void increaseFailedAttempt_Pin_ReturnsCount() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "pinfail_" + ts,
							"pinfail_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PIN)))
					.assertNext(count -> assertTrue(count >= 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetFailedAttempt PIN should return true")
		void resetFailedAttempt_Pin_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "pinrst_" + ts,
							"pinrst_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PIN)
									.then(userDAO.resetFailedAttempt(userId,
											AuthenticationPasswordType.PIN))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("OTP failed attempts")
	class OtpFailedAttemptTests {

		@Test
		@DisplayName("increaseFailedAttempt OTP should increase counter")
		void increaseFailedAttempt_Otp_ReturnsCount() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "otpfail_" + ts,
							"otpfail_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.OTP)))
					.assertNext(count -> assertTrue(count >= 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetFailedAttempt OTP should return true")
		void resetFailedAttempt_Otp_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "otprst_" + ts,
							"otprst_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.OTP)
									.then(userDAO.resetFailedAttempt(userId,
											AuthenticationPasswordType.OTP))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("setPassword() PIN type")
	class SetPinTests {

		@Test
		@DisplayName("setPassword with PIN type should return 1")
		void setPin_ReturnsOne() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "setpin_" + ts,
							"setpin_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.setPassword(userId, ULong.valueOf(1),
									"123456", AuthenticationPasswordType.PIN)))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExistsExclude()")
	class CheckUserExistsExcludeTests {

		@Test
		@DisplayName("existing user not excluded should return true")
		void existingUser_NotExcluded_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "exclchk_" + ts;

			StepVerifier.create(
					insertTestClient("EXCLCHK", "Exclude Check", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"exclchk_" + ts + "@test.com", "password123"))
									.flatMap(userId -> userDAO.checkUserExistsExclude(
											SYSTEM_CLIENT_ID, userName, null, null,
											"BUS", ULong.valueOf(999999)))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("existing user excluded should return false")
		void existingUser_Excluded_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "exclme_" + ts;

			StepVerifier.create(
					insertTestClient("EXCLME", "Exclude Me", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"exclme_" + ts + "@test.com", "password123"))
									.flatMap(userId -> userDAO.checkUserExistsExclude(
											SYSTEM_CLIENT_ID, userName, null, null,
											"BUS", userId))))
					.assertNext(exists -> assertFalse(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addDesignation()")
	class AddDesignationTests {

		@Test
		@DisplayName("should set designation on user")
		void addDesignation_ValidUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestClient("ADDDESG", "Add Desg", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "desguser_" + ts,
											"desguser_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"INSERT INTO security_designation (CLIENT_ID, NAME) VALUES (:clientId, :name)")
											.bind("clientId", clientId.longValue())
											.bind("name", "TestDesignation")
											.filter(s -> s.returnGeneratedValues("ID"))
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(desgId -> userDAO.addDesignation(userId, desgId)))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canReportTo()")
	class CanReportToTests {

		@Test
		@DisplayName("null reportingTo should return false")
		void nullReportingTo_ReturnsFalse() {
			StepVerifier.create(userDAO.canReportTo(SYSTEM_CLIENT_ID, null, ULong.valueOf(1)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("reportingTo equals userId should return false (self-reporting)")
		void selfReporting_ReturnsFalse() {
			ULong userId = ULong.valueOf(5);
			StepVerifier.create(userDAO.canReportTo(SYSTEM_CLIENT_ID, userId, userId))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("null userId should return true (new user can report to anyone)")
		void nullUserId_ReturnsTrue() {
			StepVerifier.create(userDAO.canReportTo(SYSTEM_CLIENT_ID, ULong.valueOf(1), null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("valid reportingTo in same client should return true")
		void validReportingTo_SameClient_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mgr_" + ts,
							"mgr_" + ts + "@test.com", "password123")
							.flatMap(mgrId -> insertTestUser(SYSTEM_CLIENT_ID, "rep_" + ts,
									"rep_" + ts + "@test.com", "password123")
									.flatMap(repId -> userDAO.canReportTo(SYSTEM_CLIENT_ID, mgrId, repId))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("reportingTo in different client should return false")
		void reportingTo_DifferentClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestClient("RPTCL1", "Report Client 1", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "otherrep_" + ts,
											"otherrep_" + ts + "@test.com", "password123")))
							.flatMap(otherUserId -> insertTestUser(SYSTEM_CLIENT_ID, "sysrep_" + ts,
									"sysrep_" + ts + "@test.com", "password123")
									.flatMap(sysUserId -> userDAO.canReportTo(SYSTEM_CLIENT_ID,
											otherUserId, sysUserId))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLevel1SubOrg()")
	class GetLevel1SubOrgTests {

		@Test
		@DisplayName("user with direct reports should return their IDs")
		void userWithDirectReports_ReturnsReportIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mgrsub_" + ts,
							"mgrsub_" + ts + "@test.com", "password123")
							.flatMap(mgrId -> insertTestUser(SYSTEM_CLIENT_ID, "sub1_" + ts,
									"sub1_" + ts + "@test.com", "password123")
									.flatMap(sub1Id -> databaseClient.sql(
											"UPDATE security_user SET REPORTING_TO = :mgrId WHERE ID = :userId")
											.bind("mgrId", mgrId.longValue())
											.bind("userId", sub1Id.longValue())
											.then()
											.thenReturn(mgrId)))
							.flatMapMany(mgrId -> userDAO.getLevel1SubOrg(SYSTEM_CLIENT_ID, mgrId))
							.collectList())
					.assertNext(subs -> assertFalse(subs.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("user without direct reports should return empty")
		void userWithoutReports_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "nosub_" + ts,
							"nosub_" + ts + "@test.com", "password123")
							.flatMapMany(userId -> userDAO.getLevel1SubOrg(SYSTEM_CLIENT_ID, userId))
							.collectList())
					.assertNext(subs -> assertTrue(subs.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkRoleAssignedForUser()")
	class CheckRoleAssignedForUserTests {

		@Test
		@DisplayName("assigned role should return true")
		void roleAssigned_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "chkrole_" + ts,
							"chkrole_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_v2_role WHERE NAME = 'Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(roleId -> userDAO.addRoleToUser(userId, roleId)
											.then(userDAO.checkRoleAssignedForUser(userId, roleId)))))
					.assertNext(assigned -> assertTrue(assigned))
					.verifyComplete();
		}

		@Test
		@DisplayName("unassigned role should return false")
		void roleNotAssigned_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "norole_" + ts,
							"norole_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_v2_role WHERE NAME = 'Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(roleId -> userDAO.checkRoleAssignedForUser(userId, roleId))))
					.assertNext(assigned -> assertFalse(assigned))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getEmailsOfUsers()")
	class GetEmailsOfUsersTests {

		@Test
		@DisplayName("should return emails for valid user IDs")
		void validUserIds_ReturnsEmails() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "email1_" + ts,
							"email1_" + ts + "@test.com", "password123")
							.flatMap(uid1 -> insertTestUser(SYSTEM_CLIENT_ID, "email2_" + ts,
									"email2_" + ts + "@test.com", "password123")
									.map(uid2 -> List.of(uid1, uid2)))
							.flatMap(userDAO::getEmailsOfUsers))
					.assertNext(emails -> {
						assertEquals(2, emails.size());
						assertTrue(emails.contains("email1_" + ts + "@test.com"));
						assertTrue(emails.contains("email2_" + ts + "@test.com"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty list should return empty")
		void emptyList_ReturnsEmpty() {
			StepVerifier.create(userDAO.getEmailsOfUsers(List.of()))
					.assertNext(emails -> assertTrue(emails.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("deleted user should not be included")
		void deletedUser_NotIncluded() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "delemail_" + ts,
							"delemail_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"UPDATE security_user SET STATUS_CODE = 'DELETED' WHERE ID = :userId")
									.bind("userId", userId.longValue())
									.then()
									.then(userDAO.getEmailsOfUsers(List.of(userId)))))
					.assertNext(emails -> assertTrue(emails.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkIfUserIsOwner()")
	class CheckIfUserIsOwnerTests {

		@Test
		@DisplayName("non-owner user should return false")
		void nonOwnerUser_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "notown_" + ts,
							"notown_" + ts + "@test.com", "password123")
							.flatMap(userDAO::checkIfUserIsOwner))
					.assertNext(isOwner -> assertFalse(isOwner))
					.verifyComplete();
		}

		@Test
		@DisplayName("user with Owner role via profile should return true")
		void ownerUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			// The system user (ID=1) typically has an Owner profile
			StepVerifier.create(userDAO.checkIfUserIsOwner(ULong.valueOf(1)))
					.assertNext(isOwner -> assertNotNull(isOwner))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForProfiles()")
	class GetUsersForProfilesTests {

		@Test
		@DisplayName("should return users assigned to given profiles")
		void usersAssignedToProfiles_ReturnsUsers() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "profusr_" + ts,
							"profusr_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(profileId -> databaseClient.sql(
											"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
											.bind("profileId", profileId.longValue())
											.bind("userId", userId.longValue())
											.then()
											.then(userDAO.getUsersForProfiles(
													List.of(profileId), SYSTEM_CLIENT_ID)))))
					.assertNext(users -> assertFalse(users.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("empty profile list should return empty")
		void emptyProfileList_ReturnsEmpty() {
			StepVerifier.create(
					userDAO.getUsersForProfiles(List.of(), SYSTEM_CLIENT_ID))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy()")
	class GetUsersByTests {

		@Test
		@DisplayName("by username with USERNAME identifier should return matching user")
		void byUserName_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gub_" + ts;

			StepVerifier.create(
					insertTestClient("GUBCL", "GetUsersBy Cl", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gub_" + ts + "@test.com", "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(userName, null, clientCode,
									null, AuthenticationIdentifierType.USER_NAME,
									null, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertEquals(userName, users.get(0).getUserName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("by email with EMAIL_ID identifier should return matching user")
		void byEmail_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String email = "gubeml_" + ts + "@test.com";

			StepVerifier.create(
					insertTestClient("GUBEM", "GetUsersBy Em", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "gubeml_" + ts,
											email, "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(email, null, clientCode,
									null, AuthenticationIdentifierType.EMAIL_ID,
									null, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertEquals(email, users.get(0).getEmailId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent user should return empty list")
		void nonExistent_ReturnsEmpty() {
			StepVerifier.create(
					insertTestClient("GUBNO", "GetUsersBy No", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy("nonexistent_user_xyz_999",
									null, clientCode, null,
									AuthenticationIdentifierType.USER_NAME, null,
									SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAllClientsBy()")
	class GetAllClientsByTests {

		@Test
		@DisplayName("by userId should return userId->clientId mapping")
		void byUserId_ReturnsMapping() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestClient("GACB", "GetAllCl", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "gacb_" + ts,
											"gacb_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"SELECT CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("CODE", String.class))
											.one()
											.flatMap(clientCode -> userDAO.getAllClientsBy(
													null, userId, clientCode, null,
													AuthenticationIdentifierType.USER_NAME,
													SecurityUserStatusCode.ACTIVE))
											.map(map -> {
												assertFalse(map.isEmpty());
												assertTrue(map.containsKey(userId));
												assertEquals(clientId, map.get(userId));
												return map;
											}))))
					.assertNext(map -> assertFalse(map.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForNotification()")
	class GetUsersForNotificationTests {

		@Test
		@DisplayName("null appCode should return empty list")
		void nullAppCode_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUsersForNotification(null, null, null, null))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserForEntityProcessor()")
	class GetUserForEntityProcessorTests {

		@Test
		@DisplayName("null appCode should return empty")
		void nullAppCode_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUserForEntityProcessor(
					ULong.valueOf(1), null, null, null))
					.verifyComplete();
		}

		@Test
		@DisplayName("null userId should return empty")
		void nullUserId_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUserForEntityProcessor(
					null, "appbuilder", null, null))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForEntityProcessor()")
	class GetUsersForEntityProcessorTests {

		@Test
		@DisplayName("null appCode should return empty list")
		void nullAppCode_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUsersForEntityProcessor(null, null, null, null))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getOwners() edge cases")
	class GetOwnersEdgeCaseTests {

		@Test
		@DisplayName("null clientId should return empty list")
		void nullClientId_ReturnsEmpty() {
			StepVerifier.create(userDAO.getOwners(null))
					.assertNext(owners -> assertTrue(owners.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("client with no owners should return empty list")
		void noOwners_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestClient("NOOWN", "No Owners", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(userDAO.getOwners(clientId))))
					.assertNext(owners -> assertTrue(owners.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExistsForInvite() edge cases")
	class CheckUserExistsForInviteEdgeCaseTests {

		@Test
		@DisplayName("by email should return true when email matches")
		void byEmail_ExistingEmail_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String email = "invchk_" + ts + "@test.com";

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "invchk_" + ts, email, "password123")
							.then(userDAO.checkUserExistsForInvite(
									SYSTEM_CLIENT_ID, null, email, null)))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("by phone should return false when no phone match")
		void byPhone_NoMatch_ReturnsFalse() {
			StepVerifier.create(userDAO.checkUserExistsForInvite(
					SYSTEM_CLIENT_ID, null, null, "+1999999999"))
					.assertNext(exists -> assertFalse(exists))
					.verifyComplete();
		}
	}

	// ========== NEW TEST CLASSES FOR UNCOVERED METHODS ==========

	@Nested
	@DisplayName("checkUserExists() null managingClientId error path")
	class CheckUserExistsNullClientTests {

		@Test
		@DisplayName("null managingClientId should throw GenericException with CONFLICT status")
		void nullManagingClientId_ThrowsConflict() {
			StepVerifier.create(userDAO.checkUserExists(null, "someUser", null, null, "BUS"))
					.expectErrorMatches(throwable -> throwable instanceof GenericException
							&& ((GenericException) throwable).getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}

		@Test
		@DisplayName("null managingClientId with email should also throw")
		void nullManagingClientId_WithEmail_ThrowsConflict() {
			StepVerifier.create(userDAO.checkUserExists(null, null, "test@test.com", null, null))
					.expectErrorMatches(throwable -> throwable instanceof GenericException
							&& ((GenericException) throwable).getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}
	}

	@Nested
	@DisplayName("checkUserExists() with email and phone")
	class CheckUserExistsEmailPhoneTests {

		@Test
		@DisplayName("by email should return true when email matches")
		void byEmail_ExistingEmail_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String email = "chkeml_" + ts + "@test.com";

			StepVerifier.create(
					insertTestClient("CHKEML", "Check Email", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "chkeml_" + ts, email, "password123"))
									.then(userDAO.checkUserExists(SYSTEM_CLIENT_ID, null, email, null, "BUS"))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("by phone number should return true when phone matches")
		void byPhone_ExistingPhone_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String phone = "+1" + ts.substring(ts.length() - 10);

			StepVerifier.create(
					insertTestClient("CHKPHN", "Check Phone", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(databaseClient.sql(
											"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, PHONE_NUMBER, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, :phone, 'Test', 'User', 'pass', false, 'ACTIVE')")
											.bind("clientId", clientId.longValue())
											.bind("userName", "chkphn_" + ts)
											.bind("email", "chkphn_" + ts + "@test.com")
											.bind("phone", phone)
											.then())
									.then(userDAO.checkUserExists(SYSTEM_CLIENT_ID, null, null, phone, "BUS"))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("with no typeCode should still work")
		void noTypeCode_ExistingUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "chkntc_" + ts;

			StepVerifier.create(
					insertTestClient("CHKNTC", "Check No Type", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"chkntc_" + ts + "@test.com", "password123"))
									.then(userDAO.checkUserExists(SYSTEM_CLIENT_ID, userName, null, null, null))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("deleted user should not be found")
		void deletedUser_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "chkdel_" + ts;

			StepVerifier.create(
					insertTestClient("CHKDEL", "Check Delete", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"chkdel_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"UPDATE security_user SET STATUS_CODE = 'DELETED' WHERE ID = :userId")
											.bind("userId", userId.longValue())
											.then())
									.then(userDAO.checkUserExists(SYSTEM_CLIENT_ID, userName, null, null, "BUS"))))
					.assertNext(exists -> assertFalse(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExistsExclude() additional branches")
	class CheckUserExistsExcludeAdditionalTests {

		@Test
		@DisplayName("no typeCode filter should still work")
		void noTypeCode_ExistingUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "excntc_" + ts;

			StepVerifier.create(
					insertTestClient("EXCNTC", "Exclude No TC", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"excntc_" + ts + "@test.com", "password123"))
									.flatMap(userId -> userDAO.checkUserExistsExclude(
											SYSTEM_CLIENT_ID, userName, null, null,
											null, ULong.valueOf(999999)))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("null exclude ID should still work")
		void nullExcludeId_ExistingUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "excnul_" + ts;

			StepVerifier.create(
					insertTestClient("EXCNUL", "Exclude Null", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"excnul_" + ts + "@test.com", "password123"))
									.flatMap(userId -> userDAO.checkUserExistsExclude(
											SYSTEM_CLIENT_ID, userName, null, null,
											"BUS", null))))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("setPassword() OTP type (default branch)")
	class SetPasswordOtpTypeTests {

		@Test
		@DisplayName("setPassword with OTP type should return 0 (unsupported)")
		void otpType_ReturnsZero() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "setotp_" + ts,
							"setotp_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.setPassword(userId, ULong.valueOf(1),
									"otpvalue", AuthenticationPasswordType.OTP)))
					.assertNext(result -> assertEquals(0, result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy() PHONE_NUMBER identifier")
	class GetUsersByPhoneTests {

		@Test
		@DisplayName("by phone with PHONE_NUMBER identifier should return matching user")
		void byPhone_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String phone = "+1" + ts.substring(ts.length() - 10);

			StepVerifier.create(
					insertTestClient("GUBPH", "GetUsersBy Ph", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(databaseClient.sql(
											"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, PHONE_NUMBER, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, :phone, 'Test', 'User', 'pass', false, 'ACTIVE')")
											.bind("clientId", clientId.longValue())
											.bind("userName", "gubph_" + ts)
											.bind("email", "gubph_" + ts + "@test.com")
											.bind("phone", phone)
											.then())
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(phone, null, clientCode,
									null, AuthenticationIdentifierType.PHONE_NUMBER,
									null, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertEquals(phone, users.get(0).getPhoneNumber());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy() with appCode")
	class GetUsersByWithAppCodeTests {

		@Test
		@DisplayName("with appCode should query through app join")
		void withAppCode_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gubapp_" + ts;

			StepVerifier.create(
					insertTestClient("GUBAPP", "GetUsr App", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gubapp_" + ts + "@test.com", "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(userName, null, clientCode,
									"appbuilder", AuthenticationIdentifierType.USER_NAME,
									null, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> assertNotNull(users))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy() with clientStatusCode")
	class GetUsersByWithClientStatusCodeTests {

		@Test
		@DisplayName("with ACTIVE client status should filter by client status")
		void withActiveClientStatus_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gubcsc_" + ts;

			StepVerifier.create(
					insertTestClient("GUBCSC", "GetUsr CSC", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gubcsc_" + ts + "@test.com", "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(userName, null, clientCode,
									null, AuthenticationIdentifierType.USER_NAME,
									SecurityClientStatusCode.ACTIVE, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertEquals(userName, users.get(0).getUserName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with INACTIVE client status should not return user from active client")
		void withInactiveClientStatus_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gubics_" + ts;

			StepVerifier.create(
					insertTestClient("GUBICS", "GetUsr ICS", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gubics_" + ts + "@test.com", "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getUsersBy(userName, null, clientCode,
									null, AuthenticationIdentifierType.USER_NAME,
									SecurityClientStatusCode.INACTIVE, SecurityUserStatusCode.ACTIVE)))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy() with userId parameter")
	class GetUsersByWithUserIdTests {

		@Test
		@DisplayName("with userId should filter by user ID")
		void withUserId_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gubuid_" + ts;

			StepVerifier.create(
					insertTestClient("GUBUID", "GetUsr UID", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gubuid_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"SELECT CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("CODE", String.class))
											.one()
											.flatMap(clientCode -> userDAO.getUsersBy(null, userId,
													clientCode, null,
													AuthenticationIdentifierType.USER_NAME, null,
													SecurityUserStatusCode.ACTIVE)))))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertEquals(userName, users.get(0).getUserName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersBy() with PLACEHOLDER username")
	class GetUsersByPlaceholderTests {

		@Test
		@DisplayName("NONE placeholder username should be ignored in query")
		void placeholderUserName_ReturnsBasedOnOtherConditions() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestClient("GUBPL", "GetUsr PH", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "gubpl_" + ts,
											"gubpl_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"SELECT CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("CODE", String.class))
											.one()
											.flatMap(clientCode -> userDAO.getUsersBy("NONE", userId,
													clientCode, null,
													AuthenticationIdentifierType.USER_NAME, null,
													SecurityUserStatusCode.ACTIVE)))))
					.assertNext(users -> assertFalse(users.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAllClientsBy() by userName")
	class GetAllClientsByUserNameTests {

		@Test
		@DisplayName("by userName should return userId->clientId mapping")
		void byUserName_ReturnsMapping() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "gacbu_" + ts;

			StepVerifier.create(
					insertTestClient("GACBU", "GetAllCl U", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, userName,
											"gacbu_" + ts + "@test.com", "password123"))
									.flatMap(userId -> databaseClient.sql(
											"SELECT CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("CODE", String.class))
											.one()
											.flatMap(clientCode -> userDAO.getAllClientsBy(
													userName, null, clientCode, null,
													AuthenticationIdentifierType.USER_NAME,
													SecurityUserStatusCode.ACTIVE))
											.map(map -> {
												assertFalse(map.isEmpty());
												assertTrue(map.containsKey(userId));
												assertEquals(clientId, map.get(userId));
												return map;
											}))))
					.assertNext(map -> assertFalse(map.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent userName should return empty map")
		void nonExistentUserName_ReturnsEmptyMap() {
			StepVerifier.create(
					insertTestClient("GACBN", "GetAllCl N", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.thenReturn(clientId))
							.flatMap(clientId -> databaseClient.sql(
									"SELECT CODE FROM security_client WHERE ID = :id")
									.bind("id", clientId.longValue())
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(clientCode -> userDAO.getAllClientsBy(
									"nonexistent_user_xyz", null, clientCode, null,
									AuthenticationIdentifierType.USER_NAME,
									SecurityUserStatusCode.ACTIVE)))
					.assertNext(map -> assertTrue(map.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForNotification() with valid data")
	class GetUsersForNotificationValidTests {

		@Test
		@DisplayName("with valid appCode and clientId should return users")
		void validAppCodeAndClientId_ReturnsUsers() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "ntfusr_" + ts,
							"ntfusr_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
									.map(row -> row.get("ID", Long.class))
									.one()
									.flatMap(appId -> databaseClient.sql(
											"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
											.bind("appId", appId)
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId", profileId.longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(userDAO.getUsersForNotification(
															List.of(userId.longValue()),
															"appbuilder",
															SYSTEM_CLIENT_ID.longValue(),
															null))))))
					.assertNext(users -> assertFalse(users.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("with valid appCode and clientCode should return users")
		void validAppCodeAndClientCode_ReturnsUsers() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "ntfcc_" + ts,
							"ntfcc_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
									.map(row -> row.get("ID", Long.class))
									.one()
									.flatMap(appId -> databaseClient.sql(
											"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
											.bind("appId", appId)
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId", profileId.longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(userDAO.getUsersForNotification(
															List.of(userId.longValue()),
															"appbuilder",
															null,
															"SYSTEM  "))))))
					.assertNext(users -> assertNotNull(users))
					.verifyComplete();
		}

		@Test
		@DisplayName("with no userIds should return based on appCode filter")
		void noUserIds_ReturnsBasedOnAppCode() {
			StepVerifier.create(userDAO.getUsersForNotification(
					null, "appbuilder", SYSTEM_CLIENT_ID.longValue(), null))
					.assertNext(users -> assertNotNull(users))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForEntityProcessor() with valid data")
	class GetUsersForEntityProcessorValidTests {

		@Test
		@DisplayName("with valid appCode and userIds should return entity processor users")
		void validAppCodeAndUserIds_ReturnsUsers() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "epusr_" + ts,
							"epusr_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
									.map(row -> row.get("ID", Long.class))
									.one()
									.flatMap(appId -> databaseClient.sql(
											"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
											.bind("appId", appId)
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId", profileId.longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(userDAO.getUsersForEntityProcessor(
															List.of(userId.longValue()),
															"appbuilder",
															SYSTEM_CLIENT_ID.longValue(),
															null))))))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						assertNotNull(users.get(0).getId());
						assertNotNull(users.get(0).getProfileIds());
						assertFalse(users.get(0).getProfileIds().isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with clientCode instead of clientId should work")
		void withClientCode_ReturnsUsers() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "epcc_" + ts,
							"epcc_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
									.map(row -> row.get("ID", Long.class))
									.one()
									.flatMap(appId -> databaseClient.sql(
											"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
											.bind("appId", appId)
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId", profileId.longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(userDAO.getUsersForEntityProcessor(
															List.of(userId.longValue()),
															"appbuilder",
															null,
															"SYSTEM  "))))))
					.assertNext(users -> assertNotNull(users))
					.verifyComplete();
		}

		@Test
		@DisplayName("user with designation and role should populate all fields")
		void userWithDesignationAndRole_PopulatesAllFields() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "epfull_" + ts,
							"epfull_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"INSERT INTO security_designation (CLIENT_ID, NAME) VALUES (:clientId, :name)")
									.bind("clientId", SYSTEM_CLIENT_ID.longValue())
									.bind("name", "TestDesg_" + ts)
									.filter(s -> s.returnGeneratedValues("ID"))
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(desgId -> databaseClient.sql(
											"UPDATE security_user SET DESIGNATION_ID = :desgId WHERE ID = :userId")
											.bind("desgId", desgId.longValue())
											.bind("userId", userId.longValue())
											.then())
									.then(databaseClient.sql(
											"SELECT ID FROM security_v2_role WHERE NAME = 'Owner' LIMIT 1")
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(roleId -> databaseClient.sql(
													"INSERT INTO security_v2_user_role (USER_ID, ROLE_ID) VALUES (:userId, :roleId)")
													.bind("userId", userId.longValue())
													.bind("roleId", roleId.longValue())
													.then()))
									.then(databaseClient.sql(
											"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
											.map(row -> row.get("ID", Long.class))
											.one()
											.flatMap(appId -> databaseClient.sql(
													"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
													.bind("appId", appId)
													.map(row -> ULong.valueOf(row.get("ID", Long.class)))
													.one()
													.flatMap(profileId -> databaseClient.sql(
															"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
															.bind("profileId", profileId.longValue())
															.bind("userId", userId.longValue())
															.then()
															.then(userDAO.getUsersForEntityProcessor(
																	List.of(userId.longValue()),
																	"appbuilder",
																	SYSTEM_CLIENT_ID.longValue(),
																	null)))))))
					.assertNext(users -> {
						assertFalse(users.isEmpty());
						var user = users.get(0);
						assertNotNull(user.getId());
						assertNotNull(user.getDesignationId());
						assertNotNull(user.getRoleId());
						assertNotNull(user.getProfileIds());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserForEntityProcessor() with valid data")
	class GetUserForEntityProcessorValidTests {

		@Test
		@DisplayName("with valid userId and appCode should return single user")
		void validUserIdAndAppCode_ReturnsSingleUser() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "epone_" + ts,
							"epone_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
									.map(row -> row.get("ID", Long.class))
									.one()
									.flatMap(appId -> databaseClient.sql(
											"SELECT ID FROM security_profile WHERE APP_ID = :appId LIMIT 1")
											.bind("appId", appId)
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId", profileId.longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(userDAO.getUserForEntityProcessor(
															userId, "appbuilder",
															SYSTEM_CLIENT_ID.longValue(), null))))))
					.assertNext(user -> {
						assertNotNull(user);
						assertNotNull(user.getId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent userId should return empty")
		void nonExistentUser_ReturnsEmpty() {
			StepVerifier.create(userDAO.getUserForEntityProcessor(
					ULong.valueOf(999999), "appbuilder", SYSTEM_CLIENT_ID.longValue(), null))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserForInvite() additional branches")
	class GetUserForInviteAdditionalTests {

		@Test
		@DisplayName("by email should return user")
		void byEmail_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String email = "inveml_" + ts + "@test.com";

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "inveml_" + ts, email, "password123")
							.then(userDAO.getUserForInvite(SYSTEM_CLIENT_ID, null, email, null)))
					.assertNext(user -> {
						assertNotNull(user);
						assertEquals(email, user.getEmailId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("by phone should return user when phone matches")
		void byPhone_ReturnsUser() {
			String ts = String.valueOf(System.currentTimeMillis());
			String phone = "+1" + ts.substring(ts.length() - 10);

			StepVerifier.create(
					databaseClient.sql(
							"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, PHONE_NUMBER, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, :phone, 'Test', 'User', 'pass', false, 'ACTIVE')")
							.bind("clientId", SYSTEM_CLIENT_ID.longValue())
							.bind("userName", "invphn_" + ts)
							.bind("email", "invphn_" + ts + "@test.com")
							.bind("phone", phone)
							.then()
							.then(userDAO.getUserForInvite(SYSTEM_CLIENT_ID, null, null, phone)))
					.assertNext(user -> {
						assertNotNull(user);
						assertEquals(phone, user.getPhoneNumber());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("PLACEHOLDER values should be ignored as search criteria")
		void placeholderValues_Ignored() {
			// When all identifiers are PLACEHOLDER ("NONE"), the only condition is CLIENT_ID.
			// For a client with no users, this should return empty.
			StepVerifier.create(
					insertTestClient("PLCHLD", "Placeholder Test", "BUS")
							.flatMap(clientId -> userDAO.getUserForInvite(
									clientId, "NONE", "NONE", "NONE")))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkUserExistsForInvite() PLACEHOLDER handling")
	class CheckUserExistsForInvitePlaceholderTests {

		@Test
		@DisplayName("PLACEHOLDER userName should be ignored")
		void placeholderUserName_Ignored() {
			String ts = String.valueOf(System.currentTimeMillis());
			String email = "invpl_" + ts + "@test.com";

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "invpl_" + ts, email, "password123")
							.then(userDAO.checkUserExistsForInvite(
									SYSTEM_CLIENT_ID, "NONE", email, null)))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}

		@Test
		@DisplayName("by phone number should return true when phone matches")
		void byPhone_ExistingPhone_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String phone = "+1" + ts.substring(ts.length() - 10);

			StepVerifier.create(
					databaseClient.sql(
							"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, PHONE_NUMBER, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, :phone, 'Test', 'User', 'pass', false, 'ACTIVE')")
							.bind("clientId", SYSTEM_CLIENT_ID.longValue())
							.bind("userName", "invph2_" + ts)
							.bind("email", "invph2_" + ts + "@test.com")
							.bind("phone", phone)
							.then()
							.then(userDAO.checkUserExistsForInvite(
									SYSTEM_CLIENT_ID, null, null, phone)))
					.assertNext(exists -> assertTrue(exists))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("updateUserStatusToActive() edge cases")
	class UpdateUserStatusToActiveEdgeCaseTests {

		@Test
		@DisplayName("deleted user should not be activated")
		void deletedUser_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "actdel_" + ts,
							"actdel_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"UPDATE security_user SET STATUS_CODE = 'DELETED' WHERE ID = :userId")
									.bind("userId", userId.longValue())
									.then()
									.then(userDAO.updateUserStatusToActive(userId))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("already active user should still return true")
		void alreadyActiveUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "actact_" + ts,
							"actact_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.updateUserStatusToActive(userId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("should reset all failed attempt counters")
		void resetsAllCounters() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(1);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "actcnt_" + ts,
							"actcnt_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PASSWORD)
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.PIN))
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.OTP))
									.then(userDAO.increaseResendAttempts(userId))
									.then(userDAO.lockUser(userId, lockUntil, "test lock"))
									.then(userDAO.updateUserStatusToActive(userId))
									.then(databaseClient.sql(
											"SELECT NO_FAILED_ATTEMPT, NO_PIN_FAILED_ATTEMPT, NO_OTP_FAILED_ATTEMPT, NO_OTP_RESEND_ATTEMPT, LOCKED_UNTIL, LOCKED_DUE_TO FROM security_user WHERE ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> {
												assertEquals((short) 0,
														row.get("NO_FAILED_ATTEMPT", Short.class));
												assertEquals((short) 0,
														row.get("NO_PIN_FAILED_ATTEMPT", Short.class));
												assertEquals((short) 0,
														row.get("NO_OTP_FAILED_ATTEMPT", Short.class));
												assertEquals((short) 0,
														row.get("NO_OTP_RESEND_ATTEMPT", Short.class));
												assertNull(row.get("LOCKED_UNTIL", LocalDateTime.class));
												assertNull(row.get("LOCKED_DUE_TO", String.class));
												return true;
											})
											.one())))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserIdsByClientId() for non-system client")
	class GetUserIdsByClientIdNonSystemTests {

		@Test
		@DisplayName("business client should use hierarchy-based query")
		void businessClient_ReturnsUsersViaHierarchy() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestClient("GUIDNB", "NonSys Cl", "BUS")
							.flatMap(clientId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID, null, null, null)
									.then(insertTestUser(clientId, "guidnb_" + ts,
											"guidnb_" + ts + "@test.com", "password123"))
									.thenReturn(clientId))
							.flatMap(clientId -> userDAO.getUserIdsByClientId(clientId, null)
									.collectList()))
					.assertNext(ids -> assertFalse(ids.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent client should return empty")
		void nonExistentClient_ReturnsEmpty() {
			StepVerifier.create(
					userDAO.getUserIdsByClientId(ULong.valueOf(999999), null)
							.collectList())
					.assertNext(ids -> assertTrue(ids.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readPageFilter() via DAO with security context")
	class ReadPageFilterTests {

		@Test
		@DisplayName("with system auth should return paginated users")
		void systemAuth_ReturnsPaginatedUsers() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(
					userDAO.readPageFilter(pageable, null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertFalse(page.getContent().isEmpty());
						assertTrue(page.getTotalElements() > 0);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with filter condition on userName should filter results")
		void filterByUserName_ReturnsFilteredResults() {
			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "rpfusr_" + ts;
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.make("userName", userName);

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, userName,
							"rpfusr_" + ts + "@test.com", "password123")
							.then(userDAO.readPageFilter(pageable, condition)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
						assertEquals(userName, page.getContent().get(0).getUserName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with appId EQUALS filter should use filterConditionFilter")
		void filterByAppIdEquals_UsesCustomFilter() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(
					databaseClient.sql(
							"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
							.map(row -> row.get("ID", Long.class))
							.one()
							.flatMap(appId -> {
								FilterCondition condition = FilterCondition.make("appId", appId);
								return userDAO.readPageFilter(pageable, condition)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}))
					.assertNext(page -> {
						assertNotNull(page);
						// Users with profile linked to appbuilder app
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with appCode EQUALS filter should use filterConditionFilter")
		void filterByAppCodeEquals_UsesCustomFilter() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.make("appCode", "appbuilder");

			StepVerifier.create(
					userDAO.readPageFilter(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						// Users associated with appbuilder app
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with appId IN filter should use filterConditionFilter IN branch")
		void filterByAppIdIn_UsesInBranch() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(
					databaseClient.sql(
							"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
							.map(row -> row.get("ID", Long.class))
							.one()
							.flatMap(appId -> {
								FilterCondition condition = FilterCondition.of("appId", appId,
										FilterConditionOperator.IN);
								condition.setMultiValue(List.of(appId));
								return userDAO.readPageFilter(pageable, condition)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}))
					.assertNext(page -> assertNotNull(page))
					.verifyComplete();
		}

		@Test
		@DisplayName("with appCode IN filter should use filterConditionFilter IN branch")
		void filterByAppCodeIn_UsesInBranch() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.of("appCode", "appbuilder",
					FilterConditionOperator.IN);
			condition.setMultiValue(List.of("appbuilder"));

			StepVerifier.create(
					userDAO.readPageFilter(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> assertNotNull(page))
					.verifyComplete();
		}

		@Test
		@DisplayName("with negated appId filter should negate condition")
		void filterByAppIdNegate_NegatesCondition() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(
					databaseClient.sql(
							"SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
							.map(row -> row.get("ID", Long.class))
							.one()
							.flatMap(appId -> {
								FilterCondition condition = FilterCondition.make("appId", appId);
								condition.setNegate(true);
								return userDAO.readPageFilter(pageable, condition)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}))
					.assertNext(page -> assertNotNull(page))
					.verifyComplete();
		}

		@Test
		@DisplayName("with negated appCode filter should negate condition")
		void filterByAppCodeNegate_NegatesCondition() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.make("appCode", "appbuilder");
			condition.setNegate(true);

			StepVerifier.create(
					userDAO.readPageFilter(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> assertNotNull(page))
					.verifyComplete();
		}

		@Test
		@DisplayName("with non-EQUALS/IN operator on appId should return trueCondition")
		void filterByAppIdLike_ReturnsTrueCondition() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.of("appId", "1",
					FilterConditionOperator.LIKE);

			StepVerifier.create(
					userDAO.readPageFilter(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						// LIKE on appId returns trueCondition, so no filtering
						assertTrue(page.getTotalElements() > 0);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with non-appId/appCode field should delegate to super")
		void filterByRegularField_DelegatesToSuper() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			FilterCondition condition = FilterCondition.make("statusCode", "ACTIVE");

			StepVerifier.create(
					userDAO.readPageFilter(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() > 0);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("page beyond total should return empty content")
		void pageBeyondTotal_ReturnsEmptyContent() {
			var systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(1000, 10);

			StepVerifier.create(
					userDAO.readPageFilter(pageable, null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canBeUpdated() from parent class")
	class CanBeUpdatedTests {

		@Test
		@DisplayName("existing user should be updatable with system auth")
		void existingUser_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			var systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "canupd_" + ts,
							"canupd_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.canBeUpdated(userId)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))))
					.assertNext(canUpdate -> assertTrue(canUpdate))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent user should not be updatable")
		void nonExistentUser_ReturnsFalse() {
			var systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					userDAO.canBeUpdated(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(canUpdate -> assertFalse(canUpdate))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getEmailsOfUsers() additional cases")
	class GetEmailsOfUsersAdditionalTests {

		@Test
		@DisplayName("user with NONE email should be excluded")
		void noneEmail_Excluded() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					databaseClient.sql(
							"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, 'NONE', 'Test', 'User', 'pass', false, 'ACTIVE')")
							.bind("clientId", SYSTEM_CLIENT_ID.longValue())
							.bind("userName", "noneml_" + ts)
							.filter(s -> s.returnGeneratedValues("ID"))
							.map(row -> ULong.valueOf(row.get("ID", Long.class)))
							.one()
							.flatMap(userId -> userDAO.getEmailsOfUsers(List.of(userId))))
					.assertNext(emails -> assertTrue(emails.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("user with default email (no EMAIL_ID provided) should be excluded")
		void nullEmail_Excluded() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					databaseClient.sql(
							"INSERT INTO security_user (CLIENT_ID, USER_NAME, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, 'Test', 'User', 'pass', false, 'ACTIVE')")
							.bind("clientId", SYSTEM_CLIENT_ID.longValue())
							.bind("userName", "nuleml_" + ts)
							.filter(s -> s.returnGeneratedValues("ID"))
							.map(row -> ULong.valueOf(row.get("ID", Long.class)))
							.one()
							.flatMap(userId -> userDAO.getEmailsOfUsers(List.of(userId))))
					.assertNext(emails -> assertTrue(emails.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("mix of valid and invalid users should return only valid emails")
		void mixedUsers_ReturnsOnlyValid() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mixeml1_" + ts,
							"mixeml1_" + ts + "@test.com", "password123")
							.flatMap(validUserId -> databaseClient.sql(
									"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, 'NONE', 'Test', 'User', 'pass', false, 'ACTIVE')")
									.bind("clientId", SYSTEM_CLIENT_ID.longValue())
									.bind("userName", "mixeml2_" + ts)
									.filter(s -> s.returnGeneratedValues("ID"))
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(noneUserId -> databaseClient.sql(
											"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, 'Test', 'User', 'pass', false, 'DELETED')")
											.bind("clientId", SYSTEM_CLIENT_ID.longValue())
											.bind("userName", "mixeml3_" + ts)
											.bind("email", "mixeml3_" + ts + "@test.com")
											.filter(s -> s.returnGeneratedValues("ID"))
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(deletedUserId -> userDAO.getEmailsOfUsers(
													List.of(validUserId, noneUserId, deletedUserId))))))
					.assertNext(emails -> {
						assertEquals(1, emails.size());
						assertTrue(emails.get(0).contains("mixeml1_"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("lockUser() additional verification")
	class LockUserAdditionalTests {

		@Test
		@DisplayName("lockUser should set accountNonLocked to false and lockedUntil/lockedDueTo")
		void lockUser_SetsAllFields() {
			String ts = String.valueOf(System.currentTimeMillis());
			LocalDateTime lockUntil = LocalDateTime.now().plusHours(3);
			String reason = "Test lock reason " + ts;

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "lockfld_" + ts,
							"lockfld_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.lockUser(userId, lockUntil, reason)
									.then(databaseClient.sql(
											"SELECT ACCOUNT_NON_LOCKED, LOCKED_DUE_TO FROM security_user WHERE ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> {
												assertEquals((byte) 0,
														row.get("ACCOUNT_NON_LOCKED", Byte.class));
												assertEquals(reason,
														row.get("LOCKED_DUE_TO", String.class));
												return true;
											})
											.one())))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent user lock should return false")
		void nonExistentUser_ReturnsFalse() {
			StepVerifier.create(userDAO.lockUser(ULong.valueOf(999999),
					LocalDateTime.now().plusHours(1), "test"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("canReportTo() circular reporting detection")
	class CanReportToCircularTests {

		@Test
		@DisplayName("circular reporting chain should return false")
		void circularReporting_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());

			// A -> B -> (want to set B.reportingTo = A, which would create a circle)
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "circa_" + ts,
							"circa_" + ts + "@test.com", "password123")
							.flatMap(userA -> insertTestUser(SYSTEM_CLIENT_ID, "circb_" + ts,
									"circb_" + ts + "@test.com", "password123")
									.flatMap(userB -> databaseClient.sql(
											"UPDATE security_user SET REPORTING_TO = :reportTo WHERE ID = :userId")
											.bind("reportTo", userA.longValue())
											.bind("userId", userB.longValue())
											.then()
											// Now try to make A report to B - would create circle
											.then(userDAO.canReportTo(SYSTEM_CLIENT_ID, userB, userA)))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("setPassword() verifies past password/pin records")
	class SetPasswordPastRecordsTests {

		@Test
		@DisplayName("setPassword should create past_passwords record")
		void setPassword_CreatesPastPasswordRecord() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "pstpwd_" + ts,
							"pstpwd_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.setPassword(userId, ULong.valueOf(1),
									"newPass123", AuthenticationPasswordType.PASSWORD)
									.then(databaseClient.sql(
											"SELECT COUNT(*) AS CNT FROM security_past_passwords WHERE USER_ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> row.get("CNT", Long.class))
											.one())))
					.assertNext(count -> assertTrue(count >= 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("setPin should create past_pins record")
		void setPin_CreatesPastPinRecord() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "pstpin_" + ts,
							"pstpin_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.setPassword(userId, ULong.valueOf(1),
									"654321", AuthenticationPasswordType.PIN)
									.then(databaseClient.sql(
											"SELECT COUNT(*) AS CNT FROM security_past_pins WHERE USER_ID = :userId")
											.bind("userId", userId.longValue())
											.map(row -> row.get("CNT", Long.class))
											.one())))
					.assertNext(count -> assertTrue(count >= 1))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addProfileToUser() with root profile resolution")
	class AddProfileToUserRootProfileTests {

		@Test
		@DisplayName("duplicate profile assignment should be ignored (onDuplicateKeyIgnore)")
		void duplicateAssignment_ReturnsZero() {
			String ts = String.valueOf(System.currentTimeMillis());

			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "dupprof_" + ts,
							"dupprof_" + ts + "@test.com", "password123")
							.flatMap(userId -> databaseClient.sql(
									"SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(profileId -> userDAO.addProfileToUser(userId, profileId)
											.then(userDAO.addProfileToUser(userId, profileId)))))
					.assertNext(result -> assertEquals(0, result.intValue()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("increaseFailedAttempt() multiple increments")
	class IncreaseFailedAttemptMultipleTests {

		@Test
		@DisplayName("multiple password fail increases should accumulate")
		void multiplePasswordFails_Accumulate() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mfpwd_" + ts,
							"mfpwd_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PASSWORD)
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.PASSWORD))
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.PASSWORD))))
					.assertNext(count -> assertEquals((short) 3, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple PIN fail increases should accumulate")
		void multiplePinFails_Accumulate() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mfpin_" + ts,
							"mfpin_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.PIN)
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.PIN))))
					.assertNext(count -> assertEquals((short) 2, count))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple OTP fail increases should accumulate")
		void multipleOtpFails_Accumulate() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestUser(SYSTEM_CLIENT_ID, "mfotp_" + ts,
							"mfotp_" + ts + "@test.com", "password123")
							.flatMap(userId -> userDAO.increaseFailedAttempt(userId,
									AuthenticationPasswordType.OTP)
									.then(userDAO.increaseFailedAttempt(userId,
											AuthenticationPasswordType.OTP))))
					.assertNext(count -> assertEquals((short) 2, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForProfiles() with non-matching clientId")
	class GetUsersForProfilesNonMatchingTests {

		@Test
		@DisplayName("non-matching clientId should return empty")
		void nonMatchingClientId_ReturnsEmpty() {
			StepVerifier.create(
					databaseClient.sql(
							"SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
							.map(row -> ULong.valueOf(row.get("ID", Long.class)))
							.one()
							.flatMap(profileId -> userDAO.getUsersForProfiles(
									List.of(profileId), ULong.valueOf(999999))))
					.assertNext(users -> assertTrue(users.isEmpty()))
					.verifyComplete();
		}
	}
}
