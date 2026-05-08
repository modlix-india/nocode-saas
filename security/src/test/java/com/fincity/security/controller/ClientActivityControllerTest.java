package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.ClientActivity;
import com.fincity.security.dto.User;
import com.fincity.security.service.ClientActivityService;
import com.fincity.security.testutil.TestDataFactory;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientActivityController.class, TestWebSecurityConfig.class })
class ClientActivityControllerTest {

    private static final String BASE_PATH = "/api/security/client-activities";

    private static final ULong CLIENT_ID  = ULong.valueOf(2);
    private static final ULong USER_ID    = ULong.valueOf(10);
    private static final ULong ACTIVITY_ID = ULong.valueOf(100);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientActivityService clientActivityService;

    // =========================================================================
    // POST / — create
    // =========================================================================

    @Nested
    @DisplayName("POST / — create")
    class CreateTests {

        @Test
        @DisplayName("returns 200 with created activity")
        void create_ReturnsCreatedActivity() {
            ClientActivity saved = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID,
                    "User signed up", "New user registration");

            when(clientActivityService.create(any(ClientActivity.class))).thenReturn(Mono.just(saved));

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"activityName":"User signed up","description":"New user registration"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(ACTIVITY_ID.longValue())
                    .jsonPath("$.activityName").isEqualTo("User signed up")
                    .jsonPath("$.clientId").isEqualTo(CLIENT_ID.longValue());
        }

        @Test
        @DisplayName("returns 403 when service throws FORBIDDEN")
        void create_Forbidden_Returns403() {
            when(clientActivityService.create(any(ClientActivity.class)))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "forbidden_permission")));

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"clientId":99,"activityName":"Hack"}
                            """)
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    // =========================================================================
    // GET /{clientId} — paged read
    // =========================================================================

    @Nested
    @DisplayName("GET /{clientId} — paged read")
    class ReadPageFilterTests {

        @Test
        @DisplayName("returns paged activities with createdByUser populated")
        void readPageFilter_ReturnsPage() {
            User creator = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
            ClientActivity activity = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID,
                    "Document uploaded", null);
            activity.setCreatedByUser(creator);

            Page<ClientActivity> page = new PageImpl<>(List.of(activity), PageRequest.of(0, 10), 1);

            when(clientActivityService.readPageFilter(eq(CLIENT_ID), any(Pageable.class),
                    any(AbstractCondition.class)))
                    .thenReturn(Mono.just(page));
            when(clientActivityService.fillCreatedByUser(any()))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/" + CLIENT_ID + "?page=0&size=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].id").isEqualTo(ACTIVITY_ID.longValue())
                    .jsonPath("$.content[0].activityName").isEqualTo("Document uploaded")
                    .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("returns 403 when service throws FORBIDDEN")
        void readPageFilter_Forbidden_Returns403() {
            when(clientActivityService.readPageFilter(eq(CLIENT_ID), any(Pageable.class),
                    nullable(AbstractCondition.class)))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "forbidden_permission")));

            webTestClient.get()
                    .uri(BASE_PATH + "/" + CLIENT_ID)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("returns empty page when no activities exist")
        void readPageFilter_EmptyPage_Returns200() {
            Page<ClientActivity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(clientActivityService.readPageFilter(eq(CLIENT_ID), any(Pageable.class),
                    nullable(AbstractCondition.class)))
                    .thenReturn(Mono.just(emptyPage));
            when(clientActivityService.fillCreatedByUser(any()))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/" + CLIENT_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(0)
                    .jsonPath("$.content").isEmpty();
        }
    }

    // =========================================================================
    // POST /{clientId}/query — paged read with filter body
    // =========================================================================

    @Nested
    @DisplayName("POST /{clientId}/query — paged read with filter")
    class ReadPageFilterQueryTests {

        @Test
        @DisplayName("returns paged activities matching filter")
        void readPageFilterQuery_ReturnsMatchingActivities() {
            ClientActivity activity = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID,
                    "Login", "User logged in");
            Page<ClientActivity> page = new PageImpl<>(List.of(activity), PageRequest.of(0, 10), 1);

            when(clientActivityService.readPageFilter(eq(CLIENT_ID), any(Pageable.class), any()))
                    .thenReturn(Mono.just(page));
            when(clientActivityService.fillCreatedByUser(any()))
                    .thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_PATH + "/" + CLIENT_ID + "/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"page":0,"size":10}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].activityName").isEqualTo("Login")
                    .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("returns 403 when service throws FORBIDDEN")
        void readPageFilterQuery_Forbidden_Returns403() {
            when(clientActivityService.readPageFilter(eq(CLIENT_ID), any(Pageable.class), any()))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "forbidden_permission")));

            webTestClient.post()
                    .uri(BASE_PATH + "/" + CLIENT_ID + "/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"page":0,"size":10}
                            """)
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }
}
