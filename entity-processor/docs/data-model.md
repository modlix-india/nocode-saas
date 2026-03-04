# Entity Processor - Data Model Reference

## Table of Contents

- [Overview](#overview)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Base DTO Hierarchy](#base-dto-hierarchy)
- [Core Entities](#core-entities)
  - [Ticket](#ticket)
  - [Owner](#owner)
  - [Activity](#activity)
  - [Stage](#stage)
  - [Campaign](#campaign)
  - [Partner](#partner)
- [Product Entities](#product-entities)
  - [Product](#product)
  - [ProductTemplate](#producttemplate)
  - [ProductComm](#productcomm)
  - [ProductTicketCRule](#productticketcrule)
  - [ProductTicketRuRule](#productticketrurule)
- [Content Entities](#content-entities)
  - [Task](#task)
  - [TaskType](#tasktype)
  - [Note](#note)
- [Rule Entities](#rule-entities)
  - [TicketDuplicationRule](#ticketduplicationrule)
  - [TicketPeDuplicationRule](#ticketpeduplicationrule)
  - [TicketCUserDistribution](#ticketcuserdistribution)
  - [TicketRuUserDistribution](#ticketruuserdistribution)
- [Form Entities](#form-entities)
  - [ProductWalkInForm](#productwalkinform)
  - [ProductTemplateWalkInForm](#producttemplatewalkinform)
- [Enumerations](#enumerations)
- [Common Models](#common-models)
- [Request Models](#request-models)
- [Response Models](#response-models)
- [Database Schema Conventions](#database-schema-conventions)

---

## Overview

The Entity Processor data model is built on a layered DTO hierarchy with consistent patterns for multi-tenancy, versioning, auditing, and relationship management. All entities are stored in MySQL using JOOQ-generated table mappings with R2DBC for reactive database connectivity.

### Key Conventions

- All IDs use `org.jooq.types.ULong` (BIGINT UNSIGNED in MySQL)
- All timestamps use `java.time.LocalDateTime`
- Every entity has a unique `code` field (CHAR-based)
- Multi-tenancy via `appCode` and `clientCode` on every entity
- Version-based optimistic locking on processor entities
- Standard audit fields: `createdBy`, `createdAt`, `updatedBy`, `updatedAt`
- Lombok annotations: `@Data`, `@Accessors(chain = true)`, `@FieldNameConstants`

---

## Entity Relationship Diagram

```
                         ┌──────────────────────┐
                         │   ProductTemplate     │
                         │  (PIPELINE/FORM/      │
                         │   WIZARD)             │
                         └──────────┬────────────┘
                                    │ 1
                           ┌────────┼────────┐
                           │        │        │
                          *│       *│       *│
                    ┌──────▼──┐ ┌──▼────┐ ┌─▼───────────────┐
                    │  Stage  │ │Product│ │TicketDuplication │
                    │         │ │       │ │     Rule         │
                    └──┬──────┘ └───┬───┘ └─────────────────┘
                       │ parent     │ 1
                      *│            │
                    ┌──▼──────┐     │
                    │  Stage  │     │
                    │(Status) │     │
                    └─────────┘     │
                                    │
    ┌──────────┐              ┌─────▼────┐           ┌──────────┐
    │ Campaign │──────────────│  Ticket  │───────────│  Owner   │
    │          │      *     1 │          │ *       1 │          │
    └──────────┘              └──┬──┬──┬─┘           └──────────┘
                                 │  │  │
                    ┌────────────┘  │  └────────────┐
                    │              │                 │
                   *│             *│                *│
              ┌─────▼───┐   ┌─────▼───┐      ┌─────▼───┐
              │  Task   │   │Activity │      │  Note   │
              │         │   │         │      │         │
              └─────────┘   └─────────┘      └─────────┘

    ┌──────────────┐     ┌────────────────────┐
    │  Partner     │     │  ProductComm       │
    │              │     │  (Communication    │
    │              │     │   Config)          │
    └──────────────┘     └────────────────────┘

    ┌───────────────────┐     ┌──────────────────────┐
    │ProductTicketCRule  │────│TicketCUserDistribution│
    │(Creation Rules)    │    │(User Assignment Pool) │
    └───────────────────┘     └──────────────────────┘

    ┌───────────────────┐     ┌───────────────────────┐
    │ProductTicketRuRule │────│TicketRuUserDistribution│
    │(RU Rules)          │    │(RU User Pool)          │
    └───────────────────┘     └───────────────────────┘

    ┌────────────────────┐     ┌──────────────────────────┐
    │ProductWalkInForm   │     │ProductTemplateWalkInForm  │
    └────────────────────┘     └──────────────────────────┘
```

---

## Base DTO Hierarchy

The Entity Processor uses a layered DTO hierarchy that provides progressively more functionality:

```
AbstractDTO (commons)
  └── AbstractUpdatableDTO (commons)
        └── BaseDto
              └── BaseUpdatableDto
                    ├── BaseValueDto
                    └── BaseProcessorDto
                          └── BaseRuleDto
```

### AbstractDTO (from commons)

The root DTO from the commons library. Provides the fundamental identity and audit fields.

| Field | Type | DB Column | Description |
|-------|------|-----------|-------------|
| `id` | ULong | `ID` | Primary key, auto-generated |
| `createdBy` | ULong | `CREATED_BY` | ID of the user who created the record |
| `createdAt` | LocalDateTime | `CREATED_AT` | Timestamp of creation |

### AbstractUpdatableDTO (from commons)

Extends AbstractDTO with update tracking fields.

| Field | Type | DB Column | Description |
|-------|------|-----------|-------------|
| `updatedBy` | ULong | `UPDATED_BY` | ID of the user who last updated the record |
| `updatedAt` | LocalDateTime | `UPDATED_AT` | Timestamp of last update |

### BaseDto

The Entity Processor's foundational DTO. Adds entity identity and multi-tenancy fields.

```java
public abstract class BaseDto<D extends BaseDto<D>>
        extends AbstractUpdatableDTO<ULong, D> {

    private String code;
    private String name;
    private String description;
    private String appCode;
    private String clientCode;

    // Eager loading relationship maps
    protected Map<String, Table<?>> relationsMap = new HashMap<>();
    protected Map<Class<?>, String> relationsResolverMap = new HashMap<>();
}
```

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `code` | String | `CODE` | No | Unique code identifier (auto-generated, CHAR-based) |
| `name` | String | `NAME` | Yes | Display name |
| `description` | String | `DESCRIPTION` | Yes | Description text |
| `appCode` | String | `APP_CODE` | No | Application code for multi-tenancy |
| `clientCode` | String | `CLIENT_CODE` | No | Client code for multi-tenancy |

**Relationship Maps:**
- `relationsMap` - Maps field names to JOOQ Table references for eager loading via JOINs
- `relationsResolverMap` - Maps resolver classes to field names for custom resolution (e.g., user lookup from Security service)

### BaseUpdatableDto

Extends BaseDto with an active/inactive flag.

```java
public abstract class BaseUpdatableDto<D extends BaseUpdatableDto<D>>
        extends BaseDto<D> {

    private Boolean active = Boolean.TRUE;
}
```

| Field | Type | DB Column | Default | Description |
|-------|------|-----------|---------|-------------|
| `active` | Boolean | `ACTIVE` | `true` | Whether the entity is active |

### BaseProcessorDto

The primary base class for most Entity Processor entities. Adds version-based optimistic locking and client association.

```java
public abstract class BaseProcessorDto<D extends BaseProcessorDto<D>>
        extends BaseUpdatableDto<D> {

    private Integer version = 0;
    private ULong clientId;

    public abstract EntitySeries getEntitySeries();
    public abstract ULong getAccessUser();
}
```

| Field | Type | DB Column | Default | Description |
|-------|------|-----------|---------|-------------|
| `version` | Integer | `VERSION` | `0` | Optimistic lock version counter |
| `clientId` | ULong | `CLIENT_ID` | null | Associated client ID (for Business Partner tracking) |

**Abstract Methods:**
- `getEntitySeries()` - Returns the EntitySeries enum value for this entity type
- `getAccessUser()` - Returns the user ID that controls access to this entity (e.g., assignedUserId for Ticket)

**Version-Based Optimistic Locking:**
- On create: version starts at 0
- On update: version in request must match current version in database
- If mismatch: throws 412 Precondition Failed with "version_mismatch" message
- On successful update: version is incremented by 1

### BaseValueDto

Used for simple lookup/value entities.

```java
public abstract class BaseValueDto<D extends BaseValueDto<D>>
        extends BaseDto<D> {
    // No additional fields
}
```

### BaseRuleDto

Base class for all rule entities. Adds product/template scoping and ordering.

```java
public abstract class BaseRuleDto<D extends BaseRuleDto<D>>
        extends BaseProcessorDto<D> {

    private ULong productId;
    private ULong productTemplateId;
    private Integer order;
}
```

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `productId` | ULong | `PRODUCT_ID` | Yes | Product scope (mutually exclusive or combined with templateId) |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | Yes | Template scope |
| `order` | Integer | `RULE_ORDER` | No | Execution order (0 = default rule) |

---

## Core Entities

### Ticket

**Database Table:** `entity_processor_tickets`
**EntitySeries:** `TICKET` (value: 12)
**Purpose:** The primary CRM entity representing a deal, lead, or opportunity in the pipeline.

```java
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class Ticket extends BaseProcessorDto<Ticket> {

    private ULong ownerId;
    private ULong assignedUserId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private ULong productId;
    private ULong stage;
    private ULong status;
    private String source;
    private String subSource;
    private ULong campaignId;
    private Boolean dnc = Boolean.FALSE;
    private Tag tag;
    private Map<String, Object> metaData;
    private ULong productTemplateId = null;
    private LocalDateTime latestTaskDueDate;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Default | Description |
|-------|------|-----------|----------|---------|-------------|
| `id` | ULong | `ID` | No | Auto | Primary key |
| `code` | String | `CODE` | No | Auto | Unique ticket code |
| `name` | String | `NAME` | Yes | - | Contact/deal name |
| `description` | String | `DESCRIPTION` | Yes | - | Deal description |
| `appCode` | String | `APP_CODE` | No | - | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | - | Client code |
| `version` | Integer | `VERSION` | No | 0 | Optimistic lock version |
| `clientId` | ULong | `CLIENT_ID` | Yes | null | Business Partner client ID |
| `active` | Boolean | `ACTIVE` | No | true | Active flag |
| `ownerId` | ULong | `OWNER_ID` | Yes | - | FK to Owner entity |
| `assignedUserId` | ULong | `ASSIGNED_USER_ID` | No | - | FK to Security user |
| `dialCode` | Integer | `DIAL_CODE` | No | 91 | Phone country code |
| `phoneNumber` | String | `PHONE_NUMBER` | Yes | - | Phone number |
| `email` | String | `EMAIL` | Yes | - | Email address |
| `productId` | ULong | `PRODUCT_ID` | No | - | FK to Product entity |
| `stage` | ULong | `STAGE` | No | - | FK to Stage entity (current pipeline stage) |
| `status` | ULong | `STATUS` | Yes | - | FK to Stage entity (current status within stage) |
| `source` | String | `SOURCE` | No | - | Lead source (normalized) |
| `subSource` | String | `SUB_SOURCE` | Yes | - | Lead sub-source (normalized) |
| `campaignId` | ULong | `CAMPAIGN_ID` | Yes | - | FK to Campaign entity |
| `dnc` | Boolean | `DNC` | No | false | Do-Not-Call flag |
| `tag` | Tag | `TAG` | Yes | - | Ticket tag classification |
| `metaData` | Map<String, Object> | `META_DATA` | Yes | - | Additional metadata (JSON) |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | Yes | null | Denormalized template ID |
| `latestTaskDueDate` | LocalDateTime | `LATEST_TASK_DUE_DATE` | Yes | - | Latest task due date |
| `createdBy` | ULong | `CREATED_BY` | No | - | Creator user ID |
| `createdAt` | LocalDateTime | `CREATED_AT` | No | - | Creation timestamp |
| `updatedBy` | ULong | `UPDATED_BY` | Yes | - | Last updater user ID |
| `updatedAt` | LocalDateTime | `UPDATED_AT` | Yes | - | Last update timestamp |

#### Relationships (Eager Loading)

| Field | Related Entity | Related Table | Resolution Type |
|-------|---------------|---------------|-----------------|
| `ownerId` | Owner | `entity_processor_owners` | Table JOIN |
| `productId` | Product | `entity_processor_products` | Table JOIN |
| `stage` | Stage | `entity_processor_stages` | Table JOIN |
| `status` | Stage | `entity_processor_stages` | Table JOIN |
| `campaignId` | Campaign | `entity_processor_campaigns` | Table JOIN |
| `productTemplateId` | ProductTemplate | `entity_processor_product_templates` | Table JOIN |
| `assignedUserId` | User (Security) | N/A | UserFieldResolver |

#### Factory Methods

**Ticket.of(TicketRequest)**
```java
public static Ticket of(TicketRequest ticketRequest) {
    return new Ticket()
        .setDialCode(ticketRequest.getPhoneNumber() != null
            ? ticketRequest.getPhoneNumber().getCountryCode() : null)
        .setPhoneNumber(ticketRequest.getPhoneNumber() != null
            ? ticketRequest.getPhoneNumber().getNumber() : null)
        .setEmail(ticketRequest.getEmail() != null
            ? ticketRequest.getEmail().getAddress() : null)
        .setSource(ticketRequest.getSource())
        .setSubSource(ticketRequest.getSubSource())
        .setName(ticketRequest.getName())
        .setDescription(ticketRequest.getDescription());
}
```

**Ticket.of(CampaignTicketRequest)**
Creates a ticket from campaign data. Captures `keyword` from campaign details in `metaData`. Assembles name from `firstName + lastName` or `fullName`.

**Ticket.of(WalkInFormTicketRequest)**
Creates a ticket from walk-in form submission. Sets source based on form configuration.

#### Access Control

```java
@Override
@JsonIgnore
public ULong getAccessUser() {
    return this.getAssignedUserId();
}
```

The `assignedUserId` controls who has primary access to the ticket.

#### Source Normalization

```java
public Ticket setSource(String source) {
    if (StringUtil.safeIsBlank(source)) return this;
    this.source = NameUtil.normalize(source);
    return this;
}
```

Both `source` and `subSource` are normalized via `NameUtil.normalize()` on set.

#### JSON Example

```json
{
    "id": 12345,
    "code": "TKT00001",
    "name": "John Doe",
    "description": "Premium plan inquiry",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 2,
    "clientId": null,
    "active": true,
    "ownerId": 5001,
    "assignedUserId": 2001,
    "dialCode": 91,
    "phoneNumber": "9876543210",
    "email": "john@example.com",
    "productId": 100,
    "stage": 301,
    "status": 401,
    "source": "Website",
    "subSource": "Landing Page v2",
    "campaignId": null,
    "dnc": false,
    "tag": "HOT",
    "metaData": null,
    "productTemplateId": 50,
    "latestTaskDueDate": "2024-03-10T10:00:00",
    "createdBy": 2001,
    "createdAt": "2024-03-01T09:00:00",
    "updatedBy": 2001,
    "updatedAt": "2024-03-04T14:30:00"
}
```

---

### Owner

**Database Table:** `entity_processor_owners`
**EntitySeries:** `OWNER` (value: 13)
**LeadZump Name:** Lead
**Purpose:** Represents a contact/lead associated with one or more tickets.

```java
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class Owner extends BaseProcessorDto<Owner> {

    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private String source;
    private String subSource;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Default | Description |
|-------|------|-----------|----------|---------|-------------|
| `id` | ULong | `ID` | No | Auto | Primary key |
| `code` | String | `CODE` | No | Auto | Unique owner code |
| `name` | String | `NAME` | Yes | - | Contact full name |
| `description` | String | `DESCRIPTION` | Yes | - | Description |
| `appCode` | String | `APP_CODE` | No | - | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | - | Client code |
| `version` | Integer | `VERSION` | No | 0 | Optimistic lock version |
| `clientId` | ULong | `CLIENT_ID` | Yes | null | Business Partner client ID |
| `active` | Boolean | `ACTIVE` | No | true | Active flag |
| `dialCode` | Integer | `DIAL_CODE` | No | 91 | Phone country code |
| `phoneNumber` | String | `PHONE_NUMBER` | Yes | - | Phone number |
| `email` | String | `EMAIL` | Yes | - | Email address |
| `source` | String | `SOURCE` | Yes | - | Lead source (normalized) |
| `subSource` | String | `SUB_SOURCE` | Yes | - | Sub-source (normalized) |

#### Factory Methods

**Owner.of(OwnerRequest)**
```java
public static Owner of(OwnerRequest ownerRequest) {
    return new Owner()
        .setDialCode(ownerRequest.getPhoneNumber() != null
            ? ownerRequest.getPhoneNumber().getCountryCode() : null)
        .setPhoneNumber(ownerRequest.getPhoneNumber() != null
            ? ownerRequest.getPhoneNumber().getNumber() : null)
        .setEmail(ownerRequest.getEmail() != null
            ? ownerRequest.getEmail().getAddress() : null)
        .setSource(ownerRequest.getSource())
        .setSubSource(ownerRequest.getSubSource())
        .setName(ownerRequest.getName())
        .setDescription(ownerRequest.getDescription());
}
```

**Owner.of(Ticket)**
Creates an owner from ticket data, copying phone, email, source, and name. Used when auto-creating an owner during ticket creation.

```java
public static Owner of(Ticket ticket) {
    return (Owner) new Owner()
        .setDialCode(ticket.getDialCode())
        .setPhoneNumber(ticket.getPhoneNumber())
        .setEmail(ticket.getEmail())
        .setSource(ticket.getSource())
        .setSubSource(ticket.getSubSource())
        .setName(ticket.getName())
        .setDescription(ticket.getDescription())
        .setAppCode(ticket.getAppCode())
        .setClientCode(ticket.getClientCode());
}
```

#### JSON Example

```json
{
    "id": 5001,
    "code": "OWN00001",
    "name": "John Doe",
    "description": null,
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 1,
    "clientId": null,
    "active": true,
    "dialCode": 91,
    "phoneNumber": "9876543210",
    "email": "john@example.com",
    "source": "Website",
    "subSource": "Landing Page v2",
    "createdBy": 2001,
    "createdAt": "2024-03-01T09:00:00",
    "updatedBy": 2001,
    "updatedAt": "2024-03-02T10:15:00"
}
```

---

### Activity

**Database Table:** `entity_processor_activities`
**EntitySeries:** `ACTIVITY` (value: 25)
**Purpose:** Immutable audit trail records tracking all significant actions on entities.

```java
public class Activity extends BaseDto<Activity> {

    private ULong ticketId;
    private ULong ownerId;
    private ULong userId;
    private ActivityAction action;
    private String description;
    private ULong actorId;
    private LocalDateTime activityDate;
    private String comment;
    private ActivityObject activityObject;
    private ULong taskId;
    private ULong noteId;
    private ULong stageId;
    private ULong statusId;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique activity code |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `ticketId` | ULong | `TICKET_ID` | Yes | FK to ticket (if ticket activity) |
| `ownerId` | ULong | `OWNER_ID` | Yes | FK to owner (if owner activity) |
| `userId` | ULong | `USER_ID` | Yes | FK to user (if user activity) |
| `action` | ActivityAction | `ACTION` | No | Action type enum |
| `description` | String | `DESCRIPTION` | No | Formatted message with markdown |
| `actorId` | ULong | `ACTOR_ID` | No | ID of user who performed the action |
| `activityDate` | LocalDateTime | `ACTIVITY_DATE` | No | When the action occurred |
| `comment` | String | `COMMENT` | Yes | Optional comment |
| `activityObject` | ActivityObject | `ACTIVITY_OBJECT` | Yes | JSON activity metadata |
| `taskId` | ULong | `TASK_ID` | Yes | FK to task (if task-related) |
| `noteId` | ULong | `NOTE_ID` | Yes | FK to note (if note-related) |
| `stageId` | ULong | `STAGE_ID` | Yes | FK to stage (if stage-related) |
| `statusId` | ULong | `STATUS_ID` | Yes | FK to status (if status-related) |

**Note:** Activities extend `BaseDto`, not `BaseProcessorDto`. They do not have version or clientId fields. Activities are immutable - once created, they are never updated.

#### Activity Routing

An activity is associated with exactly one of:
- `ticketId` - Activity for a ticket
- `ownerId` - Activity for an owner
- `userId` - Activity for a user

#### JSON Example

```json
{
    "id": 90001,
    "code": "ACT00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "ticketId": 12345,
    "ownerId": null,
    "userId": null,
    "action": "STAGE_UPDATE",
    "description": "Stage moved from **Qualification** to **Negotiation** by ***John Smith***.",
    "actorId": 2001,
    "activityDate": "2024-03-04T14:30:00",
    "comment": "Customer ready for pricing discussion",
    "activityObject": {
        "ticketId": 12345,
        "comment": "Customer ready for pricing discussion",
        "context": {
            "_stage": {"id": 301, "value": "Qualification"},
            "stage": {"id": 302, "value": "Negotiation"},
            "user": {"id": 2001, "value": "John Smith"}
        }
    },
    "taskId": null,
    "noteId": null,
    "stageId": 302,
    "statusId": null,
    "createdBy": 2001,
    "createdAt": "2024-03-04T14:30:00"
}
```

---

### Stage

**Database Table:** `entity_processor_stages`
**EntitySeries:** `STAGE` (value: 17)
**Purpose:** Defines pipeline stages and statuses within a product template. Stages are hierarchical - a parent stage can have child statuses.

```java
public class Stage extends BaseProcessorDto<Stage> {

    private ULong productTemplateId;
    private ULong parentId;
    private StageType stageType;
    private Integer order;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique stage code |
| `name` | String | `NAME` | No | Stage display name |
| `description` | String | `DESCRIPTION` | Yes | Description |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `active` | Boolean | `ACTIVE` | No | Active flag |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | No | FK to ProductTemplate |
| `parentId` | ULong | `PARENT_ID` | Yes | FK to parent Stage (null for top-level stages) |
| `stageType` | StageType | `STAGE_TYPE` | No | Stage classification |
| `order` | Integer | `STAGE_ORDER` | No | Display/processing order |

#### Hierarchy

- **Top-level Stage** (parentId = null): Represents a pipeline stage (e.g., "Qualification", "Negotiation", "Closing")
- **Child Stage/Status** (parentId = stage ID): Represents a status within a stage (e.g., "In Progress", "Pending Review")

#### JSON Example

```json
{
    "id": 301,
    "code": "STG00001",
    "name": "Qualification",
    "description": "Initial qualification stage",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "productTemplateId": 50,
    "parentId": null,
    "stageType": "PIPELINE",
    "order": 1,
    "createdBy": 1001,
    "createdAt": "2024-01-15T10:00:00"
}
```

**Child Status Example:**

```json
{
    "id": 401,
    "code": "STG00010",
    "name": "In Progress",
    "description": "Qualification in progress",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "productTemplateId": 50,
    "parentId": 301,
    "stageType": "PIPELINE",
    "order": 1,
    "createdBy": 1001,
    "createdAt": "2024-01-15T10:00:00"
}
```

---

### Campaign

**Database Table:** `entity_processor_campaigns`
**EntitySeries:** `CAMPAIGN` (value: 26)
**Purpose:** Represents marketing campaigns that generate leads/tickets.

```java
public class Campaign extends BaseProcessorDto<Campaign> {

    private ULong productId;
    private String campaignId;
    private CampaignPlatform platform;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique campaign code |
| `name` | String | `NAME` | No | Campaign name |
| `description` | String | `DESCRIPTION` | Yes | Description |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `active` | Boolean | `ACTIVE` | No | Active flag |
| `productId` | ULong | `PRODUCT_ID` | No | FK to Product |
| `campaignId` | String | `CAMPAIGN_ID` | No | External campaign identifier |
| `platform` | CampaignPlatform | `PLATFORM` | Yes | Campaign platform |

#### JSON Example

```json
{
    "id": 8001,
    "code": "CMP00001",
    "name": "Q1 Facebook Campaign",
    "description": "Lead generation campaign for Q1",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "productId": 100,
    "campaignId": "fb_camp_12345",
    "platform": "FACEBOOK",
    "createdBy": 1001,
    "createdAt": "2024-01-01T00:00:00"
}
```

---

### Partner

**Database Table:** `entity_processor_partners`
**EntitySeries:** `PARTNER` (value: 27)
**Purpose:** Represents business partner organizations that can create and manage tickets on behalf of clients.

```java
public class Partner extends BaseProcessorDto<Partner> {

    private ULong clientId;
    private PartnerVerificationStatus verificationStatus;
    private Boolean dnc;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique partner code |
| `name` | String | `NAME` | No | Partner organization name |
| `description` | String | `DESCRIPTION` | Yes | Description |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `active` | Boolean | `ACTIVE` | No | Active flag |
| `clientId` | ULong | `CLIENT_ID` | Yes | FK to Security client |
| `verificationStatus` | PartnerVerificationStatus | `VERIFICATION_STATUS` | Yes | Verification state |
| `dnc` | Boolean | `DNC` | Yes | Do-Not-Call flag (propagates to tickets) |

#### DNC Propagation

When a partner's `dnc` field changes, the change propagates to all tickets created by that partner (matching `clientId`).

#### JSON Example

```json
{
    "id": 7001,
    "code": "PTR00001",
    "name": "Acme Real Estate Partners",
    "description": "Premium channel partner",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 1,
    "active": true,
    "clientId": 5001,
    "verificationStatus": "VERIFIED",
    "dnc": false,
    "createdBy": 1001,
    "createdAt": "2024-01-10T09:00:00"
}
```

---

## Product Entities

### Product

**Database Table:** `entity_processor_products`
**EntitySeries:** `PRODUCT` (value: 14)
**LeadZump Name:** Project
**Purpose:** Container entity that groups tickets and defines pipeline configuration through templates.

```java
public class Product extends BaseProcessorDto<Product> {

    private ULong productTemplateId;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique product code |
| `name` | String | `NAME` | No | Product name |
| `description` | String | `DESCRIPTION` | Yes | Description |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `active` | Boolean | `ACTIVE` | No | Active flag |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | Yes | FK to ProductTemplate |

#### JSON Example

```json
{
    "id": 100,
    "code": "PRD00001",
    "name": "Premium Plan Sales",
    "description": "Sales pipeline for premium plans",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "productTemplateId": 50,
    "createdBy": 1001,
    "createdAt": "2024-01-01T00:00:00"
}
```

---

### ProductTemplate

**Database Table:** `entity_processor_product_templates`
**EntitySeries:** `PRODUCT_TEMPLATE` (value: 15)
**Purpose:** Defines the structure and type of pipeline used by products.

```java
public class ProductTemplate extends BaseProcessorDto<ProductTemplate> {

    private ProductTemplateType productTemplateType;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique template code |
| `name` | String | `NAME` | No | Template name |
| `description` | String | `DESCRIPTION` | Yes | Description |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `active` | Boolean | `ACTIVE` | No | Active flag |
| `productTemplateType` | ProductTemplateType | `PRODUCT_TEMPLATE_TYPE` | No | Template type |

**ProductTemplateType Values:**
| Value | Description |
|-------|-------------|
| `FORM` | Simple form-based workflow |
| `PIPELINE` | Multi-stage pipeline (Kanban-style) |
| `WIZARD` | Step-by-step wizard workflow |

#### JSON Example

```json
{
    "id": 50,
    "code": "TPL00001",
    "name": "Standard Sales Pipeline",
    "description": "4-stage sales pipeline",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "productTemplateType": "PIPELINE",
    "createdBy": 1001,
    "createdAt": "2024-01-01T00:00:00"
}
```

---

### ProductComm

**Database Table:** `entity_processor_product_comms`
**EntitySeries:** `PRODUCT_COMM` (value: 16)
**Purpose:** Configures which communication channels are available for a product, optionally scoped by source/subSource.

```java
public class ProductComm extends BaseProcessorDto<ProductComm> {

    private ULong productId;
    private ConnectionType connectionType;
    private ConnectionSubType connectionSubType;
    private ULong connectionId;
    private String source;
    private String subSource;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `productId` | ULong | `PRODUCT_ID` | No | FK to Product |
| `connectionType` | ConnectionType | `CONNECTION_TYPE` | No | Communication type |
| `connectionSubType` | ConnectionSubType | `CONNECTION_SUB_TYPE` | No | Provider type |
| `connectionId` | ULong | `CONNECTION_ID` | Yes | FK to Core service Connection |
| `source` | String | `SOURCE` | Yes | Source filter |
| `subSource` | String | `SUB_SOURCE` | Yes | Sub-source filter |

**ConnectionType Values:** `CALL`, `EMAIL`, `SMS`, `WHATSAPP`

**ConnectionSubType Values:** `EXOTEL`, `TWILIO`, and others

#### JSON Example

```json
{
    "id": 6001,
    "code": "PCM00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "productId": 100,
    "connectionType": "CALL",
    "connectionSubType": "EXOTEL",
    "connectionId": 500,
    "source": null,
    "subSource": null,
    "createdBy": 1001,
    "createdAt": "2024-01-15T09:00:00"
}
```

---

### ProductTicketCRule

**Database Table:** `entity_processor_product_ticket_c_rules`
**EntitySeries:** `PRODUCT_TICKET_C_RULE` (value: 20)
**Purpose:** Defines rules for user assignment when tickets are created. Rules are condition-based and ordered.

```java
public class ProductTicketCRule extends BaseRuleDto<ProductTicketCRule> {

    private AbstractCondition condition;
    private DistributionType userDistributionType;
    private ULong lastAssignedUserId;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `productId` | ULong | `PRODUCT_ID` | Yes | Product scope |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | Yes | Template scope |
| `order` | Integer | `RULE_ORDER` | No | Execution order (0 = default) |
| `condition` | AbstractCondition | `CONDITION` | Yes | Match condition (JSON) |
| `userDistributionType` | DistributionType | `USER_DISTRIBUTION_TYPE` | No | ROUND_ROBIN or RANDOM |
| `lastAssignedUserId` | ULong | `LAST_ASSIGNED_USER_ID` | Yes | Last user assigned (for round-robin) |

#### JSON Example

```json
{
    "id": 200,
    "code": "CTR00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "productId": 100,
    "productTemplateId": 50,
    "order": 0,
    "condition": null,
    "userDistributionType": "ROUND_ROBIN",
    "lastAssignedUserId": 2002,
    "version": 5,
    "createdBy": 1001,
    "createdAt": "2024-01-15T09:00:00"
}
```

---

### ProductTicketRuRule

**Database Table:** `entity_processor_product_ticket_ru_rules`
**EntitySeries:** `PRODUCT_TICKET_RU_RULE` (value: 21)
**Purpose:** Defines rules for ticket read/update permissions. Similar structure to ProductTicketCRule.

---

## Content Entities

### BaseContentDto

Base class for task and note entities. Determines which parent entity (ticket, owner, or user) the content belongs to.

```java
public abstract class BaseContentDto<D extends BaseContentDto<D>>
        extends BaseProcessorDto<D> {

    private ULong ticketId;
    private ULong ownerId;
    private ULong userId;

    public ContentEntitySeries getContentEntitySeries() {
        if (ticketId != null) return ContentEntitySeries.TICKET;
        if (ownerId != null) return ContentEntitySeries.OWNER;
        return ContentEntitySeries.USER;
    }
}
```

**ContentEntitySeries Values:**
| Value | Description |
|-------|-------------|
| `TICKET` | Content belongs to a ticket |
| `OWNER` | Content belongs to an owner |
| `USER` | Content belongs to a user |

---

### Task

**Database Table:** `entity_processor_tasks`
**EntitySeries:** `TASK` (value: 22)
**Purpose:** To-do items associated with tickets or owners.

```java
public class Task extends BaseContentDto<Task> {

    private String title;
    private LocalDateTime dueDate;
    private TaskPriority priority;
    private LocalDateTime nextReminder;
    private LocalDateTime completedDate;
    private LocalDateTime cancelledDate;
    private ULong taskTypeId;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique task code |
| `name` | String | `NAME` | Yes | Task title (also in `title`) |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `ticketId` | ULong | `TICKET_ID` | Yes | FK to Ticket |
| `ownerId` | ULong | `OWNER_ID` | Yes | FK to Owner |
| `userId` | ULong | `USER_ID` | Yes | FK to User |
| `title` | String | `TITLE` | No | Task title |
| `dueDate` | LocalDateTime | `DUE_DATE` | Yes | When the task is due |
| `priority` | TaskPriority | `PRIORITY` | Yes | HIGH, MEDIUM, or LOW |
| `nextReminder` | LocalDateTime | `NEXT_REMINDER` | Yes | Next reminder timestamp |
| `completedDate` | LocalDateTime | `COMPLETED_DATE` | Yes | When the task was completed |
| `cancelledDate` | LocalDateTime | `CANCELLED_DATE` | Yes | When the task was cancelled |
| `taskTypeId` | ULong | `TASK_TYPE_ID` | No | FK to TaskType |

**TaskPriority Values:**
| Value | Description |
|-------|-------------|
| `HIGH` | High priority |
| `MEDIUM` | Medium priority |
| `LOW` | Low priority |

#### JSON Example

```json
{
    "id": 30001,
    "code": "TSK00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "ticketId": 12345,
    "ownerId": null,
    "userId": null,
    "title": "Follow up call with John",
    "dueDate": "2024-03-10T10:00:00",
    "priority": "HIGH",
    "nextReminder": "2024-03-09T09:00:00",
    "completedDate": null,
    "cancelledDate": null,
    "taskTypeId": 1,
    "createdBy": 2001,
    "createdAt": "2024-03-04T14:30:00"
}
```

---

### TaskType

**Database Table:** `entity_processor_task_types`
**EntitySeries:** `TASK_TYPE` (value: 23)
**Purpose:** Categorizes tasks into types (e.g., "Follow Up", "Meeting", "Documentation").

Simple entity with only the base fields (name, description, appCode, clientCode).

#### JSON Example

```json
{
    "id": 1,
    "code": "TTP00001",
    "name": "Follow Up",
    "description": "Follow-up tasks for leads",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 0,
    "active": true,
    "createdBy": 1001,
    "createdAt": "2024-01-01T00:00:00"
}
```

---

### Note

**Database Table:** `entity_processor_notes`
**EntitySeries:** `NOTE` (value: 24)
**Purpose:** Text notes associated with tickets or owners.

```java
public class Note extends BaseContentDto<Note> {

    private String content;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `code` | String | `CODE` | No | Unique note code |
| `appCode` | String | `APP_CODE` | No | Application code |
| `clientCode` | String | `CLIENT_CODE` | No | Client code |
| `version` | Integer | `VERSION` | No | Optimistic lock version |
| `ticketId` | ULong | `TICKET_ID` | Yes | FK to Ticket |
| `ownerId` | ULong | `OWNER_ID` | Yes | FK to Owner |
| `userId` | ULong | `USER_ID` | Yes | FK to User |
| `content` | String | `CONTENT` | No | Note text content |

#### JSON Example

```json
{
    "id": 40001,
    "code": "NTE00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "version": 1,
    "ticketId": 12345,
    "ownerId": null,
    "userId": null,
    "content": "Customer expressed strong interest in the enterprise plan. Budget approved.",
    "createdBy": 2001,
    "createdAt": "2024-03-04T14:30:00",
    "updatedBy": 2001,
    "updatedAt": "2024-03-04T15:00:00"
}
```

---

## Rule Entities

### TicketDuplicationRule

**Database Table:** `entity_processor_ticket_duplication_rules`
**EntitySeries:** `TICKET_DUPLICATION_RULES` (value: 30)
**Purpose:** Configures rules for detecting duplicate tickets based on source and conditions.

```java
public class TicketDuplicationRule extends BaseRuleDto<TicketDuplicationRule> {

    private String source;
    private String subSource;
    private ULong maxStageId;
    private AbstractCondition condition;
}
```

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `productId` | ULong | `PRODUCT_ID` | Yes | Product scope |
| `productTemplateId` | ULong | `PRODUCT_TEMPLATE_ID` | Yes | Template scope |
| `order` | Integer | `RULE_ORDER` | No | Rule order |
| `source` | String | `SOURCE` | No | Source to match (required) |
| `subSource` | String | `SUB_SOURCE` | Yes | Sub-source (null = wildcard) |
| `maxStageId` | ULong | `MAX_STAGE_ID` | No | Only check tickets at or below this stage |
| `condition` | AbstractCondition | `CONDITION` | Yes | Additional filter condition (JSON) |

#### JSON Example

```json
{
    "id": 10001,
    "code": "DPR00001",
    "appCode": "leadzump",
    "clientCode": "ACMECORP",
    "productId": 100,
    "productTemplateId": 50,
    "order": 1,
    "source": "Website",
    "subSource": null,
    "maxStageId": 303,
    "condition": {
        "field": "phoneNumber",
        "operator": "IS_NOT_NULL"
    },
    "version": 0,
    "createdBy": 1001,
    "createdAt": "2024-01-20T09:00:00"
}
```

---

### TicketPeDuplicationRule

**Database Table:** `entity_processor_ticket_pe_duplication_rules`
**EntitySeries:** `TICKET_PE_DUPLICATION_RULES` (value: 31)
**Purpose:** Phone/Email specific duplication rules for more targeted duplicate detection.

---

### TicketCUserDistribution

**Database Table:** `entity_processor_ticket_c_user_distributions`
**EntitySeries:** `TICKET_C_USER_DISTRIBUTION` (value: 18)
**Purpose:** Defines the pool of users available for ticket assignment within a creation rule.

#### Field Reference

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `id` | ULong | `ID` | No | Primary key |
| `ruleId` | ULong | `RULE_ID` | No | FK to ProductTicketCRule |
| `userIds` | List<ULong> | `USER_IDS` | No | List of assignable user IDs |

---

### TicketRuUserDistribution

**Database Table:** `entity_processor_ticket_ru_user_distributions`
**EntitySeries:** `TICKET_RU_USER_DISTRIBUTION` (value: 19)
**Purpose:** Defines the pool of users for ticket read/update permission rules.

---

## Form Entities

### ProductWalkInForm

**Database Table:** `entity_processor_product_walk_in_forms`
**EntitySeries:** `PRODUCT_WALK_IN_FORMS` (value: 29)
**Purpose:** Configures walk-in forms for specific products. These forms are publicly accessible.

### ProductTemplateWalkInForm

**Database Table:** `entity_processor_product_template_walk_in_forms`
**EntitySeries:** `PRODUCT_TEMPLATE_WALK_IN_FORMS` (value: 28)
**Purpose:** Configures walk-in forms at the template level, inherited by products.

---

## Enumerations

### EntitySeries

The central registry for all entity types. Maps each entity to its database table, DTO class, and display name.

```java
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx"),
    TICKET("TICKET", "Ticket", 12, "Ticket"),
    OWNER("OWNER", "Owner", 13, "Owner"),
    PRODUCT("PRODUCT", "Product", 14, "Product"),
    PRODUCT_TEMPLATE("PRODUCT_TEMPLATE", "Product Template", 15, "ProductTemplate"),
    PRODUCT_COMM("PRODUCT_COMM", "Product Communications", 16, "ProductComm"),
    STAGE("STAGE", "Stage", 17, "Stage"),
    TICKET_C_USER_DISTRIBUTION("TICKET_C_USER_DISTRIBUTION", "...", 18, "..."),
    TICKET_RU_USER_DISTRIBUTION("TICKET_RU_USER_DISTRIBUTION", "...", 19, "..."),
    PRODUCT_TICKET_C_RULE("PRODUCT_TICKET_C_RULE", "...", 20, "..."),
    PRODUCT_TICKET_RU_RULE("PRODUCT_TICKET_RU_RULES", "...", 21, "..."),
    TASK("TASK", "Task", 22, "Task"),
    TASK_TYPE("TASK_TYPE", "Task Type", 23, "TaskType"),
    NOTE("NOTE", "Note", 24, "Note"),
    ACTIVITY("ACTIVITY", "Activity", 25, "Activity"),
    CAMPAIGN("CAMPAIGN", "Campaign", 26, "Campaign"),
    PARTNER("PARTNER", "Partner", 27, "Partner"),
    PRODUCT_TEMPLATE_WALK_IN_FORMS("...", "...", 28, "..."),
    PRODUCT_WALK_IN_FORMS("...", "...", 29, "..."),
    TICKET_DUPLICATION_RULES("...", "...", 30, "..."),
    TICKET_PE_DUPLICATION_RULES("...", "...", 31, "...");
}
```

#### Application-Specific Names (LeadZump Mapping)

| EntitySeries | Default Prefix | LeadZump Prefix |
|---|---|---|
| TICKET | Ticket | Deal |
| OWNER | Owner | Lead |
| PRODUCT | Product | Project |
| PRODUCT_TEMPLATE | ProductTemplate | ProjectTemplate |
| PRODUCT_COMM | ProductComm | ProjectComm |
| STAGE | Stage | Stage |
| TASK | Task | Task |
| NOTE | Note | Note |
| ACTIVITY | Activity | Activity |
| CAMPAIGN | Campaign | Campaign |
| PARTNER | Partner | Partner |

#### Key Methods

```java
// Get the JOOQ table for this entity
public Table<?> getTable()

// Get the DTO class name
public String getClassName()

// Get display prefix, optionally app-specific
public String getPrefix(String appCode)

// Get token prefix for permissions (prefix + ".")
public String getTokenPrefix(String appCode)
```

---

### ActivityAction

Defines all activity types with message templates. Each action has:
- `literal` - Database/enum value
- `template` - Message template with `$variable` placeholders
- `contextKeys` - Set of expected context keys

#### All Action Values

| Action | Template | Category |
|--------|----------|----------|
| `CREATE` | `$entity from $source created for $user.` | Deal |
| `RE_INQUIRY` | `$entity re-inquired from $source by $user.` | Deal |
| `QUALIFY` | `$entity qualified by $user.` | Deal |
| `DISQUALIFY` | `$entity marked as disqualified by $user.` | Deal |
| `DISCARD` | `$entity discarded by $user.` | Deal |
| `IMPORT` | `$entity imported via $source by $user.` | Deal |
| `STATUS_CREATE` | `$status created by $user.` | Stage |
| `STAGE_UPDATE` | `Stage moved from $_stage to $stage by $user.` | Stage |
| `WALK_IN` | `$entity walked in by $user.` | Deal |
| `DCRM_IMPORT` | `$entity imported via DCRM by $user.` | Deal |
| `TAG_CREATE` | `$entity was tagged $tag by $user.` | Tag |
| `TAG_UPDATE` | `$entity was tagged $tag from $_tag by $user.` | Tag |
| `TASK_CREATE` | `Task $taskId was created by $user.` | Task |
| `TASK_UPDATE` | `Task $taskId was updated by $user.` | Task |
| `TASK_COMPLETE` | `Task $taskId was marked as completed by $user.` | Task |
| `TASK_CANCELLED` | `Task $taskId was marked as cancelled by $user.` | Task |
| `TASK_DELETE` | `Task $taskId was deleted by $user.` | Task |
| `REMINDER_SET` | `Reminder for date $nextReminder, set for $taskId by $user.` | Task |
| `DOCUMENT_UPLOAD` | `Document $file uploaded by $user.` | Document |
| `DOCUMENT_DOWNLOAD` | `Document $file downloaded by $user.` | Document |
| `DOCUMENT_DELETE` | `Document $file deleted by $user.` | Document |
| `NOTE_ADD` | `Note $noteId added by $user.` | Note |
| `NOTE_UPDATE` | `Note $noteId was updated by $user.` | Note |
| `NOTE_DELETE` | `Note $noteId deleted by $user.` | Note |
| `ASSIGN` | `$entity was assigned to $assignedUserId by $user.` | Assignment |
| `REASSIGN` | `$entity was reassigned from $_assignedUserId to $assignedUserId by $user.` | Assignment |
| `REASSIGN_SYSTEM` | `$entity reassigned from $_assignedUserId to $assignedUserId due to availability rule by $user.` | Assignment |
| `OWNERSHIP_TRANSFER` | `Ownership transferred from $_createdBy to $createdBy by $user.` | Assignment |
| `CALL_LOG` | `Call with $customer logged by $user.` | Communication |
| `WHATSAPP` | `WhatsApp message sent to $customer by $user.` | Communication |
| `EMAIL_SENT` | `Email sent to $email by $user.` | Communication |
| `SMS_SENT` | `SMS sent to $customer by $user.` | Communication |
| `FIELD_UPDATE` | `$fields by $user.` | Field |
| `CUSTOM_FIELD_UPDATE` | `Custom field $field updated to $value by $user.` | Field |
| `LOCATION_UPDATE` | `Location updated to $location by $user.` | Field |
| `OTHER` | `$action performed on $entity by $user.` | Other |

#### Markdown Formatting Rules

```java
// In formatMarkdown():
if (key.contains("id")) return mdCode(value);       // `value`
if (key.equals("user")) return mdItalics(mdBold(value)); // ***value***
return mdBold(value);                                // **value**
```

#### Old Value Convention

Variables prefixed with `_` represent the previous value:
- `$_stage` - Old stage name
- `$_assignedUserId` - Previously assigned user
- `$_tag` - Previous tag value
- `$_createdBy` - Previous owner

---

### Tag

Ticket tag classification enum. Values represent the temperature/quality of a lead.

---

### StageType

Stage classification enum defining the type of pipeline stage.

---

### ProductTemplateType

| Value | Description |
|-------|-------------|
| `FORM` | Simple form-based data collection |
| `PIPELINE` | Multi-stage Kanban pipeline |
| `WIZARD` | Step-by-step guided workflow |

---

### CampaignPlatform

| Value | Description |
|-------|-------------|
| `FACEBOOK` | Facebook/Meta Ads |
| `GOOGLE` | Google Ads |
| (others) | Additional platforms |

---

### PartnerVerificationStatus

Partner verification state machine values.

---

### TaskPriority

| Value | Description |
|-------|-------------|
| `HIGH` | High priority task |
| `MEDIUM` | Medium priority task |
| `LOW` | Low priority task |

---

### ContentEntitySeries

| Value | Description |
|-------|-------------|
| `TICKET` | Content belongs to a ticket |
| `OWNER` | Content belongs to an owner |
| `USER` | Content belongs to a user |

---

### DistributionType

| Value | Description |
|-------|-------------|
| `ROUND_ROBIN` | Assign users in sequential order |
| `RANDOM` | Assign users randomly (avoiding consecutive repeats) |

---

### AssignmentType

Defines how ticket assignment is determined during creation.

---

### Platform

General platform classification.

---

### PhoneNumberAndEmailType

Classifies phone number and email types for duplication rules.

---

## Common Models

### Identity

A flexible identifier that can represent either a numeric ID or a string CODE. Used throughout the API in path variables and request bodies.

```java
public class Identity {
    private BigInteger id;
    private String code;

    public static Identity of(BigInteger id, String code) { ... }
    public boolean isNull() { return id == null && code == null; }
    public ULong getULongId() { return ULongUtil.valueOf(id); }
}
```

#### Serialization Forms

```json
// Numeric form
12345

// String form
"TKT00001"

// Object form with ID
{ "id": 12345 }

// Object form with CODE
{ "code": "TKT00001" }
```

Custom `IdentityTypeAdapter` handles all forms during Gson serialization/deserialization.

---

### PhoneNumber

```java
public class PhoneNumber {
    private Integer countryCode;
    private String number;
}
```

#### JSON Example

```json
{
    "countryCode": 91,
    "number": "9876543210"
}
```

Custom `PhoneNumberTypeAdapter` for Gson serialization.

---

### Email

```java
public class Email {
    private String address;
}
```

#### JSON Example

```json
{
    "address": "john@example.com"
}
```

Custom `EmailTypeAdapter` for Gson serialization.

---

### ProcessorAccess

The central security context object for all Entity Processor operations.

```java
public final class ProcessorAccess implements Serializable {

    private String appCode;
    private String clientCode;
    private ULong userId;
    private String firstName;
    private String lastName;
    private String middleName;
    private boolean hasAccessFlag;
    private ContextUser user;
    private UserInheritanceInfo userInherit;
    private boolean hasBpAccess;
}
```

#### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `of(appCode, clientCode, flag, user, inherit)` | ProcessorAccess | Static factory |
| `of(ContextAuthentication, UserInheritanceInfo)` | ProcessorAccess | From auth context |
| `ofNull()` | ProcessorAccess | Empty access |
| `isOutsideUser()` | boolean | True if Business Partner |
| `getEffectiveClientCode()` | String | Managed client code for BP, own code otherwise |
| `getUserId()` | ULong | User ID (from userId field or user context) |
| `getUserName()` | String | Assembled full name or "Anonymous" |

#### UserInheritanceInfo

```java
public static class UserInheritanceInfo implements Serializable {
    private String clientLevelType;
    private String loggedInClientCode;
    private ULong loggedInClientId;
    private String managedClientCode;
    private ULong managedClientId;
    private List<ULong> subOrg;
    private List<ULong> managingClientIds;
}
```

| Field | Description |
|-------|-------------|
| `clientLevelType` | Client level (e.g., "BP" for Business Partner) |
| `loggedInClientCode` | Client code the user authenticated from |
| `loggedInClientId` | Client ID the user authenticated from |
| `managedClientCode` | Client being managed (for BP users) |
| `managedClientId` | Client ID being managed |
| `subOrg` | List of subordinate user IDs (for reassignment validation) |
| `managingClientIds` | List of client IDs the user can manage |

---

### IdAndValue

Generic tuple used in activity context for displaying both an ID and a human-readable value.

```java
public class IdAndValue<I, V> {
    private I id;
    private V value;

    public static <I, V> IdAndValue<I, V> of(I id, V value) { ... }
}
```

Used extensively in activity logging:
```java
// Stage activity context
IdAndValue.of(stage.getId(), stage.getName())

// User activity context
IdAndValue.of(userId, "John Smith")
```

---

### ActivityObject

Wraps the full context data for an activity record.

```java
public class ActivityObject {
    private ULong ticketId;
    private ULong ownerId;
    private ULong userId;
    private String comment;
    private Map<String, Object> context;
}
```

Factory methods: `ofTicket()`, `ofOwner()`, `ofUser()`

---

## Request Models

### TicketRequest

```java
public class TicketRequest implements INoteRequest {
    private Identity productId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String name;
    private String description;
    private String source;
    private String subSource;
    private String comment;
    private NoteRequest noteRequest;
    private Boolean dnc;
}
```

### TicketStatusRequest

```java
public class TicketStatusRequest {
    private Identity stageId;
    private Identity statusId;
    private TaskRequest taskRequest;
    private String comment;
}
```

### TicketReassignRequest

```java
public class TicketReassignRequest {
    private ULong userId;
    private String comment;
}
```

### TicketTagRequest

```java
public class TicketTagRequest {
    private Tag tag;
    private TaskRequest taskRequest;
    private String comment;
}
```

### TicketPartnerRequest

For DCRM imports. Contains all ticket fields explicitly (no auto-assignment).

```java
public class TicketPartnerRequest {
    private Identity productId;
    private Identity stageId;
    private Identity statusId;
    private ULong assignedUserId;
    private ULong clientId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String name;
    private String description;
    private String source;
    private String subSource;
    private LocalDateTime createdDate;
    private Map<String, Object> activityJson;
}
```

### CampaignTicketRequest

```java
public class CampaignTicketRequest implements INoteRequest {
    private String appCode;
    private String clientCode;
    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;
    private String comment;
    private NoteRequest noteRequest;

    @Data
    public static class LeadDetails {
        private String firstName;
        private String lastName;
        private String fullName;
        private PhoneNumber phone;
        private Email email;
        private String source;
        private String subSource;
    }

    @Data
    public static class CampaignDetails {
        private String campaignId;
        private String keyword;
    }
}
```

### CallLogRequest

```java
public class CallLogRequest {
    private Identity ticketId;
    private String comment;
    private Boolean isOutbound;
    private CallStatus callStatus;
    private LocalDateTime callDate;
    private Integer callDuration;
}
```

### TaskRequest

```java
public class TaskRequest {
    private String title;
    private String description;
    private Identity ticketId;
    private Identity ownerId;
    private Identity taskTypeId;
    private LocalDateTime dueDate;
    private TaskPriority priority;
    private LocalDateTime nextReminder;
}
```

### NoteRequest

```java
public class NoteRequest implements INoteRequest {
    private Identity ticketId;
    private Identity ownerId;
    private String content;
}
```

### INoteRequest Interface

```java
public interface INoteRequest {
    String getComment();
    NoteRequest getNoteRequest();
    default boolean hasNote() {
        return getComment() != null || getNoteRequest() != null;
    }
}
```

Implemented by TicketRequest and CampaignTicketRequest to optionally include notes during ticket creation.

### Other Request Models

- **OwnerRequest** - phoneNumber, email, name, description, source, subSource
- **ProductRequest** - name, description, productTemplateId
- **StageRequest** - name, description, productTemplateId, parentId, stageType, order
- **CampaignRequest** - name, description, productId, campaignId, platform
- **PartnerRequest** - name, description, clientId, verificationStatus, dnc
- **WalkInFormRequest** - Form configuration
- **WalkInFormTicketRequest** - phoneNumber, email, name, description, subSource, assignedUserId, stageId, statusId
- **ProductPartnerUpdateRequest** - Partner-originated product updates

---

## Response Models

### ProcessorResponse

Generic response wrapper used for some API responses.

### WalkInFormResponse

Response for public form retrieval. Contains form definition, fields, validation rules, and styling configuration.

### BaseValueResponse

Simple response for lookup/value entities.

---

## Database Schema Conventions

### Table Naming

All tables use the `entity_processor_` prefix:
| Entity | Table Name |
|--------|-----------|
| Ticket | `entity_processor_tickets` |
| Owner | `entity_processor_owners` |
| Product | `entity_processor_products` |
| ProductTemplate | `entity_processor_product_templates` |
| ProductComm | `entity_processor_product_comms` |
| Stage | `entity_processor_stages` |
| Activity | `entity_processor_activities` |
| Task | `entity_processor_tasks` |
| TaskType | `entity_processor_task_types` |
| Note | `entity_processor_notes` |
| Campaign | `entity_processor_campaigns` |
| Partner | `entity_processor_partners` |
| ProductTicketCRule | `entity_processor_product_ticket_c_rules` |
| ProductTicketRuRule | `entity_processor_product_ticket_ru_rules` |
| TicketCUserDistribution | `entity_processor_ticket_c_user_distributions` |
| TicketRuUserDistribution | `entity_processor_ticket_ru_user_distributions` |
| TicketDuplicationRule | `entity_processor_ticket_duplication_rules` |
| TicketPeDuplicationRule | `entity_processor_ticket_pe_duplication_rules` |
| ProductWalkInForm | `entity_processor_product_walk_in_forms` |
| ProductTemplateWalkInForm | `entity_processor_product_template_walk_in_forms` |

### Column Types

| Java Type | MySQL Type | Notes |
|-----------|-----------|-------|
| `ULong` | `BIGINT UNSIGNED` | All IDs |
| `String` | `VARCHAR` | Variable-length strings |
| `String` (code) | `CHAR` | Fixed-length codes |
| `Integer` | `INT` | Numeric values |
| `Boolean` | `TINYINT(1)` | Boolean flags |
| `LocalDateTime` | `DATETIME` | Timestamps |
| `Tag` (enum) | `VARCHAR` | Stored as string literal |
| `ActivityAction` (enum) | `VARCHAR` | Stored as string literal |
| `Map<String, Object>` | `JSON` | JSON columns |
| `AbstractCondition` | `JSON` | Condition trees |

### Standard Columns

Every table includes:
| Column | Type | Description |
|--------|------|-------------|
| `ID` | BIGINT UNSIGNED AUTO_INCREMENT | Primary key |
| `CODE` | CHAR(N) | Unique entity code |
| `NAME` | VARCHAR(255) | Display name |
| `DESCRIPTION` | TEXT | Description |
| `APP_CODE` | VARCHAR(64) | Application code (multi-tenancy) |
| `CLIENT_CODE` | VARCHAR(64) | Client code (multi-tenancy) |
| `CREATED_BY` | BIGINT UNSIGNED | Creator user ID |
| `CREATED_AT` | DATETIME | Creation timestamp |
| `UPDATED_BY` | BIGINT UNSIGNED | Last updater user ID |
| `UPDATED_AT` | DATETIME | Last update timestamp |

### Processor Table Additional Columns

Tables for entities extending `BaseProcessorDto` also include:
| Column | Type | Description |
|--------|------|-------------|
| `VERSION` | INT | Optimistic lock version |
| `CLIENT_ID` | BIGINT UNSIGNED | Business partner client ID |
| `ACTIVE` | TINYINT(1) | Active/inactive flag |

### Indexes

Key indexes include:
- Primary key on `ID`
- Unique index on `CODE` + `APP_CODE` + `CLIENT_CODE`
- Index on `APP_CODE` + `CLIENT_CODE` (multi-tenant filtering)
- Index on `PRODUCT_ID` (for ticket-product lookups)
- Index on `OWNER_ID` (for ticket-owner lookups)
- Index on `PHONE_NUMBER` + `DIAL_CODE` (for duplicate detection)
- Index on `EMAIL` (for duplicate detection)
- Index on `ASSIGNED_USER_ID` (for user-based queries)
- Index on `STAGE` (for pipeline queries)
- Index on `CAMPAIGN_ID` (for campaign lookups)

### Schema Management

Database schema is managed by Flyway:
- Migration files in `src/main/resources/db/migration/`
- Versioned migrations (V1__, V2__, etc.)
- Run automatically on application startup
- Schema changes tracked in `flyway_schema_history` table
