package com.modlix.saas.commons2.security.util;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;

import jakarta.servlet.http.HttpServletRequest;
import com.modlix.saas.commons2.util.Tuples;

public class ServerHttpRequestUtil {

    public static Tuples.Tuple2<Boolean, String> extractBasicNBearerToken(HttpServletRequest request) {

        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (bearerToken == null || bearerToken.isBlank()) {
            // Check cookies for authorization token
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if (HttpHeaders.AUTHORIZATION.equals(cookie.getName())) {
                        bearerToken = cookie.getValue();
                        break;
                    }
                }
            }
        }

        boolean isBasic = false;
        if (bearerToken != null) {

            bearerToken = bearerToken.trim();
            String smallCaseBearerToken = bearerToken.toLowerCase();

            if (smallCaseBearerToken.startsWith("bearer ")) {
                bearerToken = bearerToken.substring(7);
            } else if (smallCaseBearerToken.startsWith("basic ")) {
                isBasic = true;
                bearerToken = bearerToken.substring(6);
            }
        }

        return Tuples.of(isBasic, bearerToken == null ? "" : bearerToken);
    }

    private ServerHttpRequestUtil() {

    }
}
