# notification

Notification microservice for managing user notification preferences and delivery. Gen2 (imperative/servlet) service.

**Port:** 8006

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `notification` |
| version | `1.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.9`

> **Note:** Gen2 module using Spring Boot 3.4.9.

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.2 |
| `saas-commons2-version` | 2.0.0 |
| `saas-commons2-jooq-version` | 2.0.0 |
| `saas-commons2-security-version` | 2.0.0 |
| `saas-commons2-mq-version` | 2.0.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework
- `spring-boot-starter-data-jpa` — JPA
- `spring-boot-starter-jooq` — Spring Boot JOOQ integration
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-freemarker` — FreeMarker template engine
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery
- `spring-cloud-starter-openfeign` — Standard OpenFeign

### Database
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j:9.4.0` — MySQL JDBC connector
- `HikariCP` — JDBC connection pooling

### Internal Commons (Gen2 — all four)
- `commons2:2.0.0` — Base Gen2 utilities
- `commons2-jooq:2.0.0` — Gen2 JOOQ data access
- `commons2-security:2.0.0` — Gen2 auth/JWT
- `commons2-mq:2.0.0` — Gen2 RabbitMQ events

### Email
- `jakarta.mail:2.0.1` — Email sending

### Monitoring
- `micrometer-registry-prometheus` (runtime) — Prometheus metrics

### Testing
- `spring-boot-starter-test`
- `spring-security-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image target (has copy-paste error) |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation from the `notification` MySQL schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/notification`
- **Schema:** `notification`
- **Table pattern:** `notification_.*`
- **Forced types:** `JSONMysqlMapConvertor` for `PREFERENCE` JSON column
- **Target package:** `com.modlix.saas.notification.jooq`

## What This Service Manages

- **Notification preferences** — User notification settings
- **Notification delivery** — Sending notifications via configured channels
- **Email notifications** — Jakarta Mail integration
- **Template rendering** — FreeMarker-based notification templates
- **Event consumption** — Listens for events via RabbitMQ (commons2-mq)

## Notable

- **Gen2 service** — Uses `com.modlix.saas` groupId, `commons2` libraries, servlet-based
- **Uses all four Gen2 commons** — commons2, commons2-jooq, commons2-security, commons2-mq
- **FreeMarker starter** — Uses `spring-boot-starter-freemarker` (Spring Boot managed) instead of standalone FreeMarker
- **Prometheus runtime scope** — Micrometer is scoped as `runtime` (not compile)
- **Jib target error** — Jib image target says `entity-processor` (copy-paste mistake)
