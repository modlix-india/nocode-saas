# commons2-security

JWT authentication and authorization for Gen2 (imperative/servlet) microservices. This is the imperative counterpart to [commons-security](commons-security.md).

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `commons2-security` |
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

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework
- `spring-boot-starter-security` — Spring Security
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-openfeign` — Standard OpenFeign

### Security / JWT
- `jjwt-api:0.11.5` — JWT API
- `jjwt-impl:0.11.5` (runtime) — JWT implementation
- `jjwt-jackson:0.11.5` (runtime) — JWT Jackson serialization

### Internal
- `commons2:2.0.0` — Base Gen2 commons library

### Testing
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

## Key Differences from commons-security (Gen1)

| Aspect | commons-security (Gen1) | commons2-security (Gen2) |
|--------|-------------------------|--------------------------|
| GroupId | `com.fincity.saas` | `com.modlix.saas` |
| Web framework | WebFlux | Servlet |
| Spring Boot | 3.4.1 | 3.4.9 |
| Reactor | Yes | No |
| FlatMapUtil | Yes | No |
| Feign | Reactive (feign-reactor-*) | Standard (OpenFeign) |
| KIRun | Yes | No |
| JWT version | 0.11.5 | 0.11.5 (same) |

## Relationship to Other Modules

```
commons2 ──> commons2-security ──> commons2-mq
                                ──> files, notification
```

Used by Gen2 services that need authentication and authorization.
