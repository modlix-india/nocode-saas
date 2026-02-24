package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.UserInviteDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserInviteDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserInviteDAO userInviteDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_user_invite WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertTestInvite(ULong clientId, String userName, String email, String inviteCode) {
		return databaseClient.sql(
				"INSERT INTO security_user_invite (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, INVITE_CODE) VALUES (:clientId, :userName, :email, 'Test', 'User', :inviteCode)")
				.bind("clientId", clientId.longValue())
				.bind("userName", userName)
				.bind("email", email)
				.bind("inviteCode", inviteCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("getUserInvitation()")
	class GetUserInvitationTests {

		@Test
		@DisplayName("existing code should return the invite with correct fields")
		void existingCode_ReturnsInvite() {
			String ts = String.valueOf(System.currentTimeMillis());
			String inviteCode = "inv-code-" + ts.substring(ts.length() - 8);
			String userName = "invusr_" + ts;
			String email = "invusr_" + ts + "@test.com";

			StepVerifier.create(
					insertTestInvite(SYSTEM_CLIENT_ID, userName, email, inviteCode)
							.then(userInviteDAO.getUserInvitation(inviteCode)))
					.assertNext(invite -> {
						assertNotNull(invite);
						assertEquals(inviteCode, invite.getInviteCode());
						assertEquals(userName, invite.getUserName());
						assertEquals(email, invite.getEmailId());
						assertEquals("Test", invite.getFirstName());
						assertEquals("User", invite.getLastName());
						assertEquals(SYSTEM_CLIENT_ID, invite.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existing code should return empty")
		void nonExistingCode_ReturnsEmpty() {
			StepVerifier.create(userInviteDAO.getUserInvitation("nonexistent-invite-code-xyz"))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple invites should return the correct one by code")
		void multipleInvites_ReturnsCorrectOne() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code1 = "code1-" + ts.substring(ts.length() - 8);
			String code2 = "code2-" + ts.substring(ts.length() - 8);
			String code3 = "code3-" + ts.substring(ts.length() - 8);

			StepVerifier.create(
					insertTestInvite(SYSTEM_CLIENT_ID, "user1_" + ts, "user1_" + ts + "@test.com", code1)
							.then(insertTestInvite(SYSTEM_CLIENT_ID, "user2_" + ts, "user2_" + ts + "@test.com", code2))
							.then(insertTestInvite(SYSTEM_CLIENT_ID, "user3_" + ts, "user3_" + ts + "@test.com", code3))
							.then(userInviteDAO.getUserInvitation(code2)))
					.assertNext(invite -> {
						assertNotNull(invite);
						assertEquals(code2, invite.getInviteCode());
						assertEquals("user2_" + ts, invite.getUserName());
						assertEquals("user2_" + ts + "@test.com", invite.getEmailId());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteUserInvitation()")
	class DeleteUserInvitationTests {

		@Test
		@DisplayName("existing code should delete and return true")
		void existingCode_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String inviteCode = "del-code-" + ts.substring(ts.length() - 8);

			StepVerifier.create(
					insertTestInvite(SYSTEM_CLIENT_ID, "delusr_" + ts, "delusr_" + ts + "@test.com", inviteCode)
							.then(userInviteDAO.deleteUserInvitation(inviteCode)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// Verify the invite is actually gone
			StepVerifier.create(userInviteDAO.getUserInvitation(inviteCode))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existing code should return false")
		void nonExistingCode_ReturnsFalse() {
			StepVerifier.create(userInviteDAO.deleteUserInvitation("nonexistent-del-code-xyz"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}
}
