# Entity Processor: Rules and Automation System

## Table of Contents

1. [Rule System Overview](#1-rule-system-overview)
2. [Architecture and Class Hierarchy](#2-architecture-and-class-hierarchy)
3. [Rule Scoping and Precedence](#3-rule-scoping-and-precedence)
4. [Rule Ordering and Default Rules](#4-rule-ordering-and-default-rules)
5. [AbstractCondition System](#5-abstractcondition-system)
6. [Condition Evaluation Engine](#6-condition-evaluation-engine)
7. [Ticket Duplication Detection](#7-ticket-duplication-detection)
8. [Phone/Email Duplication Rules](#8-phoneemail-duplication-rules)
9. [User Assignment Rules (ProductTicketCRule)](#9-user-assignment-rules-productticketcrule)
10. [User Distribution Algorithms](#10-user-distribution-algorithms)
11. [Read/Update Permission Rules (ProductTicketRuRule)](#11-readupdate-permission-rules-productticketrurule)
12. [User Distribution Services](#12-user-distribution-services)
13. [Automatic Reassignment on Stage Change](#13-automatic-reassignment-on-stage-change)
14. [Activity Automation](#14-activity-automation)
15. [DNC (Do-Not-Call) Automation](#15-dnc-do-not-call-automation)
16. [Owner Synchronization](#16-owner-synchronization)
17. [Caching Architecture and Invalidation](#17-caching-architecture-and-invalidation)
18. [Error Handling and Message Constants](#18-error-handling-and-message-constants)
19. [REST API Endpoints](#19-rest-api-endpoints)
20. [Complete Flowcharts and Decision Trees](#20-complete-flowcharts-and-decision-trees)
21. [Practical Scenarios and Examples](#21-practical-scenarios-and-examples)
22. [Database Schema Reference](#22-database-schema-reference)
23. [Troubleshooting Guide](#23-troubleshooting-guide)

---

## 1. Rule System Overview

The Entity Processor module implements a sophisticated, multi-layered rule engine designed for CRM
ticket management. This rule engine governs three critical operational areas:

1. **Ticket Duplication Detection** -- Prevents duplicate ticket creation by checking incoming
   tickets against existing ones based on configurable conditions, phone numbers, emails, source
   channels, and stage thresholds.

2. **User Assignment and Distribution** -- Automatically assigns tickets to users upon creation
   using configurable rules with multiple distribution algorithms (round-robin, random, etc.),
   supporting condition-based routing to different user pools.

3. **Read/Update Permission Control** -- Governs which users can read or update tickets for a
   given product, based on their roles, profiles, designations, and departments.

### Design Principles

The rule engine follows several key design principles:

- **Reactive-first**: All operations return `Mono<T>` or `Flux<T>`, using Project Reactor for
  non-blocking execution throughout the rule evaluation pipeline.
- **Multi-tenant isolation**: Rules are always scoped to a specific `appCode` and `clientCode`,
  ensuring complete tenant isolation.
- **Hierarchical scoping**: Rules can be defined at the product level or product-template level,
  with product-level rules taking precedence.
- **Ordered evaluation**: Rules are evaluated in ascending order, with the first matching rule
  winning (short-circuit evaluation).
- **Cache-backed**: All rule lookups are cached with intelligent invalidation to minimize
  database queries during high-throughput ticket processing.
- **Extensible conditions**: The `AbstractCondition` system supports arbitrary nesting of AND/OR
  conditions with multiple filter operators.

### Key Terminology

| Term | Definition |
|------|-----------|
| **Product** | A CRM product (e.g., "Home Loan", "Insurance Policy") that tickets belong to |
| **ProductTemplate** | A reusable template that products are based on; defines shared stages and rules |
| **Stage** | A workflow step in the ticket lifecycle (e.g., "New", "Contacted", "Qualified") |
| **Status** | A sub-state within a stage |
| **Order** | An integer that determines rule evaluation priority; 0 = default, 1+ = conditional |
| **Source** | The channel a ticket originates from (e.g., "WEBSITE", "API", "MANUAL") |
| **SubSource** | A refinement of source (e.g., "GOOGLE_ADS", "FACEBOOK") |
| **ProcessorAccess** | The security context containing appCode, clientCode, userId, and user details |
| **DistributionType** | The algorithm used to assign users (ROUND_ROBIN, RANDOM, etc.) |
| **DNC** | Do-Not-Call flag on a ticket or business partner |

---

## 2. Architecture and Class Hierarchy

### Service Hierarchy

```
BaseUpdatableService<R, D, O>
    |
    +-- BaseRuleService<R, D extends BaseRuleDto, O extends BaseRuleDAO, U>
    |       implements IRuleUserDistributionService<D, U>
    |       |
    |       +-- TicketDuplicationRuleService
    |       |       Record: EntityProcessorTicketDuplicationRulesRecord
    |       |       DTO:    TicketDuplicationRule
    |       |       DAO:    TicketDuplicationRuleDAO
    |       |       Dist:   NoOpUserDistribution (no user distribution)
    |       |
    |       +-- ProductTicketCRuleService
    |       |       Record: EntityProcessorProductTicketCRulesRecord
    |       |       DTO:    ProductTicketCRule
    |       |       DAO:    ProductTicketCRuleDAO
    |       |       Dist:   TicketCUserDistribution
    |       |
    |       +-- ProductTicketRuRuleService
    |               Record: EntityProcessorProductTicketRuRulesRecord
    |               DTO:    ProductTicketRuRule
    |               DAO:    ProductTicketRuRuleDAO
    |               Dist:   TicketRuUserDistribution
    |
    +-- TicketPeDuplicationRuleService (extends BaseUpdatableService directly)
    |
    +-- BaseUserDistributionService<R, D extends BaseUserDistributionDto, O>
            |
            +-- TicketCUserDistributionService
            +-- TicketRuUserDistributionService
```

### DTO Hierarchy

```
BaseUpdatableDto<T>
    |
    +-- BaseRuleDto<U extends BaseUserDistributionDto, T extends BaseRuleDto>
    |       Fields: productId, productTemplateId, order, userDistributionType,
    |               lastAssignedUserId, condition, userDistributions
    |       |
    |       +-- TicketDuplicationRule
    |       |       Additional: source, subSource, maxStageId
    |       |
    |       +-- ProductTicketCRule
    |       |       Additional: stageId
    |       |
    |       +-- ProductTicketRuRule
    |               Additional: canEdit
    |
    +-- BaseUserDistributionDto<T>
    |       Fields: ruleId, userId, roleId, profileId, designationId, departmentId
    |       |
    |       +-- TicketCUserDistribution
    |       +-- TicketRuUserDistribution
    |       +-- NoOpUserDistribution
    |
    +-- TicketPeDuplicationRule (extends BaseUpdatableDto directly)
            Fields: phoneNumberAndEmailType
```

### DAO Hierarchy

```
BaseUpdatableDAO<R, D>
    |
    +-- BaseRuleDAO<R, U extends BaseUserDistributionDto, D extends BaseRuleDto>
    |       Methods: getRules(), getRule(), getBaseConditions(), decrementOrdersAfter()
    |       |
    |       +-- TicketDuplicationRuleDAO
    |       |       Additional: getRules(access, productId, templateId, source, subSource)
    |       |
    |       +-- ProductTicketCRuleDAO
    |       |       Additional: getRules(access, productId, templateId, stageId)
    |       |
    |       +-- ProductTicketRuRuleDAO
    |               Additional: getUserConditions(access, isEdit, user)
    |
    +-- BaseUserDistributionDAO<R, D extends BaseUserDistributionDto>
    |       Methods: getUserDistributions(access, ruleId)
    |       |
    |       +-- TicketCUserDistributionDAO
    |       +-- TicketRuUserDistributionDAO
    |
    +-- TicketPeDuplicationRuleDAO
```

### Interface: IRuleUserDistributionService

```java
public interface IRuleUserDistributionService<
        D extends BaseRuleDto<U, D>,
        U extends BaseUserDistributionDto<U>> {

    // Override in concrete services to provide distribution service
    default BaseUserDistributionService getUserDistributionService() {
        return null; // Returns null = distribution disabled
    }

    // Automatically derived from getUserDistributionService()
    default boolean isUserDistributionEnabled() {
        return this.getUserDistributionService() != null;
    }

    // CRUD lifecycle hooks for user distributions
    default Mono<List<U>> createUserDistribution(ProcessorAccess access, D created, D requestEntity);
    default Mono<List<U>> updateUserDistribution(ProcessorAccess access, D updated, D requestEntity);
    default Mono<Integer> deleteUserDistribution(ProcessorAccess access, D entity);

    // Attach distribution data to rule listings
    default Mono<List<D>> attachDistributions(ProcessorAccess access, List<D> rules);
    default Mono<List<Map<String, Object>>> attachDistributionsEager(
            ProcessorAccess access, List<Map<String, Object>> rules);
}
```

This interface enables the `BaseRuleService` to optionally manage user distributions. The
`TicketDuplicationRuleService` returns `null` from `getUserDistributionService()` because
duplication rules do not assign users. The `ProductTicketCRuleService` and
`ProductTicketRuRuleService` both return their respective distribution services.

---

## 3. Rule Scoping and Precedence

### Product vs. ProductTemplate Scoping

Every rule must be scoped to either a **Product** or a **ProductTemplate**, but never both
simultaneously:

```
Rule
  |-- productId: ULong (nullable)
  |-- productTemplateId: ULong (nullable)

  Constraint: Exactly one of productId or productTemplateId must be non-null
```

When a rule is created with a `productId`:
1. The system looks up the product to find its associated `productTemplateId`
2. The rule is stored with `productId` set and `productTemplateId` set to null
3. This makes it a **product-level rule** -- specific to that product

When a rule is created with a `productTemplateId` (and no `productId`):
1. The system validates the template exists
2. The rule is stored with `productTemplateId` set and `productId` set to null
3. This makes it a **template-level rule** -- applies to all products using that template

### Resolution from BaseRuleService.updateProductProductTemplate()

```java
protected Mono<D> updateProductProductTemplate(ProcessorAccess access, D entity) {
    if (entity.getProductId() == null)
        // Template-scoped: validate template exists, clear productId
        return this.productTemplateService
            .readById(access, entity.getProductTemplateId())
            .map(template -> entity.setProductTemplateId(template.getId())
                                   .setProductId(null));

    // Product-scoped: lookup product, validate it has a template, clear templateId
    return this.productService.readById(access, entity.getProductId())
        .flatMap(product -> {
            if (product.getProductTemplateId() == null)
                return throwMessage(PRODUCT_TEMPLATE_MISSING, product.getId());
            return Mono.just(entity.setProductId(product.getId())
                                   .setProductTemplateId(null));
        });
}
```

### Precedence Resolution

When evaluating rules for a ticket, the system always follows this precedence:

```
1. Try PRODUCT-LEVEL rules for the ticket's productId
2. If no product-level rules found --> fall back to TEMPLATE-LEVEL rules
3. If no template-level rules found --> no rule applies (use system default behavior)
```

This precedence is implemented differently across rule types:

**Duplication Rules** (TicketDuplicationRuleService):
```
getProductDuplicateCondition(productId)
    .switchIfEmpty(getProductTemplateDuplicateCondition(templateId))
```

**Creation Rules** (ProductTicketCRuleService):
Two modes based on `product.isOverrideCTemplate()`:
- **Override mode**: Product rules completely replace template rules
- **Combine mode**: Product rules are merged with template rules (product rules get higher priority)

```
// Override mode
getProductRules(productId, stageId)
    .flatMap(rules -> rules.isEmpty()
        ? getProductTemplateRules(templateId, stageId)  // Fallback only if empty
        : Mono.just(rules))                              // Product rules win completely

// Combine mode
Mono.zip(
    getProductRules(productId, stageId),
    getProductTemplateRules(templateId, stageId),
    (productRules, templateRules) -> {
        // Product rules placed at higher priority orders
        // Template rules placed at lower priority orders
        // Both sets evaluated together
    })
```

---

## 4. Rule Ordering and Default Rules

### Order Field Semantics

Every rule has an integer `order` field that controls evaluation priority:

| Order Value | Type | Behavior |
|-------------|------|----------|
| `0` | Default rule | Used as a fallback when no conditional rule matches |
| `1` | Conditional rule | Evaluated first |
| `2` | Conditional rule | Evaluated second (if rule 1 did not match) |
| `3` | Conditional rule | Evaluated third |
| `N` | Conditional rule | Evaluated Nth |

### Default Rule (Order 0)

The default rule is a special fallback:

```java
// From BaseRuleDto
public static final int DEFAULT_ORDER = BigInteger.ZERO.intValue(); // = 0

public boolean isDefault() {
    return this.order == DEFAULT_ORDER;
}
```

Characteristics of the default rule:
- Its condition is **optional** (can be null) -- it always "matches" as a fallback
- It is only used when no conditional rule (order > 0) matches
- There can be at most one default rule per product (or template)
- For C-Rules, the default rule has its own user distribution list

### Conditional Rules (Order > 0)

Conditional rules require a non-null, non-empty condition:

```java
// From BaseRuleService.checkEntity()
if (!entity.isDefault()
        && (entity.getCondition() == null || entity.getCondition().isEmpty()))
    return throwMessage(RULE_CONDITION_MISSING, entity.getOrder());
```

Evaluation is short-circuit: the first rule whose condition evaluates to `true` is selected,
and subsequent rules are not evaluated.

### Order Uniqueness

No two rules for the same product (or template) can share the same order:

```java
// From BaseRuleService.checkEntity()
this.dao.getRule(null, access, entity.getProductId(),
                 entity.getProductTemplateId(), entity.getOrder())
    .flatMap(existing -> {
        if (existing != null && !existing.getId().equals(entity.getId()))
            return throwMessage(DUPLICATE_RULE_ORDER, existing.getId(), entity.getOrder());
        return Mono.just(entity);
    });
```

### Order Decrement on Delete

When a rule is deleted, all rules with higher orders are decremented by 1 to maintain
contiguous ordering:

```java
// From BaseRuleDAO.decrementOrdersAfter()
dslContext.update(this.table)
    .set(this.orderField, this.orderField.minus(1))
    .where(jCondition.and(isActiveTrue())
                      .and(this.orderField.gt(deletedOrder)));
```

**Example**: If rules exist with orders [0, 1, 2, 3] and rule 2 is deleted:
- Rule at order 3 becomes order 2
- Final state: [0, 1, 2]

### Decision Tree: Rule Order Assignment

```
Creating a new rule:
    |
    +-- order == 0?
    |       YES --> This is a default rule
    |       |       +-- Does a default rule already exist for this product?
    |       |               YES --> Reject: DUPLICATE_RULE_ORDER
    |       |               NO  --> Create as default (condition optional)
    |       |
    |       NO  --> This is a conditional rule (order > 0)
    |               +-- Does another rule with this order exist?
    |               |       YES --> Reject: DUPLICATE_RULE_ORDER
    |               |       NO  --> Continue
    |               +-- Is condition provided?
    |                       YES --> Create the conditional rule
    |                       NO  --> Reject: RULE_CONDITION_MISSING
```

---

## 5. AbstractCondition System

The rule engine uses a tree-structured condition system for expressing complex filter logic.
All conditions extend `AbstractCondition`, which is a serializable, nestable data structure.

### Class Hierarchy

```
AbstractCondition (abstract)
    |
    +-- FilterCondition      -- Single field comparison
    |
    +-- ComplexCondition      -- Nested AND/OR of conditions
```

### FilterCondition

A `FilterCondition` represents a single field-level comparison:

```java
@Data
@Accessors(chain = true)
public class FilterCondition extends AbstractCondition {

    private String field;                                  // Field path (e.g., "source", "stage")
    private FilterConditionOperator operator = EQUALS;     // Comparison operator
    private Object value;                                  // Comparison value
    private Object toValue;                                // Upper bound for BETWEEN
    private List<?> multiValue;                            // Value list for IN operator
    private boolean isValueField = false;                  // If true, value is a field reference
    private boolean isToValueField = false;                // If true, toValue is a field reference
    private FilterConditionOperator matchOperator = EQUALS;

    // Factory methods
    public static FilterCondition make(String field, Object value);
    public static FilterCondition of(String field, Object value, FilterConditionOperator op);
}
```

### FilterConditionOperator

All available comparison operators:

| Operator | SQL Equivalent | Description | Example |
|----------|---------------|-------------|---------|
| `EQUALS` | `= value` | Exact equality | `source EQUALS "WEBSITE"` |
| `LESS_THAN` | `< value` | Strictly less than | `amount LESS_THAN 50000` |
| `GREATER_THAN` | `> value` | Strictly greater than | `priority GREATER_THAN 3` |
| `LESS_THAN_EQUAL` | `<= value` | Less than or equal | `stage LESS_THAN_EQUAL 5` |
| `GREATER_THAN_EQUAL` | `>= value` | Greater than or equal | `score GREATER_THAN_EQUAL 80` |
| `IS_TRUE` | `= TRUE` | Boolean true check | `isVip IS_TRUE` |
| `IS_FALSE` | `= FALSE` | Boolean false check | `isDnc IS_FALSE` |
| `IS_NULL` | `IS NULL` | Null check | `email IS_NULL` |
| `BETWEEN` | `BETWEEN val AND toVal` | Range check (inclusive) | `amount BETWEEN 10000 AND 50000` |
| `IN` | `IN (multiValue)` | Set membership | `source IN ["WEBSITE", "API"]` |
| `LIKE` | `LIKE '%value%'` | Substring match | `name LIKE "kumar"` |
| `STRING_LOOSE_EQUAL` | Case-insensitive contains | Loose string match | `city STRING_LOOSE "mumbai"` |
| `MATCH` | Full-text match | Full-text search | (used in search queries) |
| `MATCH_ALL` | Full-text match all | Match all terms | (used in search queries) |
| `TEXT_SEARCH` | Text search | Text search index | (used in search queries) |

### ComplexCondition

A `ComplexCondition` combines multiple conditions with AND or OR:

```java
@Data
@Accessors(chain = true)
public class ComplexCondition extends AbstractCondition {

    private ComplexConditionOperator operator;   // AND or OR
    private List<AbstractCondition> conditions;  // Child conditions (can be nested)
    private GroupCondition groupCondition;        // Optional GROUP BY condition

    // Static factory methods
    public static ComplexCondition and(AbstractCondition... conditions);
    public static ComplexCondition and(List<AbstractCondition> conditions);
    public static ComplexCondition or(AbstractCondition... conditions);
    public static ComplexCondition or(List<AbstractCondition> conditions);
}
```

### Condition Tree Examples

**Simple condition**: Source equals "WEBSITE"
```json
{
    "type": "FilterCondition",
    "field": "source",
    "operator": "EQUALS",
    "value": "WEBSITE"
}
```

**Complex AND condition**: Source is "WEBSITE" AND city is "Mumbai"
```json
{
    "type": "ComplexCondition",
    "operator": "AND",
    "conditions": [
        { "field": "source", "operator": "EQUALS", "value": "WEBSITE" },
        { "field": "city", "operator": "EQUALS", "value": "Mumbai" }
    ]
}
```

**Nested condition**: (Source is "WEBSITE" AND city is "Mumbai") OR (Source is "API")
```json
{
    "type": "ComplexCondition",
    "operator": "OR",
    "conditions": [
        {
            "type": "ComplexCondition",
            "operator": "AND",
            "conditions": [
                { "field": "source", "operator": "EQUALS", "value": "WEBSITE" },
                { "field": "city", "operator": "EQUALS", "value": "Mumbai" }
            ]
        },
        { "field": "source", "operator": "EQUALS", "value": "API" }
    ]
}
```

### Condition Manipulation Methods

`AbstractCondition` provides several methods for dynamic condition tree manipulation:

```java
// Find all FilterConditions that reference a specific field
Flux<FilterCondition> findConditionWithField(String fieldName);

// Find all FilterConditions whose field starts with a prefix
Flux<FilterCondition> findConditionWithPrefix(String prefix);

// Remove all conditions referencing a field (returns new tree without those conditions)
Mono<AbstractCondition> removeConditionWithField(String fieldName);
```

The `removeConditionWithField` method is particularly important for the duplication rule system,
where stage conditions need to be dynamically added and removed from the condition tree.

### Condition Helpers on BaseRuleDto

```java
// From BaseRuleDto

// Returns condition AND'd with a productId filter
public AbstractCondition getConditionWithProduct() {
    if (this.productId == null) return getCondition();
    return getCondition() == null || this.getCondition().isEmpty()
        ? FilterCondition.make("productId", this.getProductId())
        : ComplexCondition.and(FilterCondition.make("productId", this.getProductId()),
                               getCondition());
}

// Returns condition AND'd with a productTemplateId filter
public AbstractCondition getConditionWithProductTemplate() {
    if (this.productTemplateId == null) return getCondition();
    return getCondition() == null || this.getCondition().isEmpty()
        ? FilterCondition.make("productTemplateId", this.getProductTemplateId())
        : ComplexCondition.and(FilterCondition.make("productTemplateId",
                               this.getProductTemplateId()), getCondition());
}

// Type checks
public boolean isSimple()  { return this.condition instanceof FilterCondition; }
public boolean isComplex() { return this.condition instanceof ComplexCondition; }
```

---

## 6. Condition Evaluation Engine

The `ConditionEvaluator` is a record-based evaluator that evaluates `AbstractCondition` trees
against `JsonElement` data. It is used by `TicketCRuleExecutionService` to determine which
creation rule matches a ticket's data at runtime.

### ConditionEvaluator Record

```java
public record ConditionEvaluator(String prefix) {

    public Mono<Boolean> evaluate(AbstractCondition condition, JsonElement json) {
        if (condition == null || condition.isEmpty()) return Mono.just(Boolean.FALSE);
        if (json == null) return Mono.just(Boolean.FALSE);

        return switch (condition) {
            case ComplexCondition cc -> evaluateComplex(cc, json);
            case FilterCondition fc -> evaluateFilter(fc, json);
            default -> Mono.just(Boolean.FALSE);
        };
    }
}
```

### How It Works

1. **Ticket data is converted to JsonElement**: The ticket DTO is serialized to a Gson
   `JsonElement` via `ticket.toJsonElement()`.

2. **Field extraction**: For each `FilterCondition`, the evaluator extracts the field value
   from the JSON using `ObjectValueSetterExtractor` with a prefix:
   ```java
   private Mono<JsonElement> extractFieldValue(JsonObject obj, String field) {
       if (!field.startsWith(prefix)) return Mono.just(JsonNull.INSTANCE);
       ObjectValueSetterExtractor extractor = new ObjectValueSetterExtractor(obj, prefix);
       return Mono.justOrEmpty(extractor.getValue(field));
   }
   ```

3. **Operator-based comparison**: The extracted value is compared against the filter's value
   using the specified operator.

### Evaluation by Operator

| Operator | Evaluation Logic |
|----------|-----------------|
| `EQUALS` | Compare JSON primitives: numbers by value, booleans by value, strings by string equality |
| `GREATER_THAN` | Numeric comparison > 0, or LocalDateTime comparison |
| `LESS_THAN` | Numeric comparison < 0, or LocalDateTime comparison |
| `BETWEEN` | `value >= filterValue AND value <= toValue` |
| `IN` | Check if value equals any element in `multiValue` list |
| `LIKE` | String `contains()` check (case-sensitive) |
| `STRING_LOOSE_EQUAL` | String `toLowerCase().contains()` (case-insensitive) |
| `IS_NULL` | `target == null || target.isJsonNull()` |
| `IS_TRUE` | `target.getAsBoolean() == true` |
| `IS_FALSE` | `target.getAsBoolean() == false` |

### Complex Condition Evaluation

For `ComplexCondition`:

- **AND**: All child conditions must be true. Uses `takeUntil(!result)` for short-circuit
  evaluation -- stops at the first `false`.
- **OR**: Any child condition must be true. Uses `takeUntil(result)` for short-circuit
  evaluation -- stops at the first `true`.

```java
// AND evaluation
if (isAnd) {
    return Flux.fromIterable(conds)
            .flatMap(sub -> evaluate(sub, json))
            .takeUntil(result -> !result)    // Stop on first false
            .all(result -> result)
            .defaultIfEmpty(true);
}

// OR evaluation
return Flux.fromIterable(conds)
        .flatMap(sub -> evaluate(sub, json))
        .takeUntil(result -> result)         // Stop on first true
        .any(result -> result)
        .defaultIfEmpty(false);
```

### Evaluator Caching

`TicketCRuleExecutionService` caches `ConditionEvaluator` instances by prefix:

```java
private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache =
    new ConcurrentHashMap<>();

// In findMatchedRules:
ConditionEvaluator evaluator = conditionEvaluatorCache.computeIfAbsent(
    prefix, ConditionEvaluator::new);
```

Since `ConditionEvaluator` is a Java record with only the `prefix` field, this cache effectively
stores one evaluator per entity prefix string, avoiding repeated object creation.

### Date Comparison

The evaluator supports `LocalDateTime` comparison for date fields:

```java
private Mono<Integer> compareDate(JsonElement jsonVal, JsonElement filterVal) {
    return Mono.fromCallable(() -> {
        try {
            LocalDateTime jsonDate = LocalDateTime.parse(jsonVal.getAsString());
            LocalDateTime compareDate = LocalDateTime.parse(filterVal.getAsString());
            return jsonDate.compareTo(compareDate);
        } catch (DateTimeParseException e) {
            return 0; // Treat parse errors as equal
        }
    });
}
```

---

## 7. Ticket Duplication Detection

### Overview

The `TicketDuplicationRuleService` prevents duplicate ticket creation by building dynamic
conditions that check incoming tickets against existing ones. This is the most complex rule
type due to its multi-layered condition construction involving sources, sub-sources, stages,
and custom conditions.

### TicketDuplicationRule DTO

```java
public class TicketDuplicationRule extends BaseRuleDto<NoOpUserDistribution, TicketDuplicationRule> {

    private String source;      // Required: Source channel (e.g., "WEBSITE")
    private String subSource;   // Optional: Sub-channel (e.g., "GOOGLE_ADS")
    private ULong maxStageId;   // Required: Max stage for duplicate checking

    // Inherits from BaseRuleDto:
    //   productId, productTemplateId, order, condition
    //   (userDistributionType and lastAssignedUserId are unused)
}
```

### Rule Validation

```
checkEntity(entity, access):
    |
    +-- source is null or empty?
    |       YES --> throw IDENTITY_MISSING("Source")
    |
    +-- maxStageId is null?
    |       YES --> throw STAGE_MISSING
    |
    +-- BaseRuleService.checkEntity() [validates order, condition, uniqueness]
    |
    +-- Validate maxStageId belongs to the product's template:
            stageService.getStage(access, templateId, maxStageId)
                .switchIfEmpty(throw TEMPLATE_STAGE_INVALID)
```

### Rule Resolution Flow

When checking for duplicates, the system builds a composite `AbstractCondition` from all
matching rules. Here is the complete flow:

```
getDuplicateRuleCondition(access, productId, source, subSource)
    |
    +-- Look up the product to get templateId
    |
    +-- Try product-level rules first:
    |       getProductDuplicateCondition(access, productId, source, subSource)
    |       |
    |       +-- Cache lookup (cacheKey: appCode + clientCode + productId + source + subSource)
    |       |
    |       +-- On miss: getProductDuplicateConditionInternal()
    |               |
    |               +-- getProductDuplicationRules(access, productId, source, subSource)
    |               |       |
    |               |       +-- DAO.getRules(access, productId, null, source, null)
    |               |       |       [Fetches all rules for this product + source,
    |               |       |        ignoring subSource at DB level for flexibility]
    |               |       |
    |               |       +-- filterRulesForSubSource(rules, subSource)
    |               |               [Separate exact matches from null-subSource wildcards]
    |               |
    |               +-- For each matching rule:
    |               |       getRuleCondition(access, rule)
    |               |       |
    |               |       +-- Get stages <= maxStageId:
    |               |       |       stageService.getHigherStages(templateId, maxStageId)
    |               |       |
    |               |       +-- Build IN condition for allowed stages
    |               |       |
    |               |       +-- AND with rule's custom condition
    |               |
    |               +-- OR all rule conditions together:
    |                       ComplexCondition.or(allRuleConditions)
    |
    +-- If product-level empty, fall back to template-level:
            getProductTemplateDuplicateCondition(access, templateId, source, subSource)
            [Same pattern but with templateId instead of productId]
```

### Source/SubSource Matching Strategy

The duplication system uses a two-tier matching strategy for sub-sources:

```java
private Mono<List<TicketDuplicationRule>> filterRulesForSubSource(
        List<TicketDuplicationRule> rules, String subSource) {

    List<TicketDuplicationRule> exactMatches = new ArrayList<>();
    List<TicketDuplicationRule> nullMatches = new ArrayList<>();

    for (TicketDuplicationRule rule : rules) {
        String ruleSubSource = rule.getSubSource();
        if (ruleSubSource == null) {
            nullMatches.add(rule);           // Wildcard rules (match any subSource)
        } else if (subSource != null && subSource.equals(ruleSubSource)) {
            exactMatches.add(rule);          // Exact subSource match
        }
    }

    // Priority: exact matches > wildcard matches
    if (!exactMatches.isEmpty()) return Mono.just(exactMatches);
    return nullMatches.isEmpty() ? Mono.empty() : Mono.just(nullMatches);
}
```

**Decision tree**:
```
Incoming ticket has source="WEBSITE", subSource="GOOGLE_ADS"

Rules in DB:
  Rule A: source="WEBSITE", subSource="GOOGLE_ADS"  --> EXACT match
  Rule B: source="WEBSITE", subSource=null           --> WILDCARD match
  Rule C: source="WEBSITE", subSource="FACEBOOK"     --> NO match

Result: Only Rule A is used (exact match takes precedence)

---

Incoming ticket has source="WEBSITE", subSource="LINKEDIN"

Rules in DB:
  Rule A: source="WEBSITE", subSource="GOOGLE_ADS"  --> NO match
  Rule B: source="WEBSITE", subSource=null           --> WILDCARD match

Result: Rule B is used (wildcard fallback)

---

Incoming ticket has source="WEBSITE", subSource="LINKEDIN"

Rules in DB:
  Rule A: source="WEBSITE", subSource="GOOGLE_ADS"  --> NO match
  [No wildcard rules]

Result: No rules match --> no duplication check
```

### Condition Building for Each Rule

For each matching rule, the system builds a condition that includes:
1. The rule's custom condition (e.g., `city = "Mumbai"`)
2. A stage filter (tickets must be at or below `maxStageId`)

```java
private Mono<AbstractCondition> getRuleCondition(ProcessorAccess access, TicketDuplicationRule rule) {
    return FlatMapUtil.flatMapMono(
        // Get all stages with order <= maxStageId's order
        () -> this.stageService.getHigherStages(access, rule.getProductTemplateId(),
                                                 rule.getMaxStageId()),
        stages -> {
            if (stages.isEmpty()) return Mono.just(rule.getCondition());

            // Build: stage IN [stage1.id, stage2.id, ...]
            AbstractCondition stageCondition = new FilterCondition()
                .setField(Ticket.Fields.stage)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(stages.stream().map(AbstractDTO::getId).toList());

            // Final: rule.condition AND stage IN [...]
            return Mono.just(ComplexCondition.and(rule.getCondition(), stageCondition));
        });
}
```

### Multiple Rules Combine with OR

When multiple rules match (e.g., multiple rules for the same source with null subSource),
their conditions are OR'd together:

```java
return Flux.fromIterable(rules)
    .flatMap(rule -> this.getRuleCondition(access, rule))
    .collectList()
    .map(ComplexCondition::or);  // Rule1.condition OR Rule2.condition OR ...
```

### Duplicate Check Execution in TicketService

The constructed condition is used by `TicketService.checkDuplicate()`:

```
checkDuplicate(access, productId, phone, email, source, subSource)
    |
    +-- getDuplicateRuleCondition(access, productId, source, subSource)
    |
    +-- If rule condition exists:
    |       |
    |       +-- Remove stage filter from condition
    |       |       condition.removeConditionWithField("stage")
    |       |
    |       +-- Query tickets matching condition WITHOUT stage filter
    |       |       [Find any potential duplicate regardless of stage]
    |       |
    |       +-- If potential duplicate found:
    |       |       |
    |       |       +-- Re-query WITH stage filter
    |       |       |       [Check if duplicate is in allowed stage range]
    |       |       |
    |       |       +-- If in range:
    |       |       |       +-- Create RE_INQUIRY activity
    |       |       |       +-- Throw DUPLICATE_ENTITY (for inside users)
    |       |       |       |   or DUPLICATE_ENTITY_OUTSIDE_USER (for BP)
    |       |       |
    |       |       +-- If not in range:
    |       |               +-- Allow ticket creation (no duplicate)
    |       |
    |       +-- If no potential duplicate:
    |               +-- Allow ticket creation
    |
    +-- If no rule condition (no rules configured):
            |
            +-- Simple phone/email lookup against product
            |
            +-- If match found:
            |       +-- Create RE_INQUIRY activity
            |       +-- Throw DUPLICATE_ENTITY
            |
            +-- If no match:
                    +-- Allow ticket creation
```

### Error Behavior

When a duplicate is detected, the error thrown depends on the user type:

| User Type | Error Code | Behavior |
|-----------|-----------|----------|
| Inside user (staff) | `DUPLICATE_ENTITY` | Includes existing ticket ID in error message |
| Outside user (Business Partner) | `DUPLICATE_ENTITY_OUTSIDE_USER` | Gentler message, no ticket ID revealed |

### Cache Architecture

The duplication rule service maintains three separate caches:

| Cache | Key Pattern | Contents |
|-------|------------|----------|
| Main rule cache | `ticketDuplicationRule:appCode:clientCode:entityId` | Individual rule entities |
| Product condition cache | `ticketDuplicationProductRuleCondition:appCode:clientCode:productId:source:subSource` | Computed AbstractCondition |
| Template condition cache | `ticketDuplicationProductTemplateRuleCondition:appCode:clientCode:templateId:source:subSource` | Computed AbstractCondition |

All three caches are evicted when any duplication rule is created, updated, or deleted:

```java
@Override
protected Mono<Boolean> evictCache(TicketDuplicationRule entity) {
    Mono<Boolean> productEviction = entity.getProductId() != null
        ? this.evictProductConditionCache(entity.getAppCode(), entity.getClientCode(),
                                          entity.getProductId())
        : Mono.just(Boolean.TRUE);

    return Mono.zip(
        super.evictCache(entity),          // Main rule cache
        productEviction,                    // Product condition cache
        this.evictProductTemplateConditionCache(entity.getAppCode(),
                                                entity.getClientCode(),
                                                entity.getProductTemplateId()))
        .map(evicted -> evicted.getT1() && evicted.getT2() && evicted.getT3());
}
```

---

## 8. Phone/Email Duplication Rules

### Overview

The `TicketPeDuplicationRuleService` provides a simpler, complementary duplication detection
mechanism focused specifically on phone number and email matching. Unlike the condition-based
`TicketDuplicationRuleService`, this service defines **how** phone/email comparisons are
performed.

### TicketPeDuplicationRule DTO

```java
public class TicketPeDuplicationRule extends BaseUpdatableDto<TicketPeDuplicationRule> {
    private PhoneNumberAndEmailType phoneNumberAndEmailType =
        PhoneNumberAndEmailType.PHONE_NUMBER_OR_EMAIL;
}
```

Note: This DTO extends `BaseUpdatableDto` directly, NOT `BaseRuleDto`. It does not participate
in the order/condition system -- there is exactly one PE duplication rule per client.

### PhoneNumberAndEmailType Enum

This enum defines four modes of phone/email matching:

| Type | Description | Required Fields |
|------|-------------|----------------|
| `PHONE_NUMBER_ONLY` | Match by phone number only | dialCode + phoneNumber |
| `EMAIL_ONLY` | Match by email only | email |
| `PHONE_NUMBER_AND_EMAIL` | Match by phone AND email (both must match) | dialCode + phoneNumber + email |
| `PHONE_NUMBER_OR_EMAIL` | Match by phone OR email (either can match) | At least one of phone or email |

### Condition Building

Each type builds a different `AbstractCondition`:

```java
public AbstractCondition getTicketCondition(PhoneNumber phoneNumber, Email email) {
    return switch (this) {
        case PHONE_NUMBER_ONLY -> {
            validatePhoneCondition(phoneCondition);
            yield phoneCondition;
        }
        case EMAIL_ONLY -> {
            validateEmailCondition(emailCondition);
            yield emailCondition;
        }
        case PHONE_NUMBER_AND_EMAIL -> {
            validatePhoneCondition(phoneCondition);
            validateEmailCondition(emailCondition);
            yield ComplexCondition.and(phoneCondition, emailCondition);
        }
        case PHONE_NUMBER_OR_EMAIL -> {
            if (phoneCondition == null && emailCondition == null)
                throw new IllegalArgumentException("At least one required");
            if (phoneCondition == null) yield emailCondition;
            if (emailCondition == null) yield phoneCondition;
            yield ComplexCondition.or(phoneCondition, emailCondition);
        }
    };
}
```

### Phone Number Condition Structure

A phone number condition is an AND of dial code and number:

```
ComplexCondition.and(
    FilterCondition.make("dialCode", phoneNumber.getCountryCode()),
    FilterCondition.make("phoneNumber", phoneNumber.getNumber())
)
```

### Default Rule Behavior

If no PE duplication rule has been configured for a client, the system uses a built-in default:

```java
private static final TicketPeDuplicationRule DEFAULT_RULE =
    new TicketPeDuplicationRule()
        .setPhoneNumberAndEmailType(PhoneNumberAndEmailType.PHONE_NUMBER_ONLY)
        .setActive(Boolean.TRUE)
        .setId(ULong.MIN);
```

The default is `PHONE_NUMBER_ONLY` -- duplicate detection by phone number when no custom
rule exists.

### Singleton Per Client

Unlike other rule types, only one PE duplication rule can exist per appCode + clientCode.
Creating a second one throws `DUPLICATE_ENTITY`:

```java
@Override
protected Mono<TicketPeDuplicationRule> checkEntity(TicketPeDuplicationRule entity,
                                                     ProcessorAccess access) {
    if (entity.getId() != null) return Mono.just(entity); // Update path

    return this.dao.readByAppCodeAndClientCode(access)
        .flatMap(existing -> throwMessage(DUPLICATE_ENTITY, ...))
        .switchIfEmpty(Mono.just(entity)); // No existing = OK to create
}
```

---

## 9. User Assignment Rules (ProductTicketCRule)

### Overview

`ProductTicketCRuleService` determines which user a ticket should be assigned to upon creation.
Rules are evaluated against ticket data to find a matching condition, then a user is selected
from the matched rule's distribution list using the configured algorithm.

### ProductTicketCRule DTO

```java
public class ProductTicketCRule extends BaseRuleDto<TicketCUserDistribution, ProductTicketCRule> {
    private ULong stageId;  // The stage this rule applies to

    // Inherits from BaseRuleDto:
    //   productId, productTemplateId, order, condition,
    //   userDistributionType (default: ROUND_ROBIN),
    //   lastAssignedUserId, userDistributions
}
```

### Rule Validation

```
checkEntity(entity, access):
    |
    +-- Is this a default rule (order == 0)?
    |       YES --> StageId is ignored (set to null)
    |               Proceed to BaseRuleService.checkEntity()
    |
    +-- StageId is null for non-default rule?
    |       YES --> throw STAGE_MISSING
    |
    +-- BaseRuleService.checkEntity() [validates order, condition, uniqueness]
    |
    +-- Validate stageId belongs to the product's template:
            stageService.getStage(access, templateId, stageId)
                .switchIfEmpty(throw TEMPLATE_STAGE_INVALID)
```

### Getting Rules with Order

The `getRulesWithOrder` method retrieves rules as a `Map<Integer, ProductTicketCRule>` keyed
by order number. This method handles the product/template precedence based on the product's
`overrideCTemplate` flag:

```java
public Mono<Map<Integer, ProductTicketCRule>> getRulesWithOrder(
        ProcessorAccess access, ULong productId, ULong stageId) {

    return productService.readById(access, productId)
        .flatMap(product -> product.isOverrideCTemplate()
            ? getRulesWithOrderWithTemplateOverride(access, product, stageId)
            : getRulesWithOrderWithTemplateCombine(access, product, stageId));
}
```

**Override Mode** (`product.overrideCTemplate = true`):
```
getProductRules(productId, stageId)
    |
    +-- rules found? --> Use product rules only
    |
    +-- empty? --> getProductTemplateRules(templateId, stageId)
                   (Complete fallback to template rules)
```

**Combine Mode** (`product.overrideCTemplate = false`):
```
Zip(getProductRules(), getProductTemplateRules())
    |
    +-- Merge both sets into a single ordered map
    +-- Product rules get higher priority (higher order numbers)
    +-- Template rules get lower priority (lower order numbers)
    +-- Both sets are evaluated together
```

### Creating Multiple Rules for Multiple Stages

The `createMultiple` method allows creating a rule for multiple stages in a single call:

```java
public Flux<ProductTicketCRule> createMultiple(ProductTicketCRule rule, List<ULong> stageIds) {
    // For each stageId:
    //   1. Set the stageId on the rule
    //   2. Auto-increment the order based on index
    //   3. Validate and create
    //   4. Create user distributions
}
```

### getUserAssignment Flow

This is the main entry point called by `TicketService` when creating a ticket:

```
getUserAssignment(access, productId, stageId, tokenPrefix, userId, ticket, isCreate)
    |
    +-- getRulesWithOrder(access, productId, stageId)
    |       --> Map<Integer, ProductTicketCRule>
    |
    +-- No rules found?
    |       --> Return Mono.empty() (caller uses loggedInUser as fallback)
    |
    +-- For updates (!isCreate): only default rule exists (size==1, key==0)?
    |       --> Return Mono.empty() (don't change assignment on updates
    |           when only default rule exists)
    |
    +-- Convert ticket to JsonElement: ticket.toJsonElement()
    |
    +-- Execute rules via TicketCRuleExecutionService:
    |       executeRules(access, rules, tokenPrefix, userId, jsonData)
    |       |
    |       +-- [See Rule Execution section below]
    |
    +-- If rule matched and user assigned:
    |       |
    |       +-- Update rule's lastAssignedUserId in DB
    |       |       (via updateInternalForOutsideUser -- bypasses auth for system updates)
    |       |
    |       +-- Return assigned userId
    |
    +-- If no user assigned:
            --> Return Mono.empty()
```

### Rule Execution (TicketCRuleExecutionService)

The execution service orchestrates the core rule matching and user distribution:

```java
public Mono<ProductTicketCRule> executeRules(
        ProcessorAccess access,
        Map<Integer, ProductTicketCRule> rules,
        String prefix,
        ULong userId,
        JsonElement data) {

    // Normalize anonymous user ID
    final ULong finalUserId = userId != null && userId.equals(ANO_USER_ID) ? null : userId;

    return this.findMatchedRules(rules, prefix, data)      // Step 1: Find matching rule
        .flatMap(matched -> handleMatchedRule(access, matched, finalUserId))  // Step 2a
        .switchIfEmpty(handleDefaultRule(access, rules, finalUserId));        // Step 2b
}
```

**Step 1: findMatchedRules()**

```java
private Mono<ProductTicketCRule> findMatchedRules(
        Map<Integer, ProductTicketCRule> rules, String prefix, JsonElement data) {

    ConditionEvaluator evaluator = conditionEvaluatorCache.computeIfAbsent(
        prefix, ConditionEvaluator::new);

    return Flux.fromStream(rules.entrySet().stream()
            .filter(e -> e.getKey() != null && e.getKey() > 0)  // Skip default (order 0)
            .sorted(Map.Entry.comparingByKey())                  // Sort by order ASC
            .map(Map.Entry::getValue))
        .concatMap(rule -> {                                     // Sequential evaluation
            if (rule.getCondition() == null) return Mono.empty();
            return evaluator.evaluate(rule.getCondition(), data)
                .filter(Boolean::booleanValue)                   // Keep only if condition true
                .map(b -> rule);
        })
        .next();  // Take FIRST match (short-circuit)
}
```

**Step 2a: handleMatchedRule()**

```java
private Mono<ProductTicketCRule> handleMatchedRule(
        ProcessorAccess access, ProductTicketCRule matchedRule, ULong finalUserId) {

    return userDistributionService.getUsersByRuleId(access, matchedRule.getId())
        .flatMap(userIds -> {
            // If logged-in user is in the rule's user pool, prefer them
            if (finalUserId != null && userIds.contains(finalUserId))
                return Mono.just(addAssignedUser(matchedRule, finalUserId));

            // Otherwise, distribute according to the algorithm
            return distributeUsers(matchedRule, userIds);
        });
}
```

**Step 2b: handleDefaultRule()**

```java
private Mono<ProductTicketCRule> handleDefaultRule(
        ProcessorAccess access, Map<Integer, ProductTicketCRule> rules, ULong finalUserId) {

    ProductTicketCRule defaultRule = rules.get(0);  // Order 0
    if (defaultRule == null) return Mono.empty();

    return userDistributionService.getUsersByRuleId(access, defaultRule.getId())
        .flatMap(userIds -> {
            // If logged-in user is in default rule's user pool, prefer them
            if (finalUserId != null && userIds.contains(finalUserId))
                return Mono.just(addAssignedUser(defaultRule, finalUserId));

            // Otherwise, distribute according to the algorithm
            return distributeUsers(defaultRule, userIds);
        });
}
```

### Complete Decision Tree

```
executeRules(rules, prefix, userId, ticketData):
    |
    +-- rules null or empty?
    |       YES --> return empty
    |
    +-- Normalize userId (ANO_USER_ID -> null)
    |
    +-- For each conditional rule (order > 0, ascending):
    |       |
    |       +-- rule.condition is null?
    |       |       YES --> skip this rule
    |       |
    |       +-- Evaluate condition against ticketData:
    |       |       evaluator.evaluate(rule.condition, ticketData)
    |       |
    |       +-- Condition is TRUE?
    |               YES --> MATCHED! Go to handleMatchedRule
    |               NO  --> Continue to next rule
    |
    +-- No conditional rule matched?
    |       |
    |       +-- Default rule (order 0) exists?
    |               YES --> Go to handleDefaultRule
    |               NO  --> return empty
    |
    +-- handleMatchedRule / handleDefaultRule:
            |
            +-- Get user IDs from distribution service
            |       getUsersByRuleId(access, rule.id)
            |       --> Set<ULong> userIds
            |
            +-- userIds empty?
            |       YES --> return empty
            |
            +-- userId (logged-in user) is in userIds?
            |       YES --> Assign to logged-in user (prefer them)
            |
            +-- Only 1 user in pool?
            |       YES --> Assign to that user
            |
            +-- Multiple users, distribute by algorithm:
                    |
                    +-- ROUND_ROBIN?
                    |       --> handleRoundRobin(rule, userIds)
                    |
                    +-- RANDOM?
                            --> getRandom(rule, userIds)
```

---

## 10. User Distribution Algorithms

### DistributionType Enum

```java
public enum DistributionType implements EnumType {
    ROUND_ROBIN("ROUND_ROBIN"),
    PERCENTAGE("PERCENTAGE"),
    WEIGHTED("WEIGHTED"),
    LOAD_BALANCED("LOAD_BALANCED"),
    PRIORITY_QUEUE("PRIORITY_QUEUE"),
    RANDOM("RANDOM"),
    HYBRID("HYBRID");
}
```

Currently, only `ROUND_ROBIN` and `RANDOM` are implemented. The others are defined in the enum
for future extensibility.

### ROUND_ROBIN Algorithm

Round-robin distributes tickets evenly across users in a deterministic order:

```java
private Mono<ProductTicketCRule> handleRoundRobin(ProductTicketCRule rule, Set<ULong> userIds) {

    TreeSet<ULong> sortedUserIds = new TreeSet<>(userIds);  // Sort by ID

    ULong lastUsedId = rule.getLastAssignedUserId();

    // Find the next user after the last assigned one
    ULong nextUserId = lastUsedId == null
        ? sortedUserIds.first()           // First time: start from beginning
        : sortedUserIds.higher(lastUsedId); // Next higher ID

    // Wrap around to beginning if we've reached the end
    if (nextUserId == null) nextUserId = sortedUserIds.first();

    return Mono.just(addAssignedUser(rule, nextUserId));
}
```

**Walkthrough example**:

```
User pool (sorted by ID): [10, 20, 30, 40]
lastAssignedUserId: null

Call 1: lastUsedId=null  --> first() = 10  --> assign 10, lastAssigned=10
Call 2: lastUsedId=10    --> higher(10) = 20 --> assign 20, lastAssigned=20
Call 3: lastUsedId=20    --> higher(20) = 30 --> assign 30, lastAssigned=30
Call 4: lastUsedId=30    --> higher(30) = 40 --> assign 40, lastAssigned=40
Call 5: lastUsedId=40    --> higher(40) = null --> first() = 10 --> assign 10
...cycles
```

**Important**: The `lastAssignedUserId` is persisted on the rule itself and updated after each
assignment. This ensures the round-robin state survives server restarts.

### RANDOM Algorithm

Random selects a user from the pool, avoiding the last assigned user:

```java
private Mono<ProductTicketCRule> getRandom(ProductTicketCRule rule, Set<ULong> userIds) {
    ULong last = rule.getLastAssignedUserId();

    // Remove last assigned to avoid consecutive assignment to same user
    if (last != null) userIds.remove(last);

    return Mono.fromSupplier(() -> userIds.stream()
            .skip(random.nextInt(userIds.size()))  // Skip random number of elements
            .findFirst()
            .orElse(null))
        .flatMap(userId -> userId == null
            ? Mono.empty()
            : Mono.just(addAssignedUser(rule, userId)));
}
```

**Key behavior**:
- The last assigned user is **excluded** from the pool to prevent consecutive assignment
- Selection uses `stream().skip(random).findFirst()` for O(n) random access
- If only one user remains after excluding the last user, that user is always selected
- The `lastAssignedUserId` is updated after each assignment

### Edge Cases

| Scenario | Behavior |
|----------|----------|
| User pool has 1 user | Always assign to that user (both algorithms) |
| User pool is empty | Return `Mono.empty()` (no assignment) |
| Last assigned user removed from pool | ROUND_ROBIN: `higher()` returns null, wraps to first. RANDOM: No exclusion needed |
| Anonymous user (ANO_USER_ID) | Treated as null userId -- normal distribution applies |
| Logged-in user in pool | Logged-in user is preferred over distribution algorithm |

### Logged-In User Preference

Both `handleMatchedRule` and `handleDefaultRule` check if the calling user is in the user pool:

```java
if (finalUserId != null && userIds.contains(finalUserId))
    return Mono.just(this.addAssignedUser(matchedRule, finalUserId));
```

This means:
- If an agent creates a ticket and they are in the assignment pool, the ticket is assigned to them
- For outside users (Business Partners), `userId` is null, so distribution always runs
- For anonymous users, `ANO_USER_ID` (ULong.MIN) is normalized to null

---

## 11. Read/Update Permission Rules (ProductTicketRuRule)

### Overview

`ProductTicketRuRuleService` controls which users can read (and optionally update) tickets
for a given product. These rules are applied as additional `WHERE` conditions on ticket
queries, ensuring users only see tickets they are permitted to access.

### ProductTicketRuRule DTO

```java
public class ProductTicketRuRule extends BaseRuleDto<TicketRuUserDistribution, ProductTicketRuRule> {
    private boolean canEdit;  // If true, matched users can also update tickets

    // Inherits: productId, productTemplateId, order, condition,
    //           userDistributionType, lastAssignedUserId, userDistributions
}
```

### How RU Rules Work

Unlike C-Rules (which select a single user), RU rules build conditions that are applied to
database queries. The flow is:

```
getUserReadConditions(access)
    |
    +-- Look up the current user's identity (via TicketRuUserDistributionService)
    |       Returns EntityProcessorUser with: userId, roleId, profileIds,
    |       designationId, departmentId
    |
    +-- Query all RU rules where the user matches the distribution:
    |       ProductTicketRuRuleDAO.getUserConditions(access, isEdit, user)
    |       |
    |       +-- JOIN rules with distributions
    |       +-- Match on: userId, roleId, designationId, departmentId, profileIds
    |       +-- Return matching ProductTicketRuRule list
    |
    +-- Build condition tree from matched rules:
            getUserReadConditions(access, rules)
```

### ProductTicketRuRuleDAO.getUserConditions

This DAO method performs a JOIN query to find rules that match the user:

```java
public Flux<ProductTicketRuRule> getUserConditions(
        ProcessorAccess access, Boolean isEdit, EntityProcessorUser user) {

    var dist = ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

    // Build OR condition: match on any of user's identifiers
    Condition matchCond = DSL.falseCondition();

    if (user.getId() != null)
        matchCond = matchCond.or(dist.USER_ID.eq(ULong.valueOf(user.getId())));
    if (user.getRoleId() != null)
        matchCond = matchCond.or(dist.ROLE_ID.eq(ULong.valueOf(user.getRoleId())));
    if (user.getDesignationId() != null)
        matchCond = matchCond.or(dist.DESIGNATION_ID.eq(...));
    if (user.getDepartmentId() != null)
        matchCond = matchCond.or(dist.DEPARTMENT_ID.eq(...));
    if (user.getProfileIds() != null && !user.getProfileIds().isEmpty())
        matchCond = matchCond.or(dist.PROFILE_ID.in(profileIds));

    // If isEdit, also filter on CAN_EDIT = true
    if (Boolean.TRUE.equals(isEdit))
        query = query.and(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.CAN_EDIT.isTrue());

    return Flux.from(query.groupBy(this.idField))
        .map(rec -> rec.into(this.pojoClass));
}
```

### Condition Building with Product/Template Override

The `buildRuleMaps` method separates rules into product-scoped and template-scoped groups:

```java
private ProductTemplateMaps buildRuleMaps(List<ProductTicketRuRule> rules) {
    Map<ULong, List<ProductTicketRuRule>> productMap = new HashMap<>();
    Map<ULong, List<ProductTicketRuRule>> templateMap = new HashMap<>();
    Set<ULong> usedTemplates = new HashSet<>();

    for (var rule : rules) {
        if (rule.getProductId() != null)
            productMap.computeIfAbsent(rule.getProductId(), x -> new ArrayList<>()).add(rule);
        if (rule.getProductTemplateId() != null)
            templateMap.computeIfAbsent(rule.getProductTemplateId(), x -> new ArrayList<>()).add(rule);
    }

    return new ProductTemplateMaps(productMap, templateMap, usedTemplates);
}
```

Then, for each product, the system checks `product.isOverrideRuTemplate()`:
- **Override**: Only product-level conditions are used; template conditions are ignored
  (but template is marked as "used" to prevent re-inclusion)
- **Merge**: Product and template conditions are OR'd together

The final result is a single `AbstractCondition` that, when applied to a ticket query, ensures
the user only sees tickets they have permission to access.

### Condition Structure Example

```
Final Condition = OR(
    // Product A (override mode):
    OR(rule1.conditionWithProduct, rule2.conditionWithProduct),

    // Product B (merge mode):
    OR(
        OR(rule3.conditionWithProduct, rule4.conditionWithProduct),
        OR(rule5.conditionWithProductTemplate)
    ),

    // Template-only (no product rules reference this template):
    OR(rule6.conditionWithProductTemplate)
)
```

---

## 12. User Distribution Services

### BaseUserDistributionService

The base class manages lists of users associated with rules. Each distribution entry represents
one user or group (role/profile/designation/department) assigned to a rule.

### User Resolution

Distribution entries can reference users in five ways:

| Field | Resolution | Description |
|-------|-----------|-------------|
| `userId` | Direct | Specific user ID |
| `roleId` | Indirect | All users with this role |
| `profileId` | Indirect | All users with this profile |
| `designationId` | Indirect | All users with this designation |
| `departmentId` | Indirect | All users in this department |

### Validation: Exactly One Field Must Be Set

```java
// From BaseUserDistributionDto.isDistributionValid()
public boolean isDistributionValid() {
    int flags = ((userId != null) ? 1 : 0)
            | ((roleId != null) ? 1 << 1 : 0)
            | ((profileId != null) ? 1 << 2 : 0)
            | ((designationId != null) ? 1 << 3 : 0)
            | ((departmentId != null) ? 1 << 4 : 0);

    // flags != 0        --> at least one field is set
    // (flags & (flags-1)) == 0 --> exactly one bit is set (power of 2)
    return (flags != 0) && ((flags & (flags - 1)) == 0);
}
```

This clever bit manipulation ensures exactly one identifier field is set per distribution entry.

### getUsersByRuleId: Resolving Users from Distributions

The key method that converts distribution entries into a concrete set of user IDs:

```java
public Mono<Set<ULong>> getUsersByRuleId(ProcessorAccess access, ULong ruleId) {
    return Mono.zip(
        this.getAllUserMappings(access),           // Get all user->group mappings
        this.getUserDistributions(access, ruleId)) // Get distribution entries
        .map(tuple -> {
            UserMaps maps = tuple.getT1();
            List<D> userDistributions = tuple.getT2();

            Set<ULong> userIds = new HashSet<>();

            for (BaseUserDistributionDto<D> dto : userDistributions) {
                // Direct user reference
                userIds.add(dto.getUserId());

                // Resolve group references to user IDs
                userIds.addAll(maps.roleMap().get(dto.getRoleId()));
                userIds.addAll(maps.profileMap().get(dto.getProfileId()));
                userIds.addAll(maps.desigMap().get(dto.getDesignationId()));
                userIds.addAll(maps.deptMap().get(dto.getDepartmentId()));
            }

            userIds.remove(null);  // Clean up nulls
            return userIds;
        });
}
```

### User Mapping Construction

The `UserMaps` record holds reverse-lookup maps from group IDs to user IDs:

```java
private record UserMaps(
    Map<ULong, Set<ULong>> roleMap,      // roleId -> Set<userId>
    Map<ULong, Set<ULong>> profileMap,   // profileId -> Set<userId>
    Map<ULong, Set<ULong>> desigMap,     // designationId -> Set<userId>
    Map<ULong, Set<ULong>> deptMap)      // departmentId -> Set<userId>
    implements Serializable {}
```

These maps are built from the security service's user list:

```java
private UserMaps buildMaps(List<EntityProcessorUser> users) {
    Map<ULong, Set<ULong>> roles = new HashMap<>();
    Map<ULong, Set<ULong>> profiles = new HashMap<>();
    Map<ULong, Set<ULong>> designations = new HashMap<>();
    Map<ULong, Set<ULong>> departments = new HashMap<>();

    for (EntityProcessorUser u : users) {
        ULong userId = ULong.valueOf(u.getId());
        addToMap(roles, u.getRoleId(), userId);
        addToMap(designations, u.getDesignationId(), userId);
        addToMap(departments, u.getDepartmentId(), userId);
        addListToMap(profiles, u.getProfileIds(), userId);
    }

    return new UserMaps(roles, profiles, designations, departments);
}
```

### Distribution CRUD Operations

```java
// Create distributions for a rule
public Flux<D> createDistributions(ProcessorAccess access, ULong ruleId,
                                    List<D> userDistributions) {
    for (D ud : userDistributions) ud.setRuleId(ruleId);
    return Flux.fromIterable(userDistributions)
        .flatMap(ud -> super.create(access, ud));
}

// Replace all distributions for a rule
public Flux<D> updateDistributions(ProcessorAccess access, ULong ruleId,
                                    List<D> userDistributions) {
    return deleteByRuleId(access, ruleId)
        .thenMany(createDistributions(access, ruleId, userDistributions));
}

// Delete all distributions for a rule
public Mono<Integer> deleteByRuleId(ProcessorAccess access, ULong ruleId) {
    return this.dao.getUserDistributions(access, ruleId)
        .flatMap(list -> Flux.fromIterable(list)
            .flatMap(entity -> super.deleteInternal(access, entity))
            .count()
            .map(Long::intValue));
}
```

---

## 13. Automatic Reassignment on Stage Change

### Overview

When a ticket's stage changes, the system can automatically reassign it to a different user
based on the C-Rules configured for the new stage. This ensures tickets move to the correct
team member as they progress through the workflow.

### Flow

```
TicketService.updateStageStatus(access, ticket, newStageId, newStatus, comment)
    |
    +-- Update ticket's stage and status in DB
    |
    +-- Did stage actually change? (oldStageId != newStageId)
    |       NO  --> Log status activity only
    |       YES --> Continue
    |
    +-- reassignForStage(access, ticket, reassignUserId, isAutomatic=true)
            |
            +-- reassignUserId provided in request?
            |       YES --> Use the provided userId
            |       NO  --> Query for automatic assignment:
            |               productTicketCRuleService.getUserAssignment(
            |                   access, productId, newStageId, prefix,
            |                   ticket.assignedUserId, ticket, isCreate=false)
            |
            +-- Got a new userId AND it differs from current assignee?
            |       YES --> Update ticket.assignedUserId
            |       |       Log activity:
            |       |       +-- isAutomatic=true  --> REASSIGN_SYSTEM activity
            |       |       +-- isAutomatic=false --> REASSIGN activity
            |       |
            |       NO  --> No reassignment needed
```

### Why isCreate=false Matters

When `getUserAssignment` is called with `isCreate=false`:

```java
// In ProductTicketCRuleService.getUserAssignment:
if (!isCreate && productRule.size() == 1 && productRule.containsKey(0))
    return Mono.empty();
```

This means: during stage-change reassignment, if only the default rule (order 0) exists and
no conditional rules exist, **no reassignment occurs**. The logic is:
- Default rule alone is meant for initial assignment
- Conditional rules represent stage-specific routing
- If no stage-specific rules exist, keep the current assignee

### Activity Logging for Reassignment

```java
// Manual reassignment
activityService.acReassign(access, ticketId, comment, oldUser, newUser)
    --> ActivityAction.REASSIGN
    --> "$entity was reassigned from $oldUser to $newUser by $user."

// Automatic reassignment (stage change)
activityService.acReassignSystem(access, ticketId, comment, oldUser, newUser)
    --> ActivityAction.REASSIGN_SYSTEM
    --> "$entity reassigned from $oldUser to $newUser due to availability rule by $user."
```

---

## 14. Activity Automation

### Overview

The `ActivityService` automatically logs an activity record for virtually every significant
event in the ticket lifecycle. Activities form an immutable audit trail with structured
context data and markdown-formatted descriptions.

### ActivityAction Enum

Every activity has an `ActivityAction` that defines its template and context keys:

```java
public enum ActivityAction implements EnumType {

    CREATE("CREATE",
        "$entity from $source created for $user.",
        keys("entity", "source")),

    RE_INQUIRY("RE_INQUIRY",
        "$entity re-inquired from $source by $user.",
        keys("entity", "source")),

    STAGE_UPDATE("STAGE_UPDATE",
        "Stage moved from $_stage to $stage by $user.",
        keys("_stage", "stage")),

    REASSIGN("REASSIGN",
        "$entity was reassigned from $_assignedUserId to $assignedUserId by $user.",
        keys("entity", "_assignedUserId", "assignedUserId")),

    REASSIGN_SYSTEM("REASSIGN_SYSTEM",
        "$entity reassigned from $_assignedUserId to $assignedUserId due to availability rule by $user.",
        keys("entity", "_assignedUserId", "assignedUserId")),

    // ... and many more
}
```

### Complete Activity Action Catalog

| Action | Template | Context Keys | Trigger |
|--------|----------|-------------|---------|
| `CREATE` | `$entity from $source created for $user.` | entity, source | Ticket creation |
| `RE_INQUIRY` | `$entity re-inquired from $source by $user.` | entity, source | Duplicate ticket found |
| `QUALIFY` | `$entity qualified by $user.` | entity | Ticket qualified |
| `DISQUALIFY` | `$entity marked as disqualified by $user.` | entity | Ticket disqualified |
| `DISCARD` | `$entity discarded by $user.` | entity | Ticket discarded |
| `IMPORT` | `$entity imported via $source by $user.` | entity, source | Ticket import |
| `STATUS_CREATE` | `$status created by $user.` | status | Status assignment |
| `STAGE_UPDATE` | `Stage moved from $_stage to $stage by $user.` | _stage, stage | Stage change |
| `WALK_IN` | `$entity walked in by $user.` | entity | Walk-in registration |
| `DCRM_IMPORT` | `$entity imported via DCRM by $user.` | entity | DCRM import |
| `TAG_CREATE` | `$entity was tagged $tag by $user.` | tag | First tag assignment |
| `TAG_UPDATE` | `$entity was tagged $tag from $_tag by $user.` | tag, _tag | Tag change |
| `TASK_CREATE` | `Task $taskId was created by $user.` | taskId | Task creation |
| `TASK_UPDATE` | `Task $taskId was updated by $user.` | taskId | Task modification |
| `TASK_COMPLETE` | `Task $taskId was marked as completed by $user.` | taskId | Task completion |
| `TASK_CANCELLED` | `Task $taskId was marked as cancelled by $user.` | taskId | Task cancellation |
| `TASK_DELETE` | `Task $taskId was deleted by $user.` | taskId | Task deletion |
| `REMINDER_SET` | `Reminder for date $nextReminder, set for $taskId by $user.` | nextReminder, taskId | Reminder creation |
| `DOCUMENT_UPLOAD` | `Document $file uploaded by $user.` | file | File upload |
| `DOCUMENT_DOWNLOAD` | `Document $file downloaded by $user.` | file | File download |
| `DOCUMENT_DELETE` | `Document $file deleted by $user.` | file | File deletion |
| `NOTE_ADD` | `Note $noteId added by $user.` | noteId | Note creation |
| `NOTE_UPDATE` | `Note $noteId was updated by $user.` | noteId | Note modification |
| `NOTE_DELETE` | `Note $noteId deleted by $user.` | noteId | Note deletion |
| `ASSIGN` | `$entity was assigned to $assignedUserId by $user.` | entity, assignedUserId | Initial assignment |
| `REASSIGN` | `$entity was reassigned from $_assignedUserId to $assignedUserId by $user.` | entity, _assignedUserId, assignedUserId | Manual reassignment |
| `REASSIGN_SYSTEM` | `$entity reassigned from $_assignedUserId to $assignedUserId due to availability rule by $user.` | entity, _assignedUserId, assignedUserId | Auto-reassignment |
| `OWNERSHIP_TRANSFER` | `Ownership transferred from $_createdBy to $createdBy by $user.` | _createdBy, createdBy | Ownership change |
| `CALL_LOG` | `Call with $customer logged by $user.` | customer | Call logging |
| `WHATSAPP` | `WhatsApp message sent to $customer by $user.` | customer | WhatsApp message |
| `EMAIL_SENT` | `Email sent to $email by $user.` | email | Email sent |
| `SMS_SENT` | `SMS sent to $customer by $user.` | customer | SMS sent |
| `FIELD_UPDATE` | `$fields by $user.` | fields | Field modification |
| `CUSTOM_FIELD_UPDATE` | `Custom field $field updated to $value by $user.` | field, value | Custom field change |
| `LOCATION_UPDATE` | `Location updated to $location by $user.` | location | Location change |
| `OTHER` | `$action performed on $entity by $user.` | action, entity | Custom action |

### Message Formatting Rules

The `formatMessage` method applies markdown formatting based on the context key name:

```java
private String formatMarkdown(String key, String value) {
    if (key.contains("id")) return mdCode(value);        // IDs: `value`
    if (key.equals("user")) return mdItalics(mdBold(value)); // User: ***value***
    return mdBold(value);                                  // Others: **value**
}
```

| Key Pattern | Format | Example |
|-------------|--------|---------|
| Contains "id" (e.g., `taskId`, `assignedUserId`) | Code: `` `value` `` | `` `42` `` |
| Equals "user" | Bold+Italic: `***value***` | `***John Doe***` |
| All others | Bold: `**value**` | `**WEBSITE**` |

### Value Extraction from Context

Context values can be several types, each extracted differently:

```java
private String getValue(Object value) {
    return switch (value) {
        case Map<?, ?> map when map.size() <= 2 && !map.isEmpty()
            -> formatMapEntries(map);         // "key1: value1, key2: value2"
        case IdAndValue<?, ?> idAndValue
            -> String.valueOf(idAndValue.getValue()); // Display name, not ID
        case ULong ulong
            -> String.valueOf(ulong.longValue());     // Numeric string
        default -> String.valueOf(value);              // toString()
    };
}
```

**Example**: For a `STAGE_UPDATE` activity:
```
Context:
  _stage = IdAndValue(id=1, value="New Lead")
  stage  = IdAndValue(id=3, value="Qualified")
  user   = IdAndValue(id=42, value="John Doe")

Template: "Stage moved from $_stage to $stage by $user."

Result: "Stage moved from **New Lead** to **Qualified** by ***John Doe***."
```

### Old Value Convention

Fields prefixed with `_` represent old/previous values:

```java
public static String getOldName(String fieldName) {
    return "_" + fieldName;  // e.g., "_stage", "_assignedUserId", "_tag"
}
```

### Diff Convention for Updates

Fields prefixed with `@` represent the difference/delta:

```java
public static String getDiffName(String fieldName) {
    return "@" + fieldName;  // e.g., "@Task"
}
```

This is used for task and note updates where the full diff between old and new versions is
stored using `DifferenceExtractor.extract()`.

### Activity Service Methods Summary

| Method | Action | Context |
|--------|--------|---------|
| `acCreate(ticket)` | CREATE | ticketId, source, subSource |
| `acReInquiry(ticket, source, subSource)` | RE_INQUIRY | ticketId, source, subSource |
| `acQualify(ticketId, comment)` | QUALIFY | ticketId |
| `acDisqualify(ticketId, comment)` | DISQUALIFY | ticketId |
| `acDiscard(ticketId, comment)` | DISCARD | ticketId |
| `acImport(ticketId, comment, source)` | IMPORT | ticketId, source |
| `acStageStatus(access, ticket, comment, oldStageId)` | STATUS_CREATE + STAGE_UPDATE | ticketId, stages |
| `acWalkIn(access, ticket, comment)` | WALK_IN | ticketId |
| `acDcrmImport(access, ticket, comment, metadata)` | DCRM_IMPORT | ticketId, metadata |
| `acTagChange(access, ticket, comment, oldTag)` | TAG_CREATE or TAG_UPDATE | ticketId, tag |
| `acTaskCreate(access, task, comment)` | TASK_CREATE | taskId |
| `acTaskUpdate(access, task, updated, comment)` | TASK_UPDATE | taskId + diff |
| `acTaskComplete(task)` | TASK_COMPLETE | taskId |
| `acTaskCancelled(task)` | TASK_CANCELLED | taskId |
| `acTaskDelete(task, comment, deletedDate)` | TASK_DELETE | taskId |
| `acReminderSet(task)` | REMINDER_SET | taskId, nextReminder |
| `acNoteAdd(access, note, comment)` | NOTE_ADD | noteId |
| `acNoteUpdate(access, note, updated, comment)` | NOTE_UPDATE | noteId + diff |
| `acNoteDelete(note, comment, deletedDate)` | NOTE_DELETE | noteId |
| `acDocumentUpload(ticketId, comment, file)` | DOCUMENT_UPLOAD | ticketId, file |
| `acDocumentDownload(ticketId, comment, file)` | DOCUMENT_DOWNLOAD | ticketId, file |
| `acDocumentDelete(ticketId, comment, file)` | DOCUMENT_DELETE | ticketId, file |
| `acAssign(ticketId, comment, newUser)` | ASSIGN | ticketId, newUser |
| `acReassign(access, ticketId, comment, old, new, isAuto)` | REASSIGN or REASSIGN_SYSTEM | ticketId, old/new users |
| `acOwnerShipTransfer(ticketId, comment, old, new)` | OWNERSHIP_TRANSFER | ticketId, old/new owners |
| `acCallLog(access, ticket, comment)` | CALL_LOG | ticketId, customer |
| `createCallLog(callLogRequest)` | CALL_LOG | ticketId, customer, call details |
| `acWhatsapp(ticketId, comment, customer)` | WHATSAPP | ticketId, customer |
| `acEmailSent(ticketId, comment, email)` | EMAIL_SENT | ticketId, email |
| `acSmsSent(ticketId, comment, customer)` | SMS_SENT | ticketId, customer |
| `acFieldUpdate(ticketId, comment, fields)` | FIELD_UPDATE | ticketId, fields |
| `acCustomFieldUpdate(ticketId, comment, field, value)` | CUSTOM_FIELD_UPDATE | ticketId, field, value |
| `acLocationUpdate(ticketId, comment, location)` | LOCATION_UPDATE | ticketId, location |

---

## 15. DNC (Do-Not-Call) Automation

### Overview

When a Business Partner's DNC (Do-Not-Call) flag is updated, the system automatically
propagates this flag to all of the partner's associated tickets.

### Flow

```
PartnerService.updateDncFlag(partnerId, dnc)
    |
    +-- Update partner's DNC flag
    |
    +-- Propagate to tickets:
            TicketService.updateTicketDncByClientId(access, clientId, dnc)
                |
                +-- Fetch all tickets for the client with OPPOSITE DNC value:
                |       dao.getAllClientTicketsByDnc(clientId, !dnc)
                |       [If setting DNC=true, find all tickets with DNC=false]
                |
                +-- Set new DNC value on each ticket:
                |       ticket.setDnc(dnc)
                |
                +-- Update tickets internally:
                        super.updateInternal(access, tickets)
```

### Implementation

```java
public Flux<Ticket> updateTicketDncByClientId(ProcessorAccess access, ULong clientId, Boolean dnc) {
    return this.dao.getAllClientTicketsByDnc(clientId, !dnc)
        .map(ticket -> ticket.setDnc(dnc))
        .flatMap(tickets -> super.updateInternal(access, tickets));
}
```

### Key Points

- DNC propagation is **bulk** -- it affects all tickets for a client at once
- Only tickets with the **opposite** DNC value are updated (optimization to avoid no-op writes)
- No activity is logged for DNC propagation (it is a background system operation)
- The update uses `updateInternal` which bypasses normal validation and auth checks

---

## 16. Owner Synchronization

### Overview

When an Owner entity is updated (name, phone, email change), the system automatically
propagates these changes to all tickets linked to that owner, but only for fields that
the ticket has not explicitly set.

### Flow

```
OwnerService.update(owner)
    |
    +-- Update owner in DB
    |
    +-- Propagate to tickets:
            TicketService.updateOwnerTickets(access, owner)
                |
                +-- For each ticket linked to this owner:
                        |
                        +-- ticket.name is null?
                        |       YES --> Copy owner.name to ticket.name
                        |
                        +-- ticket.email is null?
                        |       YES --> Copy owner.email to ticket.email
                        |
                        +-- ticket.phone is null?
                        |       YES --> Copy owner.phone to ticket.phone
                        |
                        +-- Update ticket internally
```

### Key Points

- Owner synchronization only fills in **null** ticket fields
- If a ticket already has a name/email/phone set, the owner's values do NOT overwrite it
- This ensures manual ticket data is preserved while keeping linked tickets up-to-date
- The synchronization runs as an internal update (no auth checks, no activity logging)

---

## 17. Caching Architecture and Invalidation

### Cache Namespaces

Each rule service maintains its own cache namespace to prevent key collisions:

| Service | Cache Name | Key Pattern |
|---------|-----------|-------------|
| TicketDuplicationRuleService | `ticketDuplicationRule` | `appCode:clientCode:entityId` |
| Duplication Product Condition | `ticketDuplicationProductRuleCondition` | `appCode:clientCode:productId:source:subSource` |
| Duplication Template Condition | `ticketDuplicationProductTemplateRuleCondition` | `appCode:clientCode:templateId:source:subSource` |
| TicketPeDuplicationRuleService | `ticketPeDuplicationRule` | `rule:appCode:clientCode` |
| ProductTicketCRuleService | `productTicketCRule` | Various patterns (see below) |
| ProductTicketRuRuleService | `productTicketRuRule` | Various patterns |
| RU Rule Condition | `ruleConditionCache` | `appCode:clientCode:userId` |
| TicketCUserDistributionService | `ticketCUserDistribution` | `appCode:clientCode:ruleId` |
| TicketRuUserDistributionService | `ticketRUUserDistribution` | `appCode:clientCode:ruleId` |
| User Distribution Maps | `userDistribution:appCode:clientCode` | Various patterns |

### Cache Key Construction

Keys are built using the `getCacheKey` and `getCacheName` helper methods:

```java
// From BaseUpdatableService (inherited by all services)
protected String getCacheName(String... parts) {
    return String.join(":", parts);
}

protected String getCacheKey(Object... parts) {
    return Arrays.stream(parts)
        .map(String::valueOf)
        .collect(Collectors.joining(":"));
}
```

### C-Rule Cache Keys

```
Product rules:
  cacheName: "productTicketCRule"
  cacheKey:  "appCode:clientCode:productId:productId_value:stageId_value"

Template rules:
  cacheName: "productTicketCRule"
  cacheKey:  "appCode:clientCode:productTemplateId:templateId_value:stageId_value"
```

### Cache API

```java
// Cache with value or get from cache
cacheService.cacheValueOrGet(cacheName, supplier, cacheKey)
    // If cached: return cached value
    // If not cached: call supplier, cache result, return result

// Cache including empty values (important for negative lookups)
cacheService.cacheEmptyValueOrGet(cacheName, supplier, cacheKey)
    // Same as above but also caches Mono.empty() results

// Evict single key
cacheService.evict(cacheName, cacheKey)

// Evict all keys in a namespace
cacheService.evictAll(cacheName)
```

### Invalidation Triggers

| Event | Caches Evicted |
|-------|---------------|
| Duplication rule CRUD | Main rule cache + product condition cache + template condition cache |
| PE duplication rule CRUD | Main cache + rule-specific cache |
| C-Rule CRUD | Main cache + stage-specific cache |
| RU-Rule CRUD | Main cache + condition cache |
| User distribution CRUD | Distribution cache + rule cache + user distribution maps cache |

### Example: Duplication Rule Eviction

```java
@Override
protected Mono<Boolean> evictCache(TicketDuplicationRule entity) {
    Mono<Boolean> productEviction = entity.getProductId() != null
        ? this.evictProductConditionCache(
            entity.getAppCode(), entity.getClientCode(), entity.getProductId())
        : Mono.just(Boolean.TRUE);

    return Mono.zip(
        super.evictCache(entity),           // 1. Main rule cache
        productEviction,                     // 2. Product condition cache
        this.evictProductTemplateConditionCache(
            entity.getAppCode(), entity.getClientCode(),
            entity.getProductTemplateId())) // 3. Template condition cache
        .map(t -> t.getT1() && t.getT2() && t.getT3());
}
```

### Example: C-Rule Eviction

```java
@Override
protected Mono<Boolean> evictCache(ProductTicketCRule entity) {
    if (entity.getProductId() != null)
        return Mono.zip(
            super.evictCache(entity),
            cacheService.evict(getCacheName(),
                getCacheKey(entity.getAppCode(), entity.getClientCode(),
                    "productId", entity.getProductId(), entity.getStageId())),
            (a, b) -> a && b);

    return Mono.zip(
        super.evictCache(entity),
        cacheService.evict(getCacheName(),
            getCacheKey(entity.getAppCode(), entity.getClientCode(),
                "productTemplateId", entity.getProductTemplateId(),
                entity.getStageId())),
        (a, b) -> a && b);
}
```

---

## 18. Error Handling and Message Constants

### ProcessorMessageResourceService

All error messages are defined as constants in `ProcessorMessageResourceService` and resolved
via resource bundles supporting localization.

### Rule-Specific Error Messages

| Constant | Key | Usage |
|----------|-----|-------|
| `RULE_ORDER_MISSING` | `rule_order_missing` | Order field not provided in rule creation |
| `RULE_CONDITION_MISSING` | `rule_condition_missing` | Conditional rule (order > 0) has no condition |
| `DUPLICATE_RULE_ORDER` | `duplicate_rule_order` | Another rule with this order already exists |
| `INVALID_RULE_ORDER` | `invalid_rule_order` | Order value is invalid |
| `DEFAULT_RULE_MISSING` | `default_rule_missing` | No default rule (order 0) exists |
| `RULE_PRODUCT_MISSING` | `rule_product_missing` | Neither productId nor productTemplateId provided |
| `DUPLICATE_SOURCE_SUBSOURCE_RULE` | `duplicate_source_subsource_rule` | Same source/subSource combo already exists |

### Entity and Identity Errors

| Constant | Key | Usage |
|----------|-----|-------|
| `IDENTITY_MISSING` | `identity_missing` | Required identifier (e.g., Source) not provided |
| `IDENTITY_INFO_MISSING` | `identity_info_missing` | Identity info incomplete |
| `IDENTITY_WRONG` | `identity_wrong` | Identifier does not match expected value |
| `DUPLICATE_ENTITY` | `duplicate_entity` | Duplicate ticket detected (inside user) |
| `DUPLICATE_ENTITY_OUTSIDE_USER` | `duplicate_entity_outside_user` | Duplicate ticket detected (outside user/BP) |

### Stage and Assignment Errors

| Constant | Key | Usage |
|----------|-----|-------|
| `STAGE_MISSING` | `stage_missing` | Stage ID not provided |
| `TICKET_STAGE_MISSING` | `ticket_stage_missing` | Ticket does not have a stage |
| `TEMPLATE_STAGE_MISSING` | `template_stage_missing` | Template does not have the referenced stage |
| `TEMPLATE_STAGE_INVALID` | `template_stage_invalid` | Stage does not belong to the template |
| `INVALID_STAGE_STATUS` | `invalid_stage_status` | Invalid status for the stage |
| `TICKET_ASSIGNMENT_MISSING` | `ticket_assignment_missing` | No user could be assigned |
| `USER_DISTRIBUTION_TYPE_MISSING` | `user_distribution_type_missing` | Distribution type not set |
| `USER_DISTRIBUTION_INVALID` | `user_distribution_invalid` | Distribution entry is invalid |

### Permission and Access Errors

| Constant | Key | Usage |
|----------|-----|-------|
| `FORBIDDEN_APP_ACCESS` | `forbidden_app_access` | User does not have access to this app |
| `PRODUCT_FORBIDDEN_ACCESS` | `product_forbidden_access` | User cannot access this product |
| `PRODUCT_TEMPLATE_FORBIDDEN_ACCESS` | `product_template_forbidden_access` | User cannot access this template |
| `OUTSIDE_USER_ACCESS` | `outside_user_access` | Outside user attempting restricted action |
| `PARTNER_ACCESS_DENIED` | `partner_access_denied` | Business partner access denied |
| `CONTENT_FORBIDDEN_ACCESS` | `content_forbidden_access` | User cannot access this content |

### Content and Validation Errors

| Constant | Key | Usage |
|----------|-----|-------|
| `CONTENT_MISSING` | `content_missing` | Required content not provided |
| `DATE_IN_PAST` | `date_in_past` | Date is in the past |
| `TASK_ALREADY_COMPLETED` | `task_already_completed` | Task is already completed |
| `TASK_ALREADY_CANCELLED` | `task_already_cancelled` | Task is already cancelled |
| `MISSING_PARAMETERS` | `missing_parameters` | Required parameters missing |
| `INVALID_PARAMETERS` | `invalid_parameters` | Parameters have invalid values |
| `PRODUCT_TEMPLATE_MISSING` | `product_template_missing` | Product does not have a template |

### Error Throwing Pattern

All errors are thrown using the reactive message service pattern:

```java
// Simple error
this.msgService.throwMessage(
    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
    ProcessorMessageResourceService.STAGE_MISSING);

// Error with parameters
this.msgService.throwMessage(
    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
    ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID,
    stageId,         // Parameter 1
    templateId);     // Parameter 2

// Error with HTTP status
this.msgService.throwMessage(
    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
    ProcessorMessageResourceService.IDENTITY_MISSING,
    "Source");
```

---

## 19. REST API Endpoints

### Rule Controllers

All rule controllers extend `BaseRuleController` which provides standard CRUD:

#### Ticket Duplication Rules

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/entity-processor/ticketDuplicationRules` | Create a duplication rule |
| `PUT` | `/api/entity-processor/ticketDuplicationRules` | Update a duplication rule |
| `GET` | `/api/entity-processor/ticketDuplicationRules/{id}` | Read a duplication rule by ID |
| `GET` | `/api/entity-processor/ticketDuplicationRules` | List/filter duplication rules |
| `DELETE` | `/api/entity-processor/ticketDuplicationRules/{id}` | Delete a duplication rule |

#### Phone/Email Duplication Rules

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/entity-processor/ticketPeDuplicationRules` | Create a PE duplication rule |
| `PUT` | `/api/entity-processor/ticketPeDuplicationRules` | Update a PE duplication rule |
| `GET` | `/api/entity-processor/ticketPeDuplicationRules/{id}` | Read a PE duplication rule |
| `GET` | `/api/entity-processor/ticketPeDuplicationRules` | List PE duplication rules |
| `DELETE` | `/api/entity-processor/ticketPeDuplicationRules/{id}` | Delete a PE duplication rule |

#### Product Ticket C-Rules (Creation Assignment)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/entity-processor/productTicketCRules` | Create a C-Rule |
| `PUT` | `/api/entity-processor/productTicketCRules` | Update a C-Rule |
| `GET` | `/api/entity-processor/productTicketCRules/{id}` | Read a C-Rule by ID |
| `GET` | `/api/entity-processor/productTicketCRules` | List/filter C-Rules |
| `DELETE` | `/api/entity-processor/productTicketCRules/{id}` | Delete a C-Rule |

#### Product Ticket RU-Rules (Read/Update Permission)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/entity-processor/productTicketRuRules` | Create a RU-Rule |
| `PUT` | `/api/entity-processor/productTicketRuRules` | Update a RU-Rule |
| `GET` | `/api/entity-processor/productTicketRuRules/{id}` | Read a RU-Rule by ID |
| `GET` | `/api/entity-processor/productTicketRuRules` | List/filter RU-Rules |
| `DELETE` | `/api/entity-processor/productTicketRuRules/{id}` | Delete a RU-Rule |

#### User Distribution Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/entity-processor/ticketCUserDistributions` | Create C-distribution entries |
| `PUT` | `/api/entity-processor/ticketCUserDistributions` | Update C-distribution entries |
| `GET` | `/api/entity-processor/ticketCUserDistributions` | List C-distribution entries |
| `DELETE` | `/api/entity-processor/ticketCUserDistributions/{id}` | Delete a C-distribution entry |
| `POST` | `/api/entity-processor/ticketRuUserDistributions` | Create RU-distribution entries |
| `PUT` | `/api/entity-processor/ticketRuUserDistributions` | Update RU-distribution entries |
| `GET` | `/api/entity-processor/ticketRuUserDistributions` | List RU-distribution entries |
| `DELETE` | `/api/entity-processor/ticketRuUserDistributions/{id}` | Delete a RU-distribution entry |

### Query Parameters

All list endpoints support:
- `page` -- Page number (0-indexed)
- `size` -- Page size
- `sort` -- Sort field and direction
- `fetchUserDistributions=true` -- Include user distributions in response (rules only)

---

## 20. Complete Flowcharts and Decision Trees

### Ticket Creation: Complete Rule Execution Flow

```
Incoming Ticket Creation Request
    |
    +-- Validate basic fields (name, phone, email, source, productId)
    |
    +-- PHASE 1: DUPLICATION CHECK
    |       |
    |       +-- Get PE duplication rule for client
    |       |       --> PhoneNumberAndEmailType (e.g., PHONE_NUMBER_OR_EMAIL)
    |       |
    |       +-- Build phone/email condition based on PE rule type
    |       |
    |       +-- Get condition-based duplication rules:
    |       |       getDuplicateRuleCondition(access, productId, source, subSource)
    |       |       |
    |       |       +-- Product-level rules exist?
    |       |       |       YES --> Use product rules
    |       |       |       NO  --> Template-level rules exist?
    |       |       |               YES --> Use template rules
    |       |       |               NO  --> No condition rules
    |       |       |
    |       |       +-- Filter rules by subSource:
    |       |       |       Exact match > Null wildcard > No match
    |       |       |
    |       |       +-- For each matched rule:
    |       |       |       Build: rule.condition AND stage IN [stages <= maxStageId]
    |       |       |
    |       |       +-- OR all rule conditions together
    |       |
    |       +-- Execute duplicate check:
    |       |       |
    |       |       +-- Condition-based rules exist?
    |       |       |       YES --> Query with condition (two-phase: without stage, then with stage)
    |       |       |       NO  --> Simple phone/email query
    |       |       |
    |       |       +-- Duplicate found?
    |       |               YES --> Create RE_INQUIRY activity
    |       |               |       Throw DUPLICATE_ENTITY error
    |       |               NO  --> Continue to creation
    |
    +-- PHASE 2: USER ASSIGNMENT
    |       |
    |       +-- getUserAssignment(access, productId, stageId, prefix, userId, ticket)
    |       |       |
    |       |       +-- Get rules with order for product + stage:
    |       |       |       Product override mode? --> Product rules only (or template fallback)
    |       |       |       Product combine mode?  --> Merge product + template rules
    |       |       |
    |       |       +-- No rules found?
    |       |       |       --> Use loggedInUser as fallback
    |       |       |
    |       |       +-- Execute rules:
    |       |       |       1. Evaluate conditional rules (order > 0) in order
    |       |       |       2. First matching condition wins
    |       |       |       3. No match? Use default rule (order 0)
    |       |       |
    |       |       +-- From matched rule, get user pool:
    |       |       |       getUsersByRuleId(ruleId)
    |       |       |       --> Resolve userId, roleId, profileId, designationId, departmentId
    |       |       |       --> Set<ULong> of concrete user IDs
    |       |       |
    |       |       +-- Logged-in user in pool?
    |       |       |       YES --> Assign to logged-in user
    |       |       |       NO  --> Distribute using algorithm:
    |       |       |               ROUND_ROBIN: Next in sorted order after lastAssigned
    |       |       |               RANDOM: Random user, excluding lastAssigned
    |       |       |
    |       |       +-- Update rule.lastAssignedUserId in DB
    |       |       +-- Return assigned userId
    |       |
    |       +-- Set ticket.assignedUserId
    |
    +-- PHASE 3: CREATE TICKET
    |       |
    |       +-- Save ticket to database
    |       +-- Create CREATE activity
    |       +-- Return created ticket
```

### Stage Change: Complete Reassignment Flow

```
Stage Update Request (ticketId, newStageId, newStatus)
    |
    +-- Read existing ticket
    +-- Validate new stage belongs to product template
    +-- Update ticket stage and status in DB
    |
    +-- oldStageId != newStageId?
    |       NO  --> Log STATUS_CREATE activity only, DONE
    |       YES --> Continue
    |
    +-- Log STAGE_UPDATE activity + STATUS_CREATE activity
    |
    +-- reassignUserId provided in request?
    |       YES --> Use provided userId
    |       NO  --> Auto-assign:
    |               getUserAssignment(access, productId, newStageId,
    |                                  prefix, currentAssignee, ticket, isCreate=false)
    |               |
    |               +-- Only default rule (order 0) exists?
    |               |       YES --> No reassignment (return empty)
    |               |
    |               +-- Conditional rules exist:
    |               |       Evaluate against ticket data
    |               |       Matched? --> Get user from matched rule's pool
    |               |       No match? --> Get user from default rule's pool
    |               |
    |               +-- New user == current assignee?
    |                       YES --> No reassignment
    |                       NO  --> Reassign
    |
    +-- New user assigned?
            YES --> Update ticket.assignedUserId
            |       Log REASSIGN_SYSTEM activity:
            |       "$entity reassigned from $old to $new due to availability rule by $user."
            |
            NO  --> No reassignment, DONE
```

---

## 21. Practical Scenarios and Examples

### Scenario 1: Real Estate CRM with Multiple Sources

**Setup**:
- Product: "Luxury Apartments" (productId=100, templateId=10)
- Stages: New Lead (order 1) -> Contacted (order 2) -> Site Visit (order 3) -> Qualified (order 4)
- Sources: WEBSITE, FACEBOOK, WALK_IN

**Duplication Rules**:
```json
[
    {
        "productId": 100,
        "source": "WEBSITE",
        "subSource": null,
        "maxStageId": 3,
        "order": 1,
        "condition": { "field": "city", "operator": "EQUALS", "value": "Mumbai" }
    },
    {
        "productId": 100,
        "source": "FACEBOOK",
        "subSource": "META_ADS",
        "maxStageId": 2,
        "order": 1,
        "condition": null
    }
]
```

**Behavior**:
- Website leads from Mumbai: Duplicates checked against tickets at stages 1-3 (up to Site Visit)
- Facebook Meta Ads leads: Duplicates checked against tickets at stages 1-2 (up to Contacted)
- Walk-in leads: No duplication rules, simple phone/email check applies

**C-Rules**:
```json
[
    {
        "productId": 100,
        "stageId": 1,
        "order": 0,
        "userDistributionType": "ROUND_ROBIN",
        "userDistributions": [
            { "userId": 201 },
            { "userId": 202 },
            { "userId": 203 }
        ]
    },
    {
        "productId": 100,
        "stageId": 1,
        "order": 1,
        "condition": { "field": "source", "operator": "EQUALS", "value": "WALK_IN" },
        "userDistributionType": "RANDOM",
        "userDistributions": [
            { "userId": 204 },
            { "userId": 205 }
        ]
    }
]
```

**Behavior**:
- Walk-in leads: Assigned randomly to users 204 or 205
- All other leads: Round-robin across users 201, 202, 203

### Scenario 2: Insurance Product with Department-Based Distribution

**Setup**:
- Product Template: "Motor Insurance" (templateId=20)
- Two products: "Car Insurance" (productId=200), "Bike Insurance" (productId=300)

**C-Rules on template** (apply to both products):
```json
[
    {
        "productTemplateId": 20,
        "stageId": 1,
        "order": 0,
        "userDistributionType": "ROUND_ROBIN",
        "userDistributions": [
            { "departmentId": 50 }
        ]
    }
]
```

**Behavior**:
- All new Motor Insurance tickets are round-robin distributed among users in Department 50
- Both Car Insurance and Bike Insurance share the same rule
- If Car Insurance defines its own C-Rule, it overrides the template rule (depending on
  `overrideCTemplate` flag)

### Scenario 3: RU-Rules for Tiered Access

**Setup**:
- Product: "Gold Loan" (productId=400)
- Roles: Junior Agent (roleId=10), Senior Agent (roleId=20), Manager (roleId=30)

**RU-Rules**:
```json
[
    {
        "productId": 400,
        "order": 1,
        "condition": { "field": "productId", "operator": "EQUALS", "value": 400 },
        "canEdit": false,
        "userDistributions": [
            { "roleId": 10 }
        ]
    },
    {
        "productId": 400,
        "order": 2,
        "condition": { "field": "productId", "operator": "EQUALS", "value": 400 },
        "canEdit": true,
        "userDistributions": [
            { "roleId": 20 },
            { "roleId": 30 }
        ]
    }
]
```

**Behavior**:
- Junior Agents (roleId=10): Can read Gold Loan tickets but NOT edit
- Senior Agents and Managers: Can both read AND edit Gold Loan tickets
- Users not matching any distribution: Cannot see Gold Loan tickets at all

### Scenario 4: Stage-Based Auto-Reassignment

**Setup**:
- Product: "Home Loan" (productId=500)
- Stages: New Lead -> Qualification -> Documentation -> Disbursement
- C-Rules for "Qualification" stage assign to Verification Team
- C-Rules for "Documentation" stage assign to Documentation Team

**Flow**:
```
1. Lead created at "New Lead" stage
   --> Assigned to Sales Agent via New Lead C-Rule (round-robin)

2. Sales Agent moves ticket to "Qualification" stage
   --> System auto-reassigns to Verification Team member
   --> REASSIGN_SYSTEM activity logged:
       "Home Loan Ticket reassigned from Sales Agent to Verifier
        due to availability rule by Sales Agent."

3. Verifier moves ticket to "Documentation" stage
   --> System auto-reassigns to Documentation Team member
   --> Another REASSIGN_SYSTEM activity logged

4. If no C-Rule exists for "Disbursement" stage
   --> No auto-reassignment (stays with Documentation team member)
```

---

## 22. Database Schema Reference

### Core Rule Tables

#### entity_processor_ticket_duplication_rules

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT UNSIGNED | Primary key |
| APP_CODE | VARCHAR | Application code |
| CLIENT_CODE | VARCHAR | Client code |
| PRODUCT_ID | BIGINT UNSIGNED | Product reference (nullable) |
| PRODUCT_TEMPLATE_ID | BIGINT UNSIGNED | Template reference (nullable) |
| ORDER | INT | Rule order (0 = default) |
| SOURCE | VARCHAR | Source channel (required) |
| SUB_SOURCE | VARCHAR | Sub-source (nullable) |
| MAX_STAGE_ID | BIGINT UNSIGNED | Maximum stage for duplicate check |
| CONDITION | JSON | AbstractCondition tree |
| USER_DISTRIBUTION_TYPE | ENUM | Distribution algorithm |
| LAST_ASSIGNED_USER_ID | BIGINT UNSIGNED | Last assigned user (for round-robin) |
| ACTIVE | BOOLEAN | Soft delete flag |
| CREATED_BY | BIGINT UNSIGNED | Creator |
| CREATED_AT | DATETIME | Creation timestamp |
| UPDATED_BY | BIGINT UNSIGNED | Last updater |
| UPDATED_AT | DATETIME | Last update timestamp |

#### entity_processor_product_ticket_c_rules

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT UNSIGNED | Primary key |
| APP_CODE | VARCHAR | Application code |
| CLIENT_CODE | VARCHAR | Client code |
| PRODUCT_ID | BIGINT UNSIGNED | Product reference (nullable) |
| PRODUCT_TEMPLATE_ID | BIGINT UNSIGNED | Template reference (nullable) |
| STAGE_ID | BIGINT UNSIGNED | Stage this rule applies to |
| ORDER | INT | Rule order (0 = default) |
| CONDITION | JSON | AbstractCondition tree |
| USER_DISTRIBUTION_TYPE | ENUM | Distribution algorithm |
| LAST_ASSIGNED_USER_ID | BIGINT UNSIGNED | Last assigned user |
| ACTIVE | BOOLEAN | Soft delete flag |
| CREATED_BY / CREATED_AT | -- | Audit fields |
| UPDATED_BY / UPDATED_AT | -- | Audit fields |

#### entity_processor_product_ticket_ru_rules

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT UNSIGNED | Primary key |
| APP_CODE | VARCHAR | Application code |
| CLIENT_CODE | VARCHAR | Client code |
| PRODUCT_ID | BIGINT UNSIGNED | Product reference (nullable) |
| PRODUCT_TEMPLATE_ID | BIGINT UNSIGNED | Template reference (nullable) |
| ORDER | INT | Rule order |
| CAN_EDIT | BOOLEAN | Whether matched users can update |
| CONDITION | JSON | AbstractCondition tree |
| ACTIVE | BOOLEAN | Soft delete flag |
| Audit fields | -- | Standard audit columns |

#### entity_processor_ticket_pe_duplication_rules

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT UNSIGNED | Primary key |
| APP_CODE | VARCHAR | Application code |
| CLIENT_CODE | VARCHAR | Client code |
| PHONE_NUMBER_AND_EMAIL_TYPE | ENUM | Matching mode |
| ACTIVE | BOOLEAN | Soft delete flag |
| Audit fields | -- | Standard audit columns |

### Distribution Tables

#### entity_processor_ticket_c_user_distributions

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT UNSIGNED | Primary key |
| RULE_ID | BIGINT UNSIGNED | FK to C-Rule |
| USER_ID | BIGINT UNSIGNED | Direct user (nullable) |
| ROLE_ID | BIGINT UNSIGNED | Role reference (nullable) |
| PROFILE_ID | BIGINT UNSIGNED | Profile reference (nullable) |
| DESIGNATION_ID | BIGINT UNSIGNED | Designation reference (nullable) |
| DEPARTMENT_ID | BIGINT UNSIGNED | Department reference (nullable) |
| Audit fields | -- | Standard audit columns |

Constraint: Exactly one of USER_ID, ROLE_ID, PROFILE_ID, DESIGNATION_ID, DEPARTMENT_ID
must be non-null.

#### entity_processor_ticket_ru_user_distributions

Same structure as C-distribution but FK references RU-Rule.

---

## 23. Troubleshooting Guide

### Common Issues

#### Issue: Tickets not being assigned to any user

**Possible causes**:
1. No C-Rules configured for the product and stage
2. C-Rule user distribution is empty
3. All users in the distribution have been deactivated
4. The security service cannot resolve user IDs from role/profile/designation/department

**Diagnosis**:
```
1. Check if C-Rules exist:
   GET /api/entity-processor/productTicketCRules?productId=XXX&stageId=YYY

2. Check user distributions:
   GET /api/entity-processor/ticketCUserDistributions?ruleId=ZZZ

3. Verify users are active in the security service
```

#### Issue: Duplicate detection not working

**Possible causes**:
1. No duplication rules configured for the source
2. SubSource does not match (neither exact nor wildcard)
3. MaxStageId is too low (existing ticket is at a higher stage)
4. Rule condition does not match the incoming ticket data

**Diagnosis**:
```
1. Check duplication rules for the source:
   GET /api/entity-processor/ticketDuplicationRules?source=XXX

2. Verify the PE duplication rule type:
   GET /api/entity-processor/ticketPeDuplicationRules

3. Check which stages are included (<= maxStageId)
```

#### Issue: Round-robin assigning the same user repeatedly

**Possible causes**:
1. Only one user in the distribution pool
2. `lastAssignedUserId` is not being updated (check for errors in updateInternal)
3. Cache returning stale rule data after user pool changes

**Diagnosis**:
```
1. Check the rule's lastAssignedUserId
2. Verify user distribution has multiple users
3. Force cache eviction by updating the rule
```

#### Issue: Stage change not triggering reassignment

**Possible causes**:
1. Only default rule (order 0) exists for the new stage -- no reassignment on stage change
   with only default rules
2. No C-Rules configured for the new stage at all
3. The condition-matched rule's user pool is empty
4. The rule evaluation threw an error (errors are silently swallowed via `onErrorResume`)

**Diagnosis**:
```
1. Check C-Rules for the new stage
2. Verify conditional rules (order > 0) exist
3. Check server logs for suppressed errors in getUserAssignment
```

#### Issue: RU-Rules not filtering tickets correctly

**Possible causes**:
1. User's role/profile/designation/department not matching any distribution entry
2. Product's `overrideRuTemplate` flag is set incorrectly
3. Condition cache is stale

**Diagnosis**:
```
1. Verify the user's attributes match a distribution entry
2. Check product.overrideRuTemplate setting
3. Force cache eviction by updating any RU-Rule
```

### Performance Considerations

1. **Cache warm-up**: The first request after a cache eviction will be slower as rules
   are fetched from the database. Consider pre-warming caches after bulk rule updates.

2. **User mapping resolution**: The `getAllUserMappings` call fetches all users from the
   security service. For large organizations, this can be expensive. The result is cached
   but eviction should be managed carefully.

3. **Condition evaluation**: Complex nested conditions with many levels of AND/OR can
   increase evaluation time. Keep conditions as flat as possible.

4. **Round-robin state updates**: Every ticket assignment updates the rule's
   `lastAssignedUserId` in the database. Under very high load, this can become a
   contention point. Consider RANDOM distribution for extremely high-throughput scenarios.

---

## Appendix A: DistributionType Values and Status

| Type | Status | Description |
|------|--------|-------------|
| `ROUND_ROBIN` | Implemented | Cyclic sequential assignment |
| `RANDOM` | Implemented | Random assignment (no consecutive repeat) |
| `PERCENTAGE` | Defined | Percentage-based distribution (not yet implemented) |
| `WEIGHTED` | Defined | Weighted distribution (not yet implemented) |
| `LOAD_BALANCED` | Defined | Load-balanced distribution (not yet implemented) |
| `PRIORITY_QUEUE` | Defined | Priority-based distribution (not yet implemented) |
| `HYBRID` | Defined | Hybrid distribution (not yet implemented) |

## Appendix B: EntitySeries Values for Rules

| Series | Table | Description |
|--------|-------|-------------|
| `TICKET_DUPLICATION_RULES` | entity_processor_ticket_duplication_rules | Duplication rules |
| `TICKET_PE_DUPLICATION_RULES` | entity_processor_ticket_pe_duplication_rules | PE duplication rules |
| `PRODUCT_TICKET_C_RULE` | entity_processor_product_ticket_c_rules | Creation rules |
| `PRODUCT_TICKET_RU_RULE` | entity_processor_product_ticket_ru_rules | Read/Update rules |
| `TICKET_C_USER_DISTRIBUTION` | entity_processor_ticket_c_user_distributions | C-Rule distributions |
| `TICKET_RU_USER_DISTRIBUTION` | entity_processor_ticket_ru_user_distributions | RU-Rule distributions |

## Appendix C: Key Source Files

| File | Path |
|------|------|
| BaseRuleService | `service/rule/BaseRuleService.java` |
| BaseRuleDto | `dto/rule/BaseRuleDto.java` |
| BaseRuleDAO | `dao/rule/BaseRuleDAO.java` |
| TicketDuplicationRuleService | `service/rule/TicketDuplicationRuleService.java` |
| TicketDuplicationRule | `dto/rule/TicketDuplicationRule.java` |
| TicketDuplicationRuleDAO | `dao/rule/TicketDuplicationRuleDAO.java` |
| TicketPeDuplicationRuleService | `service/rule/TicketPeDuplicationRuleService.java` |
| TicketPeDuplicationRule | `dto/rule/TicketPeDuplicationRule.java` |
| ProductTicketCRuleService | `service/product/ProductTicketCRuleService.java` |
| ProductTicketCRule | `dto/product/ProductTicketCRule.java` |
| ProductTicketCRuleDAO | `dao/product/ProductTicketCRuleDAO.java` |
| TicketCRuleExecutionService | `service/rule/TicketCRuleExecutionService.java` |
| ProductTicketRuRuleService | `service/product/ProductTicketRuRuleService.java` |
| ProductTicketRuRule | `dto/product/ProductTicketRuRule.java` |
| ProductTicketRuRuleDAO | `dao/product/ProductTicketRuRuleDAO.java` |
| BaseUserDistributionService | `service/rule/BaseUserDistributionService.java` |
| BaseUserDistributionDto | `dto/rule/BaseUserDistributionDto.java` |
| BaseUserDistributionDAO | `dao/rule/BaseUserDistributionDAO.java` |
| TicketCUserDistributionService | `service/rule/TicketCUserDistributionService.java` |
| TicketRuUserDistributionService | `service/rule/TicketRuUserDistributionService.java` |
| IRuleUserDistributionService | `service/rule/IRuleUserDistributionService.java` |
| DistributionType | `enums/rule/DistributionType.java` |
| PhoneNumberAndEmailType | `enums/PhoneNumberAndEmailType.java` |
| ActivityService | `service/ActivityService.java` |
| ActivityAction | `enums/ActivityAction.java` |
| ProcessorMessageResourceService | `service/ProcessorMessageResourceService.java` |
| AbstractCondition | `commons/model/condition/AbstractCondition.java` |
| FilterCondition | `commons/model/condition/FilterCondition.java` |
| ComplexCondition | `commons/model/condition/ComplexCondition.java` |
| FilterConditionOperator | `commons/model/condition/FilterConditionOperator.java` |
| ConditionEvaluator | `commons/service/ConditionEvaluator.java` |
