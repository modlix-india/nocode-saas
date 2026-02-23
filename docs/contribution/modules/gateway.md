# gateway

API Gateway that routes external HTTP requests to internal microservices. Handles JWT validation and request routing via Spring Cloud Gateway.

**Port:** 8080

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity` |
| artifactId | `gateway` |
| version | `1.1.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `feign-reactor.version` | 4.2.1 |
| `saas-commons-version` | 2.0.0 |
| `nocode-flatmap-util-version` | 1.3.0 |

## Dependencies

### Spring Cloud Gateway
- `spring-cloud-starter-gateway` — Reactive API Gateway
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery for route resolution
- `spring-cloud-starter-openfeign` — Inter-service HTTP calls

### Monitoring
- `micrometer-registry-prometheus` — Prometheus metrics
- `spring-boot-starter-actuator` — Health checks and metrics

### Internal
- `commons:2.0.0` — Base utilities
- `reactor-flatmap-util:1.3.0` — Reactive chaining

### Networking
- `netty-resolver-dns-native-macos` (osx-aarch_64)

### Testing
- `spring-boot-starter-test`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/gateway` |

## Profiles

None.

## What This Service Does

- **Request routing** — Routes API requests to the appropriate microservice based on path patterns
- **JWT validation** — Validates JWT tokens and populates the security context
- **Service discovery** — Uses Eureka to dynamically resolve service instances
- **Load balancing** — Distributes requests across service instances

## Notable

- **Lightest service** — Only depends on `commons` (not jooq, security, mq, or mongo)
- **No database** — No MySQL, MongoDB, or JOOQ dependencies
- **No Flyway** — No database migrations
- **No maven-compiler-plugin** — Uses Spring Boot parent's default compiler configuration
- **GroupId:** `com.fincity` (not `com.fincity.saas`)
- **Spring Cloud Gateway** is reactive by nature — uses Netty under the hood
