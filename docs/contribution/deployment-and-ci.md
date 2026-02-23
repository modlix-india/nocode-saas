# Deployment & CI/CD

This document covers Docker containerization, deployment strategies, and the CI/CD pipeline.

## Docker

### Dockerfile Pattern

Each microservice has a `Dockerfile` at its root. The standard pattern:

```dockerfile
FROM openjdk:21-ea-jdk-oracle

COPY target/security-1.1.0.jar security-1.1.0.jar

EXPOSE 8001

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/security-${INSTANCE_ID}.log", "-jar", "security-1.1.0.jar"]
```

Key elements:
- **Base image**: `openjdk:21-ea-jdk-oracle`
- **Port**: Service-specific (see [architecture-overview.md](architecture-overview.md) for port list)
- **Log volume**: `/logs` mounted for persistent logging
- **INSTANCE_ID**: Environment variable used for blue-green deployment identification

### Building a Docker Image

```bash
cd security
mvn clean package
docker build -t security-server:latest -f Dockerfile .
```

## Docker Compose

Docker Compose files are organized by environment in `server-files/`:

```
server-files/
├── dev/composer/servers/docker-compose.yml
├── stage/composer/servers/docker-compose.yml
└── prod/composer/servers/docker-compose.yml
```

### Service Definitions

Each compose file defines all microservices with:
- Config Server and Eureka starting first (health check dependency)
- Blue and green instances for application services
- Environment variables for profile, instance ID, and config server URL

```yaml
security-server:
  image: security-server:latest
  ports:
    - "8003:8001"
  environment:
    SPRING_PROFILES_ACTIVE: ocidev
    CLOUD_CONFIG_SERVER: config-server
    INSTANCE_ENVIRONMENT: Development
    INSTANCE_ID: blue
  depends_on:
    config-server:
      condition: service_healthy
  healthcheck:
    test: curl -f http://localhost:8001/actuator/health
    interval: 10s
    timeout: 5s
    retries: 10
  volumes:
    - /logs:/logs
```

### Running Locally with Docker Compose

```bash
cd server-files/dev/composer/servers
docker-compose up -d
```

## Blue-Green Deployment

The platform uses blue-green deployment for zero-downtime releases:

| Service | Blue Port | Green Port |
|---------|-----------|------------|
| ui | 8002 | 9002 |
| core | 8005 | 9005 |
| files | 8003 | 9003 |

The `keepup.sh` script manages the blue-green switch:
1. Deploy the new version to the inactive instance (green)
2. Wait for health check to pass
3. Switch traffic from blue to green
4. The old blue becomes the new green for the next deployment

## CI/CD Pipeline

### GitHub Actions

Workflows are in `.github/workflows/` with the naming pattern:

```
{service}-{environment}-ci.yml
```

Examples: `security-dev-ci.yml`, `core-stage-ci.yml`, `gateway-prod-ci.yml`

There are **37 workflow files** covering all service + environment combinations.

### Workflow Structure

Example from `security-dev-ci.yml`:

```yaml
name: security-dev-ci

on:
  push:
    branches:
      - oci-development
    paths:
      - 'security/**'
      - 'commons/**'
      - 'commons-jooq/**'
      - 'commons-security/**'
      - 'commons-mq/**'
      - '.github/workflows/security-dev-ci.yml'
  workflow_dispatch:
```

**Trigger**: Push to `oci-development` branch with changes in the service or its commons dependencies.

### Build Job

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: 'maven'

      # 1. Build commons dependencies
      - run: |
          mvn -q clean install -f commons/pom.xml
          mvn -q clean install -f commons-jooq/pom.xml
          mvn -q clean install -f commons-security/pom.xml
          mvn -q clean install -f commons-mq/pom.xml

      # 2. Package the service
      - working-directory: security
        run: mvn -q clean package

      # 3. Build and push Docker image
      - run: |
          docker build -t $OCIR_REPO:${{ github.sha }} \
                       -t $OCIR_REPO:latest \
                       -f security/Dockerfile .
          docker push $OCIR_REPO:${{ github.sha }}
          docker push $OCIR_REPO:latest
```

### Deploy Job

```yaml
  deploy:
    needs: build
    runs-on: self-hosted
    steps:
      - run: |
          ssh -i /home/ubuntu/keys/dev-internal.key opc@${{ secrets.DEV_SERVER_IP }} \
            "bash /home/opc/scripts/keepup.sh deploy security-server \
             $OCIR_REPO:${{ needs.build.outputs.image_tag }}"
```

The deploy job:
1. Runs on a **self-hosted runner** (for SSH access to the deployment server)
2. SSHs into the target server
3. Executes `keepup.sh deploy` with the service name and new image tag
4. The keepup script handles blue-green switching

### CI Pipeline Flow

```
Code Push → GitHub Actions Trigger
    ↓
Build Commons (commons → commons-jooq → commons-security → commons-mq)
    ↓
Package Service (mvn clean package)
    ↓
Docker Build & Push (tagged with git SHA + latest)
    ↓
Deploy via SSH (keepup.sh blue-green deploy)
```

### Branch-to-Environment Mapping

| Branch | Environment | Config Profile |
|--------|-------------|---------------|
| `oci-development` | Development | `ocidev` |
| `oci-staging` | Staging | `ocistage` |
| `oci-production` | Production | `ociprod` |
| `master` | — | No auto-deploy |

### Path Filters

Each workflow only triggers when relevant files change. For example, the security workflow triggers on changes to:
- `security/**` — The service itself
- `commons/**` — Core commons library
- `commons-jooq/**` — JOOQ commons
- `commons-security/**` — Security commons
- `commons-mq/**` — MQ commons

## Infrastructure Services

### Config Server (Port 8888)

Centralized configuration pulled from a Git repository:
- Repository: `https://github.com/modlix-india/oci-config.git`
- Contains per-environment YAML files
- Services fetch config on startup via Spring Cloud Config

### Eureka Server (Port 9999)

Service discovery:
- All services register with Eureka on startup
- Gateway uses Eureka to route requests to service instances
- Health checks at `/actuator/health`

### Gateway (Port 8080)

API entry point:
- Routes external requests to internal services based on path
- Validates JWT tokens
- Populates security context for downstream services

## Environment Configuration

### Profiles

| Profile | Purpose |
|---------|---------|
| `default` | Local development (uses `configfiles/application-default.yml`) |
| `test` | Test execution (Testcontainers, no Eureka) |
| `ocidev` | OCI Development environment |
| `ocistage` | OCI Staging environment |
| `ociprod` | OCI Production environment |

### Configuration Precedence

1. Spring Cloud Config Server (remote Git repo)
2. `configfiles/{service}.yml` (service-specific)
3. `configfiles/application-default.yml` (local overrides)
4. `src/main/resources/application.yml` (built into JAR)

## Container Registry

Docker images are pushed to **Oracle Cloud Infrastructure Registry (OCIR)**:
```
ocir.us-ashburn-1.oci.oraclecloud.com/idfmutpuhiky/{env}-{service}-server
```

Images are tagged with:
- `{git-sha}` — Specific commit hash for traceability
- `latest` — Most recent build
