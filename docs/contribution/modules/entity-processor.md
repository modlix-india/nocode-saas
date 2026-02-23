# entity-processor

Entity processor microservice for managing RDBMS entities, CRM workflows, ticketing, and campaign management.

**Port:** 8008

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `entity-processor` |
| version | `1.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `package.o.spring-cloud.version` | 2024.0.0-RC1 |
| `package.i.nocode-kirun-version` | 3.14.1 |
| `package.o.jooq.version` | 3.20.5 |
| `package.i.nocode-flatmap-util-version` | 1.3.0 |
| `database.schema.name` | entity_processor |
| `package.o.spotless.version` | 2.45.0 |
| `package.o.palantir-java.format.version` | 2.70.0 |

> **Note:** Uses the same namespaced property convention as [message](message.md).

## Dependencies

### Spring Framework
- `spring-boot-starter` — Base starter
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-data-jpa` — JPA for some operations
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery
- `spring-cloud-starter-openfeign` — Standard OpenFeign

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `r2dbc-mysql` — Reactive MySQL driver
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq:3.20.5` + `jooq-codegen-maven:3.20.5` — JOOQ

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `reactor-flatmap-util:1.3.0` — Reactive chaining
- `kirun-java:3.14.1` — KIRun runtime (older version)

### Communication
- `libphonenumber:9.0.9` — Phone number parsing/validation
- `jakarta.mail:2.0.2` — Email sending

### Monitoring
- `micrometer-registry-prometheus` — Prometheus metrics

### Testing
- `spring-boot-starter-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/entity-processor` |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation with extensive forced types for CRM/ticketing domain.

- **JDBC URL:** `jdbc:mysql://localhost:3306/entity_processor`
- **Schema:** `entity_processor`
- **Table pattern:** `entity_processor_.*`
- **Excluded tables:** `entity_processor_view_ticket_stage_dates`
- **Target package:** `com.fincity.saas.entity.processor.jooq`
- **Forced types** (extensive):
  - `BOOLEAN` from `TINYINT`
  - `java.util.Map` via `JSONtoClassConverter` for `OBJECT_DATA|META_DATA`
  - Custom types: `FileDetail`, `AbstractCondition`
  - Enum converters: `Platform`, `AssignmentType`, `StageType`, `DistributionType`, `ProductTemplateType`, `TaskPriority`, `ContentEntitySeries`, `EntitySeries`, `ActivityAction`, `CampaignPlatform`, `PartnerVerificationStatus`
- **Uses `com.mysql:mysql-connector-j:9.2.0`** (newer connector) and **`jakarta.xml.bind:jakarta.xml.bind-api:4.0.2`** (Jakarta namespace)

### `spotless`

Code formatting with Spotless.

- **Java:** Palantir Java Format 2.70.0, excludes JOOQ generated code
- **POM:** sortPom with dependency ordering
- **YAML:** Jackson-based formatting

## What This Service Manages

- **CRM entities** — Customer relationship management data
- **Ticketing system** — Ticket stages, assignment, distribution
- **Campaign management** — Marketing campaigns across platforms
- **Product templates** — Template-based product definitions
- **Task management** — Task priority and assignment
- **Partner management** — Partner verification workflows
- **Activity tracking** — Activity action logging

## Notable

- **JOOQ 3.20.5** — Slightly newer than other services (3.20.3)
- **kirun-java 3.14.1** — Older version (same as message)
- **Dual data access** — Uses both JPA and R2DBC/JOOQ
- **Jakarta XML Bind** — Uses newer Jakarta namespace (4.0.2) in JOOQ profile
- **Most domain-rich JOOQ config** — Extensive enum converters for CRM/ticketing domain
- **Standard OpenFeign** — Uses standard OpenFeign (not reactive feign) despite being a WebFlux service
