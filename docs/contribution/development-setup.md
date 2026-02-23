# Development Setup

This guide walks you through setting up a local development environment for the nocode-saas platform.

## Prerequisites

| Software | Version | Purpose |
|----------|---------|---------|
| **Java** | 21 (JDK) | Runtime and compilation |
| **Maven** | 3.x | Build tool |
| **MySQL** | 8.0+ | Primary relational database |
| **MongoDB** | 6.0+ | Document database (for core, ui services) |
| **Redis** | 7.0+ | Distributed caching |
| **RabbitMQ** | 3.x | Message broker |
| **Docker** | Latest | Containerized deployment (optional for local dev) |
| **Git** | Latest | Version control |

### IDE Recommendations

- **IntelliJ IDEA** (recommended) — Install the Lombok plugin
- **VS Code** — Install "Extension Pack for Java" and "Lombok Annotations Support"

> **Important**: Enable annotation processing in your IDE for Lombok to work.

## Repository Structure

```
nocode-saas/
├── commons/                # Shared utilities, caching, DTOs
├── commons-core/           # Business logic, PDF, Freemarker
├── commons-jooq/           # JOOQ base classes (services, DAOs, controllers)
├── commons-mongo/          # MongoDB reactive utilities
├── commons-mq/             # RabbitMQ event utilities
├── commons-security/       # JWT, SecurityContextUtil
├── commons2/               # Gen2 commons (for files, notification)
├── commons2-jooq/
├── commons2-mq/
├── commons2-security/
├── core/                   # Core microservice
├── security/               # Security microservice
├── gateway/                # API Gateway
├── ui/                     # UI server
├── files/                  # Files microservice
├── message/                # Message microservice
├── notification/           # Notification microservice
├── entity-collector/       # Entity collector
├── entity-processor/       # Entity processor
├── multi/                  # Multi-tenancy service
├── eureka/                 # Service discovery
├── config/                 # Config server
├── configfiles/            # Local configuration overrides
├── server-files/           # Docker Compose per environment
├── runmvn.sh               # Build orchestration script
└── docs/                   # Documentation
```

## Database Setup

### MySQL Schemas

Create the following schemas in your local MySQL instance:

```sql
CREATE DATABASE security;
CREATE DATABASE core;
CREATE DATABASE files;
CREATE DATABASE message;
CREATE DATABASE notification;
CREATE DATABASE entity_collector;
CREATE DATABASE entity_processor;
CREATE DATABASE multi;
CREATE DATABASE schedular;
CREATE DATABASE ai;
```

### MongoDB Databases

MongoDB databases (`core`, `ui`) are created automatically when services first write data. Ensure MongoDB is running with authentication enabled:

```
username: root
password: (your local password)
authSource: admin
```

### Redis

Ensure Redis is running on port 6379 with password authentication.

### RabbitMQ

Ensure RabbitMQ is running on port 5672 with:
```
username: fincity
password: fincity
```

## Configuration

### Local Configuration File

All local development overrides live in `configfiles/application-default.yml`. This file contains:

- Database connection URLs (R2DBC and JDBC) for each service
- MongoDB URIs for core and ui
- Redis connection URL
- RabbitMQ credentials
- Logging levels
- CDN and SSR configuration

**Update the passwords** in this file to match your local database credentials. The file uses these connection patterns:

```yaml
# MySQL via R2DBC (reactive) - used by most services
security:
  db:
    url: r2dbc:mysql://localhost:3306/security?serverTimezone=UTC
    username: root
    password: <your-password>
    flyway:
      url: jdbc:mysql://localhost:3306/security

# MongoDB (reactive) - used by core and ui
core:
  mongo:
    uri: mongodb://root:<your-password>@localhost:27017/core?authSource=admin

# Redis
redis:
  url: redis://<your-password>@localhost:6379

# RabbitMQ
mq:
  host: localhost
  port: 5672
  username: fincity
  password: fincity
```

> **Note**: Each service has both an R2DBC URL (for reactive database access) and a JDBC URL (for Flyway migrations). Both are required.

## Building the Project

The project does **not** have a single root POM. Each module is built independently. The `runmvn.sh` script orchestrates builds in the correct dependency order.

### First-Time Build: JOOQ Code Generation

JOOQ generates type-safe Java classes from your MySQL schemas. This must be run **after** database setup and **before** a regular build:

```bash
./runmvn.sh jooq
```

This command:
1. Builds all commons libraries (commons, commons2, commons-jooq, commons2-jooq, commons-security, commons2-security, commons-mongo, commons-mq, commons2-mq)
2. Connects to your local MySQL and generates JOOQ classes for each service with the `-Pjooq` Maven profile
3. Build order: commons-core → security → entity-collector → entity-processor → files → message → multi → notification

> **Prerequisite**: MySQL must be running with all schemas created and Flyway migrations applied. The JOOQ build connects to the live database to introspect schemas.

### Full Build

```bash
./runmvn.sh clean install
```

This builds **all 22 modules** in dependency order:
1. Commons libraries (commons → commons2 → commons-jooq → commons2-jooq → commons-security → commons2-security → commons-mongo → commons-mq → commons2-mq → commons-core)
2. Infrastructure services (config → eureka → gateway)
3. Application services (core → security → files → entity-processor → entity-collector → notification → message → multi → ui)

### Building a Single Module

If you're working on a specific service, build just that module after commons are built:

```bash
cd security
mvn clean install
```

To skip tests during build:
```bash
cd security
mvn clean install -DskipTests
```

## Running Services Locally

### Option 1: Docker Compose (Full Stack)

```bash
cd server-files/dev/composer/servers
docker-compose up -d
```

This starts all services with their dependencies.

### Option 2: Run Individual Services

Start services in this order (each in a separate terminal):

1. **Config Server** (port 8888) — must start first
   ```bash
   cd config
   mvn spring-boot:run
   ```

2. **Eureka** (port 9999) — must start before other services
   ```bash
   cd eureka
   mvn spring-boot:run
   ```

3. **Gateway** (port 8080) — API entry point
   ```bash
   cd gateway
   mvn spring-boot:run
   ```

4. **Security** (port 8003) — usually needed by all services
   ```bash
   cd security
   mvn spring-boot:run
   ```

5. **Other services** as needed (core, files, ui, etc.)
   ```bash
   cd core
   mvn spring-boot:run
   ```

> **Tip**: For local development, you can often run just config + eureka + gateway + the service you're working on.

### Verifying Services Are Running

- **Eureka Dashboard**: http://localhost:9999
- **Service Health**: http://localhost:{port}/actuator/health
- **Gateway**: http://localhost:8080 (routes to registered services)

## IDE Configuration

### IntelliJ IDEA

1. Open the project root (`nocode-saas/`)
2. Import each module as a Maven project
3. Settings → Build → Compiler → Annotation Processors → Enable annotation processing
4. Install the **Lombok** plugin
5. Set Project SDK to Java 21

### VS Code

1. Open `nocode-saas/` folder
2. Install "Extension Pack for Java"
3. Install "Lombok Annotations Support for VS Code"
4. The Java extension will auto-detect Maven modules

## Common Build Issues

### "Cannot find symbol" errors in JOOQ-generated classes
The JOOQ classes haven't been generated. Run `./runmvn.sh jooq` first.

### Flyway migration failures
Ensure your MySQL schemas exist and credentials in `configfiles/application-default.yml` are correct.

### "Cannot connect to MySQL" during JOOQ build
MySQL must be running and accessible at `localhost:3306`. Check that all schemas listed in `application-default.yml` exist.

### Lombok-related compilation errors
Enable annotation processing in your IDE and install the Lombok plugin.

### Port conflicts
Check that no other process is using ports 8080, 8888, or 9999. Use `lsof -i :PORT` to investigate.

### Circular dependency errors at startup
Some services use `@Lazy` for circular dependencies. If you're adding new dependencies between services, consider using `@Lazy` on one side.

### Out of memory during build
Increase Maven memory:
```bash
export MAVEN_OPTS="-Xmx2g"
./runmvn.sh clean install
```
