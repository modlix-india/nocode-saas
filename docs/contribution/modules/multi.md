# multi

Multi-tenancy management microservice. Handles multi-tenant data operations and tenant-specific configurations.

**Port:** 8009

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `multi` |
| version | `2.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `nocode-kirun-version` | 4.3.0 |
| `nocode-flatmap-util-version` | 1.3.0 |
| `jooq.version` | 3.20.3 |
| `img-scalr.version` | 4.2 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-data-r2dbc` — Reactive MySQL
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-boot-starter-aop` — AOP
- `spring-boot-starter-cache` — Caching
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `r2dbc-mysql` — Reactive MySQL driver
- `spring-jdbc` — JDBC for Flyway
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq-codegen-maven:3.20.3` — JOOQ code generation

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `reactor-flatmap-util:1.3.0` — Reactive chaining
- `kirun-java:4.3.0` — KIRun runtime engine

### Image Processing
- `imgscalr-lib:4.2` — Image scaling

### Caching
- `caffeine` — In-process caching

### Networking
- `reactor-netty`
- `netty-resolver-dns-native-macos` (osx-aarch_64)

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
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/multi` |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation from the `multi` MySQL schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/multi`
- **Schema:** `multi`
- **Table pattern:** `multi_.*`
- **Target package:** `com.fincity.saas.multi.jooq`
- No forced types or excludes

## What This Service Manages

- **Multi-tenant data operations** — Tenant-specific data isolation and management
- **Tenant configurations** — Per-tenant settings and customizations
- **Image processing** — Image scaling capabilities (imgscalr)

## Notable

- **Version 2.0.0** — Higher version than most services (1.0.0 or 1.1.0)
- **Pure reactive** — Uses R2DBC only (no JPA dependency)
- **No commons-mq** — Does not use RabbitMQ for event publishing
- **Similar to files** — Has image processing capability (imgscalr) and caching (caffeine)
