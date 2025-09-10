package com.modlix.saas.commons2.security;

import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.dto.Client;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.jwt.JWTClaims;
import com.modlix.saas.commons2.security.jwt.JWTUtil;
import com.modlix.saas.commons2.security.model.UserResponse;
import com.modlix.saas.commons2.security.util.LogUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Commons2SecurityTest {

    @Test
    public void testAppCreation() {
        App app = new App()
                .setId(BigInteger.ONE)
                .setClientId(BigInteger.TEN)
                .setAppName("Test App")
                .setAppCode("TEST_APP")
                .setAppType("WEB")
                .setAppAccessType("PUBLIC")
                .setTemplate(false);

        assertNotNull(app);
        assertEquals("Test App", app.getAppName());
        assertEquals("TEST_APP", app.getAppCode());
    }

    @Test
    public void testClientCreation() {
        Client client = new Client();
        client.setId(BigInteger.ONE);
        client.setCode("TEST_CLIENT");
        client.setName("Test Client");
        client.setTypeCode("ENTERPRISE");

        assertNotNull(client);
        assertEquals("TEST_CLIENT", client.getCode());
        assertEquals("Test Client", client.getName());
    }

    @Test
    public void testUserResponseCreation() {
        UserResponse user = new UserResponse()
                .setId(BigInteger.ONE)
                .setClientId(BigInteger.TEN)
                .setUserName("testuser")
                .setEmailId("test@example.com")
                .setFirstName("Test")
                .setLastName("User");

        assertNotNull(user);
        assertEquals("testuser", user.getUserName());
        assertEquals("test@example.com", user.getEmailId());
    }

    @Test
    public void testContextUserCreation() {
        ContextUser user = new ContextUser()
                .setId(BigInteger.ONE)
                .setClientId(BigInteger.TEN)
                .setUserName("testuser")
                .setEmailId("test@example.com")
                .setFirstName("Test")
                .setLastName("User")
                .setStringAuthorities(List.of("ROLE_USER", "ROLE_ADMIN"));

        assertNotNull(user);
        assertEquals("testuser", user.getUserName());
        assertNotNull(user.getAuthorities());
        assertEquals(2, user.getAuthorities().size());
    }

    @Test
    public void testContextAuthenticationCreation() {
        ContextUser user = new ContextUser()
                .setId(BigInteger.ONE)
                .setClientId(BigInteger.TEN)
                .setUserName("testuser")
                .setStringAuthorities(List.of("ROLE_USER"));

        ContextAuthentication auth = new ContextAuthentication()
                .setUser(user)
                .setClientCode("TEST_CLIENT")
                .setUrlClientCode("TEST_CLIENT")
                .setUrlAppCode("TEST_APP");
        auth.setAuthenticated(true);

        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        assertEquals("TEST_CLIENT", auth.getClientCode());
        assertEquals(user, auth.getPrincipal());
    }

    @Test
    public void testJWTClaimsCreation() {
        JWTClaims claims = new JWTClaims()
                .setUserId(BigInteger.ONE)
                .setHostName("localhost")
                .setPort("8080")
                .setLoggedInClientId(BigInteger.TEN)
                .setLoggedInClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setOneTime(false);

        assertNotNull(claims);
        assertEquals(BigInteger.ONE, claims.getUserId());
        assertEquals("localhost", claims.getHostName());
        assertEquals("8080", claims.getPort());
        assertFalse(claims.isOneTime());

        // Test claims map
        var claimsMap = claims.getClaimsMap();
        assertNotNull(claimsMap);
        assertEquals(BigInteger.ONE, claimsMap.get("userId"));
        assertEquals("localhost", claimsMap.get("hostName"));
        assertEquals("8080", claimsMap.get("port"));
    }

    @Test
    public void testJWTUtilTokenGeneration() {
        JWTUtil.JWTGenerateTokenParameters params = JWTUtil.JWTGenerateTokenParameters.builder()
                .userId(BigInteger.ONE)
                .secretKey("test-secret-key-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum")
                .expiryInMin(60)
                .host("localhost")
                .port("8080")
                .loggedInClientId(BigInteger.TEN)
                .loggedInClientCode("TEST_CLIENT")
                .appCode("TEST_APP")
                .oneTime(false)
                .build();

        var result = JWTUtil.generateToken(params);
        assertNotNull(result);
        assertNotNull(result.getT1()); // token
        assertNotNull(result.getT2()); // expiration time
        // Just verify that the token is not empty
        assertFalse(result.getT1().isEmpty());
    }

    @Test
    public void testSecurityContextUtilConstants() {
        // Test that the utility class can be instantiated and constants are accessible
        assertNotNull(LogUtil.DEBUG_KEY);
        assertNotNull(LogUtil.METHOD_NAME);
    }
}
