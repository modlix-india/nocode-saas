# commons

Shared utilities, caching, DTOs, and reactive infrastructure used by all Gen1 (reactive/WebFlux) microservices.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons` |
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
| `feign-reactor.version` | 4.2.1 |
| `nocode-flatmap-util-version` | 1.3.0 |
| `xlsx-streamer.version` | 2.1.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework (WebFlux)
- `spring-boot-starter-cache` — Caching abstraction
- `spring-boot-starter-aop` — Aspect-oriented programming
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Spring Cloud Config client
- `spring-cloud-starter-openfeign` — OpenFeign declarative HTTP clients

### Reactive & Networking
- `reactor-core` — Project Reactor core
- `reactor-test` (test) — Reactor testing utilities
- `reactor-flatmap-util:1.3.0` — Custom FlatMapUtil for reactive chaining
- `netty-resolver-dns-native-macos` (osx-aarch_64) — macOS ARM DNS resolver

### Reactive Feign
- `feign-reactor-webclient:4.2.1` — Reactive Feign WebClient integration
- `feign-reactor-cloud:4.2.1` — Reactive Feign Spring Cloud integration
- `feign-reactor-spring-configuration:4.2.1` — Reactive Feign auto-configuration

### Caching
- `caffeine` — High-performance in-process caching
- `lettuce-core` — Redis reactive client

### Utilities
- `kirun-java:4.3.0` — KIRun runtime engine
- `xlsx-streamer:2.1.0` — Streaming XLSX reader
- `lombok` — Boilerplate reduction

### Testing
- `junit-jupiter-engine`
- `reactor-test`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None.

## What This Module Provides

This is the **foundational Gen1 commons library**. It provides:

- **CacheService** — Abstraction over Caffeine (local) and Redis (distributed) caching
- **DTOs and base classes** — `AbstractDTO`, `AbstractUpdatableDTO` with `ULong` ID types
- **Reactive Feign clients** — Inter-service communication via reactive HTTP
- **Utility classes** — String, collection, and conversion helpers
- **Error handling** — `MessageResourceService` for localized error messages
- **KIRun integration** — Runtime function execution engine

## Relationship to Other Modules

```
commons  <── commons-jooq (adds JOOQ/R2DBC layer)
         <── commons-security (adds JWT/auth layer)
         <── commons-mongo (adds MongoDB layer)
         <── commons-mq (adds RabbitMQ layer)
         <── commons-core (depends on all five)
```

All Gen1 services transitively depend on this module.

## Gen2 Counterpart

[commons2](commons2.md) — Imperative/servlet version using `spring-boot-starter-web` instead of `spring-boot-starter-webflux`.
