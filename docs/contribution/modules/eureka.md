# eureka

Service discovery server using Netflix Eureka. All microservices register here and use it for service-to-service routing.

**Port:** 9999

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity` |
| artifactId | `eureka` |
| version | `1.1.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `spring-boot-admin.version` | 3.0.2 |

## Dependencies

### Spring Cloud Eureka
- `spring-cloud-starter-netflix-eureka-server` — Eureka Server
- `spring-cloud-starter-config` — Config client
- `spring-cloud-config-client` — Config client (explicit)

### Monitoring
- `micrometer-registry-prometheus` — Prometheus metrics
- `spring-boot-starter-actuator` — Health checks and metrics

### XML Binding (required for Eureka)
- `jaxb-api:2.3.1`
- `jaxb-impl:2.1`
- `javax.activation:1.1`

### Testing
- `spring-boot-starter-test`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/eureka` |

## Profiles

None.

## What This Service Does

- **Service registry** — Maintains a registry of all running microservice instances
- **Service discovery** — Allows services to discover and communicate with each other
- **Health monitoring** — Tracks service health via heartbeat mechanism
- **Load balancing support** — Provides instance lists for client-side load balancing

## How Services Register

All microservices include `spring-cloud-starter-netflix-eureka-client` and register on startup. The gateway uses Eureka to resolve service routes dynamically.

## Startup Order

Eureka must start **after** the Config Server and **before** all application services:

```
config → eureka → gateway → (security, core, ui, files, ...)
```

## Notable

- **Infrastructure service** — Not a business service; required for service discovery
- **No database** — No MySQL, MongoDB, JOOQ, or Flyway
- **No commons dependencies** — Does not depend on any commons libraries
- **GroupId:** `com.fincity` (not `com.fincity.saas`)
- **JAXB dependencies** — Required for Eureka's XML-based communication protocol
- **`spring-boot-admin.version` defined but unused** — Property exists but Spring Boot Admin is not in dependencies
- **Simplest service** alongside config — Minimal dependency footprint
