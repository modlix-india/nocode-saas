package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { SecurityCacheController.class, TestWebSecurityConfig.class })
class SecurityCacheControllerTest {

    private static final String BASE_PATH = "/api/security";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CacheService cacheService;

    @Nested
    @DisplayName("Cache Eviction Endpoints")
    class CacheEvictionTests {

        @Test
        @DisplayName("DELETE /internal/cache/{cacheName} - evicts a specific cache by name")
        void resetCacheByName_returnsTrue() {
            when(cacheService.evictAll(eq("userCache")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/internal/cache/userCache")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /internal/cache - evicts all caches")
        void resetAllCaches_returnsTrue() {
            when(cacheService.evictAllCaches())
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/internal/cache")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Cache Retrieval Endpoints")
    class CacheRetrievalTests {

        @Test
        @DisplayName("GET /internal/cache - returns all cache names")
        void getCacheNames_returnsCacheNameList() {
            Collection<String> cacheNames = List.of("userCache", "appCache", "tokenCache");
            when(cacheService.getCacheNames())
                    .thenReturn(Mono.just(cacheNames));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/cache")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0]").isEqualTo("userCache")
                    .jsonPath("$[1]").isEqualTo("appCache")
                    .jsonPath("$[2]").isEqualTo("tokenCache");
        }
    }
}
