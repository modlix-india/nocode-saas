package com.fincity.saas.common.security.jwt;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
		                .setOneTime(params.oneTime)
		                .getClaimsMap())
		        .setIssuedAt(Date.from(Instant.now()))
		        .setExpiration(Date.from(Instant.now()
		                .plus(params.expiryInMin, ChronoUnit.MINUTES)))
		        .signWith(Keys.hmacShaKeyFor(params.secretKey.getBytes()), SignatureAlgorithm.HS512)
		        .compact(), expirationTime);
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

		@Builder.Default
		boolean oneTime = false;
	}
}
