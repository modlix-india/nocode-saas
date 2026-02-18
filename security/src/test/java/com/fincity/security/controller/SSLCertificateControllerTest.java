package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.model.SSLCertificateOrder;
import com.fincity.security.model.SSLCertificateOrderRequest;
import com.fincity.security.service.SSLCertificateService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { SSLCertificateController.class, TestWebSecurityConfig.class })
class SSLCertificateControllerTest {

    private static final String BASE_PATH = "/api/security/ssl";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SSLCertificateService sslCertificateService;

    @MockBean
    private SecurityMessageResourceService msgService;

    private SSLCertificateOrderRequest createTestOrderRequest() {
        SSLCertificateOrderRequest request = new SSLCertificateOrderRequest();
        request.setDomainNames(List.of("example.com", "www.example.com"));
        request.setOrganizationName("Test Org");
        request.setUrlId(ULong.valueOf(100));
        request.setValidityInMonths(12);
        return request;
    }

    private SSLCertificateOrder createTestOrder() {
        SSLCertificateOrder order = new SSLCertificateOrder();
        order.setRequest(null);
        order.setChallenges(List.of());
        return order;
    }

    private SSLCertificate createTestCertificate() {
        SSLCertificate cert = new SSLCertificate();
        cert.setId(ULong.valueOf(1));
        cert.setUrlId(ULong.valueOf(100));
        cert.setDomains("example.com");
        cert.setOrganization("Test Org");
        cert.setIssuer("Let's Encrypt");
        cert.setCurrent(true);
        cert.setExpiryDate(LocalDateTime.of(2027, 1, 1, 0, 0));
        return cert;
    }

    @Nested
    @DisplayName("Certificate Request Endpoints")
    class CertificateRequestTests {

        @Test
        @DisplayName("POST / - creates a new certificate request")
        void createCertificateRequest_returnsOrder() {
            SSLCertificateOrder order = createTestOrder();
            when(sslCertificateService.createCertificateRequest(any(SSLCertificateOrderRequest.class)))
                    .thenReturn(Mono.just(order));

            SSLCertificateOrderRequest request = createTestOrderRequest();

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.challenges").isArray();
        }

        @Test
        @DisplayName("POST /external - creates an externally issued certificate")
        void createExternallyIssuedCertificate_returnsCertificate() {
            SSLCertificate cert = createTestCertificate();
            when(sslCertificateService.createExternallyIssuedCertificate(any(SSLCertificate.class)))
                    .thenReturn(Mono.just(cert));

            SSLCertificate request = new SSLCertificate();
            request.setUrlId(ULong.valueOf(100));
            request.setDomains("example.com");
            request.setOrganization("Test Org");
            request.setCrt("-----BEGIN CERTIFICATE-----");
            request.setCrtChain("-----BEGIN CERTIFICATE-----");

            webTestClient.post()
                    .uri(BASE_PATH + "/external")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.domains").isEqualTo("example.com")
                    .jsonPath("$.organization").isEqualTo("Test Org")
                    .jsonPath("$.issuer").isEqualTo("Let's Encrypt");
        }

        @Test
        @DisplayName("GET /request - reads request by URL ID")
        void readRequestByURLId_returnsOrder() {
            SSLCertificateOrder order = createTestOrder();
            when(sslCertificateService.readRequestByURLId(eq(ULong.valueOf(100))))
                    .thenReturn(Mono.just(order));

            webTestClient.get()
                    .uri(BASE_PATH + "/request?urlId=100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.challenges").isArray();
        }

        @Test
        @DisplayName("DELETE /request - deletes request by URL ID")
        void deleteRequestByURLId_returnsTrue() {
            when(sslCertificateService.deleteRequestByURLId(eq(ULong.valueOf(100))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/request?urlId=100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Challenge Endpoints")
    class ChallengeTests {

        @Test
        @DisplayName("GET /request/challenge - triggers a challenge")
        void triggerChallenge_returnsOrder() {
            SSLCertificateOrder order = createTestOrder();
            when(sslCertificateService.triggerChallenge(eq(ULong.valueOf(50))))
                    .thenReturn(Mono.just(order));

            webTestClient.get()
                    .uri(BASE_PATH + "/request/challenge?challengeId=50")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.challenges").isArray();
        }

        @Test
        @DisplayName("POST /request/challenges - creates challenges for a request")
        void createChallenges_returnsOrder() {
            SSLCertificateOrder order = createTestOrder();
            when(sslCertificateService.createChallenges(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(order));

            webTestClient.post()
                    .uri(BASE_PATH + "/request/challenges?requestId=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.challenges").isArray();
        }
    }

    @Nested
    @DisplayName("Certificate Retrieval and Deletion Endpoints")
    class CertificateRetrievalTests {

        @Test
        @DisplayName("GET /certificate - creates certificate from request ID")
        void createCertificate_returnsTrue() {
            when(sslCertificateService.createCertificate(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/certificate?requestId=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /{id} - deletes a certificate by ID")
        void deleteCertificate_returnsTrue() {
            when(sslCertificateService.deleteCertificate(eq(ULong.valueOf(5))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/5")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Certificate Listing Endpoints")
    class CertificateListingTests {

        @Test
        @DisplayName("GET / - returns paginated certificates")
        void readPageFilter_returnsCertificates() {
            SSLCertificate cert = createTestCertificate();
            Page<SSLCertificate> page = new PageImpl<>(List.of(cert));

            when(sslCertificateService.findSSLCertificates(any(), any(Pageable.class), any()))
                    .thenReturn(Mono.just(page));

            webTestClient.get()
                    .uri(BASE_PATH + "?urlId=100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].domains").isEqualTo("example.com")
                    .jsonPath("$.content[0].organization").isEqualTo("Test Org");
        }
    }

    @Nested
    @DisplayName("Token Endpoint")
    class TokenTests {

        @Test
        @DisplayName("GET /token/{token} - returns token content")
        void getToken_returnsTokenString() {
            when(sslCertificateService.getToken(eq("abc123")))
                    .thenReturn(Mono.just("token-content-value"));

            webTestClient.get()
                    .uri(BASE_PATH + "/token/abc123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("token-content-value");
        }
    }
}
