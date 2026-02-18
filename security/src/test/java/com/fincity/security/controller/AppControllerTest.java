package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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

import com.fincity.saas.commons.model.condition.AbstractCondition;

import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.model.AppDependency;
import com.fincity.security.model.ApplicationAccessRequest;
import com.fincity.security.model.PropertiesResponse;
import com.fincity.security.service.AppService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@WebFluxTest
@ContextConfiguration(classes = { AppController.class, TestWebSecurityConfig.class })
class AppControllerTest {

    private static final String BASE_PATH = "/api/security/applications";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AppService appService;

    private App createTestApp() {
        App app = new App();
        app.setId(ULong.valueOf(1));
        app.setClientId(ULong.valueOf(10));
        app.setAppName("Test App");
        app.setAppCode("testapp");
        return app;
    }

    @Nested
    @DisplayName("App Code Suffix/Prefix Endpoints")
    class AppCodeSuffixPrefixTests {

        @Test
        @DisplayName("GET /applyAppCodeSuffix - returns appCode with suffix appended")
        void applyAppCodeSuffix_returnsAppCodeWithSuffix() {
            webTestClient.get()
                    .uri(BASE_PATH + "/applyAppCodeSuffix?appCode=myapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("myapp");
        }

        @Test
        @DisplayName("GET /applyAppCodePrefix - returns appCode with prefix")
        void applyAppCodePrefix_returnsAppCodeWithPrefix() {
            webTestClient.get()
                    .uri(BASE_PATH + "/applyAppCodePrefix?appCode=myapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("myapp");
        }
    }

    @Nested
    @DisplayName("Access Check Endpoints")
    class AccessCheckTests {

        @Test
        @DisplayName("GET /internal/hasReadAccess - returns true when read access exists")
        void hasReadAccess_returnsTrue() {
            when(appService.hasReadAccess(eq("testapp"), eq("clientA")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/hasReadAccess?appCode=testapp&clientCode=clientA")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("GET /internal/hasWriteAccess - returns false when no write access")
        void hasWriteAccess_returnsFalse() {
            when(appService.hasWriteAccess(eq("testapp"), eq("clientB")))
                    .thenReturn(Mono.just(Boolean.FALSE));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/hasWriteAccess?appCode=testapp&clientCode=clientB")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("GET /hasDeleteAccess - returns true when delete access exists")
        void hasDeleteAccess_returnsTrue() {
            when(appService.hasDeleteAccess(eq("testapp"), eq("clientA")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/hasDeleteAccess?deleteAppCode=testapp&deleteClientCode=clientA")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("GET /clientHasReadAccess - returns access map for multiple app codes")
        void clientHasReadAccess_returnsMap() {
            Map<String, Boolean> accessMap = Map.of("app1", true, "app2", false);
            when(appService.hasReadAccess(any(String[].class)))
                    .thenReturn(Mono.just(accessMap));

            webTestClient.get()
                    .uri(BASE_PATH + "/clientHasReadAccess?appCodes=app1&appCodes=app2")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.app1").isEqualTo(true)
                    .jsonPath("$.app2").isEqualTo(false);
        }

        @Test
        @DisplayName("GET /internal/appInheritance - returns inheritance list")
        void appInheritance_returnsList() {
            when(appService.appInheritance(eq("testapp"), eq("urlClient"), eq("clientA")))
                    .thenReturn(Mono.just(List.of("testapp", "parentapp")));

            webTestClient.get()
                    .uri(BASE_PATH
                            + "/internal/appInheritance?appCode=testapp&urlClientCode=urlClient&clientCode=clientA")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0]").isEqualTo("testapp")
                    .jsonPath("$[1]").isEqualTo("parentapp");
        }
    }

    @Nested
    @DisplayName("App Lookup Endpoints")
    class AppLookupTests {

        @Test
        @DisplayName("GET /internal/appCode/{appCode} - returns app by code (internal)")
        void getAppCode_returnsApp() {
            App app = createTestApp();
            when(appService.getAppByCode(eq("testapp")))
                    .thenReturn(Mono.just(app));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/appCode/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.appName").isEqualTo("Test App");
        }

        @Test
        @DisplayName("GET /appCode/{appCode} - returns app by code with access check")
        void getAppByCode_returnsApp() {
            App app = createTestApp();
            when(appService.getAppByCodeCheckAccess(eq("testapp")))
                    .thenReturn(Mono.just(app));

            webTestClient.get()
                    .uri(BASE_PATH + "/appCode/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.appName").isEqualTo("Test App");
        }

        @Test
        @DisplayName("GET /internal/explicitInfo/{appCode} - returns explicit app info")
        void getAppExplicitInfoByCode_returnsInfo() {
            com.fincity.saas.commons.security.dto.App explicitApp = new com.fincity.saas.commons.security.dto.App();
            explicitApp.setAppCode("testapp");
            explicitApp.setAppName("Test App");
            explicitApp.setClientCode("clientA");
            explicitApp.setId(BigInteger.ONE);

            when(appService.getAppExplicitInfoByCode(eq("testapp")))
                    .thenReturn(Mono.just(explicitApp));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/explicitInfo/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.clientCode").isEqualTo("clientA");
        }

        @Test
        @DisplayName("GET /internal/appStatus/{appCode} - returns app status string")
        void getAppStatus_returnsStatus() {
            when(appService.getAppStatus(eq("testapp")))
                    .thenReturn(Mono.just("ACTIVE"));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/appStatus/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Client Access Management Endpoints")
    class ClientAccessTests {

        @Test
        @DisplayName("POST /{id}/access - adds client access to an application")
        void addClientAccess_returnsTrue() {
            when(appService.addClientAccess(eq(ULong.valueOf(1)), eq(ULong.valueOf(20)), eq(true)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            ApplicationAccessRequest request = new ApplicationAccessRequest();
            request.setClientId(ULong.valueOf(20));
            request.setWriteAccess(true);

            webTestClient.post()
                    .uri(BASE_PATH + "/1/access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("PATCH /{id}/access - updates client access")
        void updateClientAccess_returnsTrue() {
            when(appService.updateClientAccess(eq(ULong.valueOf(5)), eq(false)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            ApplicationAccessRequest request = new ApplicationAccessRequest();
            request.setId(ULong.valueOf(5));
            request.setWriteAccess(false);

            webTestClient.patch()
                    .uri(BASE_PATH + "/1/access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /{id}/access - removes client access")
        void removeClientAccess_returnsTrue() {
            when(appService.removeClient(eq(ULong.valueOf(1)), eq(ULong.valueOf(99))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/1/access?accessId=99")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("GET /clients/{appCode} - returns paginated clients for an app")
        void getAppClients_returnsPage() {
            Client client = new Client();
            client.setId(ULong.valueOf(10));
            client.setCode("clientA");
            client.setName("Client A");

            Page<Client> clientPage = new PageImpl<>(List.of(client));
            when(appService.getAppClients(eq("testapp"), any(), any(), any(Pageable.class)))
                    .thenReturn(Mono.just(clientPage));

            webTestClient.get()
                    .uri(BASE_PATH + "/clients/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].code").isEqualTo("clientA")
                    .jsonPath("$.content[0].name").isEqualTo("Client A");
        }
    }

    @Nested
    @DisplayName("Property Endpoints")
    class PropertyTests {

        @Test
        @DisplayName("GET /property - returns properties with clients")
        void getProperty_returnsResponse() {
            PropertiesResponse response = new PropertiesResponse();
            response.setProperties(Map.of());
            response.setClients(Map.of());

            when(appService.getPropertiesWithClients(any(), any(), any(), any()))
                    .thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri(BASE_PATH + "/property?appCode=testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.properties").exists()
                    .jsonPath("$.clients").exists();
        }

        @Test
        @DisplayName("POST /property - updates a property")
        void updateProperty_returnsTrue() {
            when(appService.updateProperty(any(AppProperty.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            AppProperty property = new AppProperty();
            property.setClientId(ULong.valueOf(10));
            property.setAppId(ULong.valueOf(1));
            property.setName("theme");
            property.setValue("dark");

            webTestClient.post()
                    .uri(BASE_PATH + "/property")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(property)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /property - deletes a property by clientId, appId, and name")
        void deleteProperty_returnsTrue() {
            when(appService.deleteProperty(eq(ULong.valueOf(10)), eq(ULong.valueOf(1)), eq("theme")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/property?clientId=10&appId=1&name=theme")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /property/{propertyId} - deletes a property by ID")
        void deletePropertyById_returnsTrue() {
            when(appService.deletePropertyById(eq(ULong.valueOf(42))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/property/42")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Dependency Endpoints")
    class DependencyTests {

        @Test
        @DisplayName("GET /dependencies - returns app dependencies")
        void getAppDependencies_returnsList() {
            AppDependency dep = new AppDependency();
            dep.setAppCode("testapp");
            dep.setDependentAppCode("depapp");
            dep.setAppName("Test App");
            dep.setDependentAppName("Dependent App");

            when(appService.getAppDependencies(eq("testapp")))
                    .thenReturn(Mono.just(List.of(dep)));

            webTestClient.get()
                    .uri(BASE_PATH + "/dependencies?appCode=testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].appCode").isEqualTo("testapp")
                    .jsonPath("$[0].dependentAppCode").isEqualTo("depapp");
        }

        @Test
        @DisplayName("GET /internal/dependencies - returns dependency app codes only")
        void getInternalAppDependencies_returnsCodeList() {
            AppDependency dep1 = new AppDependency();
            dep1.setDependentAppCode("depapp1");
            AppDependency dep2 = new AppDependency();
            dep2.setDependentAppCode("depapp2");

            when(appService.getAppDependencies(eq("testapp")))
                    .thenReturn(Mono.just(List.of(dep1, dep2)));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/dependencies?appCode=testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0]").isEqualTo("depapp1")
                    .jsonPath("$[1]").isEqualTo("depapp2");
        }

        @Test
        @DisplayName("POST /dependency - adds a new dependency")
        void addDependency_returnsDependency() {
            AppDependency result = new AppDependency();
            result.setAppCode("testapp");
            result.setDependentAppCode("newdep");

            when(appService.addAppDependency(eq("testapp"), eq("newdep")))
                    .thenReturn(Mono.just(result));

            AppDependency request = new AppDependency();
            request.setAppCode("testapp");
            request.setDependentAppCode("newdep");

            webTestClient.post()
                    .uri(BASE_PATH + "/dependency")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.dependentAppCode").isEqualTo("newdep");
        }

        @Test
        @DisplayName("DELETE /dependency - removes a dependency")
        void removeDependency_returnsTrue() {
            when(appService.removeAppDependency(eq("testapp"), eq("depapp")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/dependency?appCode=testapp&dependencyCode=depapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Delete Everything Endpoint")
    class DeleteEverythingTests {

        @Test
        @DisplayName("DELETE /everything/{id} - deletes everything for app")
        void deleteByAppId_returnsTrue() {
            when(appService.deleteEverything(eq(ULong.valueOf(1)), eq(false)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/everything/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("DELETE /everything/{id}?forceDelete=true - force deletes everything")
        void deleteByAppId_forceDelete_returnsTrue() {
            when(appService.deleteEverything(eq(ULong.valueOf(1)), eq(true)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/everything/1?forceDelete=true")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Find Apps and Override Endpoints")
    class FindAppsTests {

        @Test
        @DisplayName("GET /findBaseClientCode/{applicationCode} - returns client code and boolean tuple")
        void findBaseClientCodeForOverride_returnsTuple() {
            when(appService.findBaseClientCodeForOverride(eq("testapp")))
                    .thenReturn(Mono.just(Tuples.of("baseClient", Boolean.TRUE)));

            webTestClient.get()
                    .uri(BASE_PATH + "/findBaseClientCode/testapp")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.t1").isEqualTo("baseClient")
                    .jsonPath("$.t2").isEqualTo(true);
        }

        @Test
        @DisplayName("GET /findAnyApps - returns paginated apps")
        void findAnyApps_returnsPage() {
            App app = createTestApp();
            Page<App> appPage = new PageImpl<>(List.of(app));

            when(appService.findAnyAppsByPage(any(Pageable.class), any(AbstractCondition.class)))
                    .thenReturn(Mono.just(appPage));

            webTestClient.get()
                    .uri(BASE_PATH + "/findAnyApps")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].appCode").isEqualTo("testapp")
                    .jsonPath("$.content[0].appName").isEqualTo("Test App");
        }
    }

    @Nested
    @DisplayName("Inherited CRUD Endpoints")
    class CrudTests {

        @Test
        @DisplayName("POST / - creates a new application")
        void create_returnsCreatedApp() {
            App app = createTestApp();
            when(appService.create(any(App.class)))
                    .thenReturn(Mono.just(app));

            App request = new App();
            request.setAppName("Test App");
            request.setAppCode("testapp");
            request.setClientId(ULong.valueOf(10));

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.appName").isEqualTo("Test App");
        }

        @Test
        @DisplayName("GET /{id} - reads an application by ID")
        void read_returnsApp() {
            App app = createTestApp();
            when(appService.read(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(app));

            webTestClient.get()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.appCode").isEqualTo("testapp")
                    .jsonPath("$.appName").isEqualTo("Test App");
        }

        @Test
        @DisplayName("GET /{id} - returns 404 when app not found")
        void read_returnsNotFound() {
            when(appService.read(eq(ULong.valueOf(999))))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/999")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("DELETE /{id} - deletes an application by ID")
        void delete_returnsNoContent() {
            when(appService.delete(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(1));

            webTestClient.delete()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }
}
