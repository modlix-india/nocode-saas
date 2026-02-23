# core

Core business logic and data operations microservice. Handles the no-code platform's data storage, function execution, schema management, and application runtime.

**Port:** 8001 (internal), 8005/9005 (blue-green via gateway)

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `core` |
| version | `1.1.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `saas-commons-core-version` | 1.0.0 |
| `feign-reactor.version` | 4.2.1 |
| `jooq.version` | 3.20.3 |
| `openhtml.version` | 1.0.10 |

## Dependencies

### Spring Framework
- `spring-boot-starter-data-jpa` — JPA (for some blocking operations)
- `spring-boot-starter-data-r2dbc` — Reactive MySQL
- `spring-boot-starter-data-mongodb-reactive` — Reactive MongoDB
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `r2dbc-mysql` — Reactive MySQL driver
- `spring-jdbc` — JDBC for Flyway
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq-codegen-maven:3.20.3` — JOOQ code generation

### Internal Commons (ALL six)
- `commons:2.0.0` — Base utilities
- `commons-core:1.0.0` — PDF, email, AI, templates
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `commons-mongo:2.0.0` — MongoDB access
- `commons-mq:2.0.0` — RabbitMQ events

### Reactive Feign
- `feign-reactor-spring-cloud-starter:4.2.1`
- `feign-reactor-spring-configuration:4.2.1`

### Email
- `jakarta.mail:2.0.1`
- `javax.activation:1.2.0`
- `jakarta.activation:2.0.1`

### Messaging
- `spring-rabbit` — RabbitMQ direct integration

### Testing
- `spring-boot-starter-test`
- `reactor-test`
- `spring-security-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR (excludes Lombok) |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/core` |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

None declared in pom.xml. JOOQ generation for core is handled via the `commons-core` module's `jooq` profile.

## What This Service Manages

- **Application data storage** — CRUD operations for no-code app data
- **Schema management** — Dynamic schema definitions for no-code applications
- **Function execution** — KIRun-based function execution engine
- **PDF generation** — Via `commons-core` OpenHTMLToPDF integration
- **Email sending** — Via `commons-core` Jakarta Mail
- **Event publishing** — RabbitMQ events for data changes

## Notable

- **Heaviest service** — Depends on all six commons libraries (including `commons-core`)
- **Dual database** — Uses both MySQL (R2DBC + JPA) and MongoDB (reactive)
- **Spring Boot Maven plugin** — Explicitly excludes Lombok from the packaged JAR
- **No JOOQ profile** — JOOQ code generation for core's schema is in `commons-core`, not here
