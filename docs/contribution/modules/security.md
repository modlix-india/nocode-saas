# security

Authentication, authorization, and identity management microservice. Manages users, clients, roles, permissions, policies, and JWT token issuance.

**Port:** 8003 (via gateway mapping)

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `security` |
| version | `1.1.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `nocode-kirun-version` | 4.3.0 |
| `jooq.version` | 3.20.3 |
| `nocode-flatmap-util-version` | 1.3.0 |
| `byte-buddy.version` | 1.17.5 |
| `mockito.version` | 5.21.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-boot-starter-aop` — Aspect-oriented programming
- `spring-boot-starter-cache` — Caching abstraction
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Database
- `r2dbc-mysql` — Reactive MySQL driver
- `spring-jdbc` — JDBC for Flyway migrations
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq-codegen-maven:3.20.3` — JOOQ code generation
- `jooq-jackson-extensions:3.20.3` — JOOQ Jackson integration

### Security / JWT
- `jjwt-api:0.11.5` — JWT API
- `jjwt-impl:0.11.5` (runtime) — JWT implementation
- `jjwt-jackson:0.11.5` (runtime) — JWT Jackson serialization

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access layer
- `commons-security:2.0.0` — Security context utilities
- `commons-mq:2.0.0` — RabbitMQ events
- `reactor-flatmap-util:1.3.0` — Reactive chaining
- `kirun-java:4.3.0` — KIRun runtime engine

### Caching
- `caffeine` — In-process caching

### Networking
- `reactor-netty` — Netty-based HTTP client
- `netty-resolver-dns-native-macos` (osx-aarch_64)

### SSL / Certificates
- `acme4j-client:3.0.0` — ACME protocol client for Let's Encrypt certificates

### Messaging
- `spring-rabbit` — RabbitMQ direct integration

### Reactive Debugging
- `blockhound:1.0.6.RELEASE` — Detects blocking calls in reactive threads

### Testing
- `spring-boot-starter-test`
- `reactor-test`
- `spring-security-test`
- `testcontainers:mysql:1.21.4` — MySQL Testcontainers
- `testcontainers:junit-jupiter:1.21.4` — Testcontainers JUnit 5
- `testcontainers:r2dbc:1.21.4` — R2DBC Testcontainers

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)
- `org.testcontainers:testcontainers-bom:1.21.4` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/security` |
| `jacoco-maven-plugin` | 0.8.12 | Code coverage (excludes JOOQ generated code) |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation from the `security` MySQL schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/security`
- **Schema:** `security`
- **Table pattern:** `security_.*`
- **Excluded tables:** `security_package`, `security_package_role`, `security_client_package`, `security_user_role`, `security_role_permission`, `security_user_role_permission`, `security_app_reg_package`, `security_app_reg_user_role`, `security_org_structure`, `security_role`
- **Forced types:** `JSONMysqlMapConvertor` for `ARRANGEMENT|TOKEN_METADATA|USER_METADATA|REQUEST_PARAM|PAYMENT_RESPONSE|PAYMENT_GATEWAY_DETAILS` JSON columns
- **Target package:** `com.fincity.security.jooq`

## What This Service Manages

- **Clients** — Multi-tenant client hierarchy (SYSTEM > BUS > INDV)
- **Users** — User accounts, credentials, and profiles
- **Roles** — Role definitions and role assignments
- **Permissions** — Fine-grained permission definitions
- **Policies** — Policy-based access control
- **Apps** — Application registrations
- **Client hierarchy** — Parent-child client relationships for multi-tenancy
- **Client managers** — Manager relationships between users and clients
- **Token management** — JWT issuance, refresh, and revocation
- **SSL certificates** — ACME-based certificate management

## Notable

- **Only service with Testcontainers** — Has full integration test infrastructure
- **Only service with JaCoCo** — Code coverage reporting (excludes JOOQ generated code)
- **BlockHound** — Included for detecting accidental blocking calls in reactive chains
- **Most mature test suite** — Includes `AbstractServiceUnitTest`, `AbstractIntegrationTest`, `AbstractControllerTest`, `TestDataFactory`
