package com.fincity.saas.inttest.base;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;

/**
 * Authenticates against the security service and caches tokens per user key.
 */
public class AuthHelper {

    private static final String AUTH_ENDPOINT = "/api/security/authenticate";

    private static final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    /**
     * Authenticate and return an access token. Tokens are cached by a composite key
     * of clientCode + appCode + userName so repeated calls don't hit the API again.
     */
    public static String authenticate(String clientCode, String appCode, String userName, String password) {
        String cacheKey = clientCode + "|" + appCode + "|" + userName;

        return tokenCache.computeIfAbsent(cacheKey, k -> {
            String body = String.format("{\"userName\":\"%s\",\"password\":\"%s\"}", userName, password);

            String token = given()
                    .header("clientCode", clientCode)
                    .header("appCode", appCode)
                    .header("X-Forwarded-Host", "localhost")
                    .header("X-Real-IP", "127.0.0.1")
                    .contentType("application/json")
                    .body(body)
                    .post(AUTH_ENDPOINT)
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("accessToken");

            if (token == null || token.isBlank()) {
                throw new RuntimeException("Authentication failed for user: " + userName);
            }
            return token;
        });
    }

    /**
     * Clear the token cache (useful if tokens expire mid-test suite).
     */
    public static void clearCache() {
        tokenCache.clear();
    }
}
