# Entity Processor Module - Integration Guide

## Table of Contents

1. [Overview](#1-overview)
2. [Security Service Integration](#2-security-service-integration)
3. [Core Service Integration](#3-core-service-integration)
4. [Message Service Integration](#4-message-service-integration)
5. [Eureka Service Discovery](#5-eureka-service-discovery)
6. [Spring Cloud Config](#6-spring-cloud-config)
7. [KIRun Function Execution Engine](#7-kirun-function-execution-engine)
8. [Walk-In Form Integration](#8-walk-in-form-integration)
9. [Campaign Integration](#9-campaign-integration)
10. [Website Integration](#10-website-integration)
11. [Database Integration (R2DBC + JOOQ)](#11-database-integration-r2dbc--jooq)
12. [Gson Serialization Integration](#12-gson-serialization-integration)
13. [Analytics Integration](#13-analytics-integration)
14. [Eager Loading Integration](#14-eager-loading-integration)
15. [Source and SubSource Normalization](#15-source-and-subsource-normalization)
16. [Activity Logging System](#16-activity-logging-system)
17. [Business Partner Integration](#17-business-partner-integration)
18. [Caching Integration](#18-caching-integration)
19. [Rule Engine Integration](#19-rule-engine-integration)
20. [Appendix: Entity Catalog](#appendix-entity-catalog)

---

## 1. Overview

The Entity Processor module is a reactive CRM microservice within the Modlix no-code/low-code platform. It manages the complete lifecycle of leads, tickets, owners, products, campaigns, and related CRM entities. Built on Spring Boot 3.4.1 with Project Reactor, the module provides fully non-blocking, multi-tenant data operations.

### Module Identity

| Property | Value |
|----------|-------|
| Service Name | `entity-processor` |
| Base Package | `com.fincity.saas.entity.processor` |
| Database | `entity_processor` (MySQL 8.0+, R2DBC) |
| API Base Path | `/api/entity/processor/` |
| Eureka Registration | `entity-processor` |
| Main Class | `EntityProcessorApplication` |

### Architecture Layers

```
Controller Layer
    |-- BaseController / BaseProcessorController / BaseUpdatableController
    |-- Open Controllers (no auth: TicketOpenController, WalkInFormController, TicketCallController)
    |-- Authenticated Controllers (TicketController, OwnerController, etc.)

Service Layer
    |-- BaseService -> BaseUpdatableService -> BaseProcessorService
    |-- IProcessorAccessService (security context resolution)
    |-- IRepositoryProvider (KIRun function/schema integration)
    |-- ActivityService (cross-cutting activity logging)

DAO Layer
    |-- BaseDAO -> BaseUpdatableDAO -> BaseProcessorDAO
    |-- IEagerDAO (eager loading support)
    |-- ITimezoneDAO (timezone-aware queries)

Cross-Cutting Concerns
    |-- ProcessorAccess (tenant-scoped security context)
    |-- EagerUtil (relationship resolution)
    |-- Gson adapters (custom serialization)
    |-- CacheService (entity caching)
```

### Key Design Principles

1. **Fully Reactive**: All service methods return `Mono<T>` or `Flux<T>`. Blocking operations are not permitted.
2. **Multi-Tenant**: Every entity is scoped by `appCode` and `clientCode`. Access control is enforced at the DAO level.
3. **ProcessorAccess-Centric**: All authenticated operations flow through `ProcessorAccess`, which encapsulates the current user's identity, tenant context, and hierarchy information.
4. **Constructor Injection**: Services use constructor injection (with `@Lazy` for circular dependencies).
5. **FlatMapUtil Chaining**: Complex reactive chains use `FlatMapUtil.flatMapMono()` and `FlatMapUtil.flatMapMonoWithNull()` for readability.

---

## 2. Security Service Integration

### 2.1 Authentication Flow

The Entity Processor relies on the Gateway service for JWT validation. The authentication flow proceeds as follows:

```
Client Request
    |
    v
Gateway (port 8080)
    |-- Validates JWT token
    |-- Extracts user claims
    |-- Injects ContextAuthentication into ReactiveSecurityContextHolder
    |-- Forwards request to Entity Processor
    |
    v
Entity Processor
    |-- SecurityContextUtil.getUsersContextAuthentication()
    |-- Retrieves ContextAuthentication from Reactor context
    |-- Builds ProcessorAccess from ContextAuthentication
    |-- Constructs UserInheritanceInfo from security service data
```

#### Sequence Diagram: Authenticated Request

```
Client          Gateway         Entity Processor      Security Service
  |                |                   |                     |
  |-- JWT Token -->|                   |                     |
  |                |-- Validate JWT -->|                     |
  |                |                   |                     |
  |                |-- Forward Req --->|                     |
  |                |                   |                     |
  |                |                   |-- hasAccess() ----->|
  |                |                   |                     |
  |                |                   |<-- getUserSubOrg ---|
  |                |                   |<-- getManagedClient-|
  |                |                   |<-- getManagingIds --|
  |                |                   |                     |
  |                |                   |-- ProcessorAccess   |
  |                |                   |-- (constructed)     |
  |                |                   |                     |
  |<-- Response ---|<-- Response ------|                     |
```

### 2.2 ProcessorAccess Construction

`ProcessorAccess` is the central security context object for the Entity Processor. It encapsulates the authenticated user's identity, tenant information, and hierarchy data.

#### Source: `ProcessorAccess.java`

```java
@Data
@Accessors(chain = true)
public final class ProcessorAccess implements Serializable {

    private String appCode;
    private String clientCode;
    private ULong userId;
    private String firstName;
    private String lastName;
    private String middleName;

    private boolean hasAccessFlag;
    private ContextUser user;
    private UserInheritanceInfo userInherit;
    private boolean hasBpAccess;
}
```

#### Construction from Authenticated Context

For authenticated endpoints, `ProcessorAccess` is built via the `IProcessorAccessService.hasAccess()` method:

```java
// IProcessorAccessService.java
default Mono<ProcessorAccess> hasAccess() {
    return FlatMapUtil.flatMapMono(
        SecurityContextUtil::getUsersContextAuthentication,
        ca -> Mono.just(ca.isAuthenticated())
            .flatMap(BooleanUtil::safeValueOfWithEmpty)
            .switchIfEmpty(this.getMsgService()
                .throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    ProcessorMessageResourceService.LOGIN_REQUIRED)),
        (ca, isAuthenticated) -> this.getProcessorAccess(ca));
}
```

The `getProcessorAccess()` method internally calls the Security Service to build `UserInheritanceInfo`:

```java
private Mono<ProcessorAccess.UserInheritanceInfo> getUserInheritanceInfo(ContextAuthentication ca) {

    Mono<List<BigInteger>> userSubOrgMono = this.getSecurityService()
        .getUserSubOrgInternal(
            ca.getUser().getId(), ca.getUrlAppCode(), ca.getUser().getClientId());

    boolean hasOwnerRole = SecurityContextUtil.hasAuthority(
        BusinessPartnerConstant.OWNER_ROLE, ca.getUser().getAuthorities());
    boolean hasManagerRole = !hasOwnerRole
        && SecurityContextUtil.hasAuthority(
            BusinessPartnerConstant.BP_MANAGER_ROLE, ca.getUser().getAuthorities());

    Mono<List<BigInteger>> managingClientMono;
    if (hasOwnerRole) {
        managingClientMono =
            this.getSecurityService().getManagingClientIds(ca.getUser().getClientId());
    } else if (hasManagerRole) {
        managingClientMono = this.getSecurityService()
            .getClientIdsOfManager(ca.getUser().getId())
            .defaultIfEmpty(List.of());
    } else {
        managingClientMono = Mono.just(List.of());
    }

    Mono<Client> managedClientMono =
        BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(ca.getClientLevelType())
            ? this.getSecurityService()
                .getManagedClientOfClientById(ca.getUser().getClientId())
            : Mono.empty();

    return Mono.zip(userSubOrgMono, managingClientMono, managedClientMono.defaultIfEmpty(new Client()))
        .map(userInheritTup -> ProcessorAccess.UserInheritanceInfo.of(ca, userInheritTup));
}
```

#### Construction for Open Endpoints (No JWT)

For open/public endpoints (campaigns, website, walk-in forms), `ProcessorAccess` is constructed without a user:

```java
// No authenticated user - open endpoint
ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);
```

This produces a `ProcessorAccess` with:
- `appCode` and `clientCode` set from request headers or body
- `hasAccessFlag` = true
- `user` = null
- `userInherit` = null
- `hasBpAccess` = false

### 2.3 UserInheritanceInfo

`UserInheritanceInfo` captures the hierarchical context for the current user:

```java
@Data
@Accessors(chain = true)
public static class UserInheritanceInfo implements Serializable {

    private String clientLevelType;       // "CUSTOMER" for BP users, null for normal

    private String loggedInClientCode;    // Client code where user logged in
    private ULong loggedInClientId;       // Client ID where user logged in

    private String managedClientCode;     // For BP: the client being managed
    private ULong managedClientId;        // For BP: the managed client's ID

    private List<ULong> subOrg;           // Subordinate user IDs
    private List<ULong> managingClientIds;// Client IDs this user manages
}
```

This information is fetched from the Security Service via three parallel calls:
1. `getUserSubOrgInternal()` - Gets subordinate user IDs for the current user
2. `getManagingClientIds()` or `getClientIdsOfManager()` - Gets managed client IDs based on role
3. `getManagedClientOfClientById()` - For Business Partner users, gets the managed client

### 2.4 User Lookup via Security Service

The Entity Processor communicates with the Security Service through `IFeignSecurityService` (injected as `securityService` in base service classes):

| Method | Purpose | Used By |
|--------|---------|---------|
| `getUserSubOrgInternal(userId, appCode, clientId)` | Get subordinate user IDs | `IProcessorAccessService.getUserInheritanceInfo()` |
| `getManagingClientIds(clientId)` | Get client IDs managed by owner | `IProcessorAccessService.getUserInheritanceInfo()` |
| `getClientIdsOfManager(userId)` | Get client IDs for BP manager | `IProcessorAccessService.getUserInheritanceInfo()` |
| `getManagedClientOfClientById(clientId)` | Get managed client for BP | `IProcessorAccessService.getUserInheritanceInfo()` |
| `getClientById(clientId)` | Get client by ID | `TicketService.createForPartnerImportDCRM()` |
| `getUserInternal(userId, clientCode)` | Get user details | `TicketService.createForPartnerImportDCRM()` |
| `getClientByCode(clientCode)` | Get client by code | `IProcessorAccessService.getHasAccessFlag()` |
| `appInheritance(appCode, urlClientCode, clientCode)` | Check app access | `IProcessorAccessService.getHasAccessFlag()` |
| `isUserClientManageClient(appCode, userId, clientId, targetClientId)` | Check management permission | `IProcessorAccessService.getHasAccessFlag()` |

### 2.5 Authority System

Controllers that extend `BaseProcessorController` inherit `@PreAuthorize` annotations from the class hierarchy. Authority names follow the pattern:

```
Authorities.{EntityName}_{ACTION}
```

Where `{EntityName}` is derived from the entity's `EntitySeries` and `{ACTION}` is one of:
- `CREATE`
- `READ`
- `UPDATE`
- `DELETE`

For application-scoped permissions, the authority format adapts via `AuthoritiesNameUtil`:

```
Authorities.{APPCODE}.{Permission_Name}
```

#### Open Endpoints (No Authorization Required)

The following paths are excluded from security filtering in `ProcessorConfiguration`:

```java
@Bean
public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
    return this.springSecurityFilterChain(
        http,
        authService,
        this.objectMapper,
        "/api/entity/processor/functions/**",
        "/api/entity/processor/open/**",
        "/api/entity/processor/products/internal",
        "/api/entity/processor/products/internal/**",
        "/api/entity/processor/tickets/req/DCRM",
        "/api/entity/processor/functions/repositoryFilter",
        "/api/entity/processor/functions/repositoryFind",
        "/api/entity/processor/schemas/repositoryFind",
        "/api/entity/processor/schemas/repositoryFilter");
}
```

### 2.6 Client Hierarchy

Modlix supports hierarchical client relationships. The Entity Processor enforces this hierarchy through the `ProcessorAccess` model:

```
Parent Client (Owner)
    |-- Can manage child clients
    |-- getManagingClientIds() returns child client IDs
    |
    |-- Business Partner (BP) Client
    |       |-- clientLevelType = "CUSTOMER"
    |       |-- Can create tickets for managed client
    |       |-- getEffectiveClientCode() returns managed client's code
    |       |-- isBpManager(authorities) checks BP access
    |
    |-- Regular Users
            |-- getEffectiveClientCode() returns own client code
            |-- subOrg list defines subordinate users
```

#### Outside User Detection

```java
// ProcessorAccess.java
public boolean isOutsideUser() {
    if (userInherit != null)
        return BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(this.userInherit.clientLevelType);
    return false;
}

public String getEffectiveClientCode() {
    return isOutsideUser() ? this.getUserInherit().getManagedClientCode() : this.getClientCode();
}
```

When an outside (BP) user creates entities, the entity's `clientCode` is set to the managed client's code, not the BP's own client code:

```java
// BaseUpdatableService.createInternal()
entity.setClientCode(
    access.isOutsideUser() ? access.getUserInherit().getManagedClientCode() : access.getClientCode());
```

### 2.7 Reassignment Authorization

When reassigning a ticket, the service verifies that the target user is within the current user's subordinate organization:

```java
// TicketService.reassignTicket()
if (!access.getUserInherit().getSubOrg().contains(ticketReassignRequest.getUserId()))
    return this.msgService.throwMessage(
        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
        ProcessorMessageResourceService.INVALID_USER_ACCESS);
```

---

## 3. Core Service Integration

### 3.1 Connection Management

The Entity Processor integrates with the Core microservice to manage communication connections (phone, email, SMS, WhatsApp).

#### Feign Client: `IFeignCoreService`

```java
@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    String CONNECTION_PATH = "/api/core/connections/internal";

    @GetMapping(CONNECTION_PATH + "/{name}")
    Mono<Connection> getConnection(
        @PathVariable("name") String connectionName,
        @RequestParam String appCode,
        @RequestParam String clientCode,
        @RequestParam String urlClientCode,
        @RequestParam String connectionType);
}
```

#### Connection Document

The `Connection` document, fetched from the Core service, contains:

```java
public class Connection extends AbstractOverridableDTO<Connection> {
    private ConnectionType connectionType;           // CALL, TEXT, MAIL, etc.
    private ConnectionSubType connectionSubType;     // EXOTEL, WHATSAPP, SENDGRID, etc.
    private Map<String, Object> connectionDetails;   // Provider-specific config
    private Boolean isAppLevel = Boolean.FALSE;       // App-level vs client-level
    private Boolean onlyThruKIRun = Boolean.FALSE;    // Restrict to KIRun execution only
}
```

### 3.2 ConnectionType Enum

```java
public enum ConnectionType {
    APP_DATA(ConnectionSubType.MONGO),
    MAIL(ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),
    REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_AUTH, ConnectionSubType.REST_API_OAUTH2),
    NOTIFICATION,
    IN_APP,
    MOBILE_PUSH,
    WEB_PUSH,
    TEXT(ConnectionSubType.WHATSAPP),
    CALL(ConnectionSubType.EXOTEL);
}
```

### 3.3 ConnectionSubType Enum

```java
public enum ConnectionSubType {
    MONGO,
    OFFICE365,
    SENDGRID,
    REST_API_OAUTH2,
    REST_API_BASIC,
    REST_API_AUTH,
    SMTP,
    EXOTEL,
    WHATSAPP;
}
```

### 3.4 ConnectionServiceProvider

The `ConnectionServiceProvider` creates a `BaseConnectionService` instance for each `ConnectionType` at startup:

```java
@Component
public class ConnectionServiceProvider {

    private final Map<ConnectionType, BaseConnectionService> servicesByType;

    public ConnectionServiceProvider(AutowireCapableBeanFactory beanFactory) {
        EnumMap<ConnectionType, BaseConnectionService> map = new EnumMap<>(ConnectionType.class);
        for (ConnectionType type : ConnectionType.values()) {
            BaseConnectionService svc = new GenericConnectionService(type);
            beanFactory.autowireBean(svc);
            map.put(type, svc);
        }
        this.servicesByType = map;
    }

    public BaseConnectionService getService(ConnectionType type) {
        return this.servicesByType.get(type);
    }
}
```

### 3.5 Integration Flow: Ticket to Communication

```
1. Ticket created -> linked to Product (via productId)
2. Product has ProductComm entries (communication configurations)
3. ProductComm references:
   - connectionType (CALL, TEXT, MAIL, etc.)
   - connectionSubType (EXOTEL, WHATSAPP, etc.)
   - connectionName (identifies the Connection in Core service)
4. When communication is needed:
   a. Fetch ProductComm from entity_processor DB
   b. Use connectionName to fetch Connection from Core service
   c. Connection.connectionDetails contains provider credentials/URLs
   d. Use credentials to initiate communication via Message service
```

#### Example: Getting a Ticket's Product Communication

```java
// TicketService.getTicketProductComm()
public Mono<ProductComm> getTicketProductComm(
        Identity ticketId, ConnectionType connectionType, ConnectionSubType connectionSubType) {
    return FlatMapUtil.flatMapMono(
        super::hasAccess,
        access -> this.readByIdentity(access, ticketId),
        (access, ticket) -> this.productCommService.getProductComm(
            access,
            ticket.getProductId(),
            connectionType,
            connectionSubType,
            ticket.getSource(),
            ticket.getSubSource()))
    .switchIfEmpty(Mono.empty())
    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicketProductComm"));
}
```

---

## 4. Message Service Integration

### 4.1 Exotel Phone Integration

The Entity Processor handles incoming phone calls from Exotel (a cloud telephony provider) and routes them to the appropriate assigned user.

#### Feign Client: `IFeignMessageService`

```java
@ReactiveFeignClient(name = "message")
public interface IFeignMessageService {

    String MESSAGE_PATH = "/api/message";
    String EXOTEL_CALL_PATH = MESSAGE_PATH + "/call/exotel";

    @PostMapping(EXOTEL_CALL_PATH + "/connect")
    Mono<ExotelConnectAppletResponse> connectCall(
        @RequestHeader("appCode") String appCode,
        @RequestHeader("clientCode") String clientCode,
        @RequestBody IncomingCallRequest callRequest);
}
```

### 4.2 Incoming Call Controller

```java
@RestController
@RequestMapping("api/entity/processor/open/call")
public class TicketCallController {

    @GetMapping()
    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
        @RequestHeader("appCode") String appCode,
        @RequestHeader("clientCode") String clientCode,
        ServerHttpRequest request) {
        return ticketCallService.incomingExotelCall(appCode, clientCode, request);
    }
}
```

### 4.3 Call Flow

The complete call routing flow is handled by `TicketCallService`:

#### Sequence Diagram: Incoming Exotel Call

```
Exotel         Entity Processor      ProductCommService     TicketService      Message Service
  |                  |                      |                     |                   |
  |-- GET /open/call |                      |                     |                   |
  |   (CallSid,      |                      |                     |                   |
  |    From, To)     |                      |                     |                   |
  |                  |                      |                     |                   |
  |                  |-- getByPhoneNumber --|                     |                   |
  |                  |   (callerId=To)     |                     |                   |
  |                  |<-- ProductComm -----|                     |                   |
  |                  |                      |                     |                   |
  |                  |-- getTicket(From) ---|-------------------->|                   |
  |                  |                      |                     |                   |
  |                  |  [If no ticket]      |                     |                   |
  |                  |-- create(ticket) ----|-------------------->|                   |
  |                  |<-- Ticket -----------|---------------------|                   |
  |                  |                      |                     |                   |
  |                  |-- connectCall -------|---------------------|------------------>|
  |                  |   (connectionName,   |                     |                   |
  |                  |    assignedUserId)   |                     |                   |
  |                  |<-- ExotelResponse ---|---------------------|-------------------|
  |                  |                      |                     |                   |
  |                  |-- acCallLog ---------|                     |                   |
  |                  |   (log activity)     |                     |                   |
  |                  |                      |                     |                   |
  |<-- XML Response -|                      |                     |                   |
```

#### TicketCallService Implementation Details

```java
// TicketCallService.incomingExotelCall()
public Mono<ExotelConnectAppletResponse> incomingExotelCall(
        String appCode, String clientCode, Map<String, String> providerIncomingRequest) {

    ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

    ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(params);
    PhoneNumber from = PhoneNumber.of(exotelRequest.getFrom());
    PhoneNumber callerId = PhoneNumber.of(exotelRequest.getTo());

    return FlatMapUtil.flatMapMono(
        // Step 1: Find ProductComm by caller ID phone number
        () -> productCommService
            .getByPhoneNumber(access, CALL_CONNECTION, ConnectionSubType.EXOTEL, callerId)
            .switchIfEmpty(msgService.throwMessage(...)),

        // Step 2: Find or create ticket for the caller
        productComm -> ticketService
            .getTicket(access, productComm.getProductId(), from, null)
            .switchIfEmpty(Mono.defer(() -> this.createExotelTicket(access, from, productComm))),

        // Step 3: Connect the call via Message Service
        (productComm, ticket) -> messageService.connectCall(
            appCode, clientCode, new IncomingCallRequest()
                .setProviderIncomingRequest(providerIncomingRequest)
                .setConnectionName(productComm.getConnectionName())
                .setUserId(ticket.getAssignedUserId())),

        // Step 4: Log the call activity
        (productComm, ticket, response) ->
            this.logCall(access, ticket).thenReturn(response))
    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallService.incomingExotelCall"));
}
```

### 4.4 Call Models

#### IncomingCallRequest

```java
public class IncomingCallRequest extends BaseMessageRequest {
    private Map<String, String> providerIncomingRequest;  // Raw Exotel params
    private String connectionName;                        // Connection name in Core
    private ULong userId;                                 // Assigned user to connect to
}
```

#### ExotelConnectAppletRequest

Parsed from Exotel's webhook query parameters:
- `CallSid` - Unique call identifier
- `From` - Caller's phone number
- `To` - Called number (maps to ProductComm)

#### ExotelConnectAppletResponse

XML response returned to Exotel's IVR system for call routing.

### 4.5 CallStatus Enum

```java
public enum CallStatus {
    COMPLETED,
    BUSY,
    NO_ANSWER,
    FAILED,
    CANCELLED
}
```

---

## 5. Eureka Service Discovery

### 5.1 Registration

The Entity Processor registers itself with Eureka under the service name `entity-processor`. This enables other microservices (and the Entity Processor itself) to discover and communicate via service names rather than hardcoded URLs.

### 5.2 Service Resolution

Feign clients resolve service names automatically through Eureka:

```java
@ReactiveFeignClient(name = "core")     // Resolves "core" via Eureka
public interface IFeignCoreService { ... }

@ReactiveFeignClient(name = "message")  // Resolves "message" via Eureka
public interface IFeignMessageService { ... }
```

The `IFeignSecurityService` (from commons) similarly resolves `security` via Eureka.

### 5.3 Health Monitoring

Spring Boot Actuator provides health check endpoints that Eureka uses for service health monitoring:

- `/actuator/health` - Overall health status
- `/actuator/info` - Service information

### 5.4 Service Instances

At runtime, the Entity Processor discovers these services through Eureka:

| Service Name | Description | Port |
|--------------|-------------|------|
| `security` | Authentication and authorization | - |
| `core` | Core data and connections | - |
| `message` | Messaging (email, SMS, calls) | - |
| `gateway` | API Gateway | 8080 |
| `config` | Configuration Server | 8888 |
| `eureka` | Service Discovery | 9999 |

---

## 6. Spring Cloud Config

### 6.1 Configuration Source

The Entity Processor fetches its configuration from the Spring Cloud Config Server:

```yaml
# application.yml
spring:
  config:
    import: configserver:http://${CLOUD_CONFIG_SERVER}:8888/
```

The Config Server (port 8888) provides:
- Database connection URLs and credentials
- Eureka server location
- Feature flags
- Service-specific properties

### 6.2 Profile-Specific Configuration

Configuration is loaded based on the active Spring profile:

| Profile | Usage |
|---------|-------|
| `default` | Local development |
| `dev` | Development environment |
| `staging` | Staging environment |
| `prod` | Production environment |

### 6.3 Configuration Properties

Key configuration properties consumed by the Entity Processor:

```yaml
# Database (R2DBC)
spring.r2dbc.url: r2dbc:mysql://{host}:{port}/entity_processor
spring.r2dbc.username: {username}
spring.r2dbc.password: {password}

# Eureka
eureka.client.serviceUrl.defaultZone: http://{eureka-host}:9999/eureka/

# JOOQ
spring.jooq.sql-dialect: MYSQL

# Flyway
spring.flyway.enabled: true
spring.flyway.locations: classpath:db/migration
```

---

## 7. KIRun Function Execution Engine

### 7.1 Overview

KIRun is a runtime function execution engine used across the Modlix platform. The Entity Processor exposes its entity operations as KIRun functions, allowing them to be discovered, introspected, and executed programmatically via API.

### 7.2 Architecture

```
ProcessorFunctionController (/api/entity/processor/functions/)
    |
    v
ProcessorFunctionService
    |-- Collects IRepositoryProvider beans
    |-- Creates ReactiveHybridRepository (merged function repos)
    |-- Executes functions via KIRun engine
    |
    v
IRepositoryProvider implementations
    |-- TicketService
    |-- TicketCallService
    |-- OwnerService
    |-- ProductService
    |-- StageService
    |-- CampaignService
    |-- (etc.)
    |
    v
Each provides:
    |-- getFunctionRepository(appCode, clientCode) -> ReactiveRepository<ReactiveFunction>
    |-- getSchemaRepository(staticRepo, appCode, clientCode) -> ReactiveRepository<Schema>
```

### 7.3 Function Registration

Each service registers its functions in a `@PostConstruct` method. The registration pattern uses `AbstractServiceFunction.createServiceFunction()`.

#### Example: TicketService Function Registration

```java
@PostConstruct
private void init() {
    // Common CRUD functions (Create, ReadByIdentity, UpdateByIdentity, Delete, etc.)
    this.functions.addAll(super.getCommonFunctions(NAMESPACE, Ticket.class, classSchema, gson));

    // Custom function: CreateRequest
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "CreateRequest",
        ClassSchema.ArgSpec.ofRef("ticketRequest", TicketRequest.class, classSchema),
        "created",
        Schema.ofRef("EntityProcessor.DTO.Ticket"),
        gson,
        self::createRequest));

    // Custom function: CreateForCampaign
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "CreateForCampaign",
        ClassSchema.ArgSpec.ofRef("campaignTicketRequest", CampaignTicketRequest.class, classSchema),
        "created",
        Schema.ofRef("EntityProcessor.DTO.Ticket"),
        gson,
        self::createForCampaign));

    // Custom function: UpdateStageStatus
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "UpdateStageStatus",
        EntityProcessorArgSpec.identity("ticketId"),
        ClassSchema.ArgSpec.ofRef("ticketStatusRequest", TicketStatusRequest.class, classSchema),
        "result",
        Schema.ofRef("EntityProcessor.DTO.Ticket"),
        gson,
        self::updateStageStatus));

    // Custom function: ReassignTicket
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "ReassignTicket",
        EntityProcessorArgSpec.identity("ticketId"),
        ClassSchema.ArgSpec.ofRef("ticketReassignRequest", TicketReassignRequest.class, classSchema),
        "result",
        Schema.ofRef("EntityProcessor.DTO.Ticket"),
        gson,
        self::reassignTicket));

    // Custom function: GetTicketProductComm
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "GetTicketProductComm",
        EntityProcessorArgSpec.identity("ticketId"),
        ClassSchema.ArgSpec.ofRef("connectionType", ConnectionType.class, classSchema),
        ClassSchema.ArgSpec.ofRef("connectionSubType", ConnectionSubType.class, classSchema),
        "result",
        Schema.ofRef("EntityProcessor.DTO.Product.ProductComm"),
        gson,
        self::getTicketProductComm));

    // Custom function: UpdateTag
    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,
        "UpdateTag",
        EntityProcessorArgSpec.identity("ticketId"),
        ClassSchema.ArgSpec.ofRef("ticketTagRequest", TicketTagRequest.class, classSchema),
        "result",
        Schema.ofRef("EntityProcessor.DTO.Ticket"),
        gson,
        self::updateTag));
}
```

### 7.4 Common CRUD Functions

Every service that extends `BaseUpdatableService` inherits these common functions via `getCommonFunctions()`:

| Function Name | Arguments | Description |
|---------------|-----------|-------------|
| `Create` | `entity` (DTO) | Create a new entity |
| `ReadByIdentity` | `identity` (ID or Code) | Read entity by ID or code |
| `ReadEagerByIdentity` | `identity`, `fields`, `queryParams` | Read with related entities |
| `UpdateByIdentity` | `identity`, `entity` | Update entity by ID or code |
| `DeleteIdentity` | `identity` | Delete entity by ID or code |
| `ReadPageFilter` | `pageable` | Paginated list |
| `ReadPageFilterQuery` | `query` (condition + pagination) | Filtered paginated list |
| `ReadPageFilterEager` | `pageable`, `condition`, `fields`, `queryParams` | Eager paginated list |
| `ReadPageFilterEagerQuery` | `query`, `queryParams` | Filtered eager paginated list |

### 7.5 Available Namespaces

| Namespace | Service | Custom Functions |
|-----------|---------|------------------|
| `EntityProcessor.Ticket` | TicketService | CreateRequest, CreateForCampaign, CreateForWebsite, UpdateStageStatus, ReassignTicket, UpdateTag, GetTicketProductComm + CRUD |
| `EntityProcessor.TicketCall` | TicketCallService | IncomingExotelCall |
| `EntityProcessor.TicketDuplicationRule` | TicketDuplicationRuleService | CRUD |
| `EntityProcessor.Owner` | OwnerService | CRUD + custom |
| `EntityProcessor.Product` | ProductService | CRUD + custom |
| `EntityProcessor.ProductTemplate` | ProductTemplateService | CRUD |
| `EntityProcessor.Stage` | StageService | CRUD + custom |
| `EntityProcessor.Campaign` | CampaignService | CRUD |
| `EntityProcessor.Partner` | PartnerService | CRUD + custom |
| `EntityProcessor.Note` | NoteService | CRUD + custom |
| `EntityProcessor.Task` | TaskService | CRUD + custom |
| `EntityProcessor.TaskType` | TaskTypeService | CRUD |
| `EntityProcessor.ProductComm` | ProductCommService | CRUD |

### 7.6 Function Discovery API

#### List Functions

```
GET /api/entity/processor/functions/repositoryFilter
    ?appCode=myApp
    &clientCode=myClient
    &filter=EntityProcessor.Ticket
```

Returns a list of fully qualified function names matching the filter.

#### Find Function Signature

```
GET /api/entity/processor/functions/repositoryFind
    ?appCode=myApp
    &clientCode=myClient
    &namespace=EntityProcessor.Ticket
    &name=CreateRequest
```

Returns the function's signature (parameters, output schema).

### 7.7 Function Execution API

#### POST Execution (with JSON body)

```
POST /api/entity/processor/functions/execute/EntityProcessor.Ticket/CreateRequest
    ?paramAppCode=myApp
    &paramClientCode=myClient

Body:
{
    "ticketRequest": {
        "phoneNumber": {"countryCode": 91, "number": "9876543210"},
        "email": {"address": "lead@example.com"},
        "source": "Facebook",
        "subSource": "Lead Ads",
        "name": "John Doe",
        "productId": {"id": 123}
    }
}
```

#### GET Execution (with query params)

```
GET /api/entity/processor/functions/execute/EntityProcessor.Ticket/ReadByIdentity
    ?paramAppCode=myApp
    &paramClientCode=myClient
    &identity=42
```

### 7.8 Schema Repository

The `ProcessorSchemaService` generates JSON schemas for all DTOs at startup using reflection via `ClassSchema`:

```java
@Service
public class ProcessorSchemaService implements ApplicationListener<ContextRefreshedEvent> {

    private final ClassSchema classSchema =
        ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    private void init() {
        // Scan all service beans in entity.processor.service package
        // Generate schemas from all service POJOs
        ServiceSchemaGenerator generator = new ServiceSchemaGenerator(classSchema);
        this.generatedSchemas = generator.generateSchemas(services);
    }
}
```

#### Schema Discovery

```
GET /api/entity/processor/schemas/repositoryFind
    ?appCode=myApp
    &clientCode=myClient
    &namespace=EntityProcessor.DTO
    &name=Ticket
```

### 7.9 EntityProcessorArgSpec

Custom argument specifications for entity processor functions:

```java
public final class EntityProcessorArgSpec {

    public static final String IDENTITY_SCHEMA_REF = "EntityProcessor.Model.Common.Identity";

    // Identity parameter (can be ID or Code)
    public static ClassSchema.ArgSpec<Identity> identity() {
        return identity("identity");
    }

    public static ClassSchema.ArgSpec<Identity> identity(String name) {
        return ClassSchema.ArgSpec.ofRef(name, IDENTITY_SCHEMA_REF, Identity.class);
    }

    // ULong parameter (for IDs)
    public static ClassSchema.ArgSpec<ULong> uLong(String name) {
        return ClassSchema.ArgSpec.custom(
            name,
            Schema.ofInteger("ULong"),
            (g, j) -> j == null || j.isJsonNull() ? null : ULong.valueOf(j.getAsString()));
    }

    // List<ULong> parameter
    public static ClassSchema.ArgSpec<List<ULong>> uLongList(String name) {
        return ClassSchema.ArgSpec.custom(name, Schema.ofArray(name, Schema.ofInteger("ULong")),
            (g, j) -> { /* conversion logic */ });
    }
}
```

### 7.10 Type Conversion in Function Execution

The `ProcessorFunctionService` handles automatic conversion of query parameters to KIRun function arguments:

```java
// ProcessorFunctionService.java
private static final Map<SchemaType, Function<String, Number>> CONVERTOR = Map.of(
    SchemaType.DOUBLE, Double::valueOf,
    SchemaType.FLOAT, Float::valueOf,
    SchemaType.LONG, Long::valueOf,
    SchemaType.INTEGER, Integer::valueOf);
```

For POST requests, JSON objects are converted to appropriate DTO types via Gson.

---

## 8. Walk-In Form Integration

### 8.1 Overview

Walk-in forms are publicly accessible forms that allow customers to submit lead information without authentication. They are associated with products and can be customized per product or per product template.

### 8.2 Form Hierarchy

```
ProductTemplate
    |-- ProductTemplateWalkInForm (template-level form definition)
    |       |-- Inherited by all products using this template
    |
    |-- Product
            |-- ProductWalkInForm (product-specific form override)
                    |-- Overrides template-level form if present
```

### 8.3 Controller Endpoints

All walk-in form endpoints are under `/api/entity/processor/open/forms/` (no auth required):

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/open/forms/{id}` | Get form definition |
| `GET` | `/open/forms/{id}/product` | Get form's associated product |
| `GET` | `/open/forms/{id}/ticket?phoneNumber=X` | Check existing ticket by phone |
| `GET` | `/open/forms/{id}/tickets?phoneNumber=X` | List tickets by phone |
| `GET` | `/open/forms/users?appCode=X&clientCode=Y` | Get assignable users |
| `POST` | `/open/forms/{id}` | Submit walk-in form (create ticket) |

### 8.4 Form Retrieval Flow

```
Client App          WalkInFormController       ProductWalkInFormService
  |                       |                            |
  |-- GET /open/forms/{id} -->                         |
  |   Headers: appCode,   |                            |
  |            clientCode  |                            |
  |                       |-- getWalkInFormResponse -->|
  |                       |                            |-- Lookup ProductWalkInForm
  |                       |                            |-- Build WalkInFormResponse
  |                       |                            |   (form definition, fields,
  |                       |                            |    validation, styling)
  |                       |<-- WalkInFormResponse -----|
  |<-- 200 OK ------------|                            |
  |   (form definition)   |                            |
```

### 8.5 Walk-In Ticket Creation Flow

When a walk-in form is submitted:

```java
POST /api/entity/processor/open/forms/{id}
Headers:
    appCode: myApp
    clientCode: myClient
Body:
{
    "phoneNumber": {"countryCode": 91, "number": "9876543210"},
    "email": {"address": "visitor@example.com"},
    "name": "Walk-in Visitor",
    "description": "Interested in product X",
    "subSource": "Office"
}
```

#### Processing Steps

```
1. Validate form exists and is active
2. Construct ProcessorAccess from form's app/client codes (no user)
3. Lookup product from form configuration
4. Check for existing ticket by phone number
5. If duplicate ticket exists:
   a. Return existing ticket info (with ProcessorResponse)
6. If new ticket:
   a. Create Ticket from WalkInFormTicketRequest
   b. Handle stage/status assignment (default stage from product template)
   c. Create Owner (or find existing by phone)
   d. Log WALK_IN activity
   e. Return ProcessorResponse with new ticket
```

### 8.6 Walk-In Form Models

#### WalkInFormTicketRequest

```java
public class WalkInFormTicketRequest {
    private PhoneNumber phoneNumber;
    private Email email;
    private String name;
    private String description;
    private String subSource;
    private Identity stageId;      // Optional: specific stage
    private Identity statusId;     // Optional: specific status
    private Identity assignedUserId; // Optional: specific user
}
```

#### WalkInFormResponse

```java
public class WalkInFormResponse {
    // Form definition with fields, validation rules, and styling
    // Returned to the client for rendering
}
```

### 8.7 Checking Existing Tickets

Before creating a new walk-in ticket, the system checks for duplicates:

```
GET /api/entity/processor/open/forms/{id}/ticket
    ?phoneNumber=91-9876543210
    &field=ownerId
    &field=productId
    &field=assignedUserId
Headers:
    appCode: myApp
    clientCode: myClient
```

This returns an eager-loaded ticket with related entities if one exists for the given phone number.

---

## 9. Campaign Integration

### 9.1 Overview

The Entity Processor integrates with external campaign platforms (Facebook, Google, LinkedIn, X) to automatically create tickets from campaign leads.

### 9.2 Campaign Platforms

```java
public enum CampaignPlatform implements EnumType {
    GOOGLE("GOOGLE"),
    FACEBOOK("FACEBOOK"),
    LINKEDIN("LINKEDIN"),
    X("X");
}
```

### 9.3 Campaign Ticket Flow

#### Sequence Diagram

```
Campaign Platform       Entity Processor         CampaignService      TicketService
     |                       |                        |                    |
     |-- POST /open/tickets/ |                        |                    |
     |   req/campaigns       |                        |                    |
     |   {appCode,           |                        |                    |
     |    clientCode,        |                        |                    |
     |    leadDetails,       |                        |                    |
     |    campaignDetails}   |                        |                    |
     |                       |                        |                    |
     |                       |-- createForCampaign -->|                    |
     |                       |                        |                    |
     |                       |   readByCampaignId --->|                    |
     |                       |<-- Campaign -----------|                    |
     |                       |                        |                    |
     |                       |   readById(productId) -|                    |
     |                       |<-- Product ------------|                    |
     |                       |                        |                    |
     |                       |-- checkDuplicate ------|                    |
     |                       |                        |                    |
     |                       |-- create(ticket) ------|------------------>|
     |                       |<-- Created Ticket -----|--------------------|
     |                       |                        |                    |
     |                       |-- acCreate (log) ------|                    |
     |                       |                        |                    |
     |<-- 200 OK ------------|                        |                    |
```

### 9.4 CampaignTicketRequest

```java
public class CampaignTicketRequest implements Serializable, INoteRequest {
    private String appCode;
    private String clientCode;
    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;
    private String comment;

    public static class LeadDetails implements Serializable {
        private Email email;
        private String fullName;
        private PhoneNumber phone;
        private String companyName;
        private Email workEmail;
        private PhoneNumber workPhoneNumber;
        private String jobTitle;
        private String lastName;
        private String firstName;
        private String country;
        private String state;
        private String city;
        private String platform;
        private String subSource;
        private String source;
        private Map<String, Object> customFields;
    }

    public static class CampaignDetails implements Serializable {
        private String adId;
        private String adName;
        private String campaignId;
        private String campaignName;
        private String adSetId;
        private String adSetName;
        private String keyword;
    }
}
```

### 9.5 Campaign Ticket Creation Logic

```java
// TicketService.createForCampaign()
public Mono<Ticket> createForCampaign(CampaignTicketRequest cTicketRequest) {

    ProcessorAccess access = ProcessorAccess.of(
        cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

    return FlatMapUtil.flatMapMono(
        // Step 1: Look up Campaign by external campaignId
        () -> this.campaignService
            .readByCampaignId(access, cTicketRequest.getCampaignDetails().getCampaignId())
            .switchIfEmpty(msgService.throwMessage(...)),

        // Step 2: Get associated Product
        campaign -> this.productService.readById(access, campaign.getProductId()),

        // Step 3: Create Ticket with campaign association
        (campaign, product) ->
            Mono.just(Ticket.of(cTicketRequest).setCampaignId(campaign.getId())),

        // Step 4: Check for duplicate ticket
        (campaign, product, ticket) -> this.checkDuplicate(
            access, campaign.getProductId(),
            cTicketRequest.getLeadDetails().getPhone(),
            cTicketRequest.getLeadDetails().getEmail(),
            cTicketRequest.getLeadDetails().getSource(),
            cTicketRequest.getLeadDetails().getSubSource()),

        // Step 5: Set product ID and create
        (campaign, product, ticket, isDuplicate) ->
            Mono.just(ticket.setProductId(product.getId())),

        // Step 6: Persist the ticket
        (campaign, product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket),

        // Step 7: Create note if comment provided
        (campaign, product, ticket, isDuplicate, pTicket, created) ->
            this.createNote(access, cTicketRequest, created),

        // Step 8: Log CREATE activity
        (campaign, product, ticket, isDuplicate, pTicket, created, noteCreated) ->
            this.activityService.acCreate(access, created, null).thenReturn(created));
}
```

### 9.6 Campaign Keyword Storage

When a campaign ticket is created, the keyword from `CampaignDetails` is stored in the ticket's `metaData`:

```java
if (campaignTicketRequest.getCampaignDetails() != null) {
    Map<String, Object> metaData = new HashMap<>();
    CampaignDetails cd = campaignTicketRequest.getCampaignDetails();
    if (!StringUtil.safeIsBlank(cd.getKeyword()))
        metaData.put("keyword", cd.getKeyword());
    if (!metaData.isEmpty()) ticket.setMetaData(metaData);
}
```

### 9.7 Subscription Verification (Facebook Webhook)

Campaign platforms like Facebook require webhook verification before sending leads:

```java
// TicketOpenController
@GetMapping(CAMPAIGN_REQ_PATH)
public Mono<ResponseEntity<String>> verifyCreateFromCampaign(
        @RequestParam(name = "hub.mode") String mode,
        @RequestParam(name = "hub.verify_token") String verifyToken,
        @RequestParam(name = "hub.challenge") String challenge) {

    return Mono.just(
        "subscribe".equals(mode) && TOKEN.equals(verifyToken)
            ? ResponseEntity.ok(challenge)
            : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
}
```

---

## 10. Website Integration

### 10.1 Overview

Website forms can send leads directly to the Entity Processor via the website endpoint. This is similar to campaign integration but without campaign tracking.

### 10.2 Website Ticket Flow

```
Website Form        Entity Processor         ProductService        TicketService
    |                     |                       |                     |
    |-- POST /open/       |                       |                     |
    |   tickets/req/      |                       |                     |
    |   website/{code}    |                       |                     |
    |   {appCode,         |                       |                     |
    |    clientCode,      |                       |                     |
    |    leadDetails}     |                       |                     |
    |                     |                       |                     |
    |                     |-- readByCode(code) -->|                     |
    |                     |<-- Product -----------|                     |
    |                     |                       |                     |
    |                     |-- createForWebsite -->|                     |
    |                     |                       |-- checkDuplicate -->|
    |                     |                       |-- create(ticket) -->|
    |                     |<-- Created Ticket ----|---------------------|
    |                     |                       |                     |
    |<-- 200 OK ---------|                       |                     |
```

### 10.3 Website-Specific Logic

```java
// TicketService.createForWebsite()
public Mono<Ticket> createForWebsite(CampaignTicketRequest cTicketRequest, String productCode) {

    // Reject if campaignDetails are present (website != campaign)
    if (cTicketRequest.getCampaignDetails() != null)
        return this.msgService.throwMessage(..., WEBSITE_ENTITY_DATA_INVALID);

    // Default source to "Website" if not specified
    if (cTicketRequest.getLeadDetails().getSource() == null)
        cTicketRequest.getLeadDetails().setSource("Website");

    ProcessorAccess access = ProcessorAccess.of(
        cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

    return FlatMapUtil.flatMapMono(
        // Resolve product by code from URL path
        () -> this.productService.readByCode(access, productCode),
        // Create ticket
        product -> Mono.just(Ticket.of(cTicketRequest)),
        // Check duplicate
        (product, ticket) -> this.checkDuplicate(...),
        // Set product ID
        (product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
        // Persist
        (product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket),
        // Create note
        (product, ticket, isDuplicate, pTicket, created) ->
            this.createNote(access, cTicketRequest, created),
        // Log activity
        (product, ticket, isDuplicate, pTicket, created, noteCreated) ->
            this.activityService.acCreate(access, created, null).thenReturn(created));
}
```

### 10.4 Website Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/open/tickets/req/website/{productCode}` | Submit website lead |
| `GET` | `/open/tickets/req/website/{productCode}?hub.mode=subscribe&...` | Webhook verification |

### 10.5 Website Subscription Verification

Same Facebook-style verification as campaigns:

```java
@GetMapping(WEBSITE_REQ_PATH)
public Mono<ResponseEntity<String>> verifyCreateFromWebsite(
        @RequestParam(name = "hub.mode") String mode,
        @RequestParam(name = "hub.verify_token") String verifyToken,
        @RequestParam(name = "hub.challenge") String challenge) {
    // Same verification logic as campaign endpoint
}
```

---

## 11. Database Integration (R2DBC + JOOQ)

### 11.1 R2DBC Configuration

The Entity Processor uses R2DBC (Reactive Relational Database Connectivity) for non-blocking database access with MySQL 8.0+.

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://${DB_HOST}:${DB_PORT}/entity_processor
    pool:
      max-size: 20
      initial-size: 5
```

### 11.2 JOOQ Code Generation

JOOQ generates type-safe table and record classes from the database schema. Generated classes reside in the `jooq` package.

**IMPORTANT**: Do not manually edit files in the `jooq` package. They are auto-generated.

#### Generated Tables

| JOOQ Table | Entity |
|------------|--------|
| `ENTITY_PROCESSOR_TICKETS` | Ticket |
| `ENTITY_PROCESSOR_OWNERS` | Owner |
| `ENTITY_PROCESSOR_PRODUCTS` | Product |
| `ENTITY_PROCESSOR_PRODUCT_TEMPLATES` | ProductTemplate |
| `ENTITY_PROCESSOR_PRODUCT_COMMS` | ProductComm |
| `ENTITY_PROCESSOR_STAGES` | Stage |
| `ENTITY_PROCESSOR_ACTIVITIES` | Activity |
| `ENTITY_PROCESSOR_NOTES` | Note |
| `ENTITY_PROCESSOR_TASKS` | Task |
| `ENTITY_PROCESSOR_TASK_TYPES` | TaskType |
| `ENTITY_PROCESSOR_CAMPAIGNS` | Campaign |
| `ENTITY_PROCESSOR_PARTNERS` | Partner |
| `ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS` | TicketCUserDistribution |
| `ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS` | TicketRuUserDistribution |
| `ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES` | ProductTicketCRule |
| `ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES` | ProductTicketRuRule |
| `ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES` | TicketDuplicationRule |
| `ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES` | TicketPeDuplicationRule |
| `ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS` | ProductWalkInForm |
| `ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS` | ProductTemplateWalkInForm |

### 11.3 ID Type Convention

All database IDs use `BIGINT UNSIGNED` which maps to `org.jooq.types.ULong` in Java. Never use `Long` or `Integer` for entity IDs.

```java
// Correct
ULong ticketId = ULong.valueOf(42);

// Converting from BigInteger
ULong id = ULongUtil.valueOf(bigIntValue);
```

### 11.4 Timestamp Convention

All timestamps use `java.time.LocalDateTime`. The database stores times in UTC; timezone conversion is handled at the query level for analytics.

### 11.5 Multi-Tenant Query Filtering

The `BaseProcessorDAO` automatically applies tenant filtering using `processorAccessCondition()`:

```java
// BaseProcessorDAO or BaseUpdatableDAO
public Mono<AbstractCondition> processorAccessCondition(
        AbstractCondition condition, ProcessorAccess access) {
    // Adds appCode and clientCode filters
    // Adds user hierarchy filters (subOrg, managingClients)
    // Applies outside user (BP) filtering
}
```

### 11.6 Flyway Migrations

Database schema changes are managed via Flyway:

```
src/main/resources/db/migration/
    V1__initial_schema.sql
    V2__add_campaigns.sql
    V3__add_walk_in_forms.sql
    ...
```

### 11.7 Query Patterns

#### Type-Safe Queries with JOOQ

```java
// In TicketDAO
public Mono<Ticket> readTicketByNumberAndEmail(
        AbstractCondition condition,
        ProcessorAccess access,
        ULong productId,
        PhoneNumber ticketPhone,
        Email ticketMail) {
    // Builds JOOQ conditions from AbstractCondition
    // Applies tenant filtering
    // Returns reactive Mono<Ticket>
}
```

#### AbstractCondition to JOOQ Conditions

```java
// ComplexCondition with AND operator
ComplexCondition.and(
    FilterCondition.make("appCode", "myApp")
        .setOperator(FilterConditionOperator.EQUALS),
    FilterCondition.make("clientCode", "myClient")
        .setOperator(FilterConditionOperator.EQUALS),
    FilterCondition.make("source", List.of("Facebook", "Google"))
        .setOperator(FilterConditionOperator.IN)
);
```

---

## 12. Gson Serialization Integration

### 12.1 Configuration

The Entity Processor uses a custom Gson instance configured in `ProcessorConfiguration`:

```java
@Override
public Gson makeGson() {
    Gson baseGson = super.makeGson();

    ArraySchemaType.ArraySchemaTypeAdapter arraySchemaTypeAdapter =
        new ArraySchemaType.ArraySchemaTypeAdapter();
    AdditionalType.AdditionalTypeAdapter additionalTypeAdapter =
        new AdditionalType.AdditionalTypeAdapter();

    Gson gson = baseGson.newBuilder()
        .registerTypeAdapter(Identity.class, new IdentityTypeAdapter())
        .registerTypeAdapter(Email.class, new EmailTypeAdapter())
        .registerTypeAdapter(PhoneNumber.class, new PhoneNumberTypeAdapter())
        .registerTypeAdapter(Pageable.class, new PageableTypeAdapter())
        .registerTypeAdapter(Sort.class, new SortTypeAdapter())
        .registerTypeAdapterFactory(new AbstractConditionTypeAdapter.Factory())
        .registerTypeAdapterFactory(new PageTypeAdapter.Factory())
        .registerTypeAdapter(Type.class, new Type.SchemaTypeAdapter())
        .registerTypeAdapter(AdditionalType.class, additionalTypeAdapter)
        .registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)
        .create();

    arraySchemaTypeAdapter.setGson(gson);
    additionalTypeAdapter.setGson(gson);

    return gson;
}
```

### 12.2 Custom Type Adapters

#### IdentityTypeAdapter

Handles the `Identity` type which can be either an ID (numeric) or a Code (string):

```json
// ID variant
{"id": 42}
// or just the number
42

// Code variant
{"code": "TICKET-001"}
// or just the string
"TICKET-001"
```

#### PhoneNumberTypeAdapter

Serializes/deserializes `PhoneNumber` with country code:

```json
{
    "countryCode": 91,
    "number": "9876543210"
}
```

#### EmailTypeAdapter

Serializes/deserializes `Email`:

```json
{
    "address": "user@example.com"
}
```

#### AbstractConditionTypeAdapter

Handles the polymorphic `AbstractCondition` hierarchy:
- `FilterCondition` - Single field condition
- `ComplexCondition` - AND/OR group of conditions

```json
{
    "conditions": [
        {"field": "source", "value": "Facebook", "operator": "EQUALS"},
        {"field": "tag", "value": "HOT", "operator": "EQUALS"}
    ],
    "operator": "AND"
}
```

#### PageableTypeAdapter

Handles Spring Data `Pageable` for KIRun function execution:

```json
{
    "page": 0,
    "size": 10,
    "sort": "createdAt,desc"
}
```

#### SortTypeAdapter

Handles Spring Data `Sort`:

```json
{
    "orders": [
        {"property": "createdAt", "direction": "DESC"}
    ]
}
```

#### PageTypeAdapter

Wraps paginated results:

```json
{
    "content": [...],
    "totalElements": 100,
    "totalPages": 10,
    "number": 0,
    "size": 10
}
```

### 12.3 Usage in KIRun Functions

The custom Gson instance is injected into all services and used by `AbstractServiceFunction` for argument deserialization and result serialization during KIRun function execution.

---

## 13. Analytics Integration

### 13.1 Architecture

```
TicketBucketController
    |
    v
TicketBucketService
    |
    v
TicketBucketDAO (extends BaseAnalyticsDAO)
    |-- Builds JOOQ aggregation queries
    |-- Joins with activities and stages tables
    |-- Returns PerValueCount, PerDateCount models
```

### 13.2 TicketBucketDAO

The `TicketBucketDAO` provides aggregation queries for ticket analytics. It extends `BaseAnalyticsDAO` and operates on the `ENTITY_PROCESSOR_TICKETS` table.

#### Available Aggregation Methods

| Method | Group By | Count By | Description |
|--------|----------|----------|-------------|
| `getTicketPerAssignedUserStageCount` | assignedUserId | stage | Tickets per user per stage |
| `getTicketPerCreatedByStageCount` | createdBy | stage | Tickets per creator per stage |
| `getTicketPerClientIdStageCount` | clientId | stage | Tickets per client per stage |
| `getTicketPerProjectStageCount` | productId | stage | Tickets per product per stage |
| `getTicketPerAssignedUserStatusCount` | assignedUserId | status | Tickets per user per status |
| `getTicketPerCreatedByStatusCount` | createdBy | status | Tickets per creator per status |
| `getTicketPerClientIdStatusCount` | clientId | status | Tickets per client per status |
| `getTicketPerProjectStatusCount` | productId | status | Tickets per product per status |
| `getTicketCountPerStageAndDateWithClientId` | date | stage | Time-series by stage |
| `getTicketPerAssignedUserStageSourceDateCount` | source | stage+date | Time-series by source and stage |
| `getUniqueCreatedByCountPerStageAndDateWithClientId` | date | unique clients per stage | Unique client time-series |
| `getTicketCountPerProductStageAndClientId` | productId | clientId | Product-client cross-tab |
| `getTicketCountPerClientIdAndDate` | clientId | date | Client time-series |

### 13.3 TicketBucketFilter

The filter model for analytics queries:

```java
public class TicketBucketFilter extends BaseFilter {
    // Inherited from BaseFilter:
    private List<ULong> createdByIds;
    private List<ULong> assignedUserIds;
    private List<ULong> clientIds;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // Ticket-specific:
    private List<String> sources;
    private List<String> subSources;
    private List<ULong> stageIds;
    private List<ULong> statusIds;
    private List<ULong> productIds;
    private boolean includeTotal;
    private TimePeriod timePeriod;
    private String timezone;
}
```

### 13.4 TimePeriod Enum

The `TimePeriod` enum defines time bucketing granularity for analytics:

```java
public enum TimePeriod implements TemporalUnit {
    DAYS("Days", 7, ...),
    WEEKS("Weeks", 8, ...),
    MONTHS("Months", 9, ...),
    QUARTERS("Quarters", 10, ...),
    YEARS("Years", 11, ...);
}
```

### 13.5 Activity-Based Stage Analytics

For stage-based analytics, the DAO joins with the `ENTITY_PROCESSOR_ACTIVITIES` table to track when tickets entered each stage, rather than using only the current stage:

```java
private Condition buildActivityCondition(
        LocalDateTime startDate, LocalDateTime endDate, List<ULong> stageIds) {
    Condition condition = ENTITY_PROCESSOR_ACTIVITIES
        .ACTIVITY_ACTION.eq(ActivityAction.STAGE_UPDATE)
        .and(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.isNotNull());

    condition = this.applyDateRangeCondition(
        condition, ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE, startDate, endDate);

    if (stageIds != null && !stageIds.isEmpty()) {
        condition = condition.and(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.in(stageIds));
    }
    return condition;
}
```

### 13.6 Timezone Handling in Analytics

Analytics queries support timezone conversion using MySQL's `CONVERT_TZ()`:

```java
Field<LocalDateTime> effectiveDateField = StringUtil.safeIsBlank(timezone) || "UTC".equalsIgnoreCase(timezone)
    ? dateTimeField
    : DSL.field(
        "convert_tz({0}, 'UTC', {1})", SQLDataType.LOCALDATETIME,
        dateTimeField, DSL.inline(timezone));
```

### 13.7 Analytics Response Models

#### PerValueCount

```java
public class PerValueCount {
    private ULong groupedId;       // ID of grouped entity
    private String groupedValue;   // String value for grouping
    private String mapValue;       // Stage/status name
    private Long count;            // Count
}
```

#### PerDateCount

```java
public class PerDateCount {
    private LocalDateTime date;     // Time bucket date
    private String groupedValue;    // Grouped entity value
    private String mapValue;        // Stage/status name
    private Long count;             // Count
}
```

### 13.8 ReactivePaginationUtil

Custom utility for paginating reactive analytics results that cannot use standard Spring Data pagination.

---

## 14. Eager Loading Integration

### 14.1 Overview

The eager loading system resolves related entities when fetching data, similar to SQL JOINs but with support for cross-service resolution (e.g., resolving user names from the Security service).

### 14.2 Relationship Declaration

Entities declare their relationships in their constructors via `relationsMap` and `relationsResolverMap`:

```java
// Ticket.java constructor
public Ticket() {
    super();
    // Table-based relations (resolved via LEFT JOIN)
    this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
    this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
    this.relationsMap.put(Fields.stage, EntitySeries.STAGE.getTable());
    this.relationsMap.put(Fields.status, EntitySeries.STAGE.getTable());
    this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
    this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());

    // Cross-service resolver (calls Security service)
    this.relationsResolverMap.put(UserFieldResolver.class, Fields.assignedUserId);
}
```

### 14.3 EagerUtil

The `EagerUtil` class provides static utilities for eager loading:

```java
public class EagerUtil {

    public static final String EAGER = "eager";
    public static final String EAGER_FIELD = "eagerField";

    // Convert Java field name to JOOQ column name
    public static String toJooqField(String fieldName) {
        return fieldName.replaceAll("[A-Z0-9]", "_$0").toUpperCase();
        // "assignedUserId" -> "ASSIGNED_USER_ID"
    }

    // Convert JOOQ column name to Java field name
    public static String fromJooqField(String jooqFieldName) {
        // "ASSIGNED_USER_ID" -> "assignedUserId"
    }

    // Extract field parameters from query params
    public static List<String> getFieldParams(Map<String, List<String>> multiValueMap) {
        return multiValueMap.containsKey("field") ? multiValueMap.get("field") : List.of();
    }

    // Build relation map from DTO class
    public static <T extends IRelationMap> Map<String, Tuple2<Table<?>, String>> getRelationMap(Class<T> clazz) {
        // Cached reflection-based relation map construction
    }
}
```

### 14.4 Query Parameter Usage

To request eager loading:

```
GET /api/entity/processor/tickets/req/{id}
    ?field=ownerId
    &field=productId
    &field=stage
    &field=assignedUserId
    &eager=true
    &eagerField=ownerId.phoneNumber
```

Each `field` parameter triggers either:
1. A LEFT JOIN (for table-based relations like `ownerId` -> `ENTITY_PROCESSOR_OWNERS`)
2. A resolver call (for cross-service relations like `assignedUserId` -> Security service)

### 14.5 Available Field Resolvers

| Resolver | Field | Source |
|----------|-------|--------|
| `UserFieldResolver` | `assignedUserId`, `createdBy` | Security service (user lookup) |
| `ClientFieldResolver` | `clientId` | Security service (client lookup) |
| `DepartmentFieldResolver` | Department fields | Security service |
| `DesignationFieldResolver` | Designation fields | Security service |
| `ProfileFieldResolver` | Profile fields | Security service |

### 14.6 Eager Response Format

Eager loading returns `Map<String, Object>` instead of the DTO, with nested objects for related entities:

```json
{
    "id": 42,
    "name": "John Doe Lead",
    "ownerId": 10,
    "owner": {
        "id": 10,
        "name": "John Doe",
        "phoneNumber": "9876543210"
    },
    "productId": 5,
    "product": {
        "id": 5,
        "name": "Premium Product"
    },
    "stage": 3,
    "stageDetails": {
        "id": 3,
        "name": "Qualified"
    },
    "assignedUserId": 100,
    "assignedUser": {
        "id": 100,
        "firstName": "Sales",
        "lastName": "Agent"
    }
}
```

---

## 15. Source and SubSource Normalization

### 15.1 NameUtil

The `NameUtil` class provides string normalization for consistent data storage:

```java
// NameUtil.normalize() standardizes source strings
// Applied automatically when setting source/subSource on entities

// In Ticket.java:
public Ticket setSource(String source) {
    if (StringUtil.safeIsBlank(source)) return this;
    this.source = NameUtil.normalize(source);
    return this;
}

public Ticket setSubSource(String subSource) {
    if (StringUtil.safeIsBlank(subSource)) return this;
    this.subSource = NameUtil.normalize(subSource);
    return this;
}
```

#### assembleFullName

```java
// NameUtil.assembleFullName() combines first/middle/last name
// Used for ProcessorAccess.getUserName() and Activity logging
String fullName = NameUtil.assembleFullName(firstName, middleName, lastName);
```

### 15.2 PhoneUtil

```java
public class PhoneUtil {
    // Returns 91 (India) as default calling code
    public static Integer getDefaultCallingCode() {
        return 91;
    }
    // Phone number validation using libphonenumber
}
```

### 15.3 EmailUtil

Email validation and formatting utilities.

### 15.4 Default Source Values

When source information is not provided:

```java
// SourceUtil constants for default values
public static final String DEFAULT_CALL_SOURCE = "Call";      // For Exotel calls
public static final String DEFAULT_CALL_SUB_SOURCE = "Inbound"; // For inbound calls
```

Website tickets default to:
```java
if (cTicketRequest.getLeadDetails().getSource() == null)
    cTicketRequest.getLeadDetails().setSource("Website");
```

---

## 16. Activity Logging System

### 16.1 Overview

The `ActivityService` provides comprehensive activity logging for all CRM operations. Every significant action on a ticket, task, note, or other entity is recorded as an `Activity` entry.

### 16.2 ActivityAction Enum

The `ActivityAction` enum defines all trackable actions with message templates:

#### Ticket Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `CREATE` | `$entity from $source created for $user.` | entity, source |
| `RE_INQUIRY` | `$entity re-inquired from $source by $user.` | entity, source |
| `QUALIFY` | `$entity qualified by $user.` | entity |
| `DISQUALIFY` | `$entity marked as disqualified by $user.` | entity |
| `DISCARD` | `$entity discarded by $user.` | entity |
| `IMPORT` | `$entity imported via $source by $user.` | entity, source |
| `WALK_IN` | `$entity walked in by $user.` | entity |
| `DCRM_IMPORT` | `$entity imported via DCRM by $user.` | entity |

#### Stage and Status Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `STATUS_CREATE` | `$status created by $user.` | status |
| `STAGE_UPDATE` | `Stage moved from $_stage to $stage by $user.` | _stage (old), stage (new) |
| `TAG_CREATE` | `$entity was tagged $tag by $user.` | tag |
| `TAG_UPDATE` | `$entity was tagged $tag from $_tag by $user.` | tag (new), _tag (old) |

#### Assignment Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `ASSIGN` | `$entity was assigned to $assignedUserId by $user.` | entity, assignedUserId |
| `REASSIGN` | `$entity was reassigned from $_assignedUserId to $assignedUserId by $user.` | old, new userId |
| `REASSIGN_SYSTEM` | `$entity reassigned from $_assignedUserId to $assignedUserId due to availability rule by $user.` | old, new userId |
| `OWNERSHIP_TRANSFER` | `Ownership transferred from $_createdBy to $createdBy by $user.` | old, new createdBy |

#### Task Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `TASK_CREATE` | `Task $taskId was created by $user.` | taskId |
| `TASK_UPDATE` | `Task $taskId was updated by $user.` | taskId |
| `TASK_COMPLETE` | `Task $taskId was marked as completed by $user.` | taskId |
| `TASK_CANCELLED` | `Task $taskId was marked as cancelled by $user.` | taskId |
| `TASK_DELETE` | `Task $taskId was deleted by $user.` | taskId |
| `REMINDER_SET` | `Reminder for date $nextReminder, set for $taskId by $user.` | nextReminder, taskId |

#### Communication Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `CALL_LOG` | `Call with $customer logged by $user.` | customer |
| `WHATSAPP` | `WhatsApp message sent to $customer by $user.` | customer |
| `EMAIL_SENT` | `Email sent to $email by $user.` | email |
| `SMS_SENT` | `SMS sent to $customer by $user.` | customer |

#### Document and Note Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `DOCUMENT_UPLOAD` | `Document $file uploaded by $user.` | file |
| `DOCUMENT_DOWNLOAD` | `Document $file downloaded by $user.` | file |
| `DOCUMENT_DELETE` | `Document $file deleted by $user.` | file |
| `NOTE_ADD` | `Note $noteId added by $user.` | noteId |
| `NOTE_UPDATE` | `Note $noteId was updated by $user.` | noteId |
| `NOTE_DELETE` | `Note $noteId deleted by $user.` | noteId |

#### Field Update Actions

| Action | Template | Context Keys |
|--------|----------|--------------|
| `FIELD_UPDATE` | `$fields by $user.` | fields |
| `CUSTOM_FIELD_UPDATE` | `Custom field $field updated to $value by $user.` | field, value |
| `LOCATION_UPDATE` | `Location updated to $location by $user.` | location |

### 16.3 Message Formatting

Activity messages use markdown formatting for rich display:

```java
// ActivityAction.formatMessage()
private String formatMarkdown(String key, String value) {
    if (key.contains("id")) return "`" + value + "`";      // Code format for IDs
    if (key.equals("user")) return "*" + "**" + value + "**" + "*"; // Bold italic for user
    return "**" + value + "**";                               // Bold for others
}
```

### 16.4 Activity Logging in TicketService

Activities are logged at every significant operation:

```java
// After ticket creation
this.activityService.acCreate(created).thenReturn(created);

// After stage update
this.activityService.acStageStatus(access, uTicket, comment, oldStage).thenReturn(uTicket);

// After reassignment
this.activityService.acReassign(access, uTicket.getId(), comment, oldUserId, newUserId, isAutomatic);

// After tag update
this.activityService.acTagChange(access, uTicket, comment, oldTagEnum);

// After call
this.activityService.acCallLog(access, ticket, null);

// After re-inquiry (duplicate detected)
this.activityService.acReInquiry(access, ticket, null, source, subSource);

// After DCRM import
this.activityService.acDcrmImport(access, created, null, request.getActivityJson());
```

---

## 17. Business Partner Integration

### 17.1 Overview

Business Partners (BPs) are a special client type that can create and manage tickets on behalf of another client. The Entity Processor has specific handling for BP users throughout its service layer.

### 17.2 BusinessPartnerConstant

```java
@UtilityClass
public class BusinessPartnerConstant {
    public static final String BP_MANAGER_ROLE = "Authorities.ROLE_Partner_Manager";
    public static final String OWNER_ROLE = "Authorities.ROLE_Owner";
    public static final String CLIENT_LEVEL_TYPE_BP = "CUSTOMER";

    public static boolean isBpManager(Collection<? extends GrantedAuthority> collection) {
        return SecurityContextUtil.hasAuthority(BP_MANAGER_ROLE, collection)
            || SecurityContextUtil.hasAuthority(OWNER_ROLE, collection);
    }
}
```

### 17.3 BP User Detection Flow

```java
// During ProcessorAccess construction
ProcessorAccess access = new ProcessorAccess()
    .setHasBpAccess(BusinessPartnerConstant.isBpManager(user.getAuthorities()));

// Outside user check
public boolean isOutsideUser() {
    if (userInherit != null)
        return BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(this.userInherit.clientLevelType);
    return false;
}
```

### 17.4 BP Entity Creation

When a BP user creates an entity:
1. The entity's `clientCode` is set to the **managed** client's code (not the BP's)
2. The entity's `clientId` is set to the BP's client ID (for tracking)
3. The `canOutsideCreate()` method controls whether BP users can create the entity type

```java
// BaseUpdatableService.createInternal()
entity.setClientCode(
    access.isOutsideUser() ? access.getUserInherit().getManagedClientCode() : access.getClientCode());

// BaseProcessorService.create()
if (access.isOutsideUser())
    entity.setClientId(ULongUtil.valueOf(access.getUser().getClientId()));
```

### 17.5 DNC (Do Not Call) for Partners

BP users can mark leads as DNC:

```java
private Mono<Boolean> getDnc(ProcessorAccess access, TicketRequest ticketRequest) {
    if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);
    return ticketRequest.getDnc() != null
        ? Mono.just(ticketRequest.getDnc())
        : this.partnerService.getPartnerDnc(access);
}
```

### 17.6 Duplicate Handling for BP Users

When a duplicate ticket is detected, the error message differs for BP users:

```java
protected <T> Mono<T> throwDuplicateError(ProcessorAccess access, D existing) {
    if (access.isOutsideUser())
        return this.msgService.throwMessage(...,
            ProcessorMessageResourceService.DUPLICATE_ENTITY_OUTSIDE_USER, ...);

    return this.msgService.throwMessage(...,
        ProcessorMessageResourceService.DUPLICATE_ENTITY, ...);
}
```

---

## 18. Caching Integration

### 18.1 Overview

The Entity Processor uses `CacheService` for caching entity reads. Caching is implemented at the service layer with automatic eviction on updates and deletes.

### 18.2 Cache Key Strategy

Each entity type has a named cache (returned by `getCacheName()`). Cache keys are constructed as composite keys:

```java
// Simple cache key (by ID or code)
cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readInternal(id), id);

// Scoped cache key (by appCode + clientCode + ID/code)
cacheService.cacheValueOrGet(
    this.getCacheName(),
    () -> this.dao.readInternal(access, id),
    this.getCacheKey(access.getAppCode(), access.getClientCode(), id));
```

### 18.3 Cache Eviction

Eviction occurs automatically after updates and deletes:

```java
// BaseUpdatableService.updateInternal()
public Mono<D> updateInternal(ProcessorAccess access, D entity) {
    return super.update(entity)
        .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
}

// Eviction covers both simple and scoped keys
protected Mono<Boolean> evictCache(D entity) {
    return Mono.zip(
        this.evictBaseCache(entity),    // Evict by ID and code
        this.evictAcCcCache(entity),    // Evict by appCode+clientCode+ID/code
        (baseEvicted, acCcEvicted) -> baseEvicted && acCcEvicted);
}
```

### 18.4 Cache Names

| Service | Cache Name |
|---------|------------|
| TicketService | `ticket` |
| OwnerService | `owner` |
| ProductService | `product` |
| StageService | `stage` |
| CampaignService | `campaign` |
| (other services) | (entity-specific names) |

---

## 19. Rule Engine Integration

### 19.1 Overview

The Entity Processor includes a rule engine for ticket creation and user assignment. Rules control how tickets are assigned to users and how duplicates are detected.

### 19.2 Rule Types

#### Ticket Creation Rules (ProductTicketCRule)

Control user assignment during ticket creation:
- Which user to assign a new ticket to
- Based on product, stage, and distribution rules

#### Ticket Read/Update Rules (ProductTicketRuRule)

Control user visibility and update permissions for tickets.

#### Ticket Duplication Rules (TicketDuplicationRule)

Define conditions under which a ticket is considered a duplicate:
- By phone number
- By email
- By source/subsource
- By stage (configurable)

#### Ticket PE Duplication Rules (TicketPeDuplicationRule)

Extended duplication rules with additional conditions.

### 19.3 User Distribution

User distribution rules determine how tickets are assigned among available users:

```java
public enum DistributionType {
    // Distribution strategies for assigning tickets to users
}
```

#### TicketCUserDistribution

Controls ticket creation user distribution.

#### TicketRuUserDistribution

Controls ticket read/update user distribution.

### 19.4 Rule Execution Flow

```
New Ticket
    |
    v
TicketService.checkEntity()
    |
    v
setAssignmentAndStage()
    |-- setDefaultStage() -- Get first stage from product template
    |
    v
productTicketCRuleService.getUserAssignment()
    |-- Find applicable creation rule
    |-- Apply distribution strategy
    |-- Return assigned userId
    |
    v
setTicketAssignment()
    |-- Set assignedUserId on ticket
```

### 19.5 Stage-Based Reassignment

When a ticket's stage changes, the rule engine can trigger automatic reassignment:

```java
// TicketService.updateTicketStage()
(uTicket, cTask, fTicket) -> doReassignment
    ? this.reassignForStage(access, fTicket, reassignUserId, true)
    : Mono.just(fTicket)
```

The `reassignForStage()` method queries the rule engine for the appropriate user:

```java
public Mono<Ticket> reassignForStage(ProcessorAccess access, Ticket ticket, ULong userId, boolean isAutomatic) {
    if (userId != null)
        return this.updateTicketForReassignment(access, ticket, userId, AUTOMATIC_REASSIGNMENT, isAutomatic);

    return FlatMapUtil.flatMapMono(
        () -> this.productTicketCRuleService.getUserAssignment(
            access, ticket.getProductId(), ticket.getStage(),
            this.getEntityPrefix(access.getAppCode()),
            access.getUserId(), ticket, false),
        ruleUserId -> ruleUserId == null
            ? Mono.just(ticket)
            : this.updateTicketForReassignment(
                access, ticket, ruleUserId, AUTOMATIC_REASSIGNMENT, isAutomatic))
    .switchIfEmpty(Mono.just(ticket));
}
```

### 19.6 Duplicate Detection

The duplication rule system checks for existing tickets before creating new ones:

```java
// TicketService.checkDuplicate()
private Mono<Boolean> checkDuplicate(
        ProcessorAccess access, ULong productId,
        PhoneNumber ticketPhone, Email ticketMail,
        String source, String subSource) {

    return this.ticketDuplicationRuleService
        .getDuplicateRuleCondition(access, productId, source, subSource)
        .flatMap(ruleCondition -> this.handleDuplicateCheck(
            access, productId, ticketPhone, ticketMail, ruleCondition, source, subSource))
        .switchIfEmpty(
            this.handleDuplicateCheck(
                access, productId, ticketPhone, ticketMail, null, source, subSource));
}
```

When a duplicate is found, a RE_INQUIRY activity is logged and an error is thrown.

---

## Appendix: Entity Catalog

### A.1 Entity Series

The `EntitySeries` enum defines all entities in the system with their metadata:

| Entity Series | Display Name | Prefix | Table |
|---------------|-------------|--------|-------|
| `TICKET` | Ticket | Ticket | `ENTITY_PROCESSOR_TICKETS` |
| `OWNER` | Owner | Owner | `ENTITY_PROCESSOR_OWNERS` |
| `PRODUCT` | Product | Product | `ENTITY_PROCESSOR_PRODUCTS` |
| `PRODUCT_TEMPLATE` | Product Template | ProductTemplate | `ENTITY_PROCESSOR_PRODUCT_TEMPLATES` |
| `PRODUCT_COMM` | Product Communications | ProductComm | `ENTITY_PROCESSOR_PRODUCT_COMMS` |
| `STAGE` | Stage | Stage | `ENTITY_PROCESSOR_STAGES` |
| `ACTIVITY` | Activity | Activity | `ENTITY_PROCESSOR_ACTIVITIES` |
| `NOTE` | Note | Note | `ENTITY_PROCESSOR_NOTES` |
| `TASK` | Task | Task | `ENTITY_PROCESSOR_TASKS` |
| `TASK_TYPE` | Task Type | TaskType | `ENTITY_PROCESSOR_TASK_TYPES` |
| `CAMPAIGN` | Campaign | Campaign | `ENTITY_PROCESSOR_CAMPAIGNS` |
| `PARTNER` | Partner | Partner | `ENTITY_PROCESSOR_PARTNERS` |
| `TICKET_C_USER_DISTRIBUTION` | Ticket Creation User Distribution | TicketCUserDistribution | `ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS` |
| `TICKET_RU_USER_DISTRIBUTION` | Ticket Read Update User Distribution | TicketRuUserDistribution | `ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS` |
| `PRODUCT_TICKET_C_RULE` | Product Ticket Creation Rule | ProductTicketCRule | `ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES` |
| `PRODUCT_TICKET_RU_RULE` | Product Ticket Read Update Rule | ProductTicketRuRule | `ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES` |
| `TICKET_DUPLICATION_RULES` | Ticket Duplication Rules | TicketDuplicationRule | `ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES` |
| `TICKET_PE_DUPLICATION_RULES` | Ticket Pe Duplication Rules | TicketPeDuplicationRule | `ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES` |
| `PRODUCT_TEMPLATE_WALK_IN_FORMS` | Product Template Walk In Forms | ProductTemplateWalkInForm | `ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS` |
| `PRODUCT_WALK_IN_FORMS` | Product Walk In Forms | ProductWalkInForms | `ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS` |

### A.2 Leadzump Entity Mapping

For the "leadzump" application, entity names are mapped differently:

| Entity Series | Leadzump Name |
|---------------|--------------|
| `TICKET` | Deal |
| `OWNER` | Lead |
| `PRODUCT` | Project |
| `PRODUCT_COMM` | ProjectComm |
| `PRODUCT_TEMPLATE` | ProjectTemplate |

### A.3 Tag Enum

```java
public enum Tag implements EnumType {
    HOT("HOT", "Hot"),
    WARM("WARM", "Warm"),
    COLD("COLD", "Cold");
}
```

### A.4 Ticket DTO Fields

The `Ticket` DTO contains the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `ownerId` | `ULong` | Reference to the Owner entity |
| `assignedUserId` | `ULong` | User assigned to handle this ticket |
| `dialCode` | `Integer` | Phone country code (default: 91) |
| `phoneNumber` | `String` | Phone number |
| `email` | `String` | Email address |
| `productId` | `ULong` | Reference to the Product entity |
| `stage` | `ULong` | Reference to the current Stage |
| `status` | `ULong` | Reference to the current Status |
| `source` | `String` | Lead source (normalized) |
| `subSource` | `String` | Lead sub-source (normalized) |
| `campaignId` | `ULong` | Reference to the Campaign (if applicable) |
| `dnc` | `Boolean` | Do Not Call flag |
| `tag` | `Tag` | HOT, WARM, or COLD |
| `metaData` | `Map<String, Object>` | Arbitrary metadata (e.g., campaign keyword) |
| `productTemplateId` | `ULong` | Product template reference (computed) |
| `latestTaskDueDate` | `LocalDateTime` | Latest task due date (computed) |

### A.5 Controller Hierarchy

```
BaseController<R, D, O, S>
    |
    |-- BaseUpdatableController<R, D, O, S extends BaseUpdatableService>
    |       |-- CRUD endpoints: create, read, update, delete
    |       |-- Eager read endpoints
    |       |-- Paginated filter endpoints
    |       |-- Identity-based operations (ID or Code)
    |
    |-- BaseProcessorController<R, D, O, S extends BaseProcessorService>
    |       |-- Inherits BaseUpdatableController
    |       |-- Timezone-aware pagination
    |       |-- Sub-query condition support
    |
    |-- BaseValueController<R, D, O, S extends BaseValueService>
    |       |-- For value-type entities
    |
    |-- BaseContentController<R, D, O, S extends BaseContentService>
            |-- For content entities (Notes, Tasks)
            |-- Linked to parent entity (Ticket or Owner)

Open Controllers (no inheritance from base):
    |-- TicketOpenController (/open/tickets)
    |-- WalkInFormController (/open/forms)
    |-- TicketCallController (/open/call)

Analytics Controllers:
    |-- TicketBucketController
```

### A.6 Service Hierarchy

```
BaseService<R, D, O extends BaseDAO>
    |-- Extends AbstractFlowDataService
    |-- Implements IEntitySeries, IProcessorAccessService
    |-- Provides: securityService, msgService, cacheService
    |-- Multi-tenant condition management
    |
    |-- BaseUpdatableService<R, D, O extends BaseUpdatableDAO>
    |       |-- Extends AbstractFlowUpdatableService
    |       |-- CRUD operations with ProcessorAccess
    |       |-- Identity resolution (ID or Code)
    |       |-- Cache management (read, evict)
    |       |-- KIRun function registration (getCommonFunctions)
    |
    |-- BaseProcessorService<R, D, O extends BaseProcessorDAO>
    |       |-- Version-based optimistic locking
    |       |-- Duplicate detection
    |       |-- Outside user (BP) client ID handling
    |       |-- Timezone-aware pagination
    |
    |-- BaseValueService
    |
    |-- BaseContentService
            |-- For Notes, Tasks linked to Ticket/Owner
```

### A.7 DAO Hierarchy

```
BaseDAO<R, D extends BaseDto>
    |-- Basic read operations with tenant filtering
    |
    |-- BaseUpdatableDAO<R, D extends BaseUpdatableDto>
    |       |-- CRUD operations
    |       |-- Eager loading support (IEagerDAO)
    |       |-- processorAccessCondition() for tenant filtering
    |       |-- existsByName() for uniqueness checks
    |
    |-- BaseProcessorDAO<R, D extends BaseProcessorDto>
    |       |-- Enhanced tenant filtering
    |       |-- Outside user (BP) conditions
    |       |-- Timezone-aware date filtering (ITimezoneDAO)
    |
    |-- BaseAnalyticsDAO
            |-- Aggregation queries
            |-- Date bucketing
            |-- Filter condition building
```

### A.8 API Endpoint Summary

#### Authenticated Endpoints

| Path | Controller | Description |
|------|-----------|-------------|
| `/api/entity/processor/tickets/` | TicketController | Ticket CRUD + stage/tag/reassign |
| `/api/entity/processor/owners/` | OwnerController | Owner CRUD |
| `/api/entity/processor/products/` | ProductController | Product CRUD |
| `/api/entity/processor/stages/` | StageController | Stage CRUD |
| `/api/entity/processor/campaigns/` | CampaignController | Campaign CRUD |
| `/api/entity/processor/partners/` | PartnerController | Partner CRUD |
| `/api/entity/processor/activities/` | ActivityController | Activity read |
| `/api/entity/processor/notes/` | NoteController | Note CRUD |
| `/api/entity/processor/tasks/` | TaskController | Task CRUD |
| `/api/entity/processor/taskTypes/` | TaskTypeController | TaskType CRUD |
| `/api/entity/processor/productComms/` | ProductCommController | ProductComm CRUD |
| `/api/entity/processor/productTemplates/` | ProductTemplateController | ProductTemplate CRUD |
| `/api/entity/processor/productTicketCRules/` | ProductTicketCRuleController | Creation rules CRUD |
| `/api/entity/processor/productTicketRuRules/` | ProductTicketRuRuleController | Read/update rules CRUD |
| `/api/entity/processor/ticketDuplicationRules/` | TicketDuplicationRuleController | Duplication rules CRUD |
| `/api/entity/processor/productWalkInForms/` | ProductWalkInFormController | Walk-in forms CRUD |
| `/api/entity/processor/productTemplateWalkInForms/` | ProductTemplateWalkInFormController | Template walk-in forms CRUD |

#### Open Endpoints (No Auth)

| Path | Controller | Description |
|------|-----------|-------------|
| `/api/entity/processor/open/tickets/req/campaigns` | TicketOpenController | Campaign lead intake |
| `/api/entity/processor/open/tickets/req/website/{code}` | TicketOpenController | Website lead intake |
| `/api/entity/processor/open/forms/{id}` | WalkInFormController | Walk-in form operations |
| `/api/entity/processor/open/call` | TicketCallController | Exotel incoming calls |

#### Function Endpoints

| Path | Controller | Description |
|------|-----------|-------------|
| `/api/entity/processor/functions/execute/{namespace}/{name}` | ProcessorFunctionController | Execute KIRun function |
| `/api/entity/processor/functions/execute/{fullName}` | ProcessorFunctionController | Execute by full name |
| `/api/entity/processor/functions/repositoryFilter` | ProcessorFunctionController | List functions |
| `/api/entity/processor/functions/repositoryFind` | ProcessorFunctionController | Find function signature |

#### Schema Endpoints

| Path | Controller | Description |
|------|-----------|-------------|
| `/api/entity/processor/schemas/repositoryFind` | ProcessorSchemaController | Find schema |
| `/api/entity/processor/schemas/repositoryFilter` | ProcessorSchemaController | List schemas |

#### Internal Endpoints

| Path | Controller | Description |
|------|-----------|-------------|
| `/api/entity/processor/tickets/internal/{id}` | TicketController | Internal ticket read |
| `/api/entity/processor/products/internal` | ProductController | Internal product operations |
| `/api/entity/processor/tickets/req/DCRM` | TicketController | DCRM partner import |

---

## End of Integration Guide

This document covers all integration points for the Entity Processor module within the Modlix platform. For additional implementation details, refer to the source code in the `entity-processor` module, particularly:

- `src/main/java/com/fincity/saas/entity/processor/service/` - Service layer implementations
- `src/main/java/com/fincity/saas/entity/processor/controller/` - API controllers
- `src/main/java/com/fincity/saas/entity/processor/dao/` - Data access objects
- `src/main/java/com/fincity/saas/entity/processor/model/` - Request/response models
- `src/main/java/com/fincity/saas/entity/processor/configuration/` - Spring configuration
