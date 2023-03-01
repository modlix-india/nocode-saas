package com.fincity.saas.common.security.util;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class ServerHttpRequestUtil {
	
	public static Tuple2<Boolean, String> extractBasicNBearerToken(ServerHttpRequest request) {

		String bearerToken = request.getHeaders()
		        .getFirst(HttpHeaders.AUTHORIZATION);

		if (bearerToken == null || bearerToken.isBlank()) {
			HttpCookie cookie = request.getCookies()
			        .getFirst(HttpHeaders.AUTHORIZATION);
			if (cookie != null)
				bearerToken = cookie.getValue();
		}

		boolean isBasic = false;
		if (bearerToken != null) {

			bearerToken = bearerToken.trim();

			if (bearerToken.startsWith("Bearer ")) {
				bearerToken = bearerToken.substring(7);
			} else if (bearerToken.startsWith("basic ")) {
				isBasic = true;
				bearerToken = bearerToken.substring(6);
			}
		}

		return Tuples.of(isBasic, bearerToken == null ? "" : bearerToken);
	}

	private ServerHttpRequestUtil() {
		
	}
}
