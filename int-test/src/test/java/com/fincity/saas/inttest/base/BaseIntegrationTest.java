package com.fincity.saas.inttest.base;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static io.restassured.RestAssured.given;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    protected static final Properties props = new Properties();

    @BeforeAll
    void loadConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("inttest.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load inttest.properties", e);
        }

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * Base host without trailing slash.
     * e.g. "https://apps.local.modlix.com"
     */
    protected String baseHost() {
        String host = envOrProp("BASE_HOST", "base.host", "https://apps.local.modlix.com");
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    /**
     * Build the full base URL for a given appCode and clientCode.
     * URL pattern: https://apps.local.modlix.com/{appCode}/{clientCode}/page
     */
    protected String baseUrl(String appCode, String clientCode) {
        return baseHost() + "/" + appCode + "/" + clientCode + "/page";
    }

    protected String prop(String key) {
        return props.getProperty(key, "");
    }

    protected String envOrProp(String envKey, String propKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env;

        String sysProp = System.getProperty(envKey);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;

        String val = props.getProperty(propKey, defaultValue);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    /**
     * Returns a RestAssured RequestSpecification pre-configured with auth token,
     * base URI scoped to the given appCode/clientCode, and required headers.
     */
    protected RequestSpecification givenAuth(String token, String clientCode, String appCode) {
        return given()
                .baseUri(baseUrl(appCode, clientCode))
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", baseHost().replaceFirst("https?://", ""))
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json");
    }

    /**
     * Returns a RestAssured RequestSpecification with no auth (for registration/login).
     * Uses the base host directly (no appCode/clientCode in path).
     */
    protected RequestSpecification givenNoAuth(String clientCode, String appCode) {
        return given()
                .baseUri(baseUrl(appCode, clientCode))
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", baseHost().replaceFirst("https?://", ""))
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json");
    }
}
