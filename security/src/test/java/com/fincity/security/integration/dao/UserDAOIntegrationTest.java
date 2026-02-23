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

import com.fincity.security.dao.UserDAO;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;

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
}
