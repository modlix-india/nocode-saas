# Testing Guide

This document covers the testing frameworks, patterns, and utilities used across the nocode-saas platform.

## Test Framework Stack

| Framework | Version | Purpose |
|-----------|---------|---------|
| **JUnit 5** (Jupiter) | Managed by Spring Boot | Test framework |
| **Mockito** | 5.21.0 | Mocking |
| **Reactor Test** | Managed by Spring Boot | Testing Mono/Flux with StepVerifier |
| **Testcontainers** | 1.21.4 | Docker-based integration tests (MySQL) |
| **Spring Boot Test** | Managed by Spring Boot | Spring context testing |
| **Spring Security Test** | Managed by Spring Boot | Security context testing |

## Test Types

The project uses three types of tests:

| Type | Base Class | Location | Database |
|------|-----------|----------|----------|
| **Unit tests** | `AbstractServiceUnitTest` | `service/` | Mocked |
| **Controller tests** | `AbstractControllerTest` | `controller/` | Testcontainers MySQL |
| **Integration tests** | `AbstractIntegrationTest` | `integration/` | Testcontainers MySQL |

## Unit Tests (Service Layer)

Unit tests mock all dependencies and test service logic in isolation.

### Setup

Extend `AbstractServiceUnitTest` and use `@ExtendWith(MockitoExtension.class)`:

```java
@ExtendWith(MockitoExtension.class)
class ClientManagerServiceTest extends AbstractServiceUnitTest {

    @Mock
    private ClientManagerDAO dao;

    @Mock
    private SecurityMessageResourceService messageResourceService;

    @Mock
    private CacheService cacheService;

    @Mock
    private ClientService clientService;

    @Mock
    private UserService userService;

    private ClientManagerService service;

    private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
    private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
    private static final ULong USER_ID = ULong.valueOf(10);

    @BeforeEach
    void setUp() {
        // Create service with mocked dependencies
        service = new ClientManagerService(messageResourceService, cacheService,
                clientService, userService, clientHierarchyService);

        // Inject mocked DAO via reflection (set by parent class)
        try {
            var daoField = ReflectionUtils.findField(service.getClass(), "dao");
            daoField.setAccessible(true);
            daoField.set(service, dao);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject DAO", e);
        }

        lenient().when(dao.getPojoClass()).thenReturn(Mono.just(ClientManager.class));

        // Setup common mocks from AbstractServiceUnitTest
        setupMessageResourceService(messageResourceService);
        setupCacheService(cacheService);
    }
}
```

### AbstractServiceUnitTest Utilities

Located at `security/src/test/java/com/fincity/security/service/AbstractServiceUnitTest.java`:

| Method | Purpose |
|--------|---------|
| `setupSecurityContext(ca)` | Mock `SecurityContextUtil` to return given `ContextAuthentication` |
| `setupEmptySecurityContext()` | Mock `SecurityContextUtil` to return empty (unauthenticated) |
| `setupMessageResourceService(mrs)` | Mock `throwMessage()` to throw `GenericException` with the message ID |
| `setupCacheService(cs)` | Mock cache to pass through to the supplier (no actual caching) |
| `setupSoxLogService(sls)` | Mock SOX logging as no-op |

### Writing a Unit Test

```java
@Test
void create_SystemClient_HasAccess_CreatesMapping() {
    // 1. Setup authentication context
    ContextAuthentication ca = TestDataFactory.createSystemAuth();
    setupSecurityContext(ca);

    // 2. Mock dependencies
    User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
    when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

    when(dao.createIfNotExists(eq(TARGET_CLIENT_ID), eq(USER_ID), any(ULong.class)))
            .thenReturn(Mono.just(1));

    // 3. Execute and verify with StepVerifier
    StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
            .assertNext(result -> assertTrue(result))
            .verifyComplete();
}
```

### Testing Error Cases

```java
@Test
void create_SameClientAsUser_ThrowsBadRequest() {
    ContextAuthentication ca = TestDataFactory.createSystemAuth();
    setupSecurityContext(ca);

    User user = TestDataFactory.createActiveUser(USER_ID, TARGET_CLIENT_ID);
    when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

    StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
            .expectErrorMatches(e -> e instanceof GenericException
                    && ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
            .verify();
}

@Test
void create_NotHasAccess_ThrowsForbidden() {
    ContextAuthentication ca = TestDataFactory.createBusinessAuth(contextClientId, "NOACCESS",
            List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
    setupSecurityContext(ca);

    // ... mock dependencies ...

    StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
            .expectErrorMatches(e -> e instanceof GenericException
                    && ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
            .verify();
}
```

### Verifying Side Effects

```java
@Test
void create_EvictsCacheForUserAndClient() {
    // ... setup and execute ...

    StepVerifier.create(service.create(USER_ID, TARGET_CLIENT_ID))
            .assertNext(result -> assertTrue(result))
            .verifyComplete();

    // Verify cache eviction was called
    verify(cacheService).evict(eq("clientManager"), eq(USER_ID), eq(TARGET_CLIENT_ID));
}
```

## TestDataFactory

Located at `security/src/test/java/com/fincity/security/testutil/TestDataFactory.java`.

Factory methods for creating test objects without touching the database:

```java
// Clients
TestDataFactory.createSystemClient()                              // System client (SYS, ID=1)
TestDataFactory.createBusinessClient(id, code)                    // Business client
TestDataFactory.createIndividualClient(id, code)                  // Individual client

// Users
TestDataFactory.createActiveUser(id, clientId)                    // Active user
TestDataFactory.createLockedUser(id, clientId, lockUntil)         // Locked user
TestDataFactory.createPasswordExpiredUser(id, clientId)           // Password expired
TestDataFactory.createInactiveUser(id, clientId)                  // Inactive user
TestDataFactory.createDeletedUser(id, clientId)                   // Deleted user

// Authentication contexts
TestDataFactory.createSystemAuth()                                // System admin with all authorities
TestDataFactory.createBusinessAuth(clientId, code, authorities)   // Business user with specific authorities

// Other entities
TestDataFactory.createApp(id, clientId, appCode, appName, accessType)
TestDataFactory.createRoleV2(id, clientId, appId, name)
TestDataFactory.createProfile(id, clientId, appId, name)
TestDataFactory.createTokenObject(id, userId, token, expiresAt)
TestDataFactory.createClientHierarchy(clientId, level0, level1, level2, level3)
TestDataFactory.createClientManager(id, clientId, managerId)

// Policies
TestDataFactory.createPasswordPolicy()
TestDataFactory.createPinPolicy()
TestDataFactory.createOtpPolicy()
```

## Controller Tests

Controller tests use `@WebFluxTest` with the full Spring context via Testcontainers.

### Setup

Extend `AbstractControllerTest`:

```java
@WebFluxTest(ClientManagerController.class)
@ContextConfiguration(classes = { SecurityApplication.class, TestWebSecurityConfig.class })
class ClientManagerControllerTest extends AbstractControllerTest {

    @MockitoBean
    private ClientManagerService service;

    // Tests use webTestClient (inherited from AbstractControllerTest)
}
```

### AbstractControllerTest Utilities

Located at `security/src/test/java/com/fincity/security/controller/AbstractControllerTest.java`:

```java
// Inherited field
protected WebTestClient webTestClient;

// Helper methods for setting headers
withAuth(spec, token)         // Set Authorization: Bearer {token}
withAppCode(spec, appCode)    // Set appCode header
withClientCode(spec, code)    // Set clientCode header
```

## Integration Tests

Integration tests use a real MySQL database via Testcontainers with Flyway migrations.

### Setup

Extend `AbstractIntegrationTest`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClientManagerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ClientManagerService service;

    @BeforeEach
    void setUp() {
        setupMockBeans();     // Mock RabbitMQ, Feign clients
        cleanupTestData().block();
    }
}
```

### AbstractIntegrationTest

Located at `security/src/test/java/com/fincity/security/integration/AbstractIntegrationTest.java`.

Key features:
- **Testcontainers MySQL** — Starts a real MySQL 8.0 container with reuse
- **`@DynamicPropertySource`** — Configures R2DBC and Flyway to use the container
- **Mocked beans** — `CachingConnectionFactory`, `EventCreationService`, `IFeignFilesService`
- **Helper methods** for inserting test data directly via SQL

```java
// Data setup helpers
insertTestClient(code, name, typeCode)              // Insert a client
insertTestUser(clientId, userName, email, password)  // Insert a user
insertClientHierarchy(clientId, l0, l1, l2, l3)     // Insert hierarchy
insertClientManager(clientId, managerId)             // Insert manager mapping
insertTestApp(clientId, appCode, appName)            // Insert an app

// Cleanup
cleanupTestData()  // Deletes test data (preserves system client ID=1)

// Mock setup
setupMockBeans()   // Mocks EventCreationService and IFeignFilesService
```

### Integration Test Example

```java
@Test
void testCreateClientManagerWithRealDatabase() {
    // Insert test data
    ULong clientId = insertTestClient("TEST", "Test Client", "BUS").block();
    ULong userId = insertTestUser(ULong.valueOf(1), "manager", "mgr@test.com", "pass").block();
    insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null).block();

    // Mock security context
    ContextAuthentication ca = TestDataFactory.createSystemAuth();
    try (var mock = Mockito.mockStatic(SecurityContextUtil.class)) {
        mock.when(SecurityContextUtil::getUsersContextAuthentication)
                .thenReturn(Mono.just(ca));

        // Execute
        StepVerifier.create(service.create(userId, clientId))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
    }
}
```

## Test Configuration

### `application-test.yml`

Located at `security/src/test/resources/application-test.yml`. Key overrides:
- Disables Eureka client registration
- Disables Spring Cloud Config
- Excludes RabbitMQ auto-configuration
- Uses a test JWT secret key
- Test Flyway migrations from `classpath:db/migration,classpath:db/testfixture`

### TestWebSecurityConfig

Located at `security/src/test/java/com/fincity/security/testutil/TestWebSecurityConfig.java`:
- Disables CSRF for tests
- Permits all HTTP exchanges
- Configures custom ObjectMapper with ULong serialization
- Custom error handling for test responses

## StepVerifier Patterns

### Successful completion with value

```java
StepVerifier.create(mono)
        .assertNext(value -> {
            assertEquals(expected, value);
        })
        .verifyComplete();
```

### Successful completion with multiple values (Flux)

```java
StepVerifier.create(flux)
        .expectNext(value1)
        .expectNext(value2)
        .verifyComplete();
```

### Error verification

```java
StepVerifier.create(mono)
        .expectErrorMatches(e -> e instanceof GenericException
                && ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
        .verify();
```

### Empty completion

```java
StepVerifier.create(mono)
        .verifyComplete();  // Completes without emitting any value
```

## Running Tests

### All tests for a module

```bash
cd security
mvn test
```

### Single test class

```bash
cd security
mvn test -Dtest=ClientManagerServiceTest
```

### Single test method

```bash
cd security
mvn test -Dtest="ClientManagerServiceTest#create_SystemClient_HasAccess_CreatesMapping"
```

### Skip tests during build

```bash
cd security
mvn clean install -DskipTests
```

## Code Coverage

JaCoCo is configured for code coverage. JOOQ-generated code is excluded:

```xml
<exclude>com/fincity/security/jooq/**</exclude>
```

Run coverage:
```bash
cd security
mvn test jacoco:report
```

Reports are generated at `target/site/jacoco/index.html`.

## Test Naming Conventions

Follow the pattern: `methodName_condition_expectedResult`

```java
create_SystemClient_HasAccess_CreatesMapping()
create_SameClientAsUser_ThrowsBadRequest()
create_NotHasAccess_ThrowsForbidden()
getClientsOfUser_EmptyPage_ReturnsEmptyPage()
delete_NoAccess_ThrowsForbidden()
isUserClientManager_ByCA_DifferentClient_IsManager_ReturnsTrue()
```
