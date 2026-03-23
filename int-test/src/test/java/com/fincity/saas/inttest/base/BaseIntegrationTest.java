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

        RestAssured.baseURI = baseUrl();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected String baseUrl() {
        return envOrProp("BASE_URL", "base.url", "http://localhost:8080");
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
     * Returns a RestAssured RequestSpecification pre-configured with auth token and
     * required headers (clientCode, appCode, forwarded host/IP).
     */
    protected RequestSpecification givenAuth(String token, String clientCode, String appCode) {
        return given()
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", "localhost")
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json");
    }

    /**
     * Returns a RestAssured RequestSpecification with no auth (for login endpoints).
     */
    protected RequestSpecification givenNoAuth(String clientCode, String appCode) {
        return given()
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", "localhost")
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json");
    }
}
