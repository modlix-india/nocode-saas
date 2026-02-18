package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.Client;
import com.fincity.security.service.ClientManagerService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientManagerController.class, TestWebSecurityConfig.class })
class ClientManagerControllerTest {

    private static final String BASE_PATH = "/api/security/client-managers";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientManagerService clientManagerService;

    @Test
    void getClientsOfUser_returnsPageOfClients() {

        Client client = new Client();
        client.setId(ULong.valueOf(1));
        client.setCode("testClient");
        client.setName("Test Client");

        Page<Client> page = new PageImpl<>(List.of(client), PageRequest.of(0, 10), 1);

        when(clientManagerService.getClientsOfUser(eq(ULong.valueOf(10)), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        webTestClient.get()
                .uri(BASE_PATH + "/10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[0].code").isEqualTo("testClient")
                .jsonPath("$.content[0].name").isEqualTo("Test Client");
    }

    @Test
    void create_returnsTrue() {

        when(clientManagerService.create(ULong.valueOf(10), ULong.valueOf(20)))
                .thenReturn(Mono.just(Boolean.TRUE));

        webTestClient.post()
                .uri(BASE_PATH + "/10/20")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void delete_returnsTrue() {

        when(clientManagerService.delete(ULong.valueOf(10), ULong.valueOf(20)))
                .thenReturn(Mono.just(Boolean.TRUE));

        webTestClient.delete()
                .uri(BASE_PATH + "/10/20")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void getClientsOfUser_invalidULong_returns400() {

        webTestClient.get()
                .uri(BASE_PATH + "/notANumber")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getClientsOfUser_serviceError_returns500() {

        when(clientManagerService.getClientsOfUser(eq(ULong.valueOf(10)), any(Pageable.class)))
                .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

        webTestClient.get()
                .uri(BASE_PATH + "/10")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
