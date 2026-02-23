# Reactive Programming Guide

This project is built entirely on **Project Reactor** with reactive database access (R2DBC). Every service method returns `Mono<T>` or `Flux<T>`. This guide covers the key reactive patterns used throughout the codebase.

## Why Reactive?

The platform uses non-blocking I/O everywhere:
- **R2DBC** for MySQL (reactive JDBC alternative)
- **Reactive Spring Data** for MongoDB
- **Reactive Lettuce** for Redis
- **Spring WebFlux** instead of Spring MVC

This means: no blocking calls, no thread-per-request model, and all operations compose asynchronously.

## Core Pattern: `FlatMapUtil.flatMapMono()`

The `FlatMapUtil` class (from the `reactor-flatmap-util` library) is the **most important pattern** in this codebase. It chains multiple `Mono` operations where each step can access all previous results.

### How It Works

`FlatMapUtil.flatMapMono()` takes 2-6+ supplier/function arguments. Each subsequent function receives all previous results as parameters:

```java
FlatMapUtil.flatMapMono(
    () -> step1(),                              // Supplier<Mono<A>>
    a -> step2(a),                              // Function<A, Mono<B>>
    (a, b) -> step3(a, b),                      // BiFunction<A, B, Mono<C>>
    (a, b, c) -> step4(a, b, c),                // TriFunction<A, B, C, Mono<D>>
    (a, b, c, d) -> step5(a, b, c, d),          // ...and so on
    (a, b, c, d, e) -> step6(a, b, c, d, e)
)
```

Each step:
1. Receives all previous results as parameters
2. Returns a `Mono<T>` that will be subscribed to
3. If any step returns `Mono.empty()`, the entire chain completes empty

### Simple Example: `ClientService.create()`

From `security/src/main/java/com/fincity/security/service/ClientService.java:285`:

```java
@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
@Override
public Mono<Client> create(Client entity) {

    return FlatMapUtil.flatMapMono(

            // Step 1: Get the current user's authentication context
            SecurityContextUtil::getUsersContextAuthentication,

            // Step 2: Create the client (uses result from step 1)
            ca -> super.create(entity.setLevelType(
                    Client.getChildClientLevelType(ca.getClientLevelType()))),

            // Step 3: Create hierarchy if non-system client (uses results from steps 1 and 2)
            (ca, client) -> {
                if (!ca.isSystemClient())
                    return this.clientHierarchyService
                            .create(ULongUtil.valueOf(ca.getUser().getClientId()), client.getId())
                            .map(x -> client);
                return Mono.just(client);
            })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.create"));
}
```

### Complex Example: `ClientManagerService.create()`

From `security/src/main/java/com/fincity/security/service/ClientManagerService.java:82`:

```java
public Mono<Boolean> create(ULong userId, ULong clientId) {

    return FlatMapUtil.flatMapMono(

            // Step 1: Get authentication context
            SecurityContextUtil::getUsersContextAuthentication,

            // Step 2: Read the user (depends on step 1)
            ca -> this.userService.readInternal(userId),

            // Step 3: Validate — user cannot manage their own client
            (ca, user) -> {
                if (user.getClientId().equals(clientId))
                    return this.messageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.HIERARCHY_ERROR, "Client manager");
                return Mono.just(Boolean.TRUE);
            },

            // Step 4: Check access permissions
            (ca, user, validated) -> this.checkAccess(user.getClientId()),

            // Step 5: Create the manager record in database
            (ca, user, validated, hasAccess) -> {
                if (!Boolean.TRUE.equals(hasAccess))
                    return Mono.empty();
                return this.dao.createIfNotExists(clientId, userId,
                        ULongUtil.valueOf(ca.getUser().getId()));
            },

            // Step 6: Evict caches and return success
            (ca, user, validated, hasAccess, result) ->
                    this.evictCacheForUserAndClient(userId, clientId)
                            .thenReturn(Boolean.TRUE))

            // Attach method name for logging
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.create"))

            // If chain completes empty, throw forbidden error
            .switchIfEmpty(this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Manager CREATE"));
}
```

### Standard Method Template

When writing a new service method, follow this structure:

```java
@PreAuthorize("hasAuthority('Authorities.Entity_ACTION')")
public Mono<Result> doSomething(ULong entityId) {

    return FlatMapUtil.flatMapMono(

            // Step 1: Always start with authentication context
            SecurityContextUtil::getUsersContextAuthentication,

            // Step 2: Fetch required data
            ca -> this.dao.read(entityId),

            // Step 3: Validate and process
            (ca, entity) -> {
                // Business logic here
                return processEntity(entity);
            })

            // Attach logging context
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "MyService.doSomething"))

            // Handle empty (not found) case
            .switchIfEmpty(this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    SecurityMessageResourceService.PARAMS_NOT_FOUND, "Entity"));
}
```

## `switchIfEmpty` for Error Handling

When a reactive chain completes empty (no value emitted), use `switchIfEmpty` with `Mono.defer()` to throw errors:

```java
// Pattern 1: switchIfEmpty with messageResourceService
return this.dao.read(id)
        .switchIfEmpty(this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                SecurityMessageResourceService.PARAMS_NOT_FOUND, "Entity"));

// Pattern 2: switchIfEmpty with Mono.defer (for lazy evaluation)
return this.dao.read(id)
        .switchIfEmpty(Mono.defer(() ->
                Mono.error(new GenericException(HttpStatus.NOT_FOUND, "Entity not found"))));
```

**Important**: `messageResourceService.throwMessage()` already uses `Mono.defer()` internally, so you don't need to wrap it.

## Error Handling with `MessageResourceService`

The `MessageResourceService` provides localized error messages. Three common patterns:

```java
// Pattern 1: Throw with Function (most common)
this.messageResourceService.throwMessage(
        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
        SecurityMessageResourceService.PARAMS_NOT_FOUND, "Client");

// Pattern 2: Inline conditional error
if (!isValid)
    return this.messageResourceService.throwMessage(
            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            SecurityMessageResourceService.FIELDS_MISSING, "fieldName");

// Pattern 3: Terminal switchIfEmpty
.switchIfEmpty(this.messageResourceService.throwMessage(
        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "action"));
```

Common error codes (from `SecurityMessageResourceService`):
- `PARAMS_NOT_FOUND` — Entity not found (404)
- `FORBIDDEN_PERMISSION` — Access denied (403)
- `FORBIDDEN_CREATE` / `FORBIDDEN_UPDATE` / `FORBIDDEN_DELETE` — CRUD permission denied (403)
- `FIELDS_MISSING` — Required fields not provided (400)
- `HIERARCHY_ERROR` — Client hierarchy violation (400)

## Context Writing for Logging

Every `FlatMapUtil` chain should end with `.contextWrite()` to attach the method name for log tracing:

```java
.contextWrite(Context.of(LogUtil.METHOD_NAME, "ServiceName.methodName"))
```

This enables the logging framework to include the method name in log output, critical for debugging reactive chains where stack traces are less useful.

## `SecurityContextUtil` — Accessing Auth Context

The reactive security context is accessed through static methods on `SecurityContextUtil`:

```java
import com.fincity.saas.commons.security.util.SecurityContextUtil;

// Get full authentication context
Mono<ContextAuthentication> auth = SecurityContextUtil.getUsersContextAuthentication();

// Get current user
Mono<ContextUser> user = SecurityContextUtil.getUsersContextUser();

// Get current user's client ID
Mono<BigInteger> clientId = SecurityContextUtil.getUsersClientId();

// Check if user has specific authority
Mono<Boolean> hasAuth = SecurityContextUtil.hasAuthority("Authorities.Client_CREATE");
```

The `ContextAuthentication` object provides:
- `getUser()` — `ContextUser` with userId, clientId, userName, email, authorities
- `isSystemClient()` — Whether the user belongs to the system client (`typeCode == "SYS"`)
- `getClientCode()` — Current client code
- `getClientTypeCode()` — Client type: `"SYS"`, `"BUS"`, `"INDV"`
- `getLoggedInFromClientId()` — Client from which login occurred

## Caching Pattern

Use `CacheService` for caching reactive results:

```java
// Cache a value or fetch it
public Mono<Client> readInternal(ULong id) {
    return this.cacheService.cacheValueOrGet(
            CACHE_NAME_CLIENT_ID,                    // Cache name
            () -> this.dao.readInternal(id),          // Supplier if cache miss
            id);                                      // Cache key(s)
}

// Evict cache on mutation
@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
@Override
public Mono<Client> update(Client entity) {
    return super.update(entity)
            .flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_CODE, entity.getId())
                    .flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, entity.getId()))
                    .map(x -> e));
}
```

## Common Reactive Pitfalls

### 1. Never Block in Reactive Code
```java
// WRONG — blocks the event loop
Client client = this.service.read(id).block();

// CORRECT — stay reactive
return this.service.read(id).flatMap(client -> processClient(client));
```

### 2. Use `Mono.defer()` for Lazy Evaluation
```java
// WRONG — error is created eagerly even if never needed
.switchIfEmpty(Mono.error(new RuntimeException("Not found")));

// CORRECT — error is created only when switchIfEmpty triggers
.switchIfEmpty(Mono.defer(() -> Mono.error(new RuntimeException("Not found"))));
```

### 3. Don't Subscribe Inside Reactive Chains
```java
// WRONG — fire-and-forget, result is lost
.flatMap(entity -> {
    this.auditService.log(entity).subscribe();  // DON'T DO THIS
    return Mono.just(entity);
});

// CORRECT — chain the operation
.flatMap(entity -> this.auditService.log(entity).thenReturn(entity));
```

### 4. `map()` vs `flatMap()`
```java
// map() — synchronous transformation (no Mono/Flux return)
.map(entity -> entity.getName())

// flatMap() — asynchronous operation (returns Mono/Flux)
.flatMap(entity -> this.service.process(entity))
```

### 5. Empty Signals Propagate
In `FlatMapUtil.flatMapMono()`, if **any step** returns `Mono.empty()`, all subsequent steps are skipped and the entire chain completes empty. Use `switchIfEmpty` at the end to handle this.

## Quick Reference

| Operation | Pattern |
|-----------|---------|
| Chain multiple Monos | `FlatMapUtil.flatMapMono(step1, step2, ...)` |
| Handle not found | `.switchIfEmpty(messageResourceService.throwMessage(...))` |
| Log method name | `.contextWrite(Context.of(LogUtil.METHOD_NAME, "..."))` |
| Get auth context | `SecurityContextUtil::getUsersContextAuthentication` |
| Check system client | `ca.isSystemClient()` |
| Cache read | `cacheService.cacheValueOrGet(name, supplier, keys)` |
| Cache evict | `cacheService.evict(name, keys)` |
| Transform sync | `.map(x -> transform(x))` |
| Transform async | `.flatMap(x -> asyncOp(x))` |
| Run after | `.thenReturn(value)` or `.then(otherMono)` |
