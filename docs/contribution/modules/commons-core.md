# commons-core

Shared business logic library providing PDF generation, email, AI integration, and template processing. Depends on all five Gen1 commons modules.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.fincity.saas` |
| artifactId | `commons-core` |
| version | `1.0.0` |
| packaging | jar (library) |

## Parent

`org.springframework.boot:spring-boot-starter-parent:3.4.4`

> **Note:** This module uses Spring Boot 3.4.4 — different from both Gen1 (3.4.1) and Gen2 (3.4.9).

## Key Properties

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `spring-cloud.version` | 2024.0.0-RC1 |
| `saas-commons-version` | 2.0.0 |
| `saas-commons-jooq-version` | 2.0.0 |
| `saas-commons-security-version` | 2.0.0 |
| `saas-commons-mongo-version` | 2.0.0 |
| `saas-commons-mq-version` | 2.0.0 |
| `feign-reactor.version` | 4.2.1 |
| `openhtml.version` | 1.0.10 |
| `spotless.version` | 2.44.3 |
| `palantir-java.format.version` | 2.61.0 |

## Dependencies

### Spring Framework
- `spring-boot-starter-data-jpa` — JPA for blocking DB operations
- `spring-boot-starter-security` — Spring Security
- `spring-rabbit` — RabbitMQ integration

### Internal (all five Gen1 commons)
- `commons:2.0.0` — Base utilities
- `commons-jooq:2.0.0` — JOOQ data access
- `commons-security:2.0.0` — Auth/JWT
- `commons-mongo:2.0.0` — MongoDB access
- `commons-mq:2.0.0` — RabbitMQ events

### Reactive Feign
- `feign-reactor-spring-cloud-starter:4.2.1`
- `feign-reactor-spring-configuration:4.2.1`

### PDF Generation (OpenHTMLToPDF)
- `openhtmltopdf-core:1.0.10`
- `openhtmltopdf-pdfbox:1.0.10`
- `openhtmltopdf-rtl-support:1.0.10`
- `openhtmltopdf-svg-support:1.0.10`

### Email
- `jakarta.mail:2.0.1`
- `javax.activation:1.2.0`
- `jakarta.activation:2.0.1`

### AI
- `openai-java:0.31.0` — OpenAI API client

### Templating
- `freemarker-gae:2.3.32` — FreeMarker template engine
- `jsoup:1.19.1` — HTML parsing

### Build & Testing
- `lombok`
- `junit-jupiter-engine` (test)
- `reactor-test` (test)

## Dependency Management

- `org.springframework.cloud:spring-cloud-dependencies:2024.0.0-RC1` (BOM import)

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `maven-compiler-plugin` | 3.13.0 | Java 21 compilation, Lombok 1.18.38 annotation processor |

## Profiles

### `jooq`

JOOQ code generation for the `core` database schema.

- **JDBC URL:** `jdbc:mysql://localhost:3306/core`
- **Schema:** `core`
- **Table pattern:** `core_.*`
- **Target package:** `com.fincity.saas.commons.core.jooq`
- **Forced types:** `JSONMysqlMapConvertor` for `TOKEN_METADATA` JSON columns

### `spotless`

Code formatting via Spotless Maven plugin.

- **Java:** Palantir Java Format 2.61.0 (excludes JOOQ generated code)
- **YAML:** Jackson-based formatting with indent, ordered keys, minimized quotes

## What This Module Provides

This is the **heaviest commons library**, aggregating all Gen1 commons and adding business capabilities:

- **PDF generation** — HTML-to-PDF conversion using OpenHTMLToPDF + FreeMarker templates
- **Email sending** — Jakarta Mail integration
- **AI integration** — OpenAI Java SDK for AI-powered features
- **HTML processing** — Jsoup for HTML parsing and sanitization
- **Template engine** — FreeMarker for dynamic content generation
- **JOOQ code generation** — Has its own `core` schema tables

## Relationship to Other Modules

```
commons ──┐
commons-jooq ──┤
commons-security ──┤── commons-core ──> core
commons-mongo ──┤
commons-mq ──┘
```

Currently only the [core](core.md) service depends on `commons-core`.

## Notable

- **Version 1.0.0** (not 2.0.0 like other commons modules)
- **Spring Boot 3.4.4** (a third Spring Boot version in the codebase)
- Only commons module with Maven profiles (`jooq` and `spotless`)
- Only commons module that depends on all five other commons libraries
