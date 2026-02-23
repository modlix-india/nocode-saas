# commons-mongo

Reactive MongoDB data access layer for Gen1 microservices that use MongoDB alongside or instead of MySQL.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons-mongo` |
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
| `saas-commons-security-version` | 2.0.0 |
| `nocode-flatmap-util-version` | 1.3.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-data-mongodb-reactive` — Reactive MongoDB driver and Spring Data integration
- `spring-data-commons` — Common Spring Data abstractions
- `spring-cloud-starter-config` — Config client

### Reactive
- `reactor-core`
- `reactor-test` (test)
- `reactor-flatmap-util:1.3.0` — Custom FlatMapUtil

### Internal
- `commons-security:2.0.0` — Security context (transitively includes `commons`)
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

- **Reactive MongoDB repositories** — Base classes for reactive document access
- **Tenant-aware MongoDB queries** — Filters documents by client hierarchy
- **MongoDB document DTOs** — Base classes for MongoDB documents with audit fields
- **Security integration** — Uses `commons-security` to inject tenant context into queries

## Relationship to Other Modules

```
commons ──> commons-security ──> commons-mongo ──> core, ui
```

Note: `commons-mongo` depends on `commons-security` (not just `commons`), because MongoDB queries need tenant-aware filtering based on the authenticated user's client hierarchy.

## Gen2 Counterpart

There is no `commons2-mongo` module. Gen2 services currently do not use MongoDB.
