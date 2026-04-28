package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TokenDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TokenDAO tokenDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
		databaseClient.sql("DELETE FROM security_user_token").then().block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_user_token").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_activity WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertTokenViaSQL(ULong userId, String token, String partToken, String ipAddress,
			LocalDateTime expiresAt) {
		return insertTokenViaSQL(userId, token, partToken, ipAddress, expiresAt, null);
	}

	private Mono<ULong> insertTokenViaSQL(ULong userId, String token, String partToken, String ipAddress,
			LocalDateTime expiresAt, LocalDateTime lastUsedAt) {
		String sql = "INSERT INTO security_user_token (USER_ID, TOKEN, PART_TOKEN, IP_ADDRESS, EXPIRES_AT, LAST_USED_AT) VALUES (:userId, :token, :partToken, :ip, :expiresAt, :lastUsedAt)";
		var spec = databaseClient.sql(sql)
				.bind("userId", userId.longValue())
				.bind("token", token)
				.bind("partToken", partToken)
				.bind("ip", ipAddress)
				.bind("expiresAt", expiresAt);
		spec = lastUsedAt != null ? spec.bind("lastUsedAt", lastUsedAt) : spec.bindNull("lastUsedAt", LocalDateTime.class);
		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("CreateAndReadToken")
	class CreateAndReadTokenTests {

		@Test
		@DisplayName("should insert token via SQL and read it back via readById")
		void insertViaSQLAndReadById() {
			String uniqueToken = "test-token-" + UUID.randomUUID();

			Mono<TokenObject> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "tokenuser1",
					"tokenuser1@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, uniqueToken, "part-read", "127.0.0.1",
							LocalDateTime.now().plusHours(1))
							.flatMap(tokenId -> tokenDAO.readById(tokenId)));

			StepVerifier.create(pipeline)
					.assertNext(tokenObj -> {
						assertNotNull(tokenObj);
						assertNotNull(tokenObj.getId());
						assertEquals(uniqueToken, tokenObj.getToken());
						assertEquals("part-read", tokenObj.getPartToken());
						assertEquals("127.0.0.1", tokenObj.getIpAddress());
						assertNotNull(tokenObj.getExpiresAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should create token via DAO and verify via getTokensOfId")
		void createViaDaoAndVerifyViaGetTokensOfId() {
			String uniqueToken = "dao-token-" + UUID.randomUUID();

			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "daotokenuser",
					"daotokenuser@test.com", "password123")
					.flatMap(userId -> {
						TokenObject token = new TokenObject();
						token.setUserId(userId);
						token.setToken(uniqueToken);
						token.setPartToken("dao-part");
						token.setIpAddress("192.168.1.1");
						token.setExpiresAt(LocalDateTime.now().plusHours(2));
						return tokenDAO.create(token).thenReturn(userId);
					})
					.flatMapMany(userId -> tokenDAO.getTokensOfId(userId))
					.collectList();

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertNotNull(tokens);
						assertFalse(tokens.isEmpty());
						assertTrue(tokens.contains(uniqueToken));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("GetTokensOfId")
	class GetTokensOfIdTests {

		@Test
		@DisplayName("should return all tokens for a user with multiple tokens")
		void multipleTokensForUser() {
			String token1 = "multi-token-1-" + UUID.randomUUID();
			String token2 = "multi-token-2-" + UUID.randomUUID();
			String token3 = "multi-token-3-" + UUID.randomUUID();

			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "multiuser",
					"multiuser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, token1, "part1", "127.0.0.1",
							LocalDateTime.now().plusHours(1))
							.then(insertTokenViaSQL(userId, token2, "part2", "127.0.0.2",
									LocalDateTime.now().plusHours(2)))
							.then(insertTokenViaSQL(userId, token3, "part3", "127.0.0.3",
									LocalDateTime.now().plusHours(3)))
							.thenReturn(userId))
					.flatMapMany(userId -> tokenDAO.getTokensOfId(userId))
					.collectList();

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertNotNull(tokens);
						assertEquals(3, tokens.size());
						assertTrue(tokens.contains(token1));
						assertTrue(tokens.contains(token2));
						assertTrue(tokens.contains(token3));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty for user with no tokens")
		void noTokensForUser() {

			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "notokenuser",
					"notokenuser@test.com", "password123")
					.flatMapMany(userId -> tokenDAO.getTokensOfId(userId))
					.collectList();

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertNotNull(tokens);
						assertTrue(tokens.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("DeleteAllTokens")
	class DeleteAllTokensTests {

		@Test
		@DisplayName("should delete all tokens for a user and return the count")
		void deleteAllTokensReturnsCount() {

			Mono<Integer> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "deluser",
					"deluser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "del-token-1-" + UUID.randomUUID(), "part1",
							"127.0.0.1", LocalDateTime.now().plusHours(1))
							.then(insertTokenViaSQL(userId, "del-token-2-" + UUID.randomUUID(), "part2",
									"127.0.0.2", LocalDateTime.now().plusHours(2)))
							.thenReturn(userId))
					.flatMap(userId -> tokenDAO.deleteAllTokens(userId));

			StepVerifier.create(pipeline)
					.assertNext(count -> {
						assertNotNull(count);
						assertEquals(2, count);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should confirm tokens are gone after deleteAllTokens")
		void tokensGoneAfterDelete() {

			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "delverify",
					"delverify@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "gone-token-1-" + UUID.randomUUID(), "part1",
							"127.0.0.1", LocalDateTime.now().plusHours(1))
							.then(insertTokenViaSQL(userId, "gone-token-2-" + UUID.randomUUID(), "part2",
									"127.0.0.2", LocalDateTime.now().plusHours(2)))
							.thenReturn(userId))
					.flatMap(userId -> tokenDAO.deleteAllTokens(userId).thenReturn(userId))
					.flatMapMany(userId -> tokenDAO.getTokensOfId(userId))
					.collectList();

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertNotNull(tokens);
						assertTrue(tokens.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("UpdateLastUsedAt")
	class UpdateLastUsedAtTests {

		@Test
		@DisplayName("should update LAST_USED_AT for existing token")
		void updateLastUsedAt_ExistingToken_UpdatesTimestamp() {
			String uniqueToken = "lastused-token-" + UUID.randomUUID();

			Mono<TokenObject> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "lastuseruser",
					"lastuseruser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, uniqueToken, "part-lu", "127.0.0.1",
							LocalDateTime.now().plusHours(1)))
					.flatMap(tokenId -> tokenDAO.updateLastUsedAt(tokenId)
							.then(tokenDAO.readById(tokenId)));

			StepVerifier.create(pipeline)
					.assertNext(tokenObj -> {
						assertNotNull(tokenObj);
						assertNotNull(tokenObj.getLastUsedAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return 0 for non-existent token")
		void updateLastUsedAt_NonExistent_ReturnsZero() {
			ULong nonExistentId = ULong.valueOf(999999);

			StepVerifier.create(tokenDAO.updateLastUsedAt(nonExistentId))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("DeleteExpiredTokens")
	class DeleteExpiredTokensTests {

		@Test
		@DisplayName("should delete only expired tokens")
		void deleteExpiredTokens_OnlyDeletesExpired() {
			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "expuser",
					"expuser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "expired-" + UUID.randomUUID(), "part-exp",
							"127.0.0.1", LocalDateTime.now().minusHours(1))
							.then(insertTokenViaSQL(userId, "valid-" + UUID.randomUUID(), "part-val",
									"127.0.0.1", LocalDateTime.now().plusHours(1)))
							.thenReturn(userId))
					.flatMap(userId -> tokenDAO.deleteExpiredTokens()
							.flatMap(count -> {
								assertEquals(1, count);
								return tokenDAO.getTokensOfId(userId).collectList();
							}));

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertEquals(1, tokens.size());
						assertTrue(tokens.getFirst().startsWith("valid-"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return 0 when no tokens are expired")
		void deleteExpiredTokens_NoneExpired_ReturnsZero() {
			Mono<Integer> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "noexpuser",
					"noexpuser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "active-" + UUID.randomUUID(), "part-act",
							"127.0.0.1", LocalDateTime.now().plusHours(1)))
					.then(tokenDAO.deleteExpiredTokens());

			StepVerifier.create(pipeline)
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("DeleteUnusedTokens")
	class DeleteUnusedTokensTests {

		@Test
		@DisplayName("should delete tokens with LAST_USED_AT older than cutoff")
		void deleteUnusedTokens_DeletesOldUsed() {
			LocalDateTime oldUsage = LocalDateTime.now().minusDays(100);
			LocalDateTime recentUsage = LocalDateTime.now().minusDays(10);

			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "unuseduser",
					"unuseduser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "old-used-" + UUID.randomUUID(), "part-old",
							"127.0.0.1", LocalDateTime.now().plusHours(1), oldUsage)
							.then(insertTokenViaSQL(userId, "recent-used-" + UUID.randomUUID(), "part-rec",
									"127.0.0.1", LocalDateTime.now().plusHours(1), recentUsage))
							.thenReturn(userId))
					.flatMap(userId -> tokenDAO.deleteUnusedTokens(90)
							.flatMap(count -> {
								assertEquals(1, count);
								return tokenDAO.getTokensOfId(userId).collectList();
							}));

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertEquals(1, tokens.size());
						assertTrue(tokens.getFirst().startsWith("recent-used-"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should not delete tokens with NULL LAST_USED_AT")
		void deleteUnusedTokens_IgnoresNullLastUsedAt() {
			Mono<List<String>> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "nullluuser",
					"nullluuser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "null-lu-" + UUID.randomUUID(), "part-null",
							"127.0.0.1", LocalDateTime.now().plusHours(1), null)
							.thenReturn(userId))
					.flatMap(userId -> tokenDAO.deleteUnusedTokens(90)
							.flatMap(count -> {
								assertEquals(0, count);
								return tokenDAO.getTokensOfId(userId).collectList();
							}));

			StepVerifier.create(pipeline)
					.assertNext(tokens -> {
						assertEquals(1, tokens.size());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should not delete tokens used within the cutoff period")
		void deleteUnusedTokens_KeepsRecentlyUsed() {
			LocalDateTime recentUsage = LocalDateTime.now().minusDays(5);

			Mono<Integer> pipeline = insertTestUser(SYSTEM_CLIENT_ID, "recentuser",
					"recentuser@test.com", "password123")
					.flatMap(userId -> insertTokenViaSQL(userId, "recent-" + UUID.randomUUID(), "part-recent",
							"127.0.0.1", LocalDateTime.now().plusHours(1), recentUsage))
					.then(tokenDAO.deleteUnusedTokens(90));

			StepVerifier.create(pipeline)
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}
}
