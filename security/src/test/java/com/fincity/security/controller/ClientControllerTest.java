package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.security.dto.Client;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.otp.OtpGenerationRequest;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.appregistration.ClientRegistrationService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientController.class, TestWebSecurityConfig.class })
class ClientControllerTest {

    private static final String BASE_PATH = "/api/security/clients";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private ClientRegistrationService clientRegistrationService;

    private Client sampleClient;

    @BeforeEach
    void setUp() {
        sampleClient = new Client();
        sampleClient.setId(ULong.valueOf(10));
        sampleClient.setCode("TESTCLIENT");
        sampleClient.setName("Test Client");
        sampleClient.setTypeCode("BUS");
        sampleClient.setStatusCode(SecurityClientStatusCode.ACTIVE);
        sampleClient.setTokenValidityMinutes(60);
        sampleClient.setLocaleCode("en");
    }

    // ==================== GET /api/security/clients/internal/isUserClientManageClient ====================

    @Nested
    @DisplayName("GET /internal/isUserClientManageClient")
    class IsUserClientManageClientTests {

        @Test
        @DisplayName("Should return 200 with true when user client manages target client")
        void isUserClientManageClient_ReturnsTrue() {

            when(clientService.isUserClientManageClient(
                    eq("appTest"), eq(ULong.valueOf(1)), eq(ULong.valueOf(2)), eq(ULong.valueOf(3))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/isUserClientManageClient")
                            .queryParam("appCode", "appTest")
                            .queryParam("userId", "1")
                            .queryParam("userClientId", "2")
                            .queryParam("targetClientId", "3")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientService).isUserClientManageClient("appTest", ULong.valueOf(1),
                    ULong.valueOf(2), ULong.valueOf(3));
        }

        @Test
        @DisplayName("Should return 200 with false when user client does not manage target")
        void isUserClientManageClient_ReturnsFalse() {

            when(clientService.isUserClientManageClient(
                    eq("appTest"), eq(ULong.valueOf(1)), eq(ULong.valueOf(2)), eq(ULong.valueOf(99))))
                    .thenReturn(Mono.just(Boolean.FALSE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/isUserClientManageClient")
                            .queryParam("appCode", "appTest")
                            .queryParam("userId", "1")
                            .queryParam("userClientId", "2")
                            .queryParam("targetClientId", "99")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(false);
        }
    }

    // ==================== GET /api/security/clients/internal/doesClientManageClient ====================

    @Nested
    @DisplayName("GET /internal/doesClientManageClient")
    class DoesClientManageClientTests {

        @Test
        @DisplayName("Should return 200 with true when managing client manages target")
        void doesClientManageClient_ReturnsTrue() {

            when(clientService.doesClientManageClient(eq(ULong.valueOf(1)), eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/doesClientManageClient")
                            .queryParam("managingClientId", "1")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientService).doesClientManageClient(ULong.valueOf(1), ULong.valueOf(10));
        }

        @Test
        @DisplayName("Should return 500 when service throws unexpected error")
        void doesClientManageClient_ServiceError_Returns500() {

            when(clientService.doesClientManageClient(any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/doesClientManageClient")
                            .queryParam("managingClientId", "1")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    // ==================== GET /api/security/clients/internal/getClientById ====================

    @Nested
    @DisplayName("GET /internal/getClientById")
    class GetClientByIdTests {

        @Test
        @DisplayName("Should return 200 with client when found by id")
        void getClientById_Found_Returns200() {

            when(clientService.getClientInfoById(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(sampleClient));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientById")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("TESTCLIENT")
                    .jsonPath("$.name").isEqualTo("Test Client")
                    .jsonPath("$.typeCode").isEqualTo("BUS");

            verify(clientService).getClientInfoById(ULong.valueOf(10));
        }

        @Test
        @DisplayName("Should return empty when client not found by id")
        void getClientById_NotFound_ReturnsEmpty() {

            when(clientService.getClientInfoById(eq(ULong.valueOf(999))))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientById")
                            .queryParam("clientId", "999")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().isEmpty();
        }
    }

    // ==================== GET /api/security/clients/internal/getClientByCode ====================

    @Nested
    @DisplayName("GET /internal/getClientByCode")
    class GetClientByCodeTests {

        @Test
        @DisplayName("Should return 200 with client when found by code")
        void getClientByCode_Found_Returns200() {

            when(clientService.getClientBy(eq("TESTCLIENT")))
                    .thenReturn(Mono.just(sampleClient));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientByCode")
                            .queryParam("clientCode", "TESTCLIENT")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("TESTCLIENT")
                    .jsonPath("$.name").isEqualTo("Test Client");

            verify(clientService).getClientBy("TESTCLIENT");
        }

        @Test
        @DisplayName("Should return error when client code triggers GenericException")
        void getClientByCode_NotFound_ReturnsError() {

            when(clientService.getClientBy(eq("NONEXISTENT")))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.NOT_FOUND, "Client not found")));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientByCode")
                            .queryParam("clientCode", "NONEXISTENT")
                            .build())
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    // ==================== GET /api/security/clients/internal/managedClient ====================

    @Nested
    @DisplayName("GET /internal/managedClient")
    class GetManagedClientTests {

        @Test
        @DisplayName("Should return 200 with managing client of the given client")
        void getManagedClient_Found_Returns200() {

            Client managingClient = new Client();
            managingClient.setId(ULong.valueOf(1));
            managingClient.setCode("SYSTEM");
            managingClient.setName("System Client");

            when(clientService.getManagedClientOfClientById(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(managingClient));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/managedClient")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("SYSTEM")
                    .jsonPath("$.name").isEqualTo("System Client");

            verify(clientService).getManagedClientOfClientById(ULong.valueOf(10));
        }
    }

    // ==================== GET /api/security/clients/internal/clientHierarchy ====================

    @Nested
    @DisplayName("GET /internal/clientHierarchy")
    class GetClientHierarchyTests {

        @Test
        @DisplayName("Should return 200 with list of client IDs in the hierarchy")
        void getClientHierarchy_Returns200WithList() {

            List<ULong> hierarchy = List.of(ULong.valueOf(1), ULong.valueOf(5), ULong.valueOf(10));

            when(clientService.getClientHierarchy(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(hierarchy));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/clientHierarchy")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3)
                    .jsonPath("$[0]").isEqualTo(1)
                    .jsonPath("$[1]").isEqualTo(5)
                    .jsonPath("$[2]").isEqualTo(10);

            verify(clientService).getClientHierarchy(ULong.valueOf(10));
        }
    }

    // ==================== GET /api/security/clients/internal/managingClientIds ====================

    @Nested
    @DisplayName("GET /internal/managingClientIds")
    class GetManagingClientIdsTests {

        @Test
        @DisplayName("Should return 200 with list of managing client IDs")
        void getManagingClientIds_Returns200() {

            List<ULong> managingIds = List.of(ULong.valueOf(1), ULong.valueOf(2));

            when(clientService.getManagingClientIds(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(managingIds));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/managingClientIds")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0]").isEqualTo(1)
                    .jsonPath("$[1]").isEqualTo(2);

            verify(clientService).getManagingClientIds(ULong.valueOf(10));
        }
    }

    // ==================== GET /api/security/clients/internal/validateClientCode ====================

    @Nested
    @DisplayName("GET /internal/validateClientCode")
    class ValidateClientCodeTests {

        @Test
        @DisplayName("Should return 200 with true when client code is valid")
        void validateClientCode_Valid_ReturnsTrue() {

            when(clientService.getClientBy(eq("VALIDCODE")))
                    .thenReturn(Mono.just(sampleClient));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/validateClientCode")
                            .queryParam("clientCode", "VALIDCODE")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientService).getClientBy("VALIDCODE");
        }
    }

    // ==================== GET /api/security/clients/internal/getClientNAppCode ====================

    @Nested
    @DisplayName("GET /internal/getClientNAppCode")
    class GetClientNAppCodeTests {

        @Test
        @DisplayName("Should return 200 with client code and app code tuple when pattern found")
        void getClientNAppCode_Found_ReturnsTuple() {

            ClientUrlPattern pattern = new ClientUrlPattern("10", "TESTCLIENT", "https://app.test.com", "testApp");

            when(clientService.getClientPattern(eq("https"), eq("app.test.com"), eq("443")))
                    .thenReturn(Mono.just(pattern));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientNAppCode")
                            .queryParam("scheme", "https")
                            .queryParam("host", "app.test.com")
                            .queryParam("port", "443")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.t1").isEqualTo("TESTCLIENT")
                    .jsonPath("$.t2").isEqualTo("testApp");

            verify(clientService).getClientPattern("https", "app.test.com", "443");
        }

        @Test
        @DisplayName("Should return default SYSTEM/nothing when no pattern found")
        void getClientNAppCode_NotFound_ReturnsDefault() {

            when(clientService.getClientPattern(eq("http"), eq("unknown.host"), eq("80")))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/getClientNAppCode")
                            .queryParam("scheme", "http")
                            .queryParam("host", "unknown.host")
                            .queryParam("port", "80")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.t1").isEqualTo("SYSTEM")
                    .jsonPath("$.t2").isEqualTo("nothing");
        }
    }

    // ==================== GET /api/security/clients/makeClientActive ====================

    @Nested
    @DisplayName("GET /makeClientActive")
    class MakeClientActiveTests {

        @Test
        @DisplayName("Should return 200 with true when client activated successfully")
        void makeClientActive_Success_ReturnsTrue() {

            when(clientService.makeClientActiveIfInActive(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/makeClientActive")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientService).makeClientActiveIfInActive(ULong.valueOf(10));
        }

        @Test
        @DisplayName("Should return error when activation fails with forbidden")
        void makeClientActive_Forbidden_ReturnsError() {

            when(clientService.makeClientActiveIfInActive(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "Not allowed")));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/makeClientActive")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    // ==================== GET /api/security/clients/makeClientInActive ====================

    @Nested
    @DisplayName("GET /makeClientInActive")
    class MakeClientInActiveTests {

        @Test
        @DisplayName("Should return 200 with true when client deactivated successfully")
        void makeClientInActive_Success_ReturnsTrue() {

            when(clientService.makeClientInActive(eq(ULong.valueOf(10))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/makeClientInActive")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientService).makeClientInActive(ULong.valueOf(10));
        }
    }

    // ==================== POST /api/security/clients/register/otp/generate ====================

    @Nested
    @DisplayName("POST /register/otp/generate")
    class GenerateOtpTests {

        @Test
        @DisplayName("Should return 200 with true when OTP generated successfully")
        void generateOtp_Success_ReturnsTrue() {

            when(clientRegistrationService.generateOtp(any(OtpGenerationRequest.class), any()))
                    .thenReturn(Mono.just(Boolean.TRUE));

            OtpGenerationRequest request = new OtpGenerationRequest(
                    "user@test.com", null, false, OtpPurpose.REGISTRATION);

            webTestClient.post()
                    .uri(BASE_PATH + "/register/otp/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientRegistrationService).generateOtp(any(OtpGenerationRequest.class), any());
        }

        @Test
        @DisplayName("Should return error when OTP generation fails")
        void generateOtp_Failure_ReturnsError() {

            when(clientRegistrationService.generateOtp(any(OtpGenerationRequest.class), any()))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Invalid email")));

            OtpGenerationRequest request = new OtpGenerationRequest(
                    "", null, false, OtpPurpose.REGISTRATION);

            webTestClient.post()
                    .uri(BASE_PATH + "/register/otp/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ==================== POST /api/security/clients/register/otp/verify ====================

    @Nested
    @DisplayName("POST /register/otp/verify")
    class PreRegisterCheckOneTests {

        @Test
        @DisplayName("Should return 200 with true when OTP verification succeeds")
        void preRegisterCheckOne_Success_ReturnsTrue() {

            when(clientRegistrationService.preRegisterCheckOne(any(ClientRegistrationRequest.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            ClientRegistrationRequest request = new ClientRegistrationRequest()
                    .setEmailId("user@test.com")
                    .setOtp("1234");

            webTestClient.post()
                    .uri(BASE_PATH + "/register/otp/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientRegistrationService).preRegisterCheckOne(any(ClientRegistrationRequest.class));
        }
    }

    // ==================== POST /api/security/clients/register ====================

    @Nested
    @DisplayName("POST /register")
    class RegisterTests {

        @Test
        @DisplayName("Should return 200 with RegistrationResponse on successful registration")
        void register_Success_Returns200() {

            RegistrationResponse response = new RegistrationResponse(
                    true, ULong.valueOf(100), null, null);

            when(clientRegistrationService.register(any(ClientRegistrationRequest.class), any(), any()))
                    .thenReturn(Mono.just(response));

            ClientRegistrationRequest request = new ClientRegistrationRequest()
                    .setClientName("New Client")
                    .setEmailId("newclient@test.com")
                    .setFirstName("John")
                    .setLastName("Doe")
                    .setPassword("SecurePass123!");

            webTestClient.post()
                    .uri(BASE_PATH + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.created").isEqualTo(true)
                    .jsonPath("$.userId").isEqualTo(100);

            verify(clientRegistrationService).register(any(ClientRegistrationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return error when registration fails")
        void register_Failure_ReturnsError() {

            when(clientRegistrationService.register(any(ClientRegistrationRequest.class), any(), any()))
                    .thenReturn(Mono.error(
                            new GenericException(HttpStatus.BAD_REQUEST, "Client already exists")));

            ClientRegistrationRequest request = new ClientRegistrationRequest()
                    .setClientName("Existing Client")
                    .setEmailId("existing@test.com");

            webTestClient.post()
                    .uri(BASE_PATH + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ==================== POST /api/security/clients/appRegister ====================

    @Nested
    @DisplayName("POST /appRegister")
    class RegisterAppTests {

        @Test
        @DisplayName("Should return 200 with true when app registration succeeds")
        void registerApp_Success_ReturnsTrue() {

            when(clientRegistrationService.registerApp(eq("myApp"), eq(ULong.valueOf(10)), eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/appRegister")
                            .queryParam("appCode", "myApp")
                            .queryParam("clientId", "10")
                            .queryParam("userId", "1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(clientRegistrationService).registerApp("myApp", ULong.valueOf(10), ULong.valueOf(1));
        }
    }

    // ==================== GET /api/security/clients/internal/{id} ====================

    @Nested
    @DisplayName("GET /internal/{id}")
    class GetClientInternalByIdTests {

        @Test
        @DisplayName("Should return 200 with client when found by internal id")
        void getClientInternal_Found_Returns200() {

            when(clientService.readById(eq(ULong.valueOf(10)), any()))
                    .thenReturn(Mono.just(sampleClient));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("TESTCLIENT")
                    .jsonPath("$.name").isEqualTo("Test Client");

            verify(clientService).readById(eq(ULong.valueOf(10)), any());
        }

        @Test
        @DisplayName("Should return empty when client not found by internal id")
        void getClientInternal_NotFound_ReturnsEmpty() {

            when(clientService.readById(eq(ULong.valueOf(999)), any()))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/999")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().isEmpty();
        }
    }

    // ==================== GET /api/security/clients/internal (batch by query params) ====================

    @Nested
    @DisplayName("GET /internal (batch by clientIds query param)")
    class GetClientsInternalTests {

        @Test
        @DisplayName("Should return 200 with list of clients for given IDs")
        void getClientsInternal_Returns200WithList() {

            Client client2 = new Client();
            client2.setId(ULong.valueOf(20));
            client2.setCode("ANOTHERCLIENT");
            client2.setName("Another Client");

            when(clientService.readByIds(any(), any()))
                    .thenReturn(Mono.just(List.of(sampleClient, client2)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal")
                            .queryParam("clientIds", "10", "20")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0].code").isEqualTo("TESTCLIENT")
                    .jsonPath("$[1].code").isEqualTo("ANOTHERCLIENT");
        }
    }

    // ==================== POST /api/security/clients/internal/batch ====================

    @Nested
    @DisplayName("POST /internal/batch")
    class GetClientsInternalBatchTests {

        @Test
        @DisplayName("Should return 200 with clients for posted BigInteger IDs")
        void getClientsInternalBatch_Returns200() {

            when(clientService.readByIds(any(), any()))
                    .thenReturn(Mono.just(List.of(sampleClient)));

            List<BigInteger> clientIds = List.of(BigInteger.valueOf(10));

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(clientIds)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].code").isEqualTo("TESTCLIENT");

            verify(clientService).readByIds(any(), any());
        }
    }

    // ==================== POST /api/security/clients/noMapping ====================

    @Nested
    @DisplayName("POST /noMapping (readPageFilter override)")
    class NoMappingTests {

        @Test
        @DisplayName("Should return 400 bad request for the disabled endpoint")
        void noMapping_ReturnsBadRequest() {

            webTestClient.post()
                    .uri(BASE_PATH + "/noMapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new Query())
                    .exchange()
                    .expectStatus().isBadRequest();

            verifyNoInteractions(clientService);
        }
    }

    // ==================== GET /api/security/clients (readPageFilter) ====================

    @Nested
    @DisplayName("GET / (readPageFilter with pageable)")
    class ReadPageFilterTests {

        @Test
        @DisplayName("Should return 200 with page of clients")
        void readPageFilter_Returns200WithPage() {

            Page<Client> page = new PageImpl<>(List.of(sampleClient), PageRequest.of(0, 10), 1);

            when(clientService.readPageFilter(any(Pageable.class), any()))
                    .thenReturn(Mono.just(page));
            when(clientService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleClient)));

            webTestClient.get()
                    .uri(BASE_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content[0].code").isEqualTo("TESTCLIENT");

            verify(clientService).readPageFilter(any(Pageable.class), any());
            verify(clientService).fillDetails(anyList(), any());
        }

        @Test
        @DisplayName("Should return 200 with empty page when no clients exist")
        void readPageFilter_EmptyPage_Returns200() {

            Page<Client> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(clientService.readPageFilter(any(Pageable.class), any()))
                    .thenReturn(Mono.just(emptyPage));
            when(clientService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of()));

            webTestClient.get()
                    .uri(BASE_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(0);
        }
    }

    // ==================== POST /api/security/clients/socialRegister/evoke ====================

    @Nested
    @DisplayName("POST /socialRegister/evoke")
    class EvokeSocialRegisterTests {

        @Test
        @DisplayName("Should return 200 with redirect URL for social registration")
        void evokeSocialRegister_Returns200WithUrl() {

            when(clientRegistrationService.evokeRegisterWSocial(any(), any()))
                    .thenReturn(Mono.just("https://accounts.google.com/o/oauth2/auth?redirect_uri=callback"));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/socialRegister/evoke")
                            .queryParam("platform", "GOOGLE")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("https://accounts.google.com/o/oauth2/auth?redirect_uri=callback");

            verify(clientRegistrationService).evokeRegisterWSocial(any(), any());
        }
    }

    // ==================== POST /api/security/clients/internal/query ====================

    @Nested
    @DisplayName("POST /internal/query (readPageFilterInternal)")
    class ReadPageFilterInternalTests {

        @Test
        @DisplayName("Should return 200 with page of clients from internal query")
        void readPageFilterInternal_Returns200WithPage() {

            Page<Client> page = new PageImpl<>(List.of(sampleClient), PageRequest.of(0, 10), 1);

            when(clientService.readPageFilterInternal(any(Pageable.class), any()))
                    .thenReturn(Mono.just(page));
            when(clientService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleClient)));

            Query query = new Query();
            query.setPage(0);
            query.setSize(10);

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(query)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content[0].code").isEqualTo("TESTCLIENT");

            verify(clientService).readPageFilterInternal(any(Pageable.class), any());
        }

        @Test
        @DisplayName("Should return 200 with empty page when internal query returns empty")
        void readPageFilterInternal_EmptyResult_ReturnsEmptyPage() {

            Page<Client> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(clientService.readPageFilterInternal(any(Pageable.class), any()))
                    .thenReturn(Mono.just(emptyPage));
            when(clientService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of()));

            Query query = new Query();
            query.setPage(0);
            query.setSize(10);

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(query)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(0);
        }
    }
}
