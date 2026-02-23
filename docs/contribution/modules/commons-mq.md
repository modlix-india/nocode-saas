# commons-mq

RabbitMQ event publishing and consumption utilities for Gen1 (reactive) microservices.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons-mq` |
| version | `2.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `feign-reactor.version` | 4.2.1 |
| `saas-commons-version` | 2.0.0 |
| `saas-commons-security-version` | 2.0.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-amqp` — RabbitMQ / AMQP messaging
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-openfeign` — OpenFeign

### Messaging & Serialization
- `jackson-dataformat-xml` — XML serialization for messages

### Caching & Networking
- `lettuce-core` — Redis reactive client
- `netty-resolver-dns-native-macos` (osx-aarch_64) — macOS ARM DNS resolver

### Reactive
- `reactor-core`
- `reactor-test` (test)

### Internal
- `commons:2.0.0` — Base commons library
- `commons-security:2.0.0` — Security context (for auth in events)

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None.

## What This Module Provides

- **`EventCreationService`** — Publishes events to RabbitMQ with round-robin routing across queues
- **`EventQueObject`** — Event payload structure (eventName, clientCode, appCode, data map, auth context)
- **`EventNames`** — Constants for standard event names (e.g., `CLIENT_CREATED`, `USER_CREATED`, `APP_CREATED`)
- **RabbitMQ configuration** — Exchange, queue, and binding setup
- **Auth context propagation** — Carries `ContextAuthentication` data within event messages

## Relationship to Other Modules

```
commons ──> commons-security ──> commons-mq ──> security, core, commons-core
```

`commons-mq` depends on both `commons` and `commons-security` because events carry authentication context.

## Gen2 Counterpart

[commons2-mq](commons2-mq.md) — Imperative version using `spring-boot-starter-web` instead of WebFlux.
