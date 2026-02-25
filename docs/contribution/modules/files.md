# files

File storage and management microservice. Handles file uploads, downloads, image processing, and cloud storage via AWS S3.

**Port:** 8004 (via gateway mapping), 8003/9003 (blue-green)

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.modlix.saas` |
| artifactId | `files` |
| version | `1.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.9`

> **Note:** Gen2 module using Spring Boot 3.4.9 (newer than Gen1 services at 3.4.1).

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.2 |
| `saas-commons2-version` | 2.0.0 |
| `saas-commons2-jooq-version` | 2.0.0 |
| `saas-commons2-security-version` | 2.0.0 |
| `jooq.version` | 3.20.3 |

## Dependencies

### Spring Framework
- `spring-boot-starter-web` — Servlet-based web framework
- `spring-boot-starter-data-jpa` — JPA
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-boot-starter-cache` — Caching
- `spring-boot-starter-jooq` — Spring Boot JOOQ integration
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery
- `spring-cloud-starter-openfeign` — Inter-service HTTP

### Database
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j:9.4.0` — MySQL JDBC connector
- `HikariCP` — JDBC connection pooling

### Internal Commons (Gen2)
- `commons2:2.0.0` — Base Gen2 utilities
- `commons2-jooq:2.0.0` — Gen2 JOOQ data access
- `commons2-security:2.0.0` — Gen2 auth/JWT

### AWS S3 (SDK v2)
- `software.amazon.awssdk:s3` — S3 client
- `software.amazon.awssdk:apache-client` — Apache HTTP client transport
- `software.amazon.awssdk:s3-transfer-manager` — Async transfer manager
- `software.amazon.awssdk:netty-nio-client` — Netty NIO transport

### Image Processing
- `imgscalr-lib:4.2` — Image scaling library
- `imageio-webp:0.1.6` — WebP image format support

### File Handling
- `commons-fileupload2-jakarta-servlet5:2.0.0-M3` — Multipart file upload
- `commons-io:2.19.0` — File I/O utilities

### Caching
- `caffeine` — In-process caching

### Testing
- `spring-boot-starter-test`
- `spring-security-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image: `ghcr.io/fincity-india/files` |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation from the `files` MySQL schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/files`
- **Schema:** `files`
- **Table pattern:** `files_.*`
- **Target package:** `com.modlix.saas.files.jooq`
- No forced types or excludes

## What This Service Manages

- **File uploads** — Multipart file upload handling
- **Cloud storage** — AWS S3 storage for files with transfer manager
- **Image processing** — Image resizing/scaling via imgscalr, WebP support
- **File metadata** — JOOQ-backed metadata storage in MySQL
- **File downloads** — Serving files from S3

## Notable

- **Gen2 service** — Uses `com.modlix.saas` groupId, `commons2` libraries, servlet-based
- **AWS SDK v2** — Uses the modern AWS SDK v2 for S3 (not the v1 SDK)
- **No commons-mq** — Does not publish or consume RabbitMQ events
- **MySQL 9.4.0 connector** — Explicitly pinned to newer MySQL connector version
