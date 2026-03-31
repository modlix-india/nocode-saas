# Plan: Cache Entity Resolution in Analytics Using CacheService

## Context

The analytics response builders (`toFilterablePageResponse`, `toFilterableListResponse`) make **remote Feign calls** for every request to resolve entity details:
- `resolveClients(ids)` → `securityService.getClientInternalBatch()` (HTTP to security service)
- `resolveAssignedUsers(ids)` → `securityService.getUsersInternalBatch()` (HTTP to security service)
- `resolveProducts(access, ids)` → `productService.getAllProducts()` (DB query)
- `resolveSelectedProductTemplates()` → `productTemplateService.readById()` per template (DB queries)

These entities (Client, User, Product, ProductTemplate) change infrequently but are fetched on every analytics request. The DAO distinct-ID queries are fast SQL and don't need caching. The expensive part is the entity resolution.

**Approach:** Cache individual entities by ID using the existing `CacheService` (Caffeine + Redis). Fetch IDs from DB as-is, then resolve each ID through cache. Evict on entity update.

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `analytics/service/base/BaseAnalyticsService.java` | Inject CacheService, add cached resolution helpers |
| 2 | `analytics/service/TicketBucketService.java` | Replace direct Feign/service calls with cached versions |
| 3 | `service/product/ProductService.java` | Add cache eviction on product create/update |
| 4 | `service/product/template/ProductTemplateService.java` | Add cache eviction on template create/update |

## Step 1: Inject CacheService into BaseAnalyticsService

**File:** `analytics/service/base/BaseAnalyticsService.java`

Add CacheService alongside existing service injections:
```java
protected CacheService cacheService;

@Autowired
private void setCacheService(CacheService cacheService) {
    this.cacheService = cacheService;
}
```

Define cache name constants:
```java
private static final String CACHE_CLIENT = "analytics:client";
private static final String CACHE_USER = "analytics:user";
private static final String CACHE_PRODUCT = "analytics:product";
private static final String CACHE_PRODUCT_TEMPLATE = "analytics:productTemplate";
```

## Step 2: Add Cached Resolution Helpers in BaseAnalyticsService

Add methods that cache **individual entities by ID**, then compose batch results:

```java
protected Mono<Client> getCachedClient(BigInteger clientId) {
    return cacheService.cacheValueOrGet(CACHE_CLIENT,
        () -> securityService.getClientInternalBatch(List.of(clientId), fetchManagersParams)
                .map(list -> list.isEmpty() ? null : list.get(0)),
        clientId);
}

protected Mono<User> getCachedUser(BigInteger userId) {
    return cacheService.cacheValueOrGet(CACHE_USER,
        () -> securityService.getUsersInternalBatch(List.of(userId), null)
                .map(list -> list.isEmpty() ? null : list.get(0)),
        userId);
}

protected Mono<Product> getCachedProduct(ProcessorAccess access, ULong productId) {
    return cacheService.cacheValueOrGet(CACHE_PRODUCT,
        () -> productService.getAllProducts(access, List.of(productId))
                .map(list -> list.isEmpty() ? null : list.get(0)),
        access.getAppCode(), access.getEffectiveClientCode(), productId);
}

protected Mono<ProductTemplate> getCachedProductTemplate(ProcessorAccess access, ULong templateId) {
    return cacheService.cacheValueOrGet(CACHE_PRODUCT_TEMPLATE,
        () -> productTemplateService.readById(access, templateId),
        access.getAppCode(), access.getEffectiveClientCode(), templateId);
}
```

Add batch methods that fan out to individual cached lookups:
```java
protected Mono<List<Client>> getCachedClients(List<ULong> clientIds) {
    if (clientIds == null || clientIds.isEmpty()) return Mono.just(List.of());
    return Flux.fromIterable(clientIds)
        .flatMap(id -> getCachedClient(id.toBigInteger()))
        .filter(Objects::nonNull)
        .collectList();
}
// Same pattern for Users, Products, ProductTemplates
```

## Step 3: Update TicketBucketService Resolution Methods

**File:** `analytics/service/TicketBucketService.java`

Replace direct calls with cached versions:

**`resolveClients(List<ULong>)`** — replace `securityService.getClientInternalBatch(idsBigInt, params)` with `getCachedClients(clientIds)`

**`resolveAssignedUsers(List<ULong>)`** — replace `securityService.getUsersInternalBatch(idsBigInt, null)` with `getCachedUsers(userIds)`

**`resolveProducts(ProcessorAccess, List<ULong>)`** — replace `productService.getAllProducts(access, productIds)` with `getCachedProducts(access, productIds)`

**`resolveSelectedProductTemplates()`** — replace `productTemplateService.readById(access, id)` with `getCachedProductTemplate(access, id)`

**`resolveProductTemplateFromProducts()`** — same, use cached product template reads

**`resolveProductTemplates()` / `applyProductTemplateToFilter()`** — use cached reads for both product templates and product list

## Step 4: Add Cache Eviction on Entity Updates

**File:** `service/product/ProductService.java`

In `update()` and `create()` methods, evict the product cache:
```java
// After successful update/create:
cacheService.evict(CACHE_PRODUCT, access.getAppCode(), access.getEffectiveClientCode(), product.getId())
```

**File:** `service/product/template/ProductTemplateService.java`

Same pattern for template updates:
```java
cacheService.evict(CACHE_PRODUCT_TEMPLATE, access.getAppCode(), access.getEffectiveClientCode(), template.getId())
```

**Client/User eviction:** These are managed by the security service (different microservice). Since CacheService uses Redis pub/sub, we have two options:
- Use TTL-based expiry (entities cached for ~5 minutes) — simplest
- Listen for security service events — more complex, cross-service

**Recommendation:** Rely on CacheService's default TTL for Client/User caches. Product/ProductTemplate get explicit eviction since they're local to entity-processor.

## Cache Key Design

| Cache | Key Pattern | Eviction Trigger |
|-------|-------------|-----------------|
| `analytics:client` | `{clientId}` | TTL (~5 min) |
| `analytics:user` | `{userId}` | TTL (~5 min) |
| `analytics:product` | `{appCode}:{clientCode}:{productId}` | Product create/update |
| `analytics:productTemplate` | `{appCode}:{clientCode}:{templateId}` | Template create/update |

## Existing Code Reused

- `CacheService.cacheValueOrGet(cacheName, supplier, keys...)` — main caching pattern
- `CacheService.evict(cacheName, keys...)` — eviction on entity change
- Pattern from `SourceConfigService` (entity-processor) — cache + evict example
- `IFeignSecurityService.getClientInternalBatch/getUsersInternalBatch` — unchanged, just called through cache

## Verification

1. `cd nocode-saas/entity-processor && mvn compile`
2. Call an analytics endpoint — first call hits security service, second call within TTL should skip the Feign call (verify via debug logs)
3. Update a product — verify the analytics response reflects the change on next call (eviction worked)
4. Check Redis keys — `redis-cli KEYS "analytics:*"` should show cached entries
