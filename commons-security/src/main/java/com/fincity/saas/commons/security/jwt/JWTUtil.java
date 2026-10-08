package com.fincity.saas.commons.security.jwt;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Builder;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class JWTUtil {

	public static final Tuple2<String, LocalDateTime> generateToken(JWTGenerateTokenParameters params) {

		LocalDateTime expirationTime = LocalDateTime.now(ZoneId.of("UTC"))
				.plus(params.expiryInMin, ChronoUnit.MINUTES);

		return Tuples.of(Jwts.builder()
				.setIssuer("fincity")
				.setSubject(params.userId.toString())
				.setClaims(new JWTClaims().setUserId(params.userId)
						.setHostName(params.host)
						.setPort(params.port)
						.setLoggedInClientId(params.loggedInClientId)
						.setLoggedInClientCode(params.loggedInClientCode)
						.setAppCode(params.appCode)
						.setOneTime(params.oneTime)
						.getClaimsMap())
				// A random id, so no two mints are ever the same string. Everything else in a
				// token is (user, host, port, client, app, iat and exp to the second), so two
				// mints for the same user and app in one second -- a one-time-token fork right
				// after a login, two forks at once -- were byte-identical: two DB rows for one
				// string, and revoking "one" of them left the other authenticating it.
				.setId(newTokenId())
				.setIssuedAt(Date.from(Instant.now()))
				.setExpiration(Date.from(Instant.now()
						.plus(params.expiryInMin, ChronoUnit.MINUTES)))
				.signWith(Keys.hmacShaKeyFor(params.secretKey.getBytes()), SignatureAlgorithm.HS512)
				.compact(), expirationTime);
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	/** 12 random bytes, 16 URL-safe characters: kept short, the token column is VARCHAR(512). */
	static String newTokenId() {
		byte[] bytes = new byte[12];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static final JWTClaims getClaimsFromToken(String secretKey, String token) {

		JwtParser parser = Jwts.parserBuilder()
				.setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
				.build();

		Jws<Claims> parsed = parser.parseClaimsJws(token);

		return JWTClaims.from(parsed);
	}

	private JWTUtil() {
	}

	@Builder
	public static class JWTGenerateTokenParameters {

		BigInteger userId;
		String secretKey;
		Integer expiryInMin;
		String host;
		String port;
		BigInteger loggedInClientId;
		String loggedInClientCode;
		String appCode;

		@Builder.Default
		boolean oneTime = false;
	}
}
