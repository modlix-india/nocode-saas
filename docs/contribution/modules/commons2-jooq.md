# commons2-jooq

JOOQ-based imperative data access layer for Gen2 microservices. This is the imperative counterpart to [commons-jooq](commons-jooq.md), using JDBC/HikariCP instead of R2DBC.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `commons2-jooq` |
| version | `2.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.9`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.2 |
| `saas-commons2-version` | 2.0.0 |
| `jooq.version` | 3.20.3 |

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework
- `spring-data-commons` — Common Spring Data abstractions
- `spring-boot-starter-data-jdbc` — JDBC data access
- `spring-cloud-starter-config` — Config client

### Database
- `HikariCP` — JDBC connection pooling
- `jooq:3.20.3` — Type-safe SQL query builder

### Internal
- `commons2:2.0.0` — Base Gen2 commons library

### Testing
- `spring-boot-starter-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None.

## Key Differences from commons-jooq (Gen1)

| Aspect | commons-jooq (Gen1) | commons2-jooq (Gen2) |
|--------|----------------------|----------------------|
| GroupId | `com.fincity.saas` | `com.modlix.saas` |
| Web framework | WebFlux | Servlet |
| Data access | R2DBC (reactive) | JDBC/HikariCP (blocking) |
| Connection pool | r2dbc-pool | HikariCP |
| Spring Boot | 3.4.1 | 3.4.9 |
| Reactor | Yes | No |
| KIRun | Yes | No |

## Relationship to Other Modules

```
commons2 ──> commons2-jooq ──> files, notification
```

Used by Gen2 services that need MySQL database access.
