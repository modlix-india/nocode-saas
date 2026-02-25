# commons2

Shared utilities, caching, and DTOs for Gen2 (imperative/servlet) microservices. This is the imperative counterpart to [commons](commons.md).

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `commons2` |
| version | `2.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.9`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.2 |
| `nocode-kirun-version` | 4.3.0 |
| `xlsx-streamer.version` | 2.1.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework (not WebFlux)
- `spring-boot-starter-cache` — Caching abstraction
- `spring-boot-starter-aop` — Aspect-oriented programming
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Spring Cloud Config client
- `spring-cloud-starter-openfeign` — OpenFeign declarative HTTP clients

### Caching
- `caffeine` — High-performance in-process caching
- `lettuce-core` — Redis client

### Utilities
- `kirun-java:4.3.0` — KIRun runtime engine
- `xlsx-streamer:2.1.0` — Streaming XLSX reader
- `gson` — Google JSON library
- `javax.annotation-api:1.3.2` — Java annotation support
- `lombok` — Boilerplate reduction

### Testing
- `junit-jupiter-engine`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None.

## Key Differences from commons (Gen1)

| Aspect | commons (Gen1) | commons2 (Gen2) |
|--------|----------------|------------------|
| GroupId | `com.fincity.saas` | `com.modlix.saas` |
| Web framework | `spring-boot-starter-webflux` | `spring-boot-starter-web` |
| Spring Boot | 3.4.1 | 3.4.9 |
| Spring Cloud | 2024.0.0-RC1 | 2024.0.2 |
| Reactor | Yes (reactor-core, reactor-test) | No |
| FlatMapUtil | Yes | No |
| Feign | Reactive (feign-reactor-*) | Standard (OpenFeign) |
| JSON | Jackson (via Spring) | Gson + Jackson |

## Relationship to Other Modules

```
commons2  <── commons2-jooq (adds JOOQ/JDBC layer)
          <── commons2-security (adds JWT/auth layer)
          <── commons2-mq (adds RabbitMQ layer)
```

Used by Gen2 services: [files](files.md), [notification](notification.md).
