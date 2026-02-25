# config

Centralized configuration server using Spring Cloud Config. Serves configuration to all microservices from a Git repository.

**Port:** 8888

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity` |
| artifactId | `config` |
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

### Spring Cloud Config
- `spring-cloud-config-server` — Config Server (serves configuration)
- `spring-cloud-starter-netflix-eureka-client` — Registers with Eureka

### Monitoring
- `micrometer-registry-prometheus` — Prometheus metrics
- `spring-boot-starter-actuator` — Health checks and metrics

### AWS
- `aws-java-sdk-s3:1.12.319` — AWS S3 (v1 SDK) for loading config from S3 backend

### Build
- `spring-boot-configuration-processor` (optional) — Generates configuration metadata

### Testing
- `spring-boot-starter-test`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/config` |

## Profiles

None.

## What This Service Does

- **Configuration serving** — Provides centralized configuration to all microservices
- **Git-backed config** — Reads configuration files from a Git repository (`https://github.com/modlix-india/oci-config.git`)
- **Environment-specific config** — Serves different configuration per profile (default, ocidev, ocistage, ociprod)
- **S3 backend support** — Can optionally load configuration from AWS S3

## How Services Consume Config

All microservices include `spring-cloud-starter-config` and fetch their configuration on startup by connecting to the config server.

## Configuration Hierarchy

Configuration is resolved in this order (later overrides earlier):

1. Config Server (remote Git repo) — centralized, environment-specific
2. `configfiles/{service}.yml` — service-specific local overrides
3. `configfiles/application-default.yml` — local development overrides
4. `src/main/resources/application.yml` — defaults built into the JAR

## Startup Order

The Config Server must start **first**, before all other services:

```
config → eureka → gateway → (security, core, ui, files, ...)
```

## Notable

- **Must start first** — All other services depend on it for configuration
- **No database** — No MySQL, MongoDB, JOOQ, or Flyway
- **No commons dependencies** — Does not depend on any commons libraries
- **GroupId:** `com.fincity` (not `com.fincity.saas`)
- **AWS SDK v1** — Uses the older `com.amazonaws:aws-java-sdk-s3:1.12.319` (v1), not the v2 SDK
- **`spring-boot-admin.version` defined but unused** — Property exists but Spring Boot Admin is not in dependencies
- **`spring-boot-configuration-processor`** — Marked as `optional`, generates config metadata for IDE autocomplete
