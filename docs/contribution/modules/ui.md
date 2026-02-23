# ui

UI server that serves the no-code platform frontend and manages UI-related backend operations like page definitions, themes, and application store integration.

**Port:** 8002 (blue) / 9002 (green)

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `ui` |
| version | `1.1.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `saas-commons-version` | 2.0.0 |
| `saas-commons-mongo-version` | 2.0.0 |
| `feign-reactor.version` | 4.2.1 |
| `jooq.version` | 3.20.3 |
| `spring-boot-admin.version` | 3.0.2 |

## Dependencies

### Spring Framework
- `spring-boot-starter-data-jpa` — JPA for blocking database operations
- `spring-boot-starter-data-mongodb-reactive` — Reactive MongoDB for UI documents
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-mongo:2.0.0` — Reactive MongoDB data access

### Reactive Feign
- `feign-reactor-spring-cloud-starter:4.2.1`
- `feign-reactor-spring-configuration:4.2.1`

### Cryptography (Apple App Store Connect)
- `bcprov-jdk18on:1.82` — BouncyCastle provider
- `bcpkix-jdk18on:1.82` — BouncyCastle PKIX

### JWT (Apple App Store Connect API)
- `jjwt-api:0.11.5`
- `jjwt-impl:0.11.5` (runtime)
- `jjwt-jackson:0.11.5` (runtime)

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
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/ui` |

## Profiles

None.

## What This Service Manages

- **Page definitions** — No-code page structure and component trees (stored in MongoDB)
- **Themes** — Application styling and theme definitions
- **Application structure** — UI application configuration and routing
- **App Store integration** — Apple App Store Connect API via BouncyCastle + JWT

## Notable

- **MongoDB-centric** — Primary data store is MongoDB (reactive), uses JPA for some cross-service queries
- **No JOOQ** — No JOOQ code generation profile (uses MongoDB for document storage)
- **No Flyway** — No MySQL migrations
- **BouncyCastle** — Cryptographic library for Apple App Store Connect API JWT signing
- **No `commons-jooq`** — Only service that uses `commons-mongo` without `commons-jooq`
