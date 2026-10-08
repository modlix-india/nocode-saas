package com.fincity.security.integration;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.OneTimeTokenDAO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Redemption against a real MySQL: the age limit is computed by the database, and only the call
 * whose DELETE removed the row gets a session.
 */
class OneTimeTokenRedemptionIntegrationTest extends AbstractIntegrationTest {

	private static final ULong SYSTEM_USER_ID = ULong.valueOf(1);

	@Autowired
	private OneTimeTokenDAO dao;

	@AfterEach
	void cleanup() {
		databaseClient.sql("DELETE FROM security_one_time_token WHERE TOKEN LIKE 'ott-it-%'").then().block();
	}

	private Mono<Void> insert(String token, int minutesAgo) {
		return databaseClient.sql(
				"INSERT INTO security_one_time_token (USER_ID, TOKEN, IP_ADDRESS, CREATED_AT) "
						+ "VALUES (:userId, :token, '127.0.0.1', CURRENT_TIMESTAMP - INTERVAL :ago MINUTE)")
				.bind("userId", SYSTEM_USER_ID.longValue())
				.bind("token", token)
				.bind("ago", minutesAgo)
				.then();
	}

	private Mono<Long> remaining(String token) {
		return databaseClient.sql("SELECT COUNT(*) AS C FROM security_one_time_token WHERE TOKEN = :token")
				.bind("token", token)
				.map(row -> row.get("C", Long.class))
				.one();
	}

	@Test
	void freshTokenRedeemsOnce() {
		String token = "ott-it-fresh";

		StepVerifier.create(insert(token, 0).then(dao.readOneTimeTokenAndDeleteBy(token, 5)))
				.expectNextMatches(t -> token.equals(t.getToken()) && SYSTEM_USER_ID.equals(t.getUserId()))
				.verifyComplete();

		StepVerifier.create(dao.readOneTimeTokenAndDeleteBy(token, 5)).verifyComplete();
	}

	@Test
	void staleTokenIsDeletedButDoesNotRedeem() {
		String token = "ott-it-stale";

		StepVerifier.create(insert(token, 10).then(dao.readOneTimeTokenAndDeleteBy(token, 5)))
				.verifyComplete();

		StepVerifier.create(remaining(token)).expectNext(0L).verifyComplete();
	}

	@Test
	void concurrentRedemptionsGiveOneSession() {
		String token = "ott-it-race";

		insert(token, 0).block();

		StepVerifier.create(Flux.range(0, 8)
				.flatMap(i -> dao.readOneTimeTokenAndDeleteBy(token, 5), 8)
				.count())
				.expectNext(1L)
				.verifyComplete();
	}
}
