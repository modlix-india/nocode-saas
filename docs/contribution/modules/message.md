# message

Messaging microservice for managing WhatsApp, SMS (Exotel), and email communications. Handles message templates, sending, and delivery tracking.

**Port:** 8005

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `message` |
| version | `1.0.0` |
| packaging | jar (Spring Boot executable) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.1`

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `package.o.spring-cloud.version` | 2024.0.0-RC1 |
| `package.i.nocode-kirun-version` | 3.14.1 |
| `package.o.jooq.version` | 3.20.5 |
| `package.i.nocode-flatmap-util-version` | 1.3.0 |
| `package.o.sendgrid-java.version` | 4.10.3 |
| `package.o.freemarker.version` | 2.3.34 |
| `package.o.spotless.version` | 2.45.0 |
| `package.o.palantir-java.format.version` | 2.70.0 |

> **Note:** This module uses a namespaced property convention (`package.i.*` for internal, `package.o.*` for other) unique to message and entity-processor.

## Dependencies

### Spring Framework
- `spring-boot-starter-webflux` — Reactive web framework
- `spring-boot-starter-data-r2dbc` — Reactive MySQL
- `spring-boot-starter-actuator` — Health checks and metrics
- `spring-boot-starter-aop` — AOP
- `spring-boot-starter-cache` — Caching
- `spring-cloud-starter-config` — Config client
- `spring-cloud-starter-netflix-eureka-client` — Service discovery

### Database
- `r2dbc-pool` — R2DBC connection pooling
- `r2dbc-mysql` — Reactive MySQL driver
- `flyway-core` + `flyway-mysql` — Database migrations
- `mysql-connector-j` — MySQL JDBC connector
- `jooq:3.20.5` + `jooq-codegen-maven:3.20.5` — JOOQ

### Internal Commons
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `reactor-flatmap-util:1.3.0` — Reactive chaining
- `kirun-java:3.14.1` — KIRun runtime (older version)

### Messaging & Communication
- `sendgrid-java:4.10.3` — SendGrid email API
- `libphonenumber:9.0.9` — Phone number parsing/validation
- `jakarta.websocket-api:2.2.0` (provided) — WebSocket support

### Templating
- `freemarker-gae:2.3.34` — FreeMarker template engine

### Reactive Feign
- `feign-reactor-spring-cloud-starter:4.2.1`
- `feign-reactor-spring-configuration:4.2.1`

### Networking
- `reactor-netty`
- `netty-resolver-dns-native-macos` (osx-aarch_64)

### Testing
- `spring-boot-starter-test`
- `reactor-test`

### Build
- `lombok`

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `spring-boot-maven-plugin` | (managed) | Executable JAR packaging |
| `flyway-maven-plugin` | (managed) | Database migration management |
| `jib-maven-plugin` | 3.3.0 | Docker image target (has copy-paste error) |
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 |

## Profiles

### `jooq`

JOOQ code generation with extensive forced types for WhatsApp and Exotel integrations.

- **JDBC URL:** `jdbc:mysql://localhost:3306/message`
- **Schema:** `message`
- **Table pattern:** `message_.*`
- **Target package:** `com.fincity.saas.message.jooq`
- **Forced types** (extensive):
  - `BOOLEAN` from `TINYINT`
  - `java.util.Map` for EVENT columns
  - Custom types: `Message`, `MessageResponse`, `IMessage`, `ComponentList`, `FileDetail`, `ExotelCallRequest`, `ExotelCallResponse`, `ExotelConnectAppletRequest`, `SubscribedApp`, `WebhookConfig`, `Throughput`, `QualityScore`
  - Enum converters: `ExotelCallStatus`, `MessageType`, `MessageStatus`, `Category`, `SubCategory`, `ParameterFormat`, `TemplateRejectedReason`, `TemplateStatus`, `NameStatusType`, `PlatformType`, `QualityRatingType`, `MessagingLimitTier`, `Status`, `CodeVerificationStatus`

### `spotless`

Code formatting with Spotless.

- **Java:** Palantir Java Format 2.70.0, excludes JOOQ generated code
- **POM:** sortPom with dependency ordering
- **YAML:** Jackson-based formatting

## What This Service Manages

- **WhatsApp messaging** — Template management, message sending, delivery tracking
- **SMS via Exotel** — Call requests, responses, connect applet
- **Email via SendGrid** — Template-based email sending
- **Message templates** — FreeMarker-based template management
- **Phone number validation** — LibPhoneNumber integration
- **Webhook configuration** — External webhook management

## Notable

- **JOOQ 3.20.5** — Slightly newer than other services (3.20.3)
- **kirun-java 3.14.1** — Older version (vs 4.3.0 in security/core)
- **Most complex JOOQ config** — Extensive forced type mappings for WhatsApp/Exotel domain models
- **Jib target error** — Jib image target says `entity-processor` (copy-paste mistake)
- **Namespaced properties** — Uses `package.i.*`/`package.o.*` property naming convention
