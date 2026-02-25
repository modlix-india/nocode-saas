# Module Reference

Per-module documentation explaining the Maven POM configuration, dependencies, plugins, and purpose of each module in the nocode-saas platform.

## Quick Reference

### Shared Libraries (Commons)

| Module                                    | GroupId            | Generation        | Description                                        |
| ----------------------------------------- | ------------------ | ----------------- | -------------------------------------------------- |
| [commons](commons.md)                     | `com.fincity.saas` | Gen1 (reactive)   | Base utilities, caching, DTOs, reactive Feign      |
| [commons2](commons2.md)                   | `com.modlix.saas`  | Gen2 (imperative) | Base utilities, caching, DTOs, standard Feign      |
| [commons-jooq](commons-jooq.md)           | `com.fincity.saas` | Gen1              | JOOQ + R2DBC data access layer                     |
| [commons2-jooq](commons2-jooq.md)         | `com.modlix.saas`  | Gen2              | JOOQ + JDBC/HikariCP data access layer             |
| [commons-security](commons-security.md)   | `com.fincity.saas` | Gen1              | JWT auth, SecurityContextUtil, reactive security   |
| [commons2-security](commons2-security.md) | `com.modlix.saas`  | Gen2              | JWT auth, servlet-based security                   |
| [commons-mongo](commons-mongo.md)         | `com.fincity.saas` | Gen1              | Reactive MongoDB data access                       |
| [commons-mq](commons-mq.md)               | `com.fincity.saas` | Gen1              | RabbitMQ event publishing (reactive)               |
| [commons2-mq](commons2-mq.md)             | `com.modlix.saas`  | Gen2              | RabbitMQ event publishing (imperative)             |
| [commons-core](commons-core.md)           | `com.fincity.saas` | Gen1              | PDF, email, AI, templates (depends on all commons) |

### Miniservices

| Module                                  | GroupId            | Port      | Generation | Description                                        |
| --------------------------------------- | ------------------ | --------- | ---------- | -------------------------------------------------- |
| [security](security.md)                 | `com.fincity.saas` | 8003      | Gen1       | Authentication, authorization, identity management |
| [core](core.md)                         | `com.fincity.saas` | 8001      | Gen1       | No-code platform data and logic engine             |
| [gateway](gateway.md)                   | `com.fincity`      | 8080      | Gen1       | API Gateway, JWT validation, request routing       |
| [ui](ui.md)                             | `com.fincity.saas` | 8002/9002 | Gen1       | UI server, page definitions, app store integration |
| [files](files.md)                       | `com.modlix.saas`  | 8004      | Gen2       | File storage, AWS S3, image processing             |
| [message](message.md)                   | `com.fincity.saas` | 8005      | Gen1       | WhatsApp, SMS, email messaging                     |
| [notification](notification.md)         | `com.modlix.saas`  | 8006      | Gen2       | Notification preferences and delivery              |
| [entity-collector](entity-collector.md) | `com.fincity.saas` | 8007      | Gen1       | External entity data ingestion                     |
| [entity-processor](entity-processor.md) | `com.fincity.saas` | 8008      | Gen1       | CRM, ticketing, campaign management                |
| [multi](multi.md)                       | `com.fincity.saas` | 8009      | Gen1       | Multi-tenancy data management                      |

### Infrastructure

| Module              | GroupId       | Port | Description                                   |
| ------------------- | ------------- | ---- | --------------------------------------------- |
| [config](config.md) | `com.fincity` | 8888 | Centralized configuration server (Git-backed) |
| [eureka](eureka.md) | `com.fincity` | 9999 | Service discovery (Netflix Eureka)            |

## Gen1 vs Gen2

The platform has two parallel module families:

| Aspect       | Gen1 (Reactive)                                                                 | Gen2 (Imperative)         |
| ------------ | ------------------------------------------------------------------------------- | ------------------------- |
| GroupId      | `com.fincity.saas`                                                              | `com.modlix.saas`         |
| Spring Boot  | 3.4.1                                                                           | 3.4.9                     |
| Spring Cloud | 2024.0.0-RC1                                                                    | 2024.0.2                  |
| Web          | `spring-boot-starter-webflux`                                                   | `spring-boot-starter-web` |
| Database     | R2DBC (reactive)                                                                | JDBC/HikariCP (blocking)  |
| Reactor      | Yes                                                                             | No                        |
| Feign        | Reactive (feign-reactor-\*)                                                     | Standard (OpenFeign)      |
| Services     | security, core, gateway, ui, message, entity-collector, entity-processor, multi | files, notification       |

## Dependency Graph

```
                        ┌─────────────┐
                        │   commons   │
                        └──────┬──────┘
               ┌───────────────┼───────────────┬───────────────┐
               │               │               │               │
        ┌──────┴──────┐ ┌──────┴──────┐ ┌──────┴──────┐ ┌─────┴─────┐
        │commons-jooq │ │commons-sec  │ │ commons-mq  │ │commons-   │
        │             │ │             │ │             │ │  mongo    │
        └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └─────┬─────┘
               │               │               │              │
               └───────────────┼───────────────┘              │
                               │                              │
                        ┌──────┴──────┐                       │
                        │commons-core │◄──────────────────────┘
                        └──────┬──────┘
                               │
                        ┌──────┴──────┐
                        │    core     │
                        └─────────────┘

        ┌─────────────┐
        │  commons2   │
        └──────┬──────┘
       ┌───────┼───────────┐
       │       │           │
 ┌─────┴──────┐│    ┌──────┴──────┐
 │commons2-   ││    │ commons2-   │
 │  jooq      ││    │    mq       │
 └─────┬──────┘│    └──────┬──────┘
       │  ┌────┴─────┐    │
       │  │commons2- │    │
       │  │ security │    │
       │  └────┬─────┘    │
       │       │          │
       ├───────┼──────────┤
       │       │          │
  ┌────┴──┐ ┌─┴────────┐ │
  │ files │ │notification│◄┘
  └───────┘ └───────────┘
```

## Version Summary

| Property              | Gen1 Value                                | Gen2 Value |
| --------------------- | ----------------------------------------- | ---------- |
| Spring Boot           | 3.4.1 (commons-core: 3.4.4)               | 3.4.9      |
| Spring Cloud          | 2024.0.0-RC1                              | 2024.0.2   |
| Java                  | 21                                        | 21         |
| JOOQ                  | 3.20.3 (message/entity-processor: 3.20.5) | 3.20.3     |
| Lombok                | 1.18.38                                   | 1.18.38    |
| maven-compiler-plugin | 3.13.0                                    | 3.13.0     |
| JWT (jjwt)            | 0.11.5                                    | 0.11.5     |
| KIRun                 | 4.3.0 (message/entity-processor: 3.14.1)  | —          |
| reactor-flatmap-util  | 1.3.0                                     | —          |
