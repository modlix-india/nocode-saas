# Security & Multi-Tenancy

This document covers the authentication, authorization, and multi-tenancy patterns used throughout the platform.

## Authentication Flow

### JWT Token Authentication

1. User authenticates via `POST /api/security/authenticate` with credentials
2. Security service validates credentials and returns a JWT token
3. All subsequent requests include the JWT token in the `Authorization` header
4. The Gateway service validates the token and populates the reactive security context
5. Downstream services access the authenticated user via `SecurityContextUtil`

### ContextAuthentication

The authenticated user's context is stored in `ContextAuthentication` (`commons-security`):

```java
// Key fields on ContextAuthentication
ca.getUser()                    // ContextUser — userId, clientId, userName, email, authorities
ca.isAuthenticated()            // Whether the user is authenticated
ca.isSystemClient()             // Whether user belongs to system client (typeCode == "SYS")
ca.getClientCode()              // Current client code (e.g., "SYSTEM", "ACME")
ca.getClientTypeCode()          // "SYS", "BUS", or "INDV"
ca.getClientLevelType()         // "SYSTEM", "CLIENT", "CUSTOMER", "CONSUMER"
ca.getLoggedInFromClientId()    // Client from which login occurred
ca.getLoggedInFromClientCode()  // Client code from which login occurred
ca.getAccessToken()             // Current access token
ca.getUrlClientCode()           // Client code from URL
ca.getUrlAppCode()              // App code from URL
```

### SecurityContextUtil

Access the reactive security context anywhere in the service layer:

```java
import com.fincity.saas.commons.security.util.SecurityContextUtil;

// Get full authentication context
Mono<ContextAuthentication> auth = SecurityContextUtil.getUsersContextAuthentication();

// Get current user
Mono<ContextUser> user = SecurityContextUtil.getUsersContextUser();

// Get current user's client ID
Mono<BigInteger> clientId = SecurityContextUtil.getUsersClientId();

// Check authority
Mono<Boolean> hasAuth = SecurityContextUtil.hasAuthority("Authorities.Client_CREATE");

// Get user's locale
Mono<Locale> locale = SecurityContextUtil.getUsersLocale();
```

## Authorization

### Authority-Based Access Control

Every public service method must be annotated with `@PreAuthorize`:

```java
@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
public Mono<Client> create(Client entity) { ... }
```

### Authority Naming Convention

Pattern: `Authorities.{Entity}_{ACTION}`

| Authority | Description |
|-----------|-------------|
| `Authorities.Client_CREATE` | Create clients |
| `Authorities.Client_READ` | Read clients |
| `Authorities.Client_UPDATE` | Update clients |
| `Authorities.Client_DELETE` | Delete clients |
| `Authorities.User_CREATE` | Create users |
| `Authorities.Role_READ` | Read roles |
| `Authorities.Application_UPDATE` | Update applications |
| `Authorities.Profile_DELETE` | Delete profiles |
| `Authorities.ROLE_Owner` | Owner role (super admin) |
| `Authorities.Logged_IN` | Any logged-in user |

Authorities are assigned to users through **Roles** and **Profiles**.

## Multi-Tenancy Model

### Client Hierarchy

The platform uses a hierarchical client model with four levels:

```
SYSTEM (Level 0) — Platform operator
└── CLIENT (Level 1) — Direct customers
    └── CUSTOMER (Level 2) — Customer's customers
        └── CONSUMER (Level 3) — End consumers
```

Each client has a `ClientHierarchy` record that tracks its parent chain:

```
ClientHierarchy {
    clientId:            ULong    // This client
    manageClientLevel0:  ULong    // Direct parent
    manageClientLevel1:  ULong    // Grandparent
    manageClientLevel2:  ULong    // Great-grandparent
    manageClientLevel3:  ULong    // Root level
}
```

### Client Types

| Type Code | Enum | Description |
|-----------|------|-------------|
| `SYS` | `SYSTEM` | System/platform client — can see all data |
| `BUS` | `CLIENT` / `CUSTOMER` | Business client — can manage child clients |
| `INDV` | `CONSUMER` | Individual client — leaf node |

### Access Control Logic

The system client (`SYS`) bypasses hierarchy checks. Non-system clients can only access data belonging to their own client or child clients:

```java
// Common pattern in service methods
(ca, data) -> {
    if (ca.isSystemClient())
        return Mono.just(data);  // System sees everything

    // Non-system: verify hierarchy
    return this.clientHierarchyService
            .isClientBeingManagedBy(
                    ULongUtil.valueOf(ca.getUser().getClientId()),
                    targetClientId)
            .flatMap(isManaged -> Boolean.TRUE.equals(isManaged)
                    ? Mono.just(data)
                    : Mono.empty());
}
```

### DAO-Level Tenant Filtering

DAOs automatically filter results by client hierarchy. The `filter()` method in DAOs appends a hierarchy condition:

```java
@Override
public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {
    return super.filter(condition, selectJoinStep)
            .flatMap(cond -> SecurityContextUtil.getUsersContextAuthentication()
                    .map(ca -> {
                        // System client sees all
                        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                            return cond;

                        // Non-system clients filtered by hierarchy
                        ULong clientId = ULong.valueOf(ca.getUser().getClientId());
                        return DSL.and(cond,
                                ClientHierarchyDAO.getManageClientCondition(clientId));
                    }));
}
```

This means paginated list queries automatically respect tenant boundaries — a client can only see entities belonging to itself or its children.

### Client Managers

In addition to the hierarchy, users can be designated as **Client Managers** — users from a parent client who manage a child client:

```java
// ClientManagerService — assign a user as manager of a client
public Mono<Boolean> create(ULong userId, ULong clientId) { ... }

// Check if a user is a manager of a specific client
public Mono<Boolean> isUserClientManager(ContextAuthentication ca, ULong targetClientId) { ... }
```

## Key Services

### ClientHierarchyService

Located at `security/src/main/java/com/fincity/security/service/ClientHierarchyService.java`:

```java
// Create hierarchy relationship (parent → child)
Mono<ClientHierarchy> create(ULong managingClientId, ULong clientId)

// Get full hierarchy for a client
Mono<ClientHierarchy> getClientHierarchy(ULong clientId)

// Get ordered list of client IDs in hierarchy
Mono<List<ULong>> getClientHierarchyIdInOrder(ULong clientId)

// Get IDs of all clients managing this client
Mono<List<ULong>> getManagingClientIds(ULong clientId)

// Check if one client manages another
Mono<Boolean> isClientBeingManagedBy(ULong managingClientId, ULong clientId)
```

### ClientManagerService

Located at `security/src/main/java/com/fincity/security/service/ClientManagerService.java`:

```java
// Assign user as client manager
Mono<Boolean> create(ULong userId, ULong clientId)

// Remove user as client manager
Mono<Boolean> delete(ULong userId, ULong clientId)

// Get clients managed by a user
Mono<Page<Client>> getClientsOfUser(ULong userId, Pageable pageable)

// Get manager user IDs for a client
Mono<List<ULong>> getManagerIds(ULong clientId)
```

## SOX Audit Logging

Services in the security module extend `AbstractSecurityUpdatableDataService`, which automatically logs all CRUD operations for compliance:

From `security/src/main/java/com/fincity/security/service/AbstractSecurityUpdatableDataService.java`:

```java
public abstract class AbstractSecurityUpdatableDataService<R, I, D, O>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

    @Autowired
    private SoxLogService soxLogService;

    public abstract SecuritySoxLogObjectName getSoxObjectName();

    @Override
    public Mono<D> create(D entity) {
        return super.create(entity).map(e -> {
            this.dao.getPojoClass()
                    .map(Class::getSimpleName)
                    .flatMap(description -> soxLogService.create(
                            new SoxLog()
                                    .setActionName(SecuritySoxLogActionName.CREATE)
                                    .setObjectName(getSoxObjectName())
                                    .setObjectId(ULongUtil.valueOf(e.getId()))
                                    .setDescription(description + " created ")))
                    .subscribe();
            return e;
        });
    }
    // Same pattern for update() and delete()
}
```

Each service declares its audit object name:
```java
@Override
public SecuritySoxLogObjectName getSoxObjectName() {
    return SecuritySoxLogObjectName.CLIENT;
}
```

Log actions: `CREATE`, `UPDATE`, `DELETE`, `ASSIGN`, `UNASSIGN`

## Error Messages

The `SecurityMessageResourceService` provides localized error messages. Common error codes:

| Constant | Key | Typical HTTP Status |
|----------|-----|-------------------|
| `PARAMS_NOT_FOUND` | `params_not_found` | 404 |
| `FORBIDDEN_PERMISSION` | `forbidden_permission` | 403 |
| `FORBIDDEN_CREATE` | `forbidden_create` | 403 |
| `FORBIDDEN_UPDATE` | `forbidden_update` | 403 |
| `FORBIDDEN_DELETE` | `forbidden_delete` | 403 |
| `HIERARCHY_ERROR` | `hierarchy_error` | 400 |
| `FIELDS_MISSING` | `fields_missing` | 400 |
| `USER_NOT_ACTIVE` | `user_not_active` | 400 |
| `UNKNOWN_CLIENT` | `unknown_client` | 400 |
| `INACTIVE_CLIENT` | `inactive_client` | 400 |
| `USER_ALREADY_EXISTS` | `user_already_exists` | 409 |
| `TOKEN_EXPIRED` | `token_expired` | 401 |
| `LOGIN_REQUIRED` | `login_required` | 401 |

Error messages are defined in `src/main/resources/messages_en.properties` with support for parameterized placeholders.
