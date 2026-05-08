# Integration Tests (int-test)

Black-box API integration tests for the Fincity SAAS platform. These tests run against a **live running environment** (not embedded/in-process) via HTTP using REST-Assured.

## How It Works

- Tests hit the gateway at the configured `base.host` (default: `https://appbuilder.local.modlix.com`)
- No Spring context, no Testcontainers — requires the full stack running (gateway, security, entity-processor, eureka, config, etc.)
- Configuration lives in `src/test/resources/inttest.properties`
- Override base host via env var: `BASE_HOST=https://...`

## Running Tests

```bash
cd int-test
mvn test                                    # Run all integration tests
mvn test -Dtest=TicketExpiresOnScenario     # Run a single scenario
```

All services must be running before executing tests (see `nocode-saas/docker-compose.yml` or start services individually).

## Test Structure

```
src/test/java/com/fincity/saas/inttest/
  base/                          # Shared infrastructure
    BaseIntegrationTest.java     # Base class: config loading, REST-Assured spec builders
    AuthHelper.java              # Token caching + authentication helper
    SecurityApi.java             # Fluent wrapper for security endpoints (register, invite, auth)
    EntityProcessorApi.java      # Fluent wrapper for entity-processor endpoints (tickets, notes, tasks, etc.)
    ProfileHelper.java           # Profile ID resolution helper
  leadzump/
    realestate/                  # LeadZump real estate scenario tests
```

## Writing a New Scenario Test

1. **Extend `BaseIntegrationTest`** — provides `baseHost()`, `prop()`, `givenAuth()`, `givenNoAuth()`.
2. **Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`** with `@Order(N)` for sequential execution.
3. **Use `@BeforeAll`** to register a new client, authenticate, and create API helper instances.
4. **Use `mapOf(Object... keyValues)`** utility for building request bodies (allows nulls, >10 entries).
5. **Use `EntityProcessorApi`** for all entity-processor calls, **`SecurityApi`** for auth/user management.
6. **Each scenario gets its own isolated client** (via self-registration) to avoid cross-test interference.

### Patterns

- All API wrappers return raw `Response` — assert status and extract fields in the test.
- Use `assertThat(res.statusCode()).isIn(200, 201)` for create operations (backend may return either).
- Group tests into logical scenarios with order ranges (e.g., 100-199 setup, 200-299 scenario 1, etc.).
- Ticket operations: `createTicket`, `updateTicketStage`, `updateTicketTag`, `reassignTicket`, `updateTicketByCode`.
- Content operations: `createNote`, `createTask`, `updateTask`, `logCallActivity`.
- Generic field updates (email, name, description/bio): use `PUT /code/{code}` via `updateTicketByCode`.

### Optimistic Versioning

Entity-processor entities use **optimistic locking** via a `version` field. When updating via `PUT /code/{code}`:
- The request body **must** include the current `version` from the entity.
- If the version doesn't match (concurrent modification), the server returns **412 Precondition Failed**.
- On successful update, the version is incremented server-side.
- Best practice: read the full entity first (`getTicket`), modify the needed fields, send the full body back.

### ExpiresOn Behavior

The `expiresOn` field on tickets is computed server-side based on expiration rules (source + product/template), not set from the request:
- **Set on creation**: `computeAndSetExpiresOn()` calculates `now + expiryDays` from the matching rule.
- **Recalculated on stage change**: `updateTicketStage()` calls `computeAndSetExpiresOn()` again.
- **Should NOT change on**: tag update, note/task creation, reassignment, call log, or generic field updates (email, name, bio).
- **ExpiresOn is epoch seconds** (not a string or array) in JSON responses — use `Number` type when extracting via REST-Assured `.path()`.

### Entity-Processor API Endpoints Used

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Create ticket | POST | `/api/entity/processor/tickets/req` |
| Get ticket | GET | `/api/entity/processor/tickets/{id}` |
| Get ticket eager | GET | `/api/entity/processor/tickets/{id}/eager` |
| Update by code | PUT | `/api/entity/processor/tickets/code/{code}` |
| Update stage | PATCH | `/api/entity/processor/tickets/req/{id}/stage` |
| Update tag | PATCH | `/api/entity/processor/tickets/req/{id}/tag` |
| Reassign | PATCH | `/api/entity/processor/tickets/req/{id}/reassign` |
| Bulk reassign | PATCH | `/api/entity/processor/tickets/bulk-reassign` |
| Create note | POST | `/api/entity/processor/notes/req` |
| Create task | POST | `/api/entity/processor/tasks/req` |
| Update task | PUT | `/api/entity/processor/tasks/req/{id}` |
| Log call | POST | `/api/entity/processor/activities/call-log` |
| Website lead (no auth) | POST | `/api/entity/processor/open/tickets/req/website/{productCode}` |
| Expiration rule | POST | `/api/entity/processor/products/tickets/ex/rules` |
| Creation rule | POST | `/api/entity/processor/products/tickets/c/rules` |
