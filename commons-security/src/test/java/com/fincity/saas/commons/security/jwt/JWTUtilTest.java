package com.fincity.saas.commons.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.security.jwt.JWTUtil.JWTGenerateTokenParameters;

/**
 * Two mints with identical inputs in the same second used to be the same string, so a revoke
 * that deleted one row left a twin row authenticating the "revoked" token.
 */
class JWTUtilTest {

	private static final String KEY = "a_test_key_that_is_long_enough_for_hs512_signing_0123456789_0123456789_0123456789";

	private static JWTGenerateTokenParameters params() {
		return JWTGenerateTokenParameters.builder()
				.userId(BigInteger.valueOf(142))
				.secretKey(KEY)
				.expiryInMin(30)
				.host("leadzump.local")
				.port("8080")
				.loggedInClientId(BigInteger.valueOf(7))
				.loggedInClientCode("CLIENT01")
				.appCode("leadzump")
				.build();
	}

	@Test
	void sameInputsInTheSameSecondGiveDifferentTokens() {
		String a = JWTUtil.generateToken(params()).getT1();
		String b = JWTUtil.generateToken(params()).getT1();

		assertNotEquals(a, b);
	}

	@Test
	void theIdDoesNotDisturbTheClaims() {
		String token = JWTUtil.generateToken(params()).getT1();

		JWTClaims claims = JWTUtil.getClaimsFromToken(KEY, token);

		assertEquals(BigInteger.valueOf(142), claims.getUserId());
		assertEquals("leadzump.local", claims.getHostName());
		assertEquals("leadzump", claims.getAppCode());
		assertEquals("CLIENT01", claims.getLoggedInClientCode());
	}

	@Test
	void theIdIsShort() {
		// The token column is VARCHAR(512); the id must not eat the headroom.
		assertEquals(16, JWTUtil.newTokenId().length());
		assertTrue(JWTUtil.generateToken(params()).getT1().length() < 512);
	}
}
