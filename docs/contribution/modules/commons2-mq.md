# commons2-mq

RabbitMQ event publishing and consumption utilities for Gen2 (imperative/servlet) microservices. This is the imperative counterpart to [commons-mq](commons-mq.md).

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `commons2-mq` |
| version | `2.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.9`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.2 |
| `saas-commons2-version` | 2.0.0 |
| `saas-commons2-security-version` | 2.0.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework
- `spring-boot-starter-amqp` — RabbitMQ / AMQP messaging
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-openfeign` — Standard OpenFeign

### Messaging & Serialization
- `jackson-dataformat-xml` — XML serialization for messages

### Caching & Networking
- `lettuce-core` — Redis client
- `netty-resolver-dns-native-macos` (osx-aarch_64) — macOS ARM DNS resolver

### Internal
- `commons2:2.0.0` — Base Gen2 commons library
- `commons2-security:2.0.0` — Gen2 security context

### Testing
- `junit-jupiter-engine`
- `spring-boot-starter-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

None.

## Key Differences from commons-mq (Gen1)

| Aspect | commons-mq (Gen1) | commons2-mq (Gen2) |
|--------|--------------------|--------------------|
| GroupId | `com.fincity.saas` | `com.modlix.saas` |
| Web framework | WebFlux | Servlet |
| Spring Boot | 3.4.1 | 3.4.9 |
| Reactor | Yes | No |
| Internal deps | commons + commons-security | commons2 + commons2-security |

## Relationship to Other Modules

```
commons2 ──> commons2-security ──> commons2-mq ──> notification
```

Used by Gen2 services that need RabbitMQ event publishing/consumption.
