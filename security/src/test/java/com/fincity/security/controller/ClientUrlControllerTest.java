package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.testutil.TestWebSecurityConfig;

import com.fincity.security.dto.ClientUrl;
import com.fincity.security.service.ClientUrlService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientUrlController.class, TestWebSecurityConfig.class })
class ClientUrlControllerTest {

    private static final String BASE_PATH = "/api/security/clienturls";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ClientUrlService clientUrlService;

    private ClientUrl createTestClientUrl() {
        ClientUrl url = new ClientUrl();
        url.setId(ULong.valueOf(1));
        url.setClientId(ULong.valueOf(10));
        url.setUrlPattern("https://app.example.com");
        url.setAppCode("testapp");
        return url;
    }

    @Nested
    @DisplayName("Fetch URLs Endpoint")
    class FetchUrlsTests {

        @Test
        @DisplayName("GET /fetchUrls - returns URL list for an app code")
        void getUrlsOfApp_returnsUrlList() {
            List<String> urls = List.of("https://app.example.com", "https://app2.example.com");
            when(clientUrlService.getUrlsBasedOnApp(eq("testapp"), isNull()))
                    .thenReturn(Mono.just(urls));

            webTestClient.get()
                    .uri(BASE_PATH + "/fetchUrls?appCode=testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0]").isEqualTo("https://app.example.com")
                    .jsonPath("$[1]").isEqualTo("https://app2.example.com");
        }

        @Test
        @DisplayName("GET /fetchUrls - returns URL list with suffix parameter")
        void getUrlsOfApp_withSuffix_returnsFilteredList() {
            List<String> urls = List.of("https://app.example.com/suffix");
            when(clientUrlService.getUrlsBasedOnApp(eq("testapp"), eq("suffix")))
                    .thenReturn(Mono.just(urls));

            webTestClient.get()
                    .uri(BASE_PATH + "/fetchUrls?appCode=testapp&suffix=suffix")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0]").isEqualTo("https://app.example.com/suffix");
        }
    }

    @Nested
    @DisplayName("App URL Endpoint")
    class AppUrlTests {

        @Test
        @DisplayName("GET /internal/applications/property/url - returns app URL with app code only")
        void getAppUrl_withAppCodeOnly_returnsUrl() {
            when(clientUrlService.getAppUrl(eq("testapp"), isNull()))
                    .thenReturn(Mono.just("https://app.example.com"));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/applications/property/url?appCode=testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("https://app.example.com");
        }

        @Test
        @DisplayName("GET /internal/applications/property/url - returns app URL with client code")
        void getAppUrl_withClientCode_returnsUrl() {
            when(clientUrlService.getAppUrl(eq("testapp"), eq("clientA")))
                    .thenReturn(Mono.just("https://clientA.app.example.com"));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/applications/property/url?appCode=testapp&clientCode=clientA")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("https://clientA.app.example.com");
        }
    }

    @Nested
    @DisplayName("Client URLs Endpoint")
    class ClientUrlsTests {

        @Test
        @DisplayName("GET /urls - returns client URLs for app and client code")
        void getClientUrl_returnsClientUrls() {
            ClientUrl url1 = createTestClientUrl();
            ClientUrl url2 = new ClientUrl();
            url2.setId(ULong.valueOf(2));
            url2.setClientId(ULong.valueOf(10));
            url2.setUrlPattern("https://app2.example.com");
            url2.setAppCode("testapp");

            when(clientUrlService.getClientUrls(eq("testapp"), eq("clientA")))
                    .thenReturn(Mono.just(List.of(url1, url2)));

            webTestClient.get()
                    .uri(BASE_PATH + "/urls?appCode=testapp&clientCode=clientA")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].urlPattern").isEqualTo("https://app.example.com")
                    .jsonPath("$[0].appCode").isEqualTo("testapp")
                    .jsonPath("$[1].urlPattern").isEqualTo("https://app2.example.com");
        }
    }

    @Nested
    @DisplayName("Inherited CRUD Endpoints")
    class CrudTests {

        @Test
        @DisplayName("POST / - creates a new client URL")
        void create_returnsCreatedClientUrl() {
            ClientUrl url = createTestClientUrl();
            when(clientUrlService.create(any(ClientUrl.class)))
                    .thenReturn(Mono.just(url));

            ClientUrl request = new ClientUrl();
            request.setClientId(ULong.valueOf(10));
            request.setUrlPattern("https://app.example.com");
            request.setAppCode("testapp");

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.urlPattern").isEqualTo("https://app.example.com")
                    .jsonPath("$.appCode").isEqualTo("testapp");
        }

        @Test
        @DisplayName("GET /{id} - reads a client URL by ID")
        void read_returnsClientUrl() {
            ClientUrl url = createTestClientUrl();
            when(clientUrlService.read(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(url));

            webTestClient.get()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.urlPattern").isEqualTo("https://app.example.com")
                    .jsonPath("$.appCode").isEqualTo("testapp");
        }

        @Test
        @DisplayName("GET /{id} - returns 404 when client URL not found")
        void read_returnsNotFound() {
            when(clientUrlService.read(eq(ULong.valueOf(999))))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/999")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("DELETE /{id} - deletes a client URL by ID")
        void delete_returnsNoContent() {
            when(clientUrlService.delete(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(1));

            webTestClient.delete()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }
}
