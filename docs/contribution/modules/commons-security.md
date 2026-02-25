# commons-security

JWT authentication, authorization, and security context utilities for Gen1 (reactive) microservices.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons-security` |
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
| `saas-commons-version` | 2.0.0 |
| `feign-reactor.version` | 4.2.1 |
| `nocode-flatmap-util-version` | 1.3.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-security` — Spring Security
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-openfeign` — OpenFeign

### Security / JWT
- `jjwt-api:0.11.5` — JWT API
- `jjwt-impl:0.11.5` (runtime) — JWT implementation
- `jjwt-jackson:0.11.5` (runtime) — JWT Jackson serialization

### Reactive
- `reactor-core`
- `reactor-test` (test)
- `reactor-flatmap-util:1.3.0` — Custom FlatMapUtil
- `feign-reactor-spring-cloud-starter:4.2.1` — Reactive Feign

### Internal
- `commons:2.0.0` — Base commons library
- `kirun-java:4.3.0` — KIRun runtime engine

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

This module handles **authentication and authorization** across all Gen1 services:

- **`SecurityContextUtil`** — Reactive utility to access the authenticated user from the Reactor context
  - `getUsersContextAuthentication()` — Get `ContextAuthentication`
  - `getUsersContextUser()` — Get `ContextUser`
  - `getUsersClientId()` — Get current client ID
  - `hasAuthority()` — Check if user has a specific authority
- **`ContextAuthentication`** — Custom `Authentication` implementation carrying user, client, and authority data
- **`ContextUser`** — User details within the security context
- **JWT filter chain** — Extracts and validates JWT tokens on incoming requests
- **`@PreAuthorize` support** — Enables method-level security via Spring Security annotations

## Relationship to Other Modules

```
commons ──> commons-security ──> commons-mq (adds auth context to events)
                              ──> commons-mongo (adds tenant-aware queries)
                              ──> All Gen1 services
```

## Gen2 Counterpart

[commons2-security](commons2-security.md) — Imperative version without Reactor dependencies.
