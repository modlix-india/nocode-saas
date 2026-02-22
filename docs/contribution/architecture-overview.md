# Architecture Overview

This document describes the high-level architecture of the **nocode-saas** platform — a microservices-based no-code/low-code SaaS backend built with Spring Boot 3.4, Java 21, and Project Reactor.

## System Architecture

```
                        +-------------------+
                        |   Config Server   |
                        |    (port 8888)    |
                        +--------+----------+
                                 |
                        +--------v----------+
                        |   Eureka Server   |
                        |    (port 9999)    |
                        +--------+----------+
                                 |
              +------------------+------------------+
              |                                     |
     +--------v----------+               +---------v---------+
     |      Gateway      |               |     RabbitMQ      |
     |    (port 8080)    |               |   (Event Bus)     |
     +--------+----------+               +---------+---------+
              |                                     |
   +----------+----------+----------+----------+----+----+----------+
   |          |          |          |          |         |          |
+--v--+  +---v---+  +---v---+  +---v---+  +--v---+  +--v--+  +---v----+
|core |  |securi-|  | files |  |  ui   |  | msg  |  |notif|  | entity |
|8001 |  |ty 8003|  | 8004  |  |8002/  |  | 8005 |  |8006 |  |coll/proc
+--+--+  +---+---+  +---+---+  |9002  |  +--+---+  +--+--+  |8007/8008
   |         |          |      +---+---+     |         |      +---+----+
   |         |          |          |         |         |          |
   +----+----+----+-----+----+----+----+----+---------+----------+
        |         |          |         |
   +----v---+ +---v----+ +--v---+ +---v---+
   | MySQL  | |MongoDB | | Redis| |Rabbit |
   |(R2DBC) | |(React.)| |Cache | |  MQ   |
   +--------+ +--------+ +------+ +-------+
```

## Microservices

| Service | Port | Module | Group ID | Description |
|---------|------|--------|----------|-------------|
| **core** | 8001 | `/core` | `com.fincity.saas` | Core logic, data operations, no-code runtime |
| **security** | 8003 | `/security` | `com.fincity.saas` | Authentication, authorization, user/client management |
| **gateway** | 8080 | `/gateway` | `com.fincity` | API Gateway (Spring Cloud Gateway), routes requests |
| **ui** | 8002/9002 | `/ui` | `com.fincity.saas` | UI server, document management, CDN integration |
| **files** | 8004 | `/files` | `com.modlix.saas` | File storage/management, S3/Cloudflare integration |
| **message** | 8005 | `/message` | `com.fincity.saas` | Internal messaging service |
| **notification** | 8006 | `/notification` | `com.modlix.saas` | Notification delivery (email, SMS, push) |
| **entity-collector** | 8007 | `/entity-collector` | `com.fincity.saas` | Entity data collection and aggregation |
| **entity-processor** | 8008 | `/entity-processor` | `com.fincity.saas` | Processes collected entity data |
| **multi** | 8009 | `/multi` | `com.fincity.saas` | Multi-tenancy and shared services |
| **eureka** | 9999 | `/eureka` | `com.fincity.saas` | Service discovery (Spring Cloud Eureka) |
| **config** | 8888 | `/config` | `com.fincity.saas` | Centralized configuration (Spring Cloud Config) |

**Blue-green ports**: `ui` runs on 8002 (blue) / 9002 (green) for zero-downtime deployments.

## Shared Commons Libraries

The project uses two generations of shared libraries. Gen2 (`com.modlix.saas`) is the newer version used by `files` and `notification`.

### Generation 1 (`com.fincity.saas`)

| Library | Module | Purpose |
|---------|--------|---------|
| **commons** | `/commons` | Core utilities, caching (Caffeine/Redis), Feign clients, DTOs |
| **commons-core** | `/commons-core` | Business logic, PDF generation, Freemarker, AI integrations |
| **commons-jooq** | `/commons-jooq` | JOOQ base classes: `AbstractJOOQDataService`, `AbstractDAO`, controllers |
| **commons-mongo** | `/commons-mongo` | MongoDB reactive utilities |
| **commons-mq** | `/commons-mq` | RabbitMQ event creation and consumption utilities |
| **commons-security** | `/commons-security` | JWT handling, `SecurityContextUtil`, `ContextAuthentication` |

### Generation 2 (`com.modlix.saas`)

| Library | Module | Purpose |
|---------|--------|---------|
| **commons2** | `/commons2` | Modern commons replacement (Spring Boot 3.4.9) |
| **commons2-jooq** | `/commons2-jooq` | JOOQ utilities for modlix services |
| **commons2-security** | `/commons2-security` | Security utilities for modlix services |
| **commons2-mq** | `/commons2-mq` | Message queue utilities for modlix services |

### External Shared Libraries

| Library | Module | Purpose |
|---------|--------|---------|
| **reactor-flatmap-util** | `/reactor-flatmap-util` | `FlatMapUtil` — reactive Mono chaining utility |
| **kirun-java** | External (`4.3.0`) | KIRun no-code runtime execution engine |

## Inter-Service Communication

### Feign Clients (Synchronous HTTP)
Services call each other via Spring Cloud OpenFeign clients registered through Eureka:
- Controllers expose `/internal` endpoints for inter-service calls
- Feign client interfaces live in each service's `feign/` package

### RabbitMQ Events (Asynchronous)
Services publish events via `EventCreationService` from `commons-mq`:
- Events carry: `eventName`, `clientCode`, `appCode`, `data` map, `authentication`
- Exchange: `events` with routing keys `events1`, `events2`, `events3` (round-robin)
- Consumer services listen on configured queues

### Eureka Service Discovery
- All services register with Eureka at port 9999
- Gateway routes requests to services by their Eureka-registered names
- Health checks at `/actuator/health`

## Database Architecture

| Database | Driver | Services | Purpose |
|----------|--------|----------|---------|
| **MySQL** | R2DBC (reactive) | core, security, files, message, notification, entity-*, multi | Primary relational data |
| **MongoDB** | Reactive Spring Data | core, ui | Document storage (pages, definitions) |
| **Redis** | Lettuce | All services | Distributed caching |

Each service owns its own MySQL schema (e.g., `security`, `core`, `files`). There is no shared database between services.

### Schema Management
- **Flyway** manages migrations per service
- Migration files: `src/main/resources/db/migration/Vn__Description.sql`
- **JOOQ** generates type-safe query classes from the MySQL schema

## Standard Module Directory Layout

Every microservice follows this package structure:

```
{module}/
├── src/main/java/com/{groupId}/{module}/
│   ├── {Module}Application.java        # @SpringBootApplication entry point
│   ├── configuration/                  # Spring @Configuration beans
│   ├── controller/                     # REST @RestController endpoints
│   ├── service/                        # Business logic @Service classes
│   ├── dao/                            # Data access (@Service with JOOQ DSL)
│   ├── jooq/                           # JOOQ generated code (do not edit)
│   ├── model/                          # Request/response models
│   ├── dto/                            # Data transfer objects (map to DB tables)
│   ├── enums/                          # Enumeration types
│   ├── util/                           # Utility classes
│   ├── feign/                          # Feign client interfaces
│   └── mq/                            # RabbitMQ event handlers
├── src/main/resources/
│   ├── application.yml                 # Service configuration
│   ├── messages_en.properties          # i18n error messages
│   └── db/migration/                   # Flyway SQL migrations
├── src/test/java/                      # Tests mirror main package structure
├── pom.xml                             # Maven build configuration
└── Dockerfile                          # Container image definition
```

## Class Hierarchy

The framework provides base classes that all services extend:

```
AbstractDTO<I, U>
└── AbstractUpdatableDTO<I, U>          # Adds updatedAt, updatedBy
    └── Client, User, App, ...          # Concrete DTOs

AbstractDAO<R, I, D>
└── AbstractUpdatableDAO<R, I, D>       # Adds update operations
    └── ClientDAO, UserDAO, ...         # Concrete DAOs

AbstractJOOQDataService<R, I, D, O>     # CRUD with Mono/Flux returns
└── AbstractJOOQUpdatableDataService    # Adds update() method
    └── AbstractSecurityUpdatableDataService  # Adds SOX audit logging
        └── ClientService, UserService, ...   # Concrete services

AbstractJOOQDataController<R, I, D, O, S>
└── AbstractJOOQUpdatableDataController # Adds PUT endpoint
    └── ClientController, UserController, ... # Concrete controllers
```

## Configuration Flow

```
Git Repository (oci-config)
        |
   Config Server (8888)
        |
   Service Bootstrap
        |
   configfiles/{service}.yml  +  application-default.yml (local overrides)
```

- Services pull configuration from the Config Server on startup
- Local development uses `configfiles/application-default.yml` for overrides
- Environment profiles: `ocidev`, `ocistage`, `ociprod`

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.1 / 3.4.9 |
| Cloud | Spring Cloud | 2024.0.0-RC1 |
| Reactive | Project Reactor | (managed by Spring Boot) |
| Database ORM | JOOQ | 3.20.3 |
| Migrations | Flyway | (managed by Spring Boot) |
| Caching | Caffeine + Redis (Lettuce) | (managed by Spring Boot) |
| Messaging | RabbitMQ (Spring AMQP) | (managed by Spring Boot) |
| Service Discovery | Eureka | (managed by Spring Cloud) |
| API Gateway | Spring Cloud Gateway | (managed by Spring Cloud) |
| JWT | JJWT | 0.11.5 |
| Testing | JUnit 5, Mockito 5.21, Testcontainers 1.21.4 | |
| Build | Maven | 3.x |
| Containerization | Docker + Jib | 3.3.0 |
