package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.RoleV2;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.RoleV2Service;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { RoleV2Controller.class, TestWebSecurityConfig.class })
class RoleV2ControllerTest {

    private static final String BASE_PATH = "/api/security/rolev2";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RoleV2Service roleV2Service;

    @MockitoBean
    private ProfileService profileService;

    private RoleV2 createTestRole() {
        RoleV2 role = new RoleV2();
        role.setId(ULong.valueOf(1));
        role.setClientId(ULong.valueOf(100));
        role.setAppId(ULong.valueOf(200));
        role.setName("TestRole");
        role.setShortName("TR");
        role.setDescription("A test role");
        return role;
    }

    @Test
    void getAssignableRoles_returnsCombinedList() {

        RoleV2 serviceRole = createTestRole();
        serviceRole.setName("ServiceRole");

        RoleV2 profileRole = createTestRole();
        profileRole.setId(ULong.valueOf(2));
        profileRole.setName("ProfileRole");

        List<RoleV2> combined = List.of(serviceRole, profileRole);

        when(roleV2Service.getRolesForAssignmentInApp(eq("testApp")))
                .thenReturn(Mono.just(List.of(serviceRole)));
        when(profileService.getRolesForAssignmentInApp(eq("testApp")))
                .thenReturn(Mono.just(List.of(profileRole)));
        when(roleV2Service.fetchSubRolesAlso(any()))
                .thenReturn(Mono.just(combined));

        webTestClient.get()
                .uri(BASE_PATH + "/assignable/testApp")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].name").isEqualTo("ServiceRole")
                .jsonPath("$[1].name").isEqualTo("ProfileRole");
    }

    @Test
    void create_returnsCreatedRole() {

        RoleV2 role = createTestRole();

        when(roleV2Service.create(any(RoleV2.class)))
                .thenReturn(Mono.just(role));

        webTestClient.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(role)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("TestRole")
                .jsonPath("$.shortName").isEqualTo("TR")
                .jsonPath("$.description").isEqualTo("A test role");
    }

    @Test
    void read_returnsRole() {

        RoleV2 role = createTestRole();

        when(roleV2Service.read(ULong.valueOf(1)))
                .thenReturn(Mono.just(role));

        webTestClient.get()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("TestRole")
                .jsonPath("$.shortName").isEqualTo("TR");
    }

    @Test
    void update_returnsUpdatedRole() {

        RoleV2 role = createTestRole();
        role.setName("UpdatedRole");

        when(roleV2Service.update(any(RoleV2.class)))
                .thenReturn(Mono.just(role));

        webTestClient.put()
                .uri(BASE_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(role)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("UpdatedRole");
    }

    @Test
    void delete_returnsNoContent() {

        when(roleV2Service.delete(ULong.valueOf(1)))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isNoContent();
    }
}
