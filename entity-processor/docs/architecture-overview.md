# Entity Processor - Architecture Overview

## Table of Contents

- [Introduction](#introduction)
- [Module Purpose and Scope](#module-purpose-and-scope)
- [High-Level Architecture](#high-level-architecture)
- [Technology Stack](#technology-stack)
- [Directory Structure](#directory-structure)
- [Layered Architecture](#layered-architecture)
- [Multi-Tenancy Model](#multi-tenancy-model)
- [Reactive Programming Model](#reactive-programming-model)
- [Security Architecture](#security-architecture)
- [Configuration Management](#configuration-management)
- [Entity Series and Registration](#entity-series-and-registration)
- [Caching Strategy](#caching-strategy)
- [Error Handling and Internationalization](#error-handling-and-internationalization)
- [Versioning and Optimistic Locking](#versioning-and-optimistic-locking)
- [Eager Loading System](#eager-loading-system)
- [Custom Serialization (Gson)](#custom-serialization-gson)
- [Deployment and Infrastructure](#deployment-and-infrastructure)

---

## Introduction

The **Entity Processor** is a Spring Boot microservice within the Modlix no-code/low-code platform that provides a comprehensive multi-tenant CRM/lead management engine. It serves as the backbone for managing deals (tickets), contacts (owners), projects (products), pipeline stages, activities, tasks, notes, campaigns, business partners, and associated business rules.

This document describes the architectural foundations of the Entity Processor, including its layered design, reactive programming patterns, multi-tenancy strategy, security integration, and deployment model.

---

## Module Purpose and Scope

The Entity Processor handles the following core concerns within the Modlix platform:

### Primary Responsibilities

1. **Ticket (Deal) Management** - Full lifecycle management of deals/tickets from creation through pipeline stages to completion. Includes creation from multiple sources (direct, campaign, website, walk-in, DCRM import), duplication detection, automatic assignment, stage transitions, and tag management.

2. **Owner (Lead/Contact) Management** - Managing contacts associated with tickets. Automatic creation of owners when tickets are created, linking owners to tickets, and propagating owner information changes to related tickets.

3. **Product (Project) Management** - Configuration of products/projects that serve as containers for tickets. Products are linked to templates that define pipeline stages and business rules.

4. **Pipeline and Stage Management** - Defining and managing multi-stage pipelines for ticket progression. Stages are hierarchical (stages contain statuses as children) and are ordered for sequential progression.

5. **Activity Logging and Audit Trail** - Comprehensive event-driven activity logging that tracks every significant action taken on entities. Activities are formatted with markdown-based templates and stored for historical reference.

6. **Task and Note Management** - Creating and managing tasks (todos) and notes associated with tickets or owners. Tasks have priorities, due dates, completion tracking, and reminder functionality.

7. **Campaign Management** - Managing marketing campaigns that can generate tickets through external integrations.

8. **Business Partner Management** - Managing partner organizations with verification workflows, Do-Not-Call (DNC) tracking, and partner-specific access controls.

9. **Rule Engine** - Configurable business rules for:
   - Ticket duplication detection based on phone/email and custom conditions
   - User assignment distribution (round-robin, random)
   - Product-level ticket creation rules with condition-based matching
   - Product-level read/update permission rules

10. **Walk-In Form System** - Public-facing form endpoints for creating tickets through walk-in interfaces without authentication.

11. **Analytics and Reporting** - Ticket bucketing, date-based counts, and status-based analytics for dashboards and reporting.

12. **Communication Integration** - Product-level communication configuration linking to external services (Exotel, Twilio, WhatsApp, Email, SMS) through the Core microservice's connection system.

13. **Function Execution** - KIRun-based function execution engine exposing entity operations as callable functions for dynamic workflow automation.

### What Entity Processor Does NOT Handle

- **Authentication** - Handled by the Security microservice
- **File Storage** - Handled by the Files microservice
- **Notifications** - Handled by the Notification microservice
- **Message Delivery** - Handled by the Message microservice
- **API Routing** - Handled by the Gateway microservice

---

## High-Level Architecture

```
                                    ┌──────────────┐
                                    │   Gateway    │
                                    │  (port 8080) │
                                    └──────┬───────┘
                                           │
                           ┌───────────────┼───────────────┐
                           │               │               │
                    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
                    │   Security  │ │   Entity    │ │    Core     │
                    │ (port 8001) │ │  Processor  │ │ (port 8003) │
                    └──────┬──────┘ │ (port 8009) │ └──────┬──────┘
                           │        └──────┬──────┘        │
                           │               │               │
                    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
                    │   MySQL     │ │   MySQL     │ │  MongoDB    │
                    │ (security)  │ │ (entity_    │ │  (core)     │
                    │             │ │  processor) │ │             │
                    └─────────────┘ └─────────────┘ └─────────────┘
```

### Service Communication Patterns

The Entity Processor communicates with other microservices using two patterns:

1. **Feign Clients (Synchronous HTTP)** - Used for inter-service calls to Security (user lookup, client validation) and Core (connection details) microservices. Feign clients are declared as interfaces with `@FeignClient` annotations and are resolved through Eureka service discovery.

2. **Spring Security Context** - Authentication context flows through the reactive pipeline via `ReactiveSecurityContextHolder`, providing user identity, client hierarchy, and authority information to every service method.

### Service Registration

The Entity Processor registers with Eureka at startup and is discoverable by other services:
- **Service Name**: `entity-processor`
- **Default Port**: `8009`
- **Health Endpoint**: Standard Spring Boot Actuator

---

## Technology Stack

### Core Framework
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime platform |
| Spring Boot | 3.4.1 | Application framework |
| Spring WebFlux | 6.x | Reactive web framework |
| Project Reactor | 3.x | Reactive streams implementation |

### Data Access
| Technology | Version | Purpose |
|---|---|---|
| JOOQ | 3.20.5 | Type-safe SQL query builder and code generation |
| R2DBC MySQL | - | Reactive database connectivity |
| Flyway | - | Database migration management |
| MySQL | 8.0+ | Primary relational database |

### Service Integration
| Technology | Version | Purpose |
|---|---|---|
| Spring Cloud Config | - | Externalized configuration |
| Eureka | - | Service discovery |
| OpenFeign | - | Declarative HTTP clients |
| Spring Security | 6.x | Authentication and authorization |

### Utilities
| Technology | Version | Purpose |
|---|---|---|
| KIRun | 3.14.1 | Function execution engine |
| Gson | - | JSON serialization/deserialization |
| libphonenumber | - | Phone number validation and formatting |
| Lombok | - | Boilerplate reduction |
| reactor-flatmap-util | - | Custom reactive chaining utilities |

### Internal Libraries
| Library | Purpose |
|---|---|
| `commons` | Shared utilities, DTOs, conditions |
| `commons-jooq` | JOOQ base classes for services and DAOs |
| `commons-security` | Security context, JWT handling, client management |
| `reactor-flatmap-util` | `FlatMapUtil` for readable reactive chains |

---

## Directory Structure

```
entity-processor/
├── pom.xml                                    # Maven build configuration
├── Dockerfile                                 # Container deployment
├── src/main/
│   ├── java/com/fincity/saas/entity/processor/
│   │   ├── EntityProcessorApplication.java    # Spring Boot entry point
│   │   │
│   │   ├── configuration/                     # Beans, Gson, Security
│   │   │   └── ProcessorConfiguration.java
│   │   │
│   │   ├── controller/                        # REST API endpoints
│   │   │   ├── base/                          # Abstract controller classes
│   │   │   ├── content/                       # Task, Note, TaskType controllers
│   │   │   ├── product/                       # Product-related controllers
│   │   │   ├── rule/                          # Rule-related controllers
│   │   │   ├── form/                          # Walk-in form controllers
│   │   │   └── open/                          # Public (unauthenticated) endpoints
│   │   │
│   │   ├── service/                           # Business logic layer
│   │   │   ├── base/                          # Abstract service classes
│   │   │   ├── content/                       # Task, Note services
│   │   │   ├── product/                       # Product-related services
│   │   │   ├── rule/                          # Rule engine services
│   │   │   └── form/                          # Walk-in form services
│   │   │
│   │   ├── dao/                               # Data access layer
│   │   │   ├── base/                          # Abstract DAO classes
│   │   │   ├── content/                       # Task, Note DAOs
│   │   │   ├── product/                       # Product-related DAOs
│   │   │   ├── rule/                          # Rule DAOs
│   │   │   └── form/                          # Walk-in form DAOs
│   │   │
│   │   ├── dto/                               # Data transfer objects
│   │   │   ├── base/                          # Base DTO classes
│   │   │   ├── content/                       # Task, Note, TaskType DTOs
│   │   │   ├── product/                       # Product-related DTOs
│   │   │   ├── rule/                          # Rule DTOs
│   │   │   └── form/                          # Walk-in form DTOs
│   │   │
│   │   ├── model/                             # Request/Response models
│   │   │   ├── base/                          # Base request/response
│   │   │   ├── common/                        # Shared models
│   │   │   ├── request/                       # API request payloads
│   │   │   └── response/                      # API response payloads
│   │   │
│   │   ├── enums/                             # Enumerations
│   │   │   ├── content/                       # Content-related enums
│   │   │   └── rule/                          # Rule-related enums
│   │   │
│   │   ├── jooq/                              # JOOQ generated code (DO NOT EDIT)
│   │   │   ├── tables/                        # Table definitions
│   │   │   └── tables/records/                # Record classes
│   │   │
│   │   ├── feign/                             # Feign client interfaces
│   │   ├── oserver/                           # External service models
│   │   ├── gson/                              # Custom Gson adapters
│   │   ├── util/                              # Utility classes
│   │   ├── eager/                             # Eager loading system
│   │   ├── analytics/                         # Analytics and reporting
│   │   └── constant/                          # Constants
│   │
│   └── resources/
│       ├── application.yml                    # Application configuration
│       └── messages_en.properties             # i18n message templates
│
└── target/                                    # Build output
```

### Package Naming Conventions

- `controller` - REST endpoints, request validation, response wrapping
- `service` - Business logic, transaction boundaries, rule execution
- `dao` - Database operations, JOOQ queries, condition building
- `dto` - Domain entities mapped to database tables
- `model` - API request/response objects (not persisted)
- `enums` - Type-safe enumerations for status, actions, platforms
- `jooq` - Auto-generated code from database schema (never modify manually)
- `feign` - Declarative HTTP client interfaces for other microservices
- `oserver` - Models for external service integrations
- `gson` - Custom JSON serializers/deserializers
- `util` - Stateless utility functions
- `eager` - Relationship loading and field resolution
- `analytics` - Reporting DAOs, models, and controllers
- `constant` - Static constants and configuration values

---

## Layered Architecture

The Entity Processor follows a strict layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────┐
│                   Controller Layer                   │
│  - Request routing and validation                   │
│  - Response wrapping (ResponseEntity)               │
│  - @PreAuthorize security annotations               │
│  - Eager loading query parameter parsing            │
├─────────────────────────────────────────────────────┤
│                   Service Layer                      │
│  - Business logic and orchestration                 │
│  - ProcessorAccess context management               │
│  - Rule execution and condition evaluation          │
│  - Activity logging coordination                    │
│  - Cache management                                 │
│  - Entity validation and transformation             │
├─────────────────────────────────────────────────────┤
│                    DAO Layer                         │
│  - JOOQ-based reactive queries                      │
│  - Multi-tenant condition injection                 │
│  - Pagination and filtering                         │
│  - Eager loading with table joins                   │
│  - Condition-based dynamic queries                  │
├─────────────────────────────────────────────────────┤
│                  Database Layer                      │
│  - MySQL 8.0+ with R2DBC driver                     │
│  - JOOQ-generated table and record classes          │
│  - Schema managed by Flyway migrations              │
└─────────────────────────────────────────────────────┘
```

### Controller Layer

Controllers extend a base class hierarchy that provides standard CRUD and filtering operations:

```
BaseController
  └── BaseUpdatableController        (adds update, update-by-map)
        └── BaseValueController      (adds value-based lookups)
        └── BaseProcessorController  (adds eager loading, page filtering with conditions)
```

**Key Responsibilities:**
- Map HTTP endpoints to service methods
- Parse `Identity` path variables (can be ID or CODE)
- Parse eager loading fields and query parameters
- Parse `AbstractCondition` from request bodies for filtering
- Wrap responses in `ResponseEntity`
- Delegate all business logic to the service layer

### Service Layer

Services extend a base class hierarchy that provides common CRUD operations with access control:

```
BaseService                          (basic CRUD, access checking)
  └── BaseUpdatableService           (update operations, entity validation)
        └── BaseValueService         (value-based lookups)
        └── BaseProcessorService     (version checking, duplicate detection, page filtering)
              └── BaseRuleService    (rule-specific CRUD with product/template association)
```

**Key Responsibilities:**
- Implement business rules and validation
- Manage `ProcessorAccess` security context
- Coordinate between multiple DAOs
- Trigger activity logging
- Manage caches (create, update, evict)
- Handle entity lifecycle events

### DAO Layer

DAOs extend a base class hierarchy that provides JOOQ-based reactive database operations:

```
BaseDAO                              (basic CRUD, JOOQ operations)
  └── BaseProcessorDAO               (multi-tenant filtering by appCode/clientCode)
        └── BaseValueDAO             (value-based queries)
```

**Key Responsibilities:**
- Build and execute JOOQ queries
- Apply multi-tenant access conditions
- Handle pagination and sorting
- Perform eager loading with table joins
- Execute condition-based dynamic queries

---

## Multi-Tenancy Model

The Entity Processor implements a shared-database, shared-schema multi-tenancy model where every entity record is scoped by `APP_CODE` and `CLIENT_CODE`:

### Tenant Isolation

Every database table includes:
- `APP_CODE` (VARCHAR) - Application code identifying the application context
- `CLIENT_CODE` (VARCHAR) - Client code identifying the tenant organization

All queries automatically filter by these columns through the `ProcessorAccess` context:

```java
// BaseProcessorDAO automatically applies tenant filtering
public Mono<AbstractCondition> processorAccessCondition(
        AbstractCondition condition, ProcessorAccess access) {
    // Adds APP_CODE and CLIENT_CODE filters
    // For outside users (Business Partners), applies managed client code
}
```

### ProcessorAccess Context

The `ProcessorAccess` object is the central security context for the Entity Processor. It encapsulates:

```java
public final class ProcessorAccess {
    private String appCode;           // Application context
    private String clientCode;        // Tenant organization
    private ULong userId;             // Current user ID
    private String firstName;         // User first name
    private String lastName;          // User last name
    private String middleName;        // User middle name
    private boolean hasAccessFlag;    // Whether access was validated
    private ContextUser user;         // Full user context from JWT
    private UserInheritanceInfo userInherit;  // Client hierarchy info
    private boolean hasBpAccess;      // Business Partner flag
}
```

### Client Hierarchy and Inheritance

The `UserInheritanceInfo` nested class carries information about the client hierarchy:

```java
public static class UserInheritanceInfo {
    private String clientLevelType;        // e.g., "BP" for Business Partner
    private String loggedInClientCode;     // Client code the user logged in from
    private ULong loggedInClientId;        // Client ID the user logged in from
    private String managedClientCode;      // Client code being managed (for BP users)
    private ULong managedClientId;         // Client ID being managed
    private List<ULong> subOrg;            // Sub-organization user IDs
    private List<ULong> managingClientIds; // Client IDs the user can manage
}
```

### Outside User (Business Partner) Handling

Business Partners are special external users who can create and manage entities on behalf of a client:

```java
// ProcessorAccess determines if user is an outside (BP) user
public boolean isOutsideUser() {
    if (userInherit != null)
        return BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(
            this.userInherit.clientLevelType);
    return false;
}

// For outside users, the effective client code is the managed client
public String getEffectiveClientCode() {
    return isOutsideUser()
        ? this.getUserInherit().getManagedClientCode()
        : this.getClientCode();
}
```

When an outside user creates a ticket, the `clientId` on the ticket is set to the Business Partner's client ID, not the managed client's ID. This enables tracking which partner created which tickets.

---

## Reactive Programming Model

The Entity Processor is fully reactive, built on Project Reactor. Every operation from controller to database returns `Mono<T>` (single value) or `Flux<T>` (multiple values).

### FlatMapUtil Pattern

The primary pattern for chaining reactive operations is `FlatMapUtil.flatMapMono()` from the `reactor-flatmap-util` library. This provides a more readable alternative to nested `.flatMap()` chains:

```java
// Standard approach (hard to read):
return step1()
    .flatMap(a -> step2(a)
        .flatMap(b -> step3(a, b)
            .flatMap(c -> step4(a, b, c))));

// FlatMapUtil approach (readable):
return FlatMapUtil.flatMapMono(
    () -> step1(),
    a -> step2(a),
    (a, b) -> step3(a, b),
    (a, b, c) -> step4(a, b, c)
)
.contextWrite(Context.of(LogUtil.METHOD_NAME, "ServiceName.methodName"));
```

Key characteristics:
- Each step receives the results of ALL previous steps as parameters
- Steps execute sequentially (not in parallel)
- If any step returns `Mono.empty()`, the chain short-circuits
- Context logging is attached via `.contextWrite()`

### FlatMapMonoWithNull Variant

When intermediate steps may legitimately return null/empty without short-circuiting:

```java
return FlatMapUtil.flatMapMonoWithNull(
    () -> step1(),                        // Must produce value
    result1 -> optionalStep2(result1),    // May return empty
    (result1, result2OrNull) -> step3()   // result2 can be null
);
```

### Context-Based Logging

Every reactive chain includes context information for debugging:

```java
.contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createRequest"))
```

This context propagates through the reactive pipeline and is available in log output, enabling tracing of method execution chains.

### Parallel Execution

When multiple independent operations need to execute concurrently:

```java
return Mono.zip(
    this.productService.readByIdentity(access, ticketRequest.getProductId()),
    this.getDnc(access, ticketRequest)
);
// Both operations execute in parallel, result is a Tuple2
```

### Error Handling in Reactive Chains

Errors are propagated through the reactive chain using `switchIfEmpty` and exception throwing:

```java
return FlatMapUtil.flatMapMono(
    () -> this.someOperation(),
    result -> {
        if (result == null)
            return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                ProcessorMessageResourceService.OBJECT_NOT_FOUND, "Entity");
        return Mono.just(result);
    }
);
```

---

## Security Architecture

### Authentication Flow

1. Requests arrive at the Gateway with a JWT token
2. The Gateway validates the token with the Security microservice
3. The validated `ContextAuthentication` is placed in `ReactiveSecurityContextHolder`
4. Each service method retrieves authentication via `SecurityContextUtil`
5. `ProcessorAccess` is constructed from `ContextAuthentication`

### Authorization Model

Authorization operates at two levels:

**1. Method-Level (Controller Layer)**

Controllers use `@PreAuthorize` annotations with Spring Security's `hasAuthority()`:

```java
@PreAuthorize("hasAuthority('Authorities.Ticket_CREATE')")
@PostMapping(REQ_PATH)
public Mono<ResponseEntity<Ticket>> createRequest(@RequestBody TicketRequest request)
```

**2. Data-Level (Service/DAO Layer)**

The `ProcessorAccess` context determines data visibility:
- **App Code Filtering** - Only entities matching the current app code are visible
- **Client Code Filtering** - Only entities matching the current client code are visible
- **Sub-Organization Filtering** - Users can only reassign to users within their sub-organization hierarchy
- **Business Partner Filtering** - Outside users can only access entities through their managed client relationship

### Open Endpoints

Some endpoints are publicly accessible without authentication (configured in `ProcessorConfiguration`):

```java
// Security filter chain configuration
.authorizeExchange(exchanges -> exchanges
    .pathMatchers("/api/entity/processor/open/**").permitAll()
    .anyExchange().authenticated())
```

Open endpoints serve:
- Campaign ticket creation (`/open/tickets/req/campaigns`)
- Website ticket creation (`/open/tickets/req/website/{code}`)
- Walk-in form retrieval and submission (`/open/forms/**`)
- Phone call handling (`/open/call`)

These endpoints construct `ProcessorAccess` from request parameters (appCode, clientCode) rather than from JWT authentication.

---

## Configuration Management

### Application Configuration

The Entity Processor uses Spring Cloud Config for externalized configuration. The local `application.yml` contains bootstrap settings:

```yaml
server:
  port: 8009

spring:
  application:
    name: entity-processor
  config:
    import: configserver:http://{CLOUD_CONFIG_SERVER}:8888/
  profiles:
    active: default
  codec:
    max-in-memory-size: 10MB
```

### ProcessorConfiguration Bean

The `ProcessorConfiguration` class (extends `AbstractJooqBaseConfiguration`) configures:

1. **Gson Instance** - Custom Gson with type adapters for:
   - `Identity` (ID or CODE)
   - `PhoneNumber` (country code + number)
   - `Email` (address string)
   - `AbstractCondition` (complex query conditions)
   - `Page` and `Pageable` (pagination)
   - `Sort` (sorting specifications)

2. **Security Filter Chain** - Permits open endpoints, requires authentication for others

3. **FlatMapUtil Logging** - Configures reactive chain logging

4. **Bean Registrations** - Service beans and their dependencies

---

## Entity Series and Registration

The `EntitySeries` enum is the central registry for all entity types in the system. It maps each entity to its:
- Database table
- DTO class
- Display name
- Code prefix
- Numeric identifier

```java
public enum EntitySeries implements EnumType {
    TICKET("TICKET", "Ticket", 12, "Ticket"),
    OWNER("OWNER", "Owner", 13, "Owner"),
    PRODUCT("PRODUCT", "Product", 14, "Product"),
    PRODUCT_TEMPLATE("PRODUCT_TEMPLATE", "Product Template", 15, "ProductTemplate"),
    STAGE("STAGE", "Stage", 17, "Stage"),
    TASK("TASK", "Task", 22, "Task"),
    NOTE("NOTE", "Note", 24, "Note"),
    ACTIVITY("ACTIVITY", "Activity", 25, "Activity"),
    CAMPAIGN("CAMPAIGN", "Campaign", 26, "Campaign"),
    PARTNER("PARTNER", "Partner", 27, "Partner"),
    // ... and more
}
```

### Application-Specific Naming

Entity names can vary by application. For example, the "leadzump" application uses different terminology:

| EntitySeries | Default Name | LeadZump Name |
|---|---|---|
| TICKET | Ticket | Deal |
| OWNER | Owner | Lead |
| PRODUCT | Product | Project |

This is implemented through the `getPrefix(String appCode)` method:

```java
public String getPrefix(String appCode) {
    if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this);
    return this.prefix;
}
```

---

## Caching Strategy

The Entity Processor uses a custom caching service (from `commons-jooq`) to cache frequently accessed data:

### Cached Entities

Each service defines a cache name:

```java
@Override
protected String getCacheName() {
    return "ticket";        // TicketService
    return "owner";         // OwnerService
    return "product";       // ProductService
    return "stage";         // StageService
    // etc.
}
```

### Cache Operations

```java
// Read-through caching
cacheService.cacheValueOrGet(
    cacheName,                    // Cache region
    () -> fetchFromDatabase(),    // Cache miss supplier
    cacheKey                      // Cache key
);

// Cache eviction (on update/delete)
cacheService.evictAll(cacheName);
```

### Rule Condition Caching

Duplication rules and user distribution rules have specialized caches:
- `ticketDuplicationProductRuleCondition` - Cached rule conditions per product
- `ticketDuplicationProductTemplateRuleCondition` - Cached rule conditions per product template
- Cache keys include `appCode`, `clientCode`, `productId`, `source`, and `subSource`

Caches are evicted when rules are created, updated, or deleted.

---

## Error Handling and Internationalization

### Message Resource Service

The `ProcessorMessageResourceService` provides centralized error message management:

```java
// Throwing an error with message template
this.msgService.throwMessage(
    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
    ProcessorMessageResourceService.IDENTITY_MISSING,
    "Source"
);
```

### Message Template Format

Messages use `$` as placeholder tokens (defined in `messages_en.properties`):

```properties
duplicate_entity=A $ already exists with ID: '$' for the provided information. \
    Please review that $ before proceeding.
ticket_stage_missing=Unable to find a stage for the $. \
    Please go through stage creation and try again.
identity_info_missing=We need a phone number or email to add $.
```

### Message Key Constants

All message keys are defined as constants in `ProcessorMessageResourceService`:

```java
public static final String DUPLICATE_ENTITY = "duplicate_entity";
public static final String DUPLICATE_ENTITY_OUTSIDE_USER = "duplicate_entity_outside_user";
public static final String TICKET_STAGE_MISSING = "ticket_stage_missing";
public static final String TICKET_ASSIGNMENT_MISSING = "ticket_assignment_missing";
public static final String VERSION_MISMATCH = "version_mismatch";
// ... 65+ message keys
```

### Error Categories

| Category | HTTP Status | Examples |
|---|---|---|
| Validation | 400 Bad Request | Missing fields, invalid parameters, duplicate entities |
| Access Control | 403 Forbidden | Insufficient permissions, wrong client access |
| Not Found | 404 Not Found | Entity not found by ID or CODE |
| Versioning | 412 Precondition Failed | Optimistic lock version mismatch |
| Server Error | 500 Internal Server Error | Unexpected errors |

---

## Versioning and Optimistic Locking

The Entity Processor uses version-based optimistic locking to prevent concurrent modification conflicts:

### Version Field

Every entity extending `BaseProcessorDto` has an integer `version` field that starts at 0 and increments with each update.

### Update Flow

```java
// BaseProcessorService.updatableEntity()
@Override
protected Mono<D> updatableEntity(D entity) {
    return FlatMapUtil.flatMapMono(
        () -> super.updatableEntity(entity),
        existing -> {
            // Check version matches
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                    ProcessorMessageResourceService.VERSION_MISMATCH);

            // Increment version
            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        }
    );
}
```

### Client-Side Contract

Clients must:
1. Read the current entity (including its `version`)
2. Send the `version` value back in the update request
3. If the version has changed since the read, a `412 Precondition Failed` is returned
4. The client must re-read and retry the update

---

## Eager Loading System

The Entity Processor includes a sophisticated eager loading system that allows API consumers to request related entities in a single query:

### How It Works

1. **Controller** parses `fields` query parameter listing relationships to load
2. **DAO** uses the `EagerUtil` to resolve field names to related tables
3. **JOOQ** joins related tables and returns combined results
4. **Response** includes nested related entity data as `Map<String, Object>`

### Relationship Declaration

DTOs declare their relationships in constructors:

```java
public Ticket() {
    super();
    this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
    this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
    this.relationsMap.put(Fields.stage, EntitySeries.STAGE.getTable());
    this.relationsMap.put(Fields.status, EntitySeries.STAGE.getTable());
    this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
    this.relationsMap.put(Fields.productTemplateId,
        EntitySeries.PRODUCT_TEMPLATE.getTable());
    // Special resolver for user fields (fetched from Security service)
    this.relationsResolverMap.put(UserFieldResolver.class, Fields.assignedUserId);
}
```

### API Usage

```
GET /api/entity/processor/tickets/123?fields=ownerId,productId,stage
```

Returns the ticket with nested owner, product, and stage data instead of just foreign key IDs.

---

## Custom Serialization (Gson)

The Entity Processor uses Gson (not Jackson) for JSON serialization, with several custom type adapters:

### IdentityTypeAdapter

Handles the `Identity` class which can represent either a numeric ID or a string CODE:

```json
// Numeric ID
{ "id": 12345 }

// String CODE
{ "code": "ABC123" }

// Compact form
12345
"ABC123"
```

### PhoneNumberTypeAdapter

Serializes/deserializes phone numbers with country code:

```json
{
    "countryCode": 91,
    "number": "9876543210"
}
```

### EmailTypeAdapter

Serializes/deserializes email addresses:

```json
{
    "address": "user@example.com"
}
```

### AbstractConditionTypeAdapter

Handles complex query condition trees (AND/OR/BETWEEN/IN/EQUALS/etc.) used for dynamic filtering:

```json
{
    "conditions": [
        {
            "field": "source",
            "operator": "EQUALS",
            "value": "Website"
        },
        {
            "field": "stage",
            "operator": "IN",
            "multiValue": [1, 2, 3]
        }
    ],
    "operator": "AND"
}
```

---

## Deployment and Infrastructure

### Docker Deployment

The Entity Processor is containerized using Docker:

```dockerfile
FROM openjdk:21-jdk-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Service Dependencies

At startup, the Entity Processor requires:
1. **MySQL** - Database must be available with schema created
2. **Eureka** - Service registry for registration and discovery
3. **Spring Cloud Config** - Configuration server for externalized properties
4. **Security Service** - For user and client validation operations

### Environment Configuration

Key environment variables:
- `CLOUD_CONFIG_SERVER` - Config server hostname
- `EUREKA_SERVER` - Eureka server hostname
- `DB_HOST`, `DB_PORT`, `DB_NAME` - Database connection
- `DB_USERNAME`, `DB_PASSWORD` - Database credentials

### Blue-Green Deployment

The Modlix platform supports blue-green deployments managed by `keepupscript.sh`. The Entity Processor can run multiple instances behind the Gateway load balancer for high availability.

### Monitoring

- Spring Boot Actuator endpoints for health checks
- Reactive logging through `LogUtil.METHOD_NAME` context propagation
- JOOQ query logging for database debugging

---

## Summary

The Entity Processor is a well-architected, fully reactive microservice that serves as the CRM engine for the Modlix platform. Its key architectural strengths include:

1. **Consistent Layered Design** - Clear separation between controllers, services, and DAOs
2. **Type-Safe Database Access** - JOOQ code generation ensures compile-time query safety
3. **Reactive Throughout** - From HTTP to database, everything is non-blocking
4. **Flexible Multi-Tenancy** - App code + client code + business partner support
5. **Extensible Rule Engine** - Configurable duplication detection and user assignment
6. **Comprehensive Audit Trail** - Activity logging for every significant action
7. **Readable Reactive Chains** - FlatMapUtil makes complex async flows maintainable
8. **Application-Aware Naming** - Entity names adapt per application context

The architecture supports the platform's no-code/low-code philosophy by making entity management configurable through products, templates, stages, rules, and dynamic function execution rather than requiring code changes.

---

## Appendix A: Service Dependency Graph

The following shows the dependency relationships between services in the Entity Processor:

```
TicketService
  ├── OwnerService (@Lazy - bidirectional)
  ├── ProductService
  ├── StageService
  ├── ProductTicketCRuleService
  ├── TicketDuplicationRuleService
  ├── ActivityService
  ├── TaskService (@Lazy)
  ├── NoteService (@Lazy)
  ├── CampaignService (@Lazy)
  ├── PartnerService (@Lazy)
  ├── ProductCommService
  └── Gson

ActivityService
  ├── StageService (@Lazy)
  ├── TicketService (@Lazy)
  └── SecurityService (from base)

OwnerService
  ├── TicketService (@Lazy - bidirectional)
  └── ActivityService

ProductTicketCRuleService
  ├── TicketCRuleExecutionService
  ├── StageService
  └── ProductService

TicketDuplicationRuleService
  ├── StageService (@Lazy)
  └── ProductService (from base)

TicketCRuleExecutionService
  └── TicketCUserDistributionService

TaskService
  ├── ActivityService
  └── TicketService

NoteService
  ├── ActivityService
  └── TicketService

CampaignService
  └── ProductService

PartnerService
  ├── TicketService
  └── SecurityService

ProductCommService
  ├── ProductService
  └── ConnectionServiceProvider (Core Feign)

TicketCallService
  ├── TicketService
  ├── ProductCommService
  └── MessageService (Feign)

WalkInFormService
  ├── TicketService
  ├── ProductService
  ├── StageService
  └── ActivityService
```

**@Lazy Annotations:**
Several services have circular dependencies (e.g., TicketService <-> OwnerService). These are resolved using Spring's `@Lazy` annotation, which creates a proxy that defers initialization until first use.

---

## Appendix B: Request Processing Pipeline

Every incoming request passes through the following pipeline:

```
1. HTTP Request arrives at Gateway (port 8080)
   │
2. Gateway: JWT validation with Security service
   │
3. Gateway: Route to Entity Processor (port 8009)
   │
4. Spring Security Filter Chain
   │  ├── Open endpoint (/open/**) → Skip authentication
   │  └── Authenticated endpoint → Validate ContextAuthentication
   │
5. Controller Method
   │  ├── @PreAuthorize check (authority validation)
   │  ├── Parse path variables (Identity, etc.)
   │  ├── Parse query parameters (fields, pagination)
   │  └── Deserialize request body (Gson)
   │
6. Service Method
   │  ├── hasAccess() → Build ProcessorAccess
   │  │   ├── Get ContextAuthentication from ReactiveSecurityContextHolder
   │  │   ├── Get UserInheritanceInfo (subOrg, managingClientIds)
   │  │   └── Construct ProcessorAccess
   │  │
   │  ├── Business Logic Execution
   │  │   ├── Entity validation (checkEntity)
   │  │   ├── Rule evaluation (duplication, assignment)
   │  │   ├── Related entity operations
   │  │   └── Activity logging
   │  │
   │  └── Cache Management (read-through, eviction)
   │
7. DAO Method
   │  ├── processorAccessCondition (add tenant filters)
   │  ├── JOOQ query building
   │  ├── R2DBC reactive execution
   │  └── Eager loading (optional JOINs)
   │
8. Response
   ├── Entity wrapped in ResponseEntity
   ├── Serialized to JSON (Gson)
   └── Returned through reactive chain
```

---

## Appendix C: Database Connection Configuration

The Entity Processor uses R2DBC for non-blocking database connections:

```yaml
# Typical externalized configuration (from Config Server)
spring:
  r2dbc:
    url: r2dbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
      validation-query: SELECT 1
```

### Connection Pool Sizing

The R2DBC connection pool is configured for reactive workloads:
- **initial-size**: Number of connections created at startup
- **max-size**: Maximum concurrent connections
- **max-idle-time**: How long idle connections are kept alive
- **validation-query**: Health check query for connections

### JOOQ Configuration

JOOQ is configured through `AbstractJooqBaseConfiguration` in the commons library:
- SQL dialect: `MYSQL`
- Record mapper: Custom mapper for reactive records
- Settings: Render schema = false, render name case = AS_IS

### Code Generation

JOOQ code generation runs during the Maven build:
```bash
./runmvn.sh jooq    # Generates JOOQ classes from database schema
```

Generated classes are placed in `com.fincity.saas.entity.processor.jooq` package and should never be manually edited.

---

## Appendix D: Logging and Debugging

### Context-Based Logging

Every reactive chain includes method context for tracing:

```java
.contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createRequest"))
```

This produces log output like:
```
[TicketService.createRequest] Creating ticket for product 100
[TicketService.checkEntity] Setting assignment and stage
[StageService.getFirstStage] Getting first stage for template 50
[ProductTicketCRuleService.getUserAssignment] Evaluating rules for product 100
```

### Debug Logging Levels

| Logger | Level | Purpose |
|--------|-------|---------|
| `com.fincity.saas.entity.processor` | DEBUG | Service and DAO debug output |
| `org.jooq` | DEBUG | SQL query logging |
| `io.r2dbc` | DEBUG | R2DBC connection and query logging |
| `org.springframework.security` | DEBUG | Security context logging |
| `com.fincity.nocode.reactor.util` | DEBUG | FlatMapUtil chain logging |

### Common Debugging Scenarios

**1. Ticket not being created:**
- Check if duplicate detection is blocking creation (look for RE_INQUIRY activity)
- Verify product exists and has a template with stages
- Check assignment rules have valid users in the sub-organization

**2. Stage update failing:**
- Verify the target stage belongs to the product's template
- Check that status (if provided) belongs to the target stage
- Ensure version matches for optimistic locking

**3. Reassignment failing:**
- Verify the target user is in the current user's sub-organization
- Check that the user has the required authority

**4. Eager loading returning incomplete data:**
- Verify the field names match DTO field names exactly
- Check that the related entity exists in the database
- For user fields, verify the Security service is reachable

---

## Appendix E: Performance Considerations

### Reactive Benefits

The fully reactive architecture provides:
- **Non-blocking I/O**: Database queries don't block threads
- **Backpressure**: Automatic flow control prevents memory overflow
- **Efficient Thread Utilization**: Fewer threads serve more concurrent requests
- **Scalability**: Horizontal scaling by adding more instances

### Caching Strategy Impact

The multi-level caching system significantly reduces database load:
- Entity caches reduce repeated reads for the same entity
- Rule condition caches avoid recomputing complex conditions
- Cache eviction is targeted (per-entity, per-product) to minimize stale data

### Query Optimization

- JOOQ generates optimized SQL with proper indexing hints
- Eager loading uses LEFT JOINs instead of N+1 queries
- Pagination limits result set sizes
- Multi-tenant filtering uses indexed columns (APP_CODE, CLIENT_CODE)

### Memory Management

```yaml
spring:
  codec:
    max-in-memory-size: 10MB    # Maximum buffer for request/response bodies
```

Large request bodies (e.g., bulk imports) are limited to 10MB to prevent memory exhaustion.
