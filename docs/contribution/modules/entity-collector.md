# entity-collector

Entity collector microservice for ingesting and collecting external entity data into the platform.

**Port:** 8007

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `entity-collector` |
| version | `1.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `nocode-kirun-version` | 4.3.0 |
| `saas-commons-version` | 2.0.0 |
| `saas-commons-jooq-version` | 2.0.0 |
| `saas-commons-security-version` | 2.0.0 |
| `nocode-flatmap-util-version` | 1.3.0 |
| `spotless.version` | 2.44.3 |
| `palantir-java.format.version` | 2.61.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-data-r2dbc` — Reactive MySQL
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-boot-starter-aop` — AOP
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `r2dbc-mysql` — Reactive MySQL driver (implicit via R2DBC)
- `spring-jdbc` — JDBC for Flyway
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq` (managed) — JOOQ runtime
- `jooq-codegen-maven:3.18.3` — JOOQ code generation (oldest version in codebase)

### Security / JWT
- `jjwt-api:0.11.5`
- `jjwt-impl:0.11.5` (runtime)
- `jjwt-jackson:0.11.5` (runtime)

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `kirun-java:4.3.0` — KIRun runtime engine

### Caching
- `caffeine` — In-process caching

### Networking
- `reactor-netty` — Netty HTTP client
- `netty-resolver-dns-native-macos` (osx-aarch_64)

### Monitoring
- `micrometer-registry-prometheus` — Prometheus metrics

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/entity-collector` |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation from the `entity_collector` MySQL schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/entity_collector`
- **Schema:** `entity_collector`
- **Table pattern:** `.*` (all tables)
- **Forced types:** `JSONMysqlMapConvertor` for `INCOMING_ENTITY_DATA|OUTGOING_ENTITY_DATA` JSON columns
- **Target package:** `com.fincity.saas.entity.collector.jooq`

### `spotless`

Code formatting with Spotless.

- **Java:** Palantir Java Format 2.61.0, excludes JOOQ generated code
- **POM:** sortPom with dependency ordering
- **YAML:** Jackson-based formatting

## What This Service Manages

- **Entity ingestion** — Collects external entity data (incoming entities)
- **Data transformation** — Transforms incoming entity data for processing
- **Entity routing** — Routes collected entities to the entity-processor

## Notable

- **JOOQ codegen 3.18.3** — Oldest JOOQ code generation version in the codebase
- **JWT included directly** — Includes jjwt libraries directly (unlike services that get it transitively from commons-security)
- **No commons-mq** — Does not use RabbitMQ for event publishing
- **No reactor-flatmap-util in deps** — Listed in properties but not in dependencies
- **Caffeine without cache starter** — Has caffeine dependency but no explicit `spring-boot-starter-cache`
