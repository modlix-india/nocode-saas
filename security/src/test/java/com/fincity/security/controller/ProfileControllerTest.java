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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.Profile;
import com.fincity.security.service.ProfileService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ProfileController.class, TestWebSecurityConfig.class })
class ProfileControllerTest {

    private static final String BASE_PATH = "/api/security/app";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProfileService profileService;

    private Profile createTestProfile() {
        Profile profile = new Profile();
        profile.setId(ULong.valueOf(1));
        profile.setAppId(ULong.valueOf(100));
        profile.setClientId(ULong.valueOf(200));
        profile.setName("TestProfile");
        profile.setTitle("Test Profile Title");
        profile.setDescription("A test profile");
        profile.setDefaultProfile(false);
        return profile;
    }

    @Test
    void createOrUpdate_post_createsProfile() {

        Profile profile = createTestProfile();

        when(profileService.create(any(Profile.class)))
                .thenReturn(Mono.just(profile));

        webTestClient.post()
                .uri(BASE_PATH + "/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(profile)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("TestProfile")
                .jsonPath("$.title").isEqualTo("Test Profile Title")
                .jsonPath("$.description").isEqualTo("A test profile");
    }

    @Test
    void createOrUpdate_put_updatesProfile() {

        Profile profile = createTestProfile();
        profile.setName("UpdatedProfile");

        when(profileService.create(any(Profile.class)))
                .thenReturn(Mono.just(profile));

        webTestClient.put()
                .uri(BASE_PATH + "/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(profile)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("UpdatedProfile");
    }

    @Test
    void read_returnsProfile() {

        Profile profile = createTestProfile();

        when(profileService.read(ULong.valueOf(1)))
                .thenReturn(Mono.just(profile));

        webTestClient.get()
                .uri(BASE_PATH + "/profiles/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("TestProfile")
                .jsonPath("$.title").isEqualTo("Test Profile Title");
    }

    @Test
    void readPageFilter_returnsPage() {

        Profile profile = createTestProfile();
        Page<Profile> page = new PageImpl<>(List.of(profile));

        when(profileService.readAll(eq(ULong.valueOf(100)), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        webTestClient.get()
                .uri(BASE_PATH + "/100/profiles")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[0].name").isEqualTo("TestProfile");
    }

    @Test
    void delete_returnsTrue() {

        when(profileService.delete(ULong.valueOf(1)))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/profiles/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void restrictClient_returnsTrue() {

        when(profileService.restrictClient(ULong.valueOf(1), ULong.valueOf(200)))
                .thenReturn(Mono.just(Boolean.TRUE));

        webTestClient.post()
                .uri(BASE_PATH + "/profiles/1/restrictClient/200")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void getProfileUsers_returnsUserIds() {

        List<ULong> userIds = List.of(ULong.valueOf(10), ULong.valueOf(20));

        when(profileService.getProfileUsers(eq("testApp"), any()))
                .thenReturn(Mono.just(userIds));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_PATH + "/profiles/internal/users")
                        .queryParam("profileIds", "1", "2")
                        .build())
                .header("appCode", "testApp")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void getProfileInternal_returnsProfile() {

        Profile profile = createTestProfile();

        when(profileService.readById(ULong.valueOf(1)))
                .thenReturn(Mono.just(profile));

        webTestClient.get()
                .uri(BASE_PATH + "/profiles/internal/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("TestProfile")
                .jsonPath("$.description").isEqualTo("A test profile");
    }
}
