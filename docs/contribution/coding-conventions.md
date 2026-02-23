# Coding Conventions

This document covers the coding standards and patterns enforced across all nocode-saas microservices. Following these conventions ensures consistency and makes the codebase easier to navigate.

## Type Conventions

### IDs: Always Use `ULong`

All database IDs use `org.jooq.types.ULong` — **never** `Long`, `Integer`, or `BigInteger`.

```java
import org.jooq.types.ULong;

// Correct
ULong clientId = ULong.valueOf(1);

// Wrong - never use these for DB IDs
Long clientId = 1L;
Integer clientId = 1;
BigInteger clientId = BigInteger.ONE;
```

For conversion between types, use the utility:
```java
import com.fincity.saas.commons.jooq.util.ULongUtil;

ULong id = ULongUtil.valueOf(bigIntegerValue);
```

### Timestamps: Always Use `LocalDateTime`

All timestamps use `java.time.LocalDateTime` — **never** `java.util.Date` or `Calendar`.

```java
import java.time.LocalDateTime;

private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

## DTO Conventions

DTOs (Data Transfer Objects) map to database tables and carry data between layers.

### Base Classes

- `AbstractDTO<I, U>` — Base with `id`, `createdAt`, `createdBy`
- `AbstractUpdatableDTO<I, U>` — Extends above with `updatedAt`, `updatedBy`

### Annotations

Every DTO must have these Lombok annotations:

```java
@Data                                    // Generates getters, setters, toString, equals, hashCode
@Accessors(chain = true)                // Enables fluent API: dto.setName("x").setCode("y")
@EqualsAndHashCode(callSuper = true)    // Includes parent class fields
@ToString(callSuper = true)             // Includes parent class fields
```

### Complete DTO Example

From `security/src/main/java/com/fincity/security/dto/Client.java`:

```java
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Client extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 4312344235572008119L;

    private String code;
    private String name;
    private String typeCode;
    private int tokenValidityMinutes;
    private String localeCode;
    private SecurityClientStatusCode statusCode;   // JOOQ-generated enum
    private SecurityClientLevelType levelType;      // JOOQ-generated enum

    // Computed property
    @JsonProperty(value = "totalUsers")
    public Integer getTotalUsers() {
        return activeUsers + inactiveUsers + deletedUsers + lockedUsers + passwordExpiredUsers;
    }
}
```

Key points:
- Extends `AbstractUpdatableDTO<ULong, ULong>` (ID type, audit user type)
- Uses JOOQ-generated enums for status codes
- Includes `serialVersionUID` for serialization
- Computed properties use `@JsonProperty`
- Uses `@Accessors(chain = true)` for fluent setters

## Service Layer Conventions

### Base Class Hierarchy

```
AbstractJOOQDataService<R, I, D, O>         // Read-only CRUD
└── AbstractJOOQUpdatableDataService         // + update operations
    └── AbstractSecurityUpdatableDataService // + SOX audit logging (security module)
```

Generic type parameters:
- `R` — JOOQ `UpdatableRecord` (e.g., `SecurityClientRecord`)
- `I` — ID type (always `ULong`)
- `D` — DTO type (e.g., `Client`)
- `O` — DAO type (e.g., `ClientDAO`)

### Service Declaration

```java
@Service
public class ClientService
        extends AbstractSecurityUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {
    // ...
}
```

### Dependency Injection

**Constructor injection only** — no `@Autowired` on fields (except for legacy code that still uses field injection):

```java
public UserService(
    ClientService clientService,
    AppService appService,
    PasswordEncoder passwordEncoder,
    SecurityMessageResourceService securityMessageResourceService) {
    this.clientService = clientService;
    this.appService = appService;
    this.passwordEncoder = passwordEncoder;
    this.securityMessageResourceService = securityMessageResourceService;
}
```

For circular dependencies, use `@Lazy`:
```java
@Autowired
@Lazy
private UserService userService;
```

### Authorization: `@PreAuthorize` on All Public Methods

Every public service method that is exposed to end users must have a `@PreAuthorize` annotation:

```java
@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
@Override
public Mono<Client> create(Client entity) { ... }

@PreAuthorize("hasAuthority('Authorities.Client_READ')")
@Override
public Mono<Client> read(ULong id) { ... }

@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
@Override
public Mono<Client> update(Client entity) { ... }

@PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
@Override
public Mono<Integer> delete(ULong id) { ... }
```

Authority naming pattern: `Authorities.{Entity}_{ACTION}`
- Actions: `CREATE`, `READ`, `UPDATE`, `DELETE`
- Examples: `Authorities.Client_CREATE`, `Authorities.User_READ`, `Authorities.Role_UPDATE`

### Internal Methods

Methods called by other services (via Feign or internally) do **not** need `@PreAuthorize`:

```java
// No @PreAuthorize — internal use only
public Mono<Client> readInternal(ULong id) {
    return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_ID, () -> this.dao.readInternal(id), id);
}
```

### Return Types

All service methods return `Mono<T>` or `Flux<T>` — **never** synchronous types:

```java
// Correct
public Mono<Client> create(Client entity) { ... }
public Mono<Page<Client>> readPageFilter(Pageable pageable, AbstractCondition condition) { ... }
public Flux<Client> readAllFilter(AbstractCondition condition) { ... }

// Wrong - never return synchronous types
public Client create(Client entity) { ... }
```

## Controller Layer Conventions

### Base Class

```java
AbstractJOOQDataController<R, I, D, O, S>
└── AbstractJOOQUpdatableDataController     // Adds PUT endpoint
```

The base class provides standard CRUD endpoints:
- `POST /` — Create
- `GET /{id}` — Read by ID
- `GET /` — Paginated list with filters
- `POST /query` — Complex query with conditions
- `PUT /{id}` — Update
- `DELETE /{id}` — Delete

### Controller Declaration

```java
@RestController
@RequestMapping("api/security/clients")
public class ClientController
        extends AbstractJOOQUpdatableDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {

    private final ClientRegistrationService clientRegistrationService;

    public ClientController(ClientRegistrationService clientRegistrationService) {
        this.clientRegistrationService = clientRegistrationService;
    }
}
```

### Route Naming Pattern

- External routes: `api/{service}/{resource}` (e.g., `api/security/clients`)
- Internal routes (inter-service): `api/{service}/{resource}/internal/...`
- Query endpoints: `api/{service}/{resource}/query`

### Response Pattern

All endpoints return `Mono<ResponseEntity<T>>`:

```java
@GetMapping("/internal/isUserClientManageClient")
public Mono<ResponseEntity<Boolean>> isUserClientManageClient(
        String appCode, ULong userId, ULong userClientId, ULong targetClientId) {
    return this.service.isUserClientManageClient(appCode, userId, userClientId, targetClientId)
            .map(ResponseEntity::ok);
}
```

### Authorization

Controllers delegate authorization to the service layer via `@PreAuthorize`. Controllers themselves typically do **not** have security annotations.

## DAO Layer Conventions

### Base Class

```java
AbstractDAO<R, I, D>
└── AbstractUpdatableDAO<R, I, D>     // Adds update operations
```

### DAO Declaration

DAOs are annotated with `@Service` (not `@Repository`):

```java
@Service
public class ClientDAO extends AbstractUpdatableDAO<SecurityClientRecord, ULong, Client> {

    protected ClientDAO() {
        super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
    }
}
```

Constructor arguments:
1. `Client.class` — The DTO POJO class
2. `SECURITY_CLIENT` — JOOQ-generated table reference (static import)
3. `SECURITY_CLIENT.ID` — JOOQ-generated ID field

### Static Imports for JOOQ Tables

Use static imports for JOOQ-generated table and field references:

```java
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
```

### Custom Query Methods

```java
public Mono<Client> readInternal(ULong id) {
    return Mono.from(this.dslContext.selectFrom(this.table)
            .where(this.idField.eq(id))
            .limit(1))
            .map(e -> e.into(this.pojoClass));
}

public Mono<Client> getClientBy(String clientCode) {
    return Flux.from(this.dslContext.select(SECURITY_CLIENT.fields())
            .from(SECURITY_CLIENT)
            .where(SECURITY_CLIENT.CODE.eq(clientCode))
            .limit(1))
            .singleOrEmpty()
            .map(e -> e.into(Client.class));
}
```

## Package Structure

Standard package layout for each microservice:

```
com.fincity.saas.{module}/
├── {Module}Application.java       # @SpringBootApplication
├── configuration/                 # @Configuration beans
├── controller/                    # @RestController
├── service/                       # @Service business logic
├── dao/                           # @Service data access (JOOQ)
├── jooq/                          # Generated code (DO NOT EDIT)
├── model/                         # Request/response POJOs
├── dto/                           # DTOs extending AbstractDTO
├── enums/                         # Enumerations
├── util/                          # Utility classes
├── feign/                         # Feign client interfaces
└── mq/                            # RabbitMQ handlers
```

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Service classes | `{Entity}Service` | `ClientService`, `UserService` |
| DAO classes | `{Entity}DAO` | `ClientDAO`, `UserDAO` |
| Controller classes | `{Entity}Controller` | `ClientController` |
| DTO classes | Entity name | `Client`, `User`, `App` |
| JOOQ records | `Security{Entity}Record` | `SecurityClientRecord` |
| JOOQ tables | `SECURITY_{ENTITY}` | `SECURITY_CLIENT` |
| Cache names | `camelCase` | `"clientId"`, `"clientCodeId"` |
| Authority strings | `Authorities.{Entity}_{ACTION}` | `Authorities.Client_CREATE` |
| Message resource keys | `snake_case` | `params_not_found` |
| API routes | `api/{service}/{resource}` | `api/security/clients` |
| Flyway migrations | `Vn__Description.sql` | `V71__Add Client Manager.sql` |

## Lombok Usage

### Preferred Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Data` | Getters, setters, toString, equals, hashCode |
| `@Accessors(chain = true)` | Fluent setters returning `this` |
| `@EqualsAndHashCode(callSuper = true)` | Include parent fields in equality |
| `@ToString(callSuper = true)` | Include parent fields in string output |
| `@FieldNameConstants` | Generate string constants for field names |

### Avoid

- `@Builder` — not used in this codebase; use `@Accessors(chain = true)` instead
- `@AllArgsConstructor` / `@NoArgsConstructor` — use explicit constructors
- `@Slf4j` — use explicit `LoggerFactory.getLogger()` in DAOs

## Error Handling

Use `MessageResourceService` for all user-facing errors:

```java
// 404 - Entity not found
messageResourceService.throwMessage(
    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
    SecurityMessageResourceService.PARAMS_NOT_FOUND, "Client");

// 403 - Permission denied
messageResourceService.throwMessage(
    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
    SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client CREATE");

// 400 - Bad request
messageResourceService.throwMessage(
    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
    SecurityMessageResourceService.FIELDS_MISSING, "clientCode");
```

See [reactive-programming-guide.md](reactive-programming-guide.md) for how to use these in reactive chains.

## Configuration Properties

Use `@Value` for configuration injection:

```java
@Value("${security.subdomain.endings}")
private String[] subDomainURLEndings;

@Value("${jwt.key}")
private String tokenKey;
```

Use `@PostConstruct` for initialization after injection:

```java
@PostConstruct
private void init() {
    policyServices.put(AuthenticationPasswordType.PASSWORD, clientPasswordPolicyService);
    policyServices.put(AuthenticationPasswordType.PIN, clientPinPolicyService);
    policyServices.put(AuthenticationPasswordType.OTP, clientOtpPolicyService);
}
```
