# nocode-saas Contribution Guide

Welcome to the **nocode-saas** contribution documentation. This guide covers everything you need to know to contribute to the Modlix no-code/low-code SaaS platform backend.

## Quick Links

| I want to... | Go to |
|--------------|-------|
| Understand the system | [Architecture Overview](architecture-overview.md) |
| Set up my dev environment | [Development Setup](development-setup.md) |
| Write code | [Coding Conventions](coding-conventions.md) |
| Work with reactive streams | [Reactive Programming Guide](reactive-programming-guide.md) |
| Understand auth & tenancy | [Security & Multi-Tenancy](security-and-multitenancy.md) |
| Add a database table | [Database & JOOQ](database-and-jooq.md) |
| Write tests | [Testing Guide](testing-guide.md) |
| Publish/consume events | [Events & Messaging](event-and-messaging.md) |
| Deploy my changes | [Deployment & CI/CD](deployment-and-ci.md) |
| Create a PR | [Git Workflow](git-workflow.md) |
| Understand a specific module | [Module Reference](modules/README.md) |

## Recommended Reading Order

If you're new to the project, read the docs in this order:

1. **[Architecture Overview](architecture-overview.md)** — Understand the microservices, shared libraries, and how they connect
2. **[Development Setup](development-setup.md)** — Get your local environment running
3. **[Coding Conventions](coding-conventions.md)** — Learn the rules: type conventions, base classes, package structure
4. **[Reactive Programming Guide](reactive-programming-guide.md)** — Master `FlatMapUtil`, `Mono`/`Flux` chaining, and error handling
5. **[Security & Multi-Tenancy](security-and-multitenancy.md)** — Authentication, authorization, and client hierarchy
6. **[Database & JOOQ](database-and-jooq.md)** — Schema management, JOOQ code generation, and DAO patterns
7. **[Testing Guide](testing-guide.md)** — Unit tests, integration tests, and test utilities
8. **[Events & Messaging](event-and-messaging.md)** — RabbitMQ event publishing and consumption
9. **[Deployment & CI/CD](deployment-and-ci.md)** — Docker, blue-green deployment, GitHub Actions
10. **[Git Workflow](git-workflow.md)** — Branching, commits, and PR process

## Tech Stack at a Glance

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1, Spring WebFlux |
| Reactive | Project Reactor |
| Database ORM | JOOQ 3.20.3 |
| Databases | MySQL (R2DBC), MongoDB, Redis |
| Messaging | RabbitMQ |
| Service Discovery | Spring Cloud Eureka |
| Configuration | Spring Cloud Config |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven |
| CI/CD | GitHub Actions |
| Containerization | Docker |

## Project Structure

```
nocode-saas/
├── commons/                # Shared utilities, caching, DTOs
├── commons-core/           # Business logic, PDF, AI integrations
├── commons-jooq/           # JOOQ base classes (services, DAOs, controllers)
├── commons-mongo/          # MongoDB reactive utilities
├── commons-mq/             # RabbitMQ event utilities
├── commons-security/       # JWT, SecurityContextUtil
├── commons2*/              # Gen2 commons (for files, notification)
├── core/                   # Core microservice (port 8001)
├── security/               # Security microservice (port 8003)
├── gateway/                # API Gateway (port 8080)
├── ui/                     # UI server (ports 8002/9002)
├── files/                  # Files microservice (port 8004)
├── message/                # Message microservice (port 8005)
├── notification/           # Notification microservice (port 8006)
├── entity-collector/       # Entity collector (port 8007)
├── entity-processor/       # Entity processor (port 8008)
├── multi/                  # Multi-tenancy service (port 8009)
├── eureka/                 # Service discovery (port 9999)
├── config/                 # Config server (port 8888)
├── configfiles/            # Local configuration overrides
├── server-files/           # Docker Compose per environment
├── runmvn.sh               # Build orchestration script
└── docs/contribution/      # This documentation
```

## Getting Started in 5 Minutes

```bash
# 1. Clone the repo
git clone <repo-url>
cd nocode-saas

# 2. Set up databases (MySQL, MongoDB, Redis, RabbitMQ)

# 3. Update configfiles/application-default.yml with your credentials

# 4. Generate JOOQ classes (requires MySQL running with schemas)
./runmvn.sh jooq

# 5. Build everything
./runmvn.sh clean install

# 6. Start services (config → eureka → gateway → your service)
```

See [Development Setup](development-setup.md) for the full guide.

## Module Reference

For detailed per-module documentation (Maven coordinates, dependencies, plugins, profiles), see the **[Module Reference](modules/README.md)**.

Each module has its own page covering:
- Maven coordinates and parent POM
- All dependencies organized by category
- Plugin configuration
- JOOQ code generation profiles (where applicable)
- What the module provides and its relationship to other modules
