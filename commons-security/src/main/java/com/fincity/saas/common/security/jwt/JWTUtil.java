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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class JWTUtil {

	public static final Tuple2<String, LocalDateTime> generateToken(BigInteger userId, String secretKey,
	        Integer expiryInMin, String host, String port, BigInteger loggedInClientId, String loggedInClientCode) {

		LocalDateTime expirationTime = LocalDateTime.now(ZoneId.of("UTC"))
		        .plus(expiryInMin, ChronoUnit.MINUTES);

		return Tuples.of(Jwts.builder()
		        .setIssuer("fincity")
		        .setSubject(userId.toString())
		        .setClaims(new JWTClaims().setUserId(userId)
		                .setHostName(host)
		                .setPort(port)
		                .setLoggedInClientId(loggedInClientId)
		                .setLoggedInClientCode(loggedInClientCode)
		                .getClaimsMap())
		        .setIssuedAt(Date.from(Instant.now()))
		        .setExpiration(Date.from(Instant.now()
		                .plus(expiryInMin, ChronoUnit.MINUTES)))
		        .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()), SignatureAlgorithm.HS512)
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
}
