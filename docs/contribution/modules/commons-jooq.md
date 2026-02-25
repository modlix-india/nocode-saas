# commons-jooq

JOOQ-based reactive data access layer for Gen1 microservices. Provides abstract base classes for DAOs, services, and controllers that use JOOQ with R2DBC.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons-jooq` |
| version | `2.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `nocode-kirun-version` | 4.3.0 |
| `saas-commons-version` | 2.0.0 |
| `jooq.version` | 3.20.3 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-data-commons` — Common Spring Data abstractions
- `spring-data-r2dbc` — Reactive relational data access
- `spring-cloud-starter-config` — Config client

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `jooq:3.20.3` — Type-safe SQL query builder

### Internal
- `commons:2.0.0` — Base commons library
- `kirun-java:4.3.0` — KIRun runtime engine

### Reactive
- `reactor-core`
- `reactor-test` (test)

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None. (JOOQ code generation profiles are in the individual service modules, not here.)

## What This Module Provides

This is the **JOOQ data access layer** for Gen1 services. Key classes:

- **`AbstractDAO`** — Base DAO with reactive JOOQ query execution via R2DBC
- **`AbstractUpdatableDAO`** — Adds create/update/delete operations
- **`AbstractJOOQDataService`** — Base service with read operations
- **`AbstractJOOQUpdatableDataService`** — Full CRUD service with caching and SOX audit logging
- **`AbstractJOOQDataController`** — Base REST controller for read endpoints
- **`AbstractJOOQUpdatableDataController`** — Full CRUD REST controller
- **`JSONMysqlMapConvertor`** — JOOQ forced type converter for MySQL JSON columns to `java.util.Map`
- **Type converters** — Custom converters for `ULong`, `LocalDateTime`, and JSON columns

## Relationship to Other Modules

```
commons ──> commons-jooq ──> security, core, multi, message, entity-collector, entity-processor
```

## Gen2 Counterpart

[commons2-jooq](commons2-jooq.md) — Imperative version using JDBC/HikariCP instead of R2DBC.
