# Entity Processor Module -- Business Logic Documentation

## Table of Contents

1. [Module Overview](#1-module-overview)
2. [Core Domain Model](#2-core-domain-model)
3. [Security and Access Control](#3-security-and-access-control)
4. [Ticket Lifecycle Management](#4-ticket-lifecycle-management)
5. [Owner Management](#5-owner-management)
6. [Product and Template Management](#6-product-and-template-management)
7. [Stage and Pipeline Management](#7-stage-and-pipeline-management)
8. [Activity Service -- Comprehensive Audit Trail](#8-activity-service----comprehensive-audit-trail)
9. [Task Management](#9-task-management)
10. [Note Management](#10-note-management)
11. [Campaign Management](#11-campaign-management)
12. [Partner and Business Partner Management](#12-partner-and-business-partner-management)
13. [Walk-In Form System](#13-walk-in-form-system)
14. [Communication Integration](#14-communication-integration)
15. [Duplication Rule Engine](#15-duplication-rule-engine)
16. [Assignment Rule Engine](#16-assignment-rule-engine)
17. [BaseProcessorService Common Patterns](#17-baseprocessorservice-common-patterns)
18. [Error Handling Patterns](#18-error-handling-patterns)
19. [Caching Strategy](#19-caching-strategy)
20. [Analytics and Reporting](#20-analytics-and-reporting)

---

## 1. Module Overview

The `entity-processor` module is a **Spring Boot reactive CRM microservice** built on
Project Reactor, JOOQ, and the Modlix no-code platform commons. It manages the complete
lifecycle of tickets (deals/leads), owners (contacts), products (projects), campaigns,
partners (business partners), tasks, notes, stages, and activities within a multi-tenant
SaaS environment.

### Technology Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.1 with WebFlux |
| Reactive | Project Reactor (Mono/Flux) |
| ORM | JOOQ with R2DBC |
| Java Version | Java 21 |
| Utility | FlatMapUtil (custom reactor chaining library) |
| Serialization | Gson, Jackson |
| Caching | Custom CacheService abstraction |
| Service Discovery | Eureka |
| Inter-service | Feign clients (Core, Message, Security, Files) |

### Architectural Principles

- **Fully Reactive**: Every service method returns `Mono<T>` or `Flux<T>`. No blocking calls.
- **Multi-tenant**: Every entity is scoped to an `appCode` + `clientCode` pair.
- **Outside User Model**: Business Partner (BP) users operate through a separate access
  path with restricted permissions and delegated client context.
- **Event Sourcing via Activities**: Every significant mutation creates an immutable
  Activity record forming a complete audit trail.
- **Rule-Based Assignment**: Configurable rules determine ticket assignment at creation
  and on stage transitions.
- **Version-Controlled Updates**: ProcessorDto entities carry a `version` field; updates
  require version match (optimistic locking with HTTP 412).

### Entity Hierarchy

```
BaseDto
  |-- BaseUpdatableDto
  |     |-- BaseProcessorDto (adds version, clientId, accessUser)
  |     |     |-- Ticket
  |     |     |-- Owner
  |     |     |-- ProductComm
  |     |     |-- ProductTicketCRule / ProductTicketRuRule
  |     |-- Campaign
  |     |-- Partner
  |     |-- Stage
  |     |-- Activity
  |-- BaseContentDto (adds ticketId, ownerId, userId)
        |-- Task
        |-- Note
```

### Entity Series Registry

The `EntitySeries` enum assigns each entity a unique numeric series prefix and maps it
to its JOOQ table. This registry is used for code generation, identity resolution, and
eager-loading relation mapping:

| Entity Series | Value | Prefix | Display Name |
|--------------|-------|--------|--------------|
| TICKET | 12 | Ticket | Ticket |
| OWNER | 13 | Owner | Owner |
| PRODUCT | 14 | Product | Product |
| PRODUCT_TEMPLATE | 15 | ProductTemplate | Product Template |
| PRODUCT_COMM | 16 | ProductComm | Product Communications |
| STAGE | 17 | Stage | Stage |
| TICKET_C_USER_DISTRIBUTION | 18 | TicketCUserDistribution | Ticket Creation User Distribution |
| TICKET_RU_USER_DISTRIBUTION | 19 | TicketRuUserDistribution | Ticket Read Update User Distribution |
| PRODUCT_TICKET_C_RULE | 20 | ProductTicketCRule | Product Ticket Creation Rule |
| PRODUCT_TICKET_RU_RULE | 21 | ProductTicketRuRule | Product Ticket Read Update Rule |
| TASK | 22 | Task | Task |
| TASK_TYPE | 23 | TaskType | Task Type |
| NOTE | 24 | Note | Note |
| ACTIVITY | 25 | Activity | Activity |
| CAMPAIGN | 26 | Campaign | Campaign |
| PARTNER | 27 | Partner | Partner |
| PRODUCT_TEMPLATE_WALK_IN_FORMS | 28 | ProductTemplateWalkInForm | Product Template Walk In Forms |
| PRODUCT_WALK_IN_FORMS | 29 | ProductWalkInForms | Product Walk In Forms |
| TICKET_DUPLICATION_RULES | 30 | TicketDuplicationRule | Ticket Duplication Rules |
| TICKET_PE_DUPLICATION_RULES | 31 | TicketPeDuplicationRule | Ticket Pe Duplication Rules |

App-specific entity naming is supported: for the `leadzump` app, Tickets become "Deals",
Owners become "Leads", and Products become "Projects".

---

## 2. Core Domain Model

### 2.1 Ticket DTO

The `Ticket` is the central entity of the CRM. It represents a sales lead, deal, inquiry,
or any processable request.

**Key fields:**

| Field | Type | Description |
|-------|------|-------------|
| `ownerId` | `ULong` | Foreign key to the Owner (contact) |
| `assignedUserId` | `ULong` | The user currently responsible for this ticket |
| `dialCode` | `Integer` | Phone country code (default: 91) |
| `phoneNumber` | `String` | Contact phone number |
| `email` | `String` | Contact email address |
| `productId` | `ULong` | Foreign key to Product (project) |
| `stage` | `ULong` | Current pipeline stage ID |
| `status` | `ULong` | Current status within the stage |
| `source` | `String` | Lead source (e.g., "Website", "Facebook") |
| `subSource` | `String` | Lead sub-source (e.g., campaign name) |
| `campaignId` | `ULong` | Foreign key to Campaign (if campaign-originated) |
| `dnc` | `Boolean` | Do-Not-Call flag |
| `tag` | `Tag` | Temperature tag: HOT, WARM, or COLD |
| `metaData` | `Map<String, Object>` | Arbitrary key-value metadata (e.g., keyword from campaigns) |
| `version` | `Integer` | Optimistic locking version (inherited from BaseProcessorDto) |

**Factory methods:**

- `Ticket.of(TicketRequest)` -- Creates from a standard API ticket request
- `Ticket.of(CampaignTicketRequest)` -- Creates from a campaign-originated lead
- `Ticket.of(WalkInFormTicketRequest)` -- Creates from a walk-in form submission

**Source normalization:** The `setSource()` and `setSubSource()` methods automatically
normalize input through `NameUtil.normalize()` to ensure consistent casing and trimming.

### 2.2 Owner DTO

The `Owner` represents a contact/lead person. Multiple tickets can reference the same owner.

**Key fields:**

| Field | Type | Description |
|-------|------|-------------|
| `dialCode` | `Integer` | Phone country code |
| `phoneNumber` | `String` | Contact phone |
| `email` | `String` | Contact email |
| `source` | `String` | Normalized source string |
| `subSource` | `String` | Normalized sub-source string |

**Factory methods:**

- `Owner.of(OwnerRequest)` -- Creates from an API owner creation request
- `Owner.of(Ticket)` -- Creates an owner from ticket data (used during ticket creation
  when no owner exists)

### 2.3 Tag Enum

Tags represent the temperature/priority of a ticket:

| Tag | Display Name |
|-----|-------------|
| `HOT` | Hot |
| `WARM` | Warm |
| `COLD` | Cold |

---

## 3. Security and Access Control

### 3.1 ProcessorAccess

The `ProcessorAccess` object is the security context carrier for all entity-processor
operations. It encapsulates:

- **`appCode`**: The application context (e.g., "leadzump")
- **`clientCode`**: The tenant/client code
- **`userId`**: The authenticated user's ID
- **`hasAccessFlag`**: Whether the user has been verified for access
- **`user`** (`ContextUser`): The full authenticated user from the JWT
- **`userInherit`** (`UserInheritanceInfo`): Organizational hierarchy information
- **`hasBpAccess`**: Whether the user has Business Partner management authority

**Construction patterns:**

```java
// From authenticated JWT context
ProcessorAccess.of(ca.getUrlAppCode(), ca.getClientCode(), true, ca.getUser(), userInherit);

// From external request (campaign, website) - no JWT, outside=true
ProcessorAccess.of(appCode, clientCode, true, null, null);
```

### 3.2 Outside User Detection

The `isOutsideUser()` method checks if the user belongs to a Business Partner client
(`CLIENT_LEVEL_TYPE_BP`). Outside users have restricted access:

- They can create tickets (via `canOutsideCreate()`)
- Their `clientId` is set on entities they create (via `BaseProcessorService.create()`)
- They receive different error messages for duplicate tickets
- Their `effectiveClientCode` resolves to the managed client's code, not their own

### 3.3 User Inheritance Info

The `UserInheritanceInfo` inner class carries:

- **`clientLevelType`**: The type of client (e.g., "BP" for Business Partner)
- **`loggedInClientCode/Id`**: The client the user actually logged in from
- **`managedClientCode/Id`**: The client being managed (for BP users)
- **`subOrg`**: List of user IDs in the user's sub-organization (used for reassignment validation)
- **`managingClientIds`**: List of client IDs the user can manage

### 3.4 Access Check Flow

Every service operation begins with `hasAccess()`:

1. Extract `ContextAuthentication` from the reactive security context
2. Resolve user inheritance (sub-org, managing clients, client hierarchy)
3. Construct `ProcessorAccess` object
4. For Partner operations, additionally verify `hasBpAccess` flag

---

## 4. Ticket Lifecycle Management

The `TicketService` is the largest and most complex service in the entity-processor module.
It handles the full lifecycle of tickets from creation through stage progression to
reassignment.

### 4.1 Standard Creation Flow (`createRequest`)

This is the primary ticket creation endpoint used by authenticated internal and external
(BP) users.

**Flow Diagram:**

```
TicketRequest
    |
    v
[1] Validate source info present (hasSourceInfo)
    |
    v
[2] Convert TicketRequest -> Ticket DTO (Ticket.of())
    |
    v
[3] Check access (hasAccess -> ProcessorAccess)
    |
    v
[4] In parallel:
    |-- Resolve product from Identity (readByIdentity)
    |-- Check DNC status (getDnc)
    |
    v
[5] Run duplicate detection (checkDuplicate)
    |
    v
[6] Set productId on ticket, set DNC flag
    |
    v
[7] Create ticket (super.create -> triggers checkEntity)
    |     |
    |     v
    |   [7a] Validate productId present
    |   [7b] setAssignmentAndStage
    |   [7c] getOrCreateTicketOwner
    |   [7d] updateTicketFromOwner
    |
    v
[8] Create note if provided
    |
    v
[9] Log CREATE activity
```

**Detailed step-by-step:**

**Step 1: Source Validation**

```java
if (!ticketRequest.hasSourceInfo())
    return this.msgService.throwMessage(
            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            ProcessorMessageResourceService.IDENTITY_MISSING, "Source");
```

The `hasSourceInfo()` check ensures the ticket has a source identifier. Without a
source, the system cannot track where the lead originated.

**Step 2: DTO Conversion**

```java
Ticket ticket = Ticket.of(ticketRequest);
```

The `Ticket.of(TicketRequest)` factory method extracts:
- Phone number (dial code + number) from `PhoneNumber` object
- Email address from `Email` object
- Source and sub-source (both normalized via `NameUtil.normalize()`)
- Name and description

**Step 3: Access Check**

The `hasAccess()` method from `BaseProcessorService` constructs the `ProcessorAccess`
context from the current security context. This resolves the user, their client hierarchy,
sub-organization membership, and Business Partner access flags.

**Step 4: Product Resolution and DNC Check (Parallel)**

These two operations run concurrently using `Mono.zip()`:

- **Product Resolution**: `productService.readByIdentity(access, ticketRequest.getProductId())`
  resolves the product by its identity (which can be either a direct ID or a code-based
  identity). The product defines which template and rules apply to the ticket.

- **DNC Check**: `getDnc(access, ticketRequest)` determines the Do-Not-Call status:
  - For inside users: always returns `false`
  - For outside (BP) users: either uses the explicit `dnc` field from the request,
    or falls back to the partner's DNC setting via `partnerService.getPartnerDnc()`

**Step 5: Duplicate Detection**

The `checkDuplicate()` method is a multi-layered detection system (detailed in
Section 15).

**Step 6: Ticket Enrichment**

After duplicate check passes, the ticket is enriched:
```java
ticket.setProductId(productIdentity.getT1().getId())
      .setDnc(productIdentity.getT2());
```

**Step 7: Entity Creation (`checkEntity`)**

The `checkEntity()` method is a template method hook called during `super.create()`. For
tickets, it performs:

**7a. Product ID Validation:**
```java
if (ticket.getProductId() == null)
    return this.msgService.throwMessage(
            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            ProcessorMessageResourceService.IDENTITY_MISSING,
            this.productService.getEntityName());
```

**7b. Assignment and Stage Resolution (`setAssignmentAndStage`):**

This method handles three scenarios:

| Scenario | AssignedUserId | Stage | Behavior |
|----------|---------------|-------|----------|
| Both set | Present | Present | Use as-is, no changes |
| User only | Present | null | Set default stage only |
| Neither set | null | null | Set default stage, then get user from rule engine |

For the "neither set" case:
```java
return FlatMapUtil.flatMapMonoWithNull(
    () -> this.setDefaultStage(access, ticket),
    sTicket -> this.productTicketCRuleService.getUserAssignment(
            access, sTicket.getProductId(), sTicket.getStage(),
            this.getEntityPrefix(access.getAppCode()),
            loggedInAssignedUser, sTicket),
    (sTicket, userId) -> this.setTicketAssignment(
            access, sTicket, userId != null ? userId : loggedInAssignedUser));
```

The `loggedInAssignedUser` is `null` for outside users, meaning the rule engine must
provide a user, or else the logged-in user is used as fallback for inside users.

**Default Stage Resolution (`setDefaultStage`):**

```
Product -> ProductTemplateId -> getFirstStage() -> getFirstStatus()
```

1. Read the product to get its `productTemplateId`
2. Get the first stage from the template (ordered by `order` field, ascending)
3. Get the first status within that stage (first child)
4. Set both on the ticket

If no stage is found, a `TICKET_STAGE_MISSING` error is thrown.

**Assignment Validation (`setTicketAssignment`):**

```java
if (userId == null || userId.equals(ULong.valueOf(0)))
    return this.msgService.throwMessage(
            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            ProcessorMessageResourceService.TICKET_ASSIGNMENT_MISSING,
            this.getEntityPrefix(access.getAppCode()));
```

A ticket must always have a valid assigned user (non-null, non-zero).

**7c. Owner Resolution (`getOrCreateTicketOwner`):**

The owner service looks up or creates the contact record. See Section 5 for details.

**7d. Ticket-Owner Sync (`updateTicketFromOwner`):**

After the owner is resolved, the ticket inherits missing contact info:
```java
ticket.setOwnerId(owner.getId());
if (ticket.getName() == null && owner.getName() != null)
    ticket.setName(owner.getName());
if (ticket.getEmail() == null && owner.getEmail() != null)
    ticket.setEmail(owner.getEmail());
if (ticket.getPhoneNumber() == null && owner.getPhoneNumber() != null) {
    ticket.setDialCode(owner.getDialCode());
    ticket.setPhoneNumber(owner.getPhoneNumber());
}
```

**Step 8: Note Creation**

If the request includes a note or comment:
```java
if (!noteRequest.hasNote()) return Mono.just(Boolean.FALSE);
```

The note is created via `NoteService.createRequest()` and linked to the new ticket.

**Step 9: Activity Logging**

```java
this.activityService.acCreate(created).thenReturn(created)
```

Creates a `CREATE` activity with the ticket's source information.

**Complete FlatMapUtil chain:**

```java
return FlatMapUtil.flatMapMono(
    super::hasAccess,                                              // Step 3
    access -> Mono.zip(                                            // Step 4
        this.productService.readByIdentity(access, ...),
        this.getDnc(access, ticketRequest)),
    (access, productIdentity) -> this.checkDuplicate(...),         // Step 5
    (access, productIdentity, isDuplicate) -> Mono.just(           // Step 6
        ticket.setProductId(...).setDnc(...)),
    (access, ..., pTicket) -> super.create(access, pTicket),       // Step 7
    (access, ..., created) -> this.createNote(...),                // Step 8
    (access, ..., noteCreated) -> this.activityService             // Step 9
        .acCreate(created).thenReturn(created))
.contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.create[TicketRequest]"));
```

### 4.2 Campaign Ticket Creation (`createForCampaign`)

Campaign tickets originate from external marketing platforms (Facebook, Google Ads, etc.)
and are processed without JWT authentication.

**Flow:**

```
CampaignTicketRequest
    |
    v
[1] Construct ProcessorAccess from request appCode/clientCode
    (no JWT, outside=true, no user context)
    |
    v
[2] Look up Campaign by external campaignId
    |-- switchIfEmpty -> 404 IDENTITY_WRONG
    |
    v
[3] Read Product from campaign.getProductId()
    |
    v
[4] Convert CampaignTicketRequest -> Ticket DTO
    |-- Set campaignId on ticket
    |-- Capture keyword in metaData if present
    |
    v
[5] Run duplicate detection against campaign's product
    |
    v
[6] Set productId on ticket
    |
    v
[7] Create ticket (super.create)
    |-- switchIfEmpty -> TICKET_CREATION_FAILED for "campaign"
    |
    v
[8] Create note if provided
    |
    v
[9] Log CREATE activity
```

**Key differences from standard creation:**

- No JWT context -- `ProcessorAccess` constructed with `outside=true` and `null` user
- Campaign is validated and its product is used (not from request)
- Campaign metadata (keyword) is captured in ticket's `metaData` map
- `campaignId` is set on the created ticket for tracking
- Error messages reference "campaign" context on creation failure

**Campaign ticket request structure:**

The `CampaignTicketRequest` contains:
- `appCode` / `clientCode` -- Tenant identification
- `campaignDetails.campaignId` -- External campaign identifier
- `campaignDetails.keyword` -- Optional campaign keyword
- `leadDetails.phone` / `email` / `source` / `subSource` / `firstName` / `lastName`

### 4.3 Website Ticket Creation (`createForWebsite`)

Website tickets come from public-facing forms on customer websites.

**Flow:**

```
CampaignTicketRequest (reused DTO) + productCode
    |
    v
[1] Validate NO campaign details present
    |-- If present -> WEBSITE_ENTITY_DATA_INVALID
    |
    v
[2] Default source to "Website" if not provided
    |
    v
[3] Construct ProcessorAccess (outside=true, no user)
    |
    v
[4] Look up Product by code (readByCode)
    |
    v
[5] Convert request -> Ticket DTO
    |
    v
[6] Run duplicate detection
    |
    v
[7] Set productId, create ticket
    |-- switchIfEmpty -> TICKET_CREATION_FAILED for "website"
    |
    v
[8] Create note if provided
    |
    v
[9] Log CREATE activity
```

**Key differences:**

- Validates that `campaignDetails` is null (websites should not carry campaign data)
- Default source is "Website" when not explicitly provided
- Product is resolved by `productCode` string instead of identity object
- No campaign association

### 4.4 DCRM Partner Import (`createForPartnerImportDCRM`)

This flow handles bulk imports from DCRM (Distributed CRM) systems. It bypasses
auto-assignment and allows explicit setting of all ticket fields.

**Flow:**

```
TicketPartnerRequest + appCode + clientCode (from headers)
    |
    v
[1] Construct ProcessorAccess from headers (outside=true)
    |
    v
[2] Resolve product from request's productId
    |
    v
[3] Validate stage/status belong to product template
    |-- getParentChild() validates hierarchy
    |-- switchIfEmpty -> STAGE_MISSING
    |
    v
[4] Validate client exists (if clientId provided)
    |-- getClientById or use empty Client
    |
    v
[5] Validate assigned user exists
    |-- getUserInternal(assignedUserId)
    |
    v
[6] Check for duplicate ticket
    |-- getTicket by phone/email
    |-- If found -> throwDuplicateError
    |
    v
[7] Build ticket with ALL explicit fields:
    |-- name, description, assignedUserId, phone, email
    |-- source, subSource, productId, stage, status
    |-- clientId (from partner), createdBy (assigned user)
    |-- createdAt (from request's createdDate)
    |-- NO auto-assignment (user set explicitly)
    |
    v
[8] Get or create owner
    |
    v
[9] Create ticket internally (createInternal, not create)
    |
    v
[10] Log DCRM_IMPORT activity with metadata
```

**Key differences:**

- Uses `createInternal` instead of `create` -- bypasses `checkEntity` entirely
- All fields are explicitly set (no auto-assignment, no default stage)
- Stage and status are validated against the product template
- Activity metadata from the import is preserved in the `DCRM_IMPORT` activity
- The `createdAt` timestamp can be backdated to the original creation time

### 4.5 Stage/Status Updates (`updateStageStatus`)

Stage transitions move tickets through the sales pipeline.

**Flow Diagram:**

```
TicketStatusRequest (stageId, statusId?, taskRequest?, comment?)
    |
    v
[1] Validate stageId is provided and non-null
    |-- isNull() -> identityMissingError for Stage
    |
    v
[2] Check access -> ProcessorAccess
    |
    v
[3] Fetch ticket by identity (readByIdentity)
    |
    v
[4] Fetch product to get productTemplateId
    |-- If no templateId -> PRODUCT_TEMPLATE_TYPE_MISSING
    |
    v
[5] Validate stage/status belong to template
    |-- getParentChild(templateId, stageId, statusId)
    |-- switchIfEmpty -> STAGE_MISSING
    |
    v
[6] Resolve stage and status IDs from validated entity
    |-- resolvedStageId = stageStatusEntity.getKey().getId()
    |-- resolvedStatusId = first status child or null
    |
    v
[7] Check if update is needed:
    |-- If statusId not in request AND stage unchanged -> return ticket unchanged
    |
    v
[8] updateTicketStage(access, ticket, null, stageId, statusId, taskRequest, comment)
         |
         v
       [8a] Clone old stage for activity logging
       [8b] Set new stage/status on ticket
       [8c] Update ticket internally
       [8d] Create task if taskRequest provided
       [8e] Log STAGE_UPDATE + STATUS_CREATE activities
       [8f] If stage changed, trigger reassignForStage
```

**updateTicketStage implementation:**

```java
public Mono<Ticket> updateTicketStage(
        ProcessorAccess access, Ticket ticket, ULong reassignUserId,
        ULong stageId, ULong statusId, TaskRequest taskRequest, String comment) {

    ULong oldStage = CloneUtil.cloneObject(ticket.getStage());
    boolean doReassignment = !oldStage.equals(stageId);

    ticket.setStage(stageId);
    ticket.setStatus(statusId);

    return FlatMapUtil.flatMapMono(
        () -> super.updateInternal(access, ticket),              // 8c
        uTicket -> taskRequest != null                            // 8d
            ? this.createTask(access, taskRequest, uTicket)
            : Mono.just(Boolean.FALSE),
        (uTicket, cTask) -> this.activityService                 // 8e
            .acStageStatus(access, uTicket, comment, oldStage)
            .thenReturn(uTicket),
        (uTicket, cTask, fTicket) -> doReassignment              // 8f
            ? this.reassignForStage(access, fTicket, reassignUserId, true)
            : Mono.just(fTicket))
    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketStage"));
}
```

**Stage/Status Activity Logic (`acStageStatus`):**

The activity service creates either one or two activity records:

- If the stage did NOT change (only status changed): creates only `STATUS_CREATE` activity
- If the stage DID change: creates both `STATUS_CREATE` and `STAGE_UPDATE` activities
  concurrently via `Mono.when()`

### 4.6 Reassignment Flow (`reassignTicket`)

Manual reassignment of a ticket to a different user.

**Flow:**

```
TicketReassignRequest (userId, comment?)
    |
    v
[1] Validate userId is provided
    |-- null -> IDENTITY_MISSING for "reassign user"
    |
    v
[2] Check access
    |
    v
[3] Fetch ticket by identity
    |
    v
[4] Validate target user is in sub-organization
    |-- access.getUserInherit().getSubOrg().contains(userId)
    |-- If not -> INVALID_USER_ACCESS
    |
    v
[5] updateTicketForReassignment(access, ticket, userId, comment, isAutomatic=false)
    |
    v
[6] Internal reassignment:
    |-- Check if old user == new user -> return ticket unchanged
    |-- Set new assignment on ticket
    |-- Update ticket internally
    |-- Log REASSIGN activity (with old and new user names)
```

### 4.7 Automatic Reassignment (`reassignForStage`)

Triggered automatically when a stage changes (from `updateTicketStage`).

**Flow:**

```
reassignForStage(access, ticket, reassignUserId, isAutomatic=true)
    |
    v
[1] If specific userId provided -> use it directly
    |     |
    |     v
    |   updateTicketForReassignment(isAutomatic=true)
    |
    v (no userId)
[2] Call ProductTicketCRuleService.getUserAssignment
    |-- access, productId, stageId, tokenPrefix
    |-- currentUserId, ticket
    |-- allowSameUser=false (isCreate=false)
    |
    v
[3] If rule returns a userId -> reassign
    |-- updateTicketForReassignment(isAutomatic=true)
    |
    v (no rule match)
[4] Keep current assignment -> return ticket unchanged
```

**Key detail:** When `isCreate=false`, the rule engine skips the default rule (order 0).
This means if only a default rule exists and no stage-specific rules are configured,
the assignment will not change on stage transition.

The activity logged for automatic reassignment is `REASSIGN_SYSTEM` rather than
`REASSIGN`, clearly distinguishing system-initiated from user-initiated reassignments.

### 4.8 Tag Management (`updateTag`)

Tags (HOT, WARM, COLD) are used to categorize ticket priority/temperature.

**Flow:**

```
TicketTagRequest (tag, taskRequest?, comment?)
    |
    v
[1] Validate tag is provided
    |-- null -> identityMissingError for "tag"
    |
    v
[2] Check access
    |
    v
[3] Fetch ticket by identity
    |
    v
[4] Resolve tag value
    |
    v
[5] Save old tag for activity
    |
    v
[6] Update ticket with new tag
    |
    v
[7] Create task if taskRequest provided
    |
    v
[8] Log activity:
    |-- If oldTag was null -> TAG_CREATE activity
    |-- If oldTag existed -> TAG_UPDATE activity (shows old -> new)
```

### 4.9 DNC Propagation

When a ticket's DNC flag changes through partner operations, the change propagates:

```java
public Flux<Ticket> updateTicketDncByClientId(ProcessorAccess access, ULong clientId, Boolean dnc) {
    return this.dao.getAllClientTicketsByDnc(clientId, !dnc)
        .map(ticket -> ticket.setDnc(dnc))
        .flatMap(tickets -> super.updateInternal(access, tickets));
}
```

This fetches all tickets for the client with the opposite DNC value and bulk-updates them.

### 4.10 Updatable Entity Pattern

The `updatableEntity()` method controls which fields can be modified during updates:

```java
@Override
protected Mono<Ticket> updatableEntity(Ticket ticket) {
    return super.updatableEntity(ticket)    // Version check + increment
        .flatMap(existing -> {
            existing.setEmail(ticket.getEmail());
            existing.setAssignedUserId(ticket.getAssignedUserId());
            existing.setStage(ticket.getStage());
            existing.setStatus(ticket.getStatus());
            existing.setSubSource(ticket.getSubSource());
            existing.setTag(ticket.getTag());
            return Mono.just(existing);
        });
}
```

Only the listed fields are copied from the incoming entity to the existing record.
All other fields (name, productId, ownerId, etc.) are preserved from the database.

---

## 5. Owner Management

The `OwnerService` manages contact records (leads/contacts) that are shared across tickets.

### 5.1 getOrCreateTicketOwner

This is the primary entry point called during ticket creation:

```java
public Mono<Owner> getOrCreateTicketOwner(ProcessorAccess access, Ticket ticket) {
    if (ticket.getOwnerId() != null)
        return this.readById(access, ULongUtil.valueOf(ticket.getOwnerId()));

    return this.getOrCreateTicketPhoneOwner(access, ticket);
}
```

**Two paths:**

1. **Owner ID provided**: Simply look up and return the existing owner
2. **No owner ID**: Look up by phone number and email, or create new

**Phone/email lookup and creation:**

```java
private Mono<Owner> getOrCreateTicketPhoneOwner(ProcessorAccess access, Ticket ticket) {
    return FlatMapUtil.flatMapMono(
        () -> this.dao.readByNumberAndEmail(
                access, ticket.getDialCode(), ticket.getPhoneNumber(), ticket.getEmail())
            .switchIfEmpty(this.create(access, Owner.of(ticket))),
        owner -> {
            if (owner.getId() == null)
                return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                    ProcessorMessageResourceService.OWNER_NOT_CREATED);
            return Mono.just(owner);
        });
}
```

The DAO's `readByNumberAndEmail` performs a phone+email match within the tenant scope.
If no match is found, the `switchIfEmpty` creates a new owner from the ticket data.

### 5.2 Owner-Ticket Bidirectional Sync

When an owner is updated, changes propagate to all linked tickets:

```java
@Override
public Mono<Owner> update(Owner entity) {
    return FlatMapUtil.flatMapMono(
        super::hasAccess,
        access -> super.update(access, entity),
        (access, updated) -> this.updateOwnerTickets(access, updated).thenReturn(updated));
}
```

And conversely, when a ticket is updated, the owner is synced:

```java
public Mono<Ticket> updateTicketOwner(ProcessorAccess access, Ticket ticket) {
    return this.readById(access, ticket.getOwnerId()).flatMap(owner -> {
        owner.setName(ticket.getName());
        owner.setEmail(ticket.getEmail());
        return this.update(access, owner).thenReturn(ticket);
    });
}
```

### 5.3 Owner Duplicate Detection

Before creating a standalone owner, the service checks for duplicates:

```java
private Mono<Boolean> checkDuplicate(ProcessorAccess access, OwnerRequest ownerRequest) {
    return this.dao.readByNumberAndEmail(access, countryCode, number, email)
        .flatMap(existing -> {
            if (existing.getId() != null) return super.throwDuplicateError(access, existing);
            return Mono.just(Boolean.FALSE);
        })
        .switchIfEmpty(Mono.just(Boolean.FALSE));
}
```

---

## 6. Product and Template Management

### 6.1 Product

Products (called "Projects" in leadzump) define the business context for tickets. Each
product links to a `ProductTemplate` that defines available stages, rules, and workflows.

**Key relationships:**

- Product -> ProductTemplate (defines stages and pipeline structure)
- Product -> ProductTicketCRule (creation assignment rules)
- Product -> ProductTicketRuRule (read/update access rules)
- Product -> ProductComm (communication channels)
- Product -> ProductWalkInForm (walk-in form configuration)

### 6.2 ProductTemplate

Templates define the structure shared across products:

| Type | Description |
|------|-------------|
| `FORM` | Simple form-based data collection |
| `PIPELINE` | Multi-stage pipeline with progression |
| `WIZARD` | Step-by-step guided workflow |

Templates own the stage definitions. Products can either inherit template stages directly
or override them with product-specific rules.

### 6.3 Template Override Behavior

The `ProductTicketCRuleService` supports two modes:

- **Override**: Product rules replace template rules. If no product rules exist for a
  stage, falls back to template rules.
- **Combine**: Product rules are merged with template rules. Product rules take precedence
  by order, and template rules fill remaining positions.

---

## 7. Stage and Pipeline Management

The `StageService` manages the hierarchical stage/status structure.

### 7.1 Hierarchical Model

```
ProductTemplate
  |-- Stage (parent, has order)
  |     |-- Status (child, has order)
  |     |-- Status
  |     |-- Status
  |-- Stage
  |     |-- Status
  |-- Stage
```

Stages are parents. Statuses are children of stages. Both are stored in the same
`entity_processor_stages` table, with parent-child relationships.

### 7.2 Key Operations

**getFirstStage**: Returns the stage with the lowest order for a template:

```java
public Mono<Stage> getFirstStage(ProcessorAccess access, ULong productTemplateId) {
    return super.getAllValuesInOrder(access, null, productTemplateId)
        .map(NavigableMap::firstKey);
}
```

**getFirstStatus**: Returns the first child status of a given stage:

```java
public Mono<Stage> getFirstStatus(ProcessorAccess access, ULong productTemplateId, ULong stageId) {
    return super.getAllValuesInOrder(access, null, productTemplateId)
        .flatMap(navigableMap -> {
            Stage stage = navigableMap.keySet().stream()
                .filter(key -> key.getId().equals(stageId))
                .findFirst().orElse(null);
            if (stage == null || !navigableMap.containsKey(stage)) return Mono.empty();
            if (navigableMap.get(stage) == null || navigableMap.get(stage).isEmpty())
                return Mono.empty();
            return Mono.justOrEmpty(navigableMap.get(stage).first());
        });
}
```

**getParentChild**: Validates that a stage/status combination belongs to a template and
returns the resolved pair. This is critical for stage update validation.

**getHigherStages**: Returns all stages with order >= the given stage. Used by the
duplication rule engine to define the stage range for duplicate detection.

**getStage**: Validates that a specific stage ID belongs to a template. Returns empty
if the stage does not belong.

### 7.3 Stage Ordering

Stages have an explicit `order` field. The `applyOrder()` method handles order management:

1. If the stage is a child (status), no ordering is applied
2. If an explicit order is provided:
   - Check if the order already exists
   - If it does, shift all subsequent stages up by 1
3. If no order is provided:
   - Find the latest stage by order
   - Set order = latestOrder + 1 (or 1 if no stages exist)

### 7.4 Stage Reordering

The `reorderStages()` method allows bulk reordering:

1. Validate the reorder request contains valid orders
2. Resolve the product template
3. Resolve all stage identities from the request
4. Fetch all existing parent stages
5. Verify ALL parent stages are included in the request
6. Update each stage with its new order

### 7.5 Stage Properties

| Property | Type | Description |
|----------|------|-------------|
| `name` | String | Display name of the stage |
| `description` | String | Optional description |
| `stageType` | StageType | Type classification |
| `isSuccess` | Boolean | Whether this stage represents a successful outcome |
| `isFailure` | Boolean | Whether this stage represents a failed outcome |
| `platform` | Platform | Platform-specific stage (nullable) |
| `order` | Integer | Sort order within the template |
| `productTemplateId` | ULong | Parent template |

### 7.6 Cache Invalidation

When stages change, the `productTicketCRule` cache is also invalidated because rules
reference stages:

```java
@Override
protected Mono<Boolean> evictCache(Stage entity) {
    return Mono.zip(
        super.evictCache(entity),
        super.cacheService.evictAll("productTicketCRule"),
        (stageEvicted, productTicketCRuleEvicted) -> stageEvicted && productTicketCRuleEvicted);
}
```

---

## 8. Activity Service -- Comprehensive Audit Trail

The `ActivityService` is the event-sourcing backbone of the entity-processor. Every
significant action creates an immutable `Activity` record.

### 8.1 Activity Record Structure

| Field | Type | Description |
|-------|------|-------------|
| `ticketId` | ULong | Associated ticket (nullable) |
| `ownerId` | ULong | Associated owner (nullable) |
| `userId` | ULong | Associated user (nullable) |
| `action` | ActivityAction | The type of action performed |
| `description` | String | Human-readable formatted description |
| `comment` | String | User-provided comment |
| `actorId` | ULong | The user who performed the action |
| `activityDate` | LocalDateTime | When the action occurred |
| `activityObject` | ActivityObject | JSON blob with full context details |
| `taskId` | ULong | Associated task (for task activities) |
| `noteId` | ULong | Associated note (for note activities) |
| `stageId` | ULong | Associated stage (for stage activities) |
| `statusId` | ULong | Associated status (for status activities) |

### 8.2 ActivityAction Enum

The `ActivityAction` enum defines 35+ action types, each with a message template:

**Deal/Ticket Actions:**

| Action | Template |
|--------|----------|
| `CREATE` | `$entity from $source created for $user.` |
| `RE_INQUIRY` | `$entity re-inquired from $source by $user.` |
| `QUALIFY` | `$entity qualified by $user.` |
| `DISQUALIFY` | `$entity marked as disqualified by $user.` |
| `DISCARD` | `$entity discarded by $user.` |
| `IMPORT` | `$entity imported via $source by $user.` |
| `STATUS_CREATE` | `$status created by $user.` |
| `STAGE_UPDATE` | `Stage moved from $_stage to $stage by $user.` |
| `WALK_IN` | `$entity walked in by $user.` |
| `DCRM_IMPORT` | `$entity imported via DCRM by $user.` |
| `TAG_CREATE` | `$entity was tagged $tag by $user.` |
| `TAG_UPDATE` | `$entity was tagged $tag from $_tag by $user.` |

**Task Actions:**

| Action | Template |
|--------|----------|
| `TASK_CREATE` | `Task $taskId was created by $user.` |
| `TASK_UPDATE` | `Task $taskId was updated by $user.` |
| `TASK_COMPLETE` | `Task $taskId was marked as completed by $user.` |
| `TASK_CANCELLED` | `Task $taskId was marked as cancelled by $user.` |
| `TASK_DELETE` | `Task $taskId was deleted by $user.` |
| `REMINDER_SET` | `Reminder for date $nextReminder, set for $taskId by $user.` |

**Document Actions:**

| Action | Template |
|--------|----------|
| `DOCUMENT_UPLOAD` | `Document $file uploaded by $user.` |
| `DOCUMENT_DOWNLOAD` | `Document $file downloaded by $user.` |
| `DOCUMENT_DELETE` | `Document $file deleted by $user.` |

**Note Actions:**

| Action | Template |
|--------|----------|
| `NOTE_ADD` | `Note $noteId added by $user.` |
| `NOTE_UPDATE` | `Note $noteId was updated by $user.` |
| `NOTE_DELETE` | `Note $noteId deleted by $user.` |

**Assignment Actions:**

| Action | Template |
|--------|----------|
| `ASSIGN` | `$entity was assigned to $assignedUserId by $user.` |
| `REASSIGN` | `$entity was reassigned from $_assignedUserId to $assignedUserId by $user.` |
| `REASSIGN_SYSTEM` | `$entity reassigned from $_assignedUserId to $assignedUserId due to availability rule by $user.` |
| `OWNERSHIP_TRANSFER` | `Ownership transferred from $_createdBy to $createdBy by $user.` |

**Communication Actions:**

| Action | Template |
|--------|----------|
| `CALL_LOG` | `Call with $customer logged by $user.` |
| `WHATSAPP` | `WhatsApp message sent to $customer by $user.` |
| `EMAIL_SENT` | `Email sent to $email by $user.` |
| `SMS_SENT` | `SMS sent to $customer by $user.` |

**Field Update Actions:**

| Action | Template |
|--------|----------|
| `FIELD_UPDATE` | `$fields by $user.` |
| `CUSTOM_FIELD_UPDATE` | `Custom field $field updated to $value by $user.` |
| `LOCATION_UPDATE` | `Location updated to $location by $user.` |

### 8.3 Template Variable Resolution

Templates use `$variable` placeholders that are replaced at runtime:

```java
public String formatMessage(Map<String, Object> context) {
    String formattedMessage = template;
    for (Map.Entry<String, Object> entry : context.entrySet())
        formattedMessage = formattedMessage.replace(
            "$" + entry.getKey(),
            this.formatMarkdown(entry.getKey(), this.getValue(entry.getValue())));
    return formattedMessage;
}
```

**Markdown formatting rules:**

| Key pattern | Format | Example |
|-------------|--------|---------|
| Contains "id" | Code: `` `value` `` | `` `12345` `` |
| Key is "user" | Bold + Italics: `***value***` | `***John Doe***` |
| All others | Bold: `**value**` | `**Stage Name**` |

**Value extraction rules:**

| Type | Extraction |
|------|-----------|
| `Map` (size <= 2) | `key: value, key: value` |
| `IdAndValue` | The value component (display name) |
| `ULong` | Long value as string |
| Other | `String.valueOf()` |

### 8.4 Activity Routing

Activities are routed to one of three entity types based on context:

```java
private Mono<Void> createActivityInternal(
        ProcessorAccess access, ActivityAction action, LocalDateTime createdOn,
        String comment, Map<String, Object> context) {

    ULong ticketId = context.containsKey(Activity.Fields.ticketId)
        ? ULongUtil.valueOf(context.get(Activity.Fields.ticketId)) : null;
    ULong ownerId = context.containsKey(Activity.Fields.ownerId)
        ? ULongUtil.valueOf(context.get(Activity.Fields.ownerId)) : null;
    ULong userId = context.containsKey(Activity.Fields.userId)
        ? ULongUtil.valueOf(context.get(Activity.Fields.userId)) : null;

    if (isValidId(ticketId))
        return this.createActivityForTicket(access, action, createdOn, comment, context, ticketId);
    if (isValidId(ownerId))
        return this.createActivityForOwner(access, action, createdOn, comment, context, ownerId);
    if (isValidId(userId))
        return this.createActivityForUser(access, action, createdOn, comment, context, userId);

    return Mono.empty();
}
```

Priority: ticket > owner > user. The first valid ID in the context determines routing.

### 8.5 DifferenceExtractor

For task and note updates, the service computes a JSON diff:

```java
private Mono<JsonNode> extractDifference(JsonNode incoming, JsonNode existing) {
    ObjectNode iObject = incoming.deepCopy();
    ObjectNode eObject = existing.deepCopy();

    // Remove system fields before diff
    sUpdatedFields.forEach(key -> {
        iObject.remove(key);
        eObject.remove(key);
    });

    return DifferenceExtractor.extract(iObject, eObject);
}
```

Fields excluded from diff: `id`, `createdBy`, `createdAt`, `updatedBy`, `updatedAt`, `code`.

### 8.6 Activity ID Linking

After an activity is created, relevant IDs are set from the context:

```java
private void updateActivityIds(Activity activity, Map<String, Object> context, boolean isDelete) {
    if (isDelete) return;  // Delete activities do not link
    this.updateIdFromContext(Activity.Fields.taskId, context, activity::setTaskId);
    this.updateIdFromContext(Activity.Fields.noteId, context, activity::setNoteId);
    this.updateIdFromContext(Ticket.Fields.stage, context, activity::setStageId);
    this.updateIdFromContext(Ticket.Fields.status, context, activity::setStatusId);
}
```

---

## 9. Task Management

The `TaskService` manages tasks that can be linked to tickets, owners, or users via the
`ContentEntitySeries` system.

### 9.1 Task Creation

```java
public Mono<Task> createRequest(ProcessorAccess access, TaskRequest taskRequest) {
    return FlatMapUtil.flatMapMono(
        () -> this.updateIdentities(access, taskRequest),
        task -> this.createContent(taskRequest),
        (task, content) -> super.createContent(access, content));
}
```

**Validation rules:**

1. Due date cannot be in the past
2. Task type ID must be provided (`TASK_TYPE_MISSING`)
3. Task type determines the content entity series (TICKET, OWNER, or USER)
4. The corresponding entity ID must be present for the series (`TASK_TYPE_ENTITY_ID_MISSING`)

### 9.2 Task Type Validation

```java
@Override
protected Mono<Task> checkEntity(Task entity, ProcessorAccess access) {
    if (entity.getTaskTypeId() == null)
        return this.msgService.throwMessage(..., TASK_TYPE_MISSING);

    return this.taskTypeService.readById(access, entity.getTaskTypeId())
        .flatMap(taskType -> {
            entity.setContentEntitySeries(taskType.getContentEntitySeries());
            return this.checkTaskHasEntityForSeries(entity, taskType).thenReturn(entity);
        });
}
```

The `ContentEntitySeries` enum controls where tasks are linked:

| Series | Required Field | Display Name |
|--------|---------------|--------------|
| `TICKET` | `ticketId` | Ticket |
| `OWNER` | `ownerId` | Owner |
| `USER` | `userId` | User |

### 9.3 Task Completion

```java
public Mono<Task> setTaskCompleted(Identity taskIdentity, Boolean isCompleted, LocalDateTime completedDate) {
    return this.setTaskStatus(taskIdentity, isCompleted, completedDate, true)
        .flatMap(task -> this.activityService.acTaskComplete(task).then(Mono.just(task)));
}
```

**Validation:**

- Cannot complete an already completed task (`TASK_ALREADY_COMPLETED`)
- Cannot complete an already cancelled task (`TASK_ALREADY_CANCELLED`)
- Completion date cannot be in the past (`DATE_IN_PAST`)
- If the due date is before the completion date, the task is marked as `delayed=true`

### 9.4 Task Cancellation

```java
public Mono<Task> setTaskCancelled(Identity taskIdentity, Boolean isCancelled, LocalDateTime cancelledDate) {
    return this.setTaskStatus(taskIdentity, isCancelled, cancelledDate, false)
        .flatMap(task -> this.activityService.acTaskCancelled(task).then(Mono.just(task)));
}
```

Same validation rules as completion. Cancelled tasks set `cancelledDate` and `cancelled=true`.

### 9.5 Reminder Setting

```java
public Mono<Task> setReminder(Identity taskIdentity, LocalDateTime reminderDate) {
    return FlatMapUtil.flatMapMono(
        super::hasAccess,
        access -> this.readByIdentity(access, taskIdentity),
        (access, task) -> this.checkTaskStatus(task, reminderDate, LocalDateTime.now(), "Reminder"),
        (access, task, vTask) -> {
            vTask.setHasReminder(Boolean.TRUE);
            vTask.setNextReminder(reminderDate);
            return this.update(access, vTask);
        },
        (access, task, vTask, uTask) -> this.activityService.acReminderSet(uTask).then(Mono.just(uTask)));
}
```

Reminders cannot be set on completed or cancelled tasks.

### 9.6 Updatable Fields

```java
@Override
protected Mono<Task> updatableEntity(Task entity) {
    return super.updatableEntity(entity).flatMap(existing -> {
        existing.setDueDate(entity.getDueDate());
        existing.setTaskPriority(entity.getTaskPriority());
        // Completed and cancelled flags with timestamps
        existing.setDelayed(entity.isDelayed());
        existing.setHasReminder(entity.isHasReminder());
        existing.setNextReminder(entity.getNextReminder());
        return Mono.just(existing);
    });
}
```

---

## 10. Note Management

The `NoteService` manages text notes attached to tickets or owners.

### 10.1 Note Creation

```java
public Mono<Note> createRequest(ProcessorAccess access, NoteRequest noteRequest) {
    return FlatMapUtil.flatMapMono(
        () -> super.updateBaseIdentities(access, noteRequest),
        this::createContent,
        (uRequest, content) -> super.createContent(access, content));
}
```

**Validation:** Content is required (`CONTENT_MISSING`).

### 10.2 Note Update

Notes support updating their content and attachment file detail:

```java
@Override
protected Mono<Note> updatableEntity(Note entity) {
    return super.updatableEntity(entity).flatMap(existing -> {
        existing.setAttachmentFileDetail(entity.getAttachmentFileDetail());
        return Mono.just(existing);
    });
}
```

### 10.3 Activity Logging

- **NOTE_ADD**: Logged on creation with the note's full content in the activity object
- **NOTE_UPDATE**: Logged with a diff between old and new note content
- **NOTE_DELETE**: Logged with the note's content at time of deletion

---

## 11. Campaign Management

The `CampaignService` manages the mapping between external marketing campaigns and
internal products.

### 11.1 Campaign Structure

| Field | Type | Description |
|-------|------|-------------|
| `campaignId` | String | External campaign identifier (e.g., Facebook campaign ID) |
| `campaignName` | String | Display name |
| `campaignType` | String | Type classification |
| `campaignPlatform` | CampaignPlatform | Source platform |
| `productId` | ULong | Associated product |

### 11.2 Campaign Platforms

The `CampaignPlatform` enum tracks the origin:

- Facebook
- Google
- Instagram
- LinkedIn
- Other marketing platforms

### 11.3 Campaign Creation

```java
public Mono<Campaign> createRequest(CampaignRequest campaignRequest) {
    return FlatMapUtil.flatMapMono(
        this::hasAccess,
        access -> this.productService.readByIdentity(access, campaignRequest.getProductId()),
        (access, product) -> super.createInternal(
            access, Campaign.of(campaignRequest).setProductId(product.getId())));
}
```

The product is validated before associating it with the campaign.

### 11.4 Campaign Lookup

Campaigns are looked up by their external `campaignId` during ticket creation:

```java
public Mono<Campaign> readByCampaignId(ProcessorAccess access, String campaignId) {
    return super.cacheService.cacheValueOrGet(
        this.getCacheName(),
        () -> this.dao.readByCampaignId(access, campaignId),
        super.getCacheKey(access.getAppCode(), access.getClientCode(), campaignId));
}
```

Campaign lookups are cached for performance since they are hit on every campaign ticket
creation request.

---

## 12. Partner and Business Partner Management

The `PartnerService` manages external organizations (Business Partners) that operate
within the platform.

### 12.1 Partner Structure

| Field | Type | Description |
|-------|------|-------------|
| `clientId` | ULong | The Security client ID for this partner |
| `managerId` | ULong | The managing user ID |
| `description` | String | Partner description |
| `active` | Boolean | Whether the partner is active |
| `tempActive` | Boolean | Temporary activation flag |
| `partnerVerificationStatus` | PartnerVerificationStatus | Verification workflow status |
| `dnc` | Boolean | Do-Not-Call flag |

### 12.2 Verification Status Workflow

The `PartnerVerificationStatus` enum defines the partner lifecycle:

| Status | Description |
|--------|-------------|
| `INVITATION_SENT` | Initial state when partner is created |
| (other statuses) | Progress through verification workflow |

### 12.3 Partner Access Control

The `PartnerService` overrides `hasAccess()` with an additional check:

```java
@Override
public Mono<ProcessorAccess> hasAccess() {
    return FlatMapUtil.flatMapMono(super::hasAccess, access -> {
        if (!access.isHasBpAccess())
            return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.PARTNER_ACCESS_DENIED);
        return Mono.just(access);
    });
}
```

Only users with the BP management authority can access partner operations.

### 12.4 Partner Creation

```java
public Mono<Partner> createRequest(PartnerRequest partnerRequest) {
    return FlatMapUtil.flatMapMono(
        this::hasAccess,
        access -> super.securityService.getClientById(partnerRequest.getClientId().toBigInteger()),
        (access, client) -> {
            if (!client.getLevelType().equals(BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP))
                return super.msgService.throwMessage(..., INVALID_CLIENT_TYPE, ...);
            return super.createInternal(access,
                Partner.of(partnerRequest)
                    .setManagerId(null)
                    .setPartnerVerificationStatus(PartnerVerificationStatus.INVITATION_SENT));
        });
}
```

**Validation:**
- The target client must have `levelType` equal to `CLIENT_LEVEL_TYPE_BP`
- Manager is initially null
- Status starts at `INVITATION_SENT`

### 12.5 DNC Toggle with Propagation

When a partner's DNC flag is toggled, it propagates to all their tickets:

```java
public Mono<Partner> togglePartnerDnc(Identity partnerId) {
    return FlatMapUtil.flatMapMono(
        this::hasAccess,
        access -> super.readByIdentity(access, partnerId),
        (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
        (access, partner, updated) -> this.evictCache(partner),
        (access, partner, updated, evicted) -> this.ticketService
            .updateTicketDncByClientId(access, partner.getClientId(), !partner.getDnc())
            .then(Mono.just(updated)));
}
```

This ensures consistency: when a partner opts out of calls, ALL their tickets are flagged.

### 12.6 Logged-In Partner Operations

Partners can perform self-service operations:

- `getLoggedInPartner()` -- Retrieves the partner record for the current user's client
- `updateLoggedInPartnerVerificationStatus()` -- Self-service status update
- `toggleLoggedInPartnerDnc()` -- Self-service DNC toggle with ticket propagation

### 12.7 Partner Client and Teammate Views

The partner service provides enriched views:

- `readPartnerClient()` -- Lists partner clients with optional ticket counts per stage
- `readPartnerTeammates()` -- Lists users within a partner with optional ticket counts
- Both support `fetchPartners` and `fetchLeads` query parameters for eager loading

---

## 13. Walk-In Form System

The walk-in form system provides public-facing forms for in-person lead capture.

### 13.1 Form Hierarchy

```
ProductTemplateWalkInForm (template-level default)
    |
    v
ProductWalkInForm (product-level override)
```

Product-level forms take precedence. If no product form exists, the template form is used.

### 13.2 Form Configuration

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Form display name |
| `productId` / `productTemplateId` | ULong | Associated entity |
| `stageId` | ULong | Stage to assign walk-in tickets |
| `statusId` | ULong | Status to assign walk-in tickets |
| `assignmentType` | AssignmentType | How users are assigned |
| `active` | Boolean | Whether the form accepts submissions |

### 13.3 Assignment Types

| Type | Behavior |
|------|----------|
| `MANUAL` | User must be explicitly selected in the form |
| `DEAL_FLOW` | Assignment follows the standard rule engine (userId forced to null) |

### 13.4 Walk-In Ticket Creation Flow

```
WalkInFormTicketRequest + appCode + clientCode + productId
    |
    v
[1] Construct ProcessorAccess (outside=true)
    |
    v
[2] Resolve product -> get (productId, productTemplateId)
    |
    v
[3] Get WalkInFormResponse (product-level, then template-level fallback)
    |
    v
[4] Validate form is active
    |-- Inactive -> ENTITY_INACTIVE
    |
    v
[5] If MANUAL assignment, validate userId is provided
    |-- Missing -> IDENTITY_MISSING for "Owner User"
    |
    v
[6] Determine if existing or new ticket:
    |
    +-- [Existing ticket (ticketId provided)]
    |     |
    |     v
    |   Validate user, fetch ticket by identity
    |     |
    |     v
    |   Update existing ticket:
    |     - Update email, name, description, subSource
    |     - If stage/status changed -> updateTicketStage
    |     - If unchanged -> updateInternal only
    |     |
    |     v
    |   Log WALK_IN activity
    |
    +-- [New ticket (no ticketId)]
          |
          v
        Validate user and check for existing tickets by phone
          |
          v
        If tickets exist -> TICKET_ID_NOT_SELECTED (force selection)
          |
          v
        Create new ticket:
          - Set stage/status from walk-in form
          - Set source = "Walk In"
          - Set productId, assignedUserId
          |
          v
        Log WALK_IN + STAGE_UPDATE + STATUS_CREATE activities
```

### 13.5 Walk-In Support Functions

- `getWalkInFromUsers()` -- Lists active users for manual assignment dropdown
- `getWalkInTickets()` -- Searches existing tickets by phone for duplicate selection
- `getWalkInProduct()` -- Returns product details for form rendering
- `getWalkInFormResponse()` -- Returns the form configuration for rendering

---

## 14. Communication Integration

The `ProductCommService` manages communication channel configurations per product.

### 14.1 Communication Channel Resolution

Channels are resolved through a cascading priority:

```java
public Mono<ProductComm> getProductComm(
        ProcessorAccess access, ULong productId,
        ConnectionType connectionType, ConnectionSubType connectionSubType,
        String source, String subSource) {

    return this.getProductCommInternal(access, productId, connectionType,
                                       connectionSubType, source, subSource)
        .switchIfEmpty(this.getDefault(access, productId, connectionType, connectionSubType))
        .switchIfEmpty(this.getAppDefault(access, connectionType, connectionSubType))
        .switchIfEmpty(Mono.empty());
}
```

**Resolution order:**

1. **Exact match**: Product + connectionType + connectionSubType + source + subSource
2. **Product default**: Product + connectionType + connectionSubType (isDefault=true)
3. **App default**: connectionType + connectionSubType (no product, isDefault=true)
4. **Empty**: No configuration found

### 14.2 Connection Types

| Type | Sub-Types | Validation |
|------|-----------|------------|
| `MAIL` | Various | Requires email address |
| `CALL` | Various | Requires dialCode + phoneNumber |
| `TEXT` | Various | Requires dialCode + phoneNumber |

### 14.3 Communication Validation

The `checkEntity()` method validates based on connection type:

```java
private Mono<ProductComm> validateByType(ProductComm entity, ProcessorAccess access,
        ConnectionType connectionType, ConnectionSubType connectionSubType) {
    return switch (connectionType) {
        case MAIL -> this.validateMail(entity)
            .flatMap(valid -> this.checkDuplicate(...));
        case CALL, TEXT -> this.validatePhone(entity)
            .flatMap(valid -> this.checkDuplicate(...));
        default -> Mono.just(entity);
    };
}
```

### 14.4 Connection Service Provider

The `ConnectionServiceProvider` fetches actual connection details from the Core service
via Feign. This enables the entity-processor to retrieve phone numbers, email configurations,
and other communication channel settings defined in the Core module.

---

## 15. Duplication Rule Engine

The `TicketDuplicationRuleService` provides configurable duplicate detection logic.

### 15.1 Rule Structure

| Field | Type | Description |
|-------|------|-------------|
| `source` | String | Source to match (required) |
| `subSource` | String | Sub-source to match (optional, null = wildcard) |
| `maxStageId` | ULong | Maximum stage for duplicate consideration |
| `productId` | ULong | Product-level rule (nullable) |
| `productTemplateId` | ULong | Template-level rule |
| `condition` | AbstractCondition | Additional filter conditions |

### 15.2 Duplicate Check Flow

```
checkDuplicate(access, productId, phone, email, source, subSource)
    |
    v
[1] Validate phone or email is present
    |-- Neither -> IDENTITY_INFO_MISSING
    |
    v
[2] Get duplicate rule condition:
    |-- getDuplicateRuleCondition(access, productId, source, subSource)
    |     |
    |     v
    |   [2a] Look up product -> get productTemplateId
    |   [2b] Try product-level rules first
    |   [2c] Fall back to template-level rules
    |
    v
[3] If rule found -> handleDuplicateCheck WITH rule condition
    If no rule  -> handleDuplicateCheck WITHOUT rule condition (simple lookup)
```

### 15.3 Rule Condition Building

For each duplication rule:

1. Get all stages with order >= maxStageId (`getHigherStages`)
2. Create a `FilterCondition` with `stage IN [higherStageIds]`
3. AND it with the rule's custom condition
4. Multiple rules are OR'd together: `ComplexCondition.or(conditions)`

### 15.4 Duplicate Check with Rules

```
handleDuplicateCheck(access, productId, phone, email, ruleCondition, source, subSource)
    |
    v
[1] If ruleCondition is non-empty:
    |
    v
[2] Remove stage field from condition -> conditionWithoutStage
    |
    v
[3] Search for tickets matching conditionWithoutStage + phone/email
    |
    v
[4] If NO tickets found (no matches without stage filter):
    |-- Fall back to simple phone/email lookup
    |
    v
[5] If tickets found:
    |-- Search again WITH full rule condition (including stage filter)
    |
    v
[6] fetchDuplicateAndLog:
    |-- If duplicate found:
    |     |-- Create RE_INQUIRY activity on the existing ticket
    |     |-- Throw duplicate error
    |-- If no duplicate -> return false (allow creation)
```

### 15.5 SubSource Matching

Rules are filtered by sub-source with this priority:

1. **Exact match**: Rules where `subSource` equals the ticket's sub-source
2. **Wildcard match**: Rules where `subSource` is null (matches any sub-source)
3. Exact matches take precedence over wildcards

### 15.6 Duplicate Error Messages

Different messages for different user types:

```java
protected <T> Mono<T> throwDuplicateError(ProcessorAccess access, D existing) {
    if (access.isOutsideUser())
        return this.msgService.throwMessage(..., DUPLICATE_ENTITY_OUTSIDE_USER, entityPrefix);

    return this.msgService.throwMessage(..., DUPLICATE_ENTITY, entityPrefix, existing.getId(), entityPrefix);
}
```

Outside users get a generic "duplicate entity" message. Inside users get the duplicate's
ID so they can navigate to the existing ticket.

---

## 16. Assignment Rule Engine

The `ProductTicketCRuleService` manages rules that determine which user gets assigned to
a ticket.

### 16.1 Rule Resolution

Rules are resolved with this priority:

1. **Product-level rules** for the specific stage
2. **Template-level rules** for the specific stage
3. **Default rules** (stageId = null, order = 0)

### 16.2 Override vs Combine Modes

**Override mode** (`product.isOverrideCTemplate() = true`):

```
Product rules for stage exist? -> Use them
No product rules? -> Fall back to template rules
```

**Combine mode**:

Product rules and template rules are merged, with product rules taking higher priority
(lower order numbers).

### 16.3 Rule Execution

```java
public Mono<ULong> getUserAssignment(
        ProcessorAccess access, ULong productId, ULong stageId,
        String tokenPrefix, ULong userId, Ticket ticket, boolean isCreate) {

    return FlatMapUtil.flatMapMono(
        () -> this.getRulesWithOrder(access, productId, stageId),
        productRule -> {
            if (productRule.isEmpty()) return Mono.empty();

            // During updates, skip if only default rule exists
            if (!isCreate && productRule.size() == 1 && productRule.containsKey(0))
                return Mono.empty();

            return FlatMapUtil.flatMapMono(
                () -> this.ticketCRuleExecutionService.executeRules(
                    access, productRule, tokenPrefix, userId, ticket.toJsonElement()),
                super::updateInternalForOutsideUser,
                (eRule, uRule) -> {
                    ULong assignedUserId = uRule.getLastAssignedUserId();
                    if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0)))
                        return Mono.empty();
                    return Mono.just(assignedUserId);
                });
        })
    .onErrorResume(e -> Mono.empty());
}
```

**Key behaviors:**

- Empty rule set returns empty (caller handles fallback)
- During updates (`isCreate=false`), a single default rule is ignored
- Rule execution errors are swallowed (returns empty, allowing fallback)
- The rule's `lastAssignedUserId` is updated and persisted after execution

### 16.4 User Distribution

Rules reference `TicketCUserDistribution` entries that define how users are distributed:

| Distribution Type | Description |
|------------------|-------------|
| Round-Robin | Cycles through available users sequentially |
| Weighted | Distributes based on configured weights |
| Other | Custom distribution strategies |

### 16.5 Multi-Stage Rule Creation

The `createMultiple` method allows creating the same rule for multiple stages at once:

```java
public Flux<ProductTicketCRule> createMultiple(ProductTicketCRule rule, List<ULong> stageIds) {
    // Validates distributions, then creates a rule for each stageId
    // Each rule gets an incremented order offset
}
```

---

## 17. BaseProcessorService Common Patterns

### 17.1 Optimistic Locking

```java
@Override
protected Mono<D> updatableEntity(D entity) {
    return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
        if (existing.getVersion() != entity.getVersion())
            return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                ProcessorMessageResourceService.VERSION_MISMATCH);

        existing.setVersion(existing.getVersion() + 1);
        return Mono.just(existing);
    });
}
```

Version mismatch returns HTTP 412 Precondition Failed. The version is auto-incremented
on every successful update.

### 17.2 Outside User Client Setting

```java
@Override
public Mono<D> create(ProcessorAccess access, D entity) {
    return FlatMapUtil.flatMapMono(
        () -> access.isOutsideUser()
            ? Mono.just(entity.setClientId(ULongUtil.valueOf(access.getUser().getClientId())))
            : Mono.just(entity),
        uEntity -> super.create(access, uEntity));
}
```

For outside (BP) users, the `clientId` is automatically set from their user context.

### 17.3 Filtered Page Reading

```java
public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition, String timezone) {
    return FlatMapUtil.flatMapMono(
        this::hasAccess,
        access -> this.dao.processorAccessCondition(condition, access),
        (access, pCondition) -> this.dao.readPageFilter(pageable, pCondition, timezone));
}
```

The `processorAccessCondition()` method adds tenant-scoping filters (appCode, clientCode)
to any user-provided conditions.

### 17.4 Eager Loading

```java
public Mono<Page<Map<String, Object>>> readPageFilterEager(
        Pageable pageable, AbstractCondition condition,
        List<String> fields, String timezone,
        MultiValueMap<String, String> queryParams,
        Map<String, AbstractCondition> subQueryConditions) {

    return FlatMapUtil.flatMapMono(
        this::hasAccess,
        access -> this.dao.processorAccessCondition(condition, access),
        (access, pCondition) -> this.dao.readPageFilterEager(
            pageable, pCondition, fields, timezone, queryParams, subQueryConditions));
}
```

Eager loading resolves foreign key references (owner, product, stage, assigned user, etc.)
and returns enriched `Map<String, Object>` results instead of typed DTOs.

---

## 18. Error Handling Patterns

### 18.1 GenericException

All errors are thrown as `GenericException` with an HTTP status and internationalized message:

```java
return this.msgService.throwMessage(
    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
    ProcessorMessageResourceService.IDENTITY_MISSING,
    "Source");
```

### 18.2 Message Keys

The `ProcessorMessageResourceService` defines 65+ message constants:

| Key | Context |
|-----|---------|
| `version_mismatch` | Optimistic locking failure |
| `forbidden_app_access` | App-level access denied |
| `login_required` | Authentication missing |
| `name_missing` | Entity name not provided |
| `connection_not_found` | Communication connection not found |
| `duplicate_name_for_entity` | Duplicate name in child entities |
| `invalid_user_for_client` | User does not belong to client |
| `invalid_user_access` | User not in sub-organization |
| `identity_missing` | Required identity field missing |
| `identity_info_missing` | Neither phone nor email provided |
| `identity_wrong` | Identity resolves to no entity |
| `product_forbidden_access` | Product access denied |
| `ticket_stage_missing` | No stages configured for product |
| `ticket_assignment_missing` | No user assigned to ticket |
| `owner_not_created` | Owner creation failed |
| `duplicate_entity` | Duplicate entity for inside users |
| `duplicate_entity_outside_user` | Duplicate entity for outside users |
| `stage_missing` | Stage not found or invalid |
| `content_missing` | Note/content body empty |
| `date_in_past` | Date validation failure |
| `task_already_completed` | Cannot modify completed task |
| `task_already_cancelled` | Cannot modify cancelled task |
| `partner_access_denied` | No BP management permission |
| `invalid_client_type` | Client is not a Business Partner |
| `website_entity_data_invalid` | Campaign data on website request |
| `entity_inactive` | Walk-in form is not active |
| `ticket_creation_failed` | Ticket creation returned empty |
| `ticket_id_not_selected` | Existing tickets found, must select one |
| `task_type_missing` | Task type ID not provided |
| `task_type_entity_id_missing` | Required entity ID missing for task type |

### 18.3 switchIfEmpty Pattern

The `switchIfEmpty` pattern is used for not-found handling:

```java
this.campaignService.readByCampaignId(access, campaignId)
    .switchIfEmpty(this.msgService.throwMessage(
        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
        ProcessorMessageResourceService.IDENTITY_WRONG,
        this.campaignService.getEntityName(),
        campaignId));
```

This throws only if the upstream Mono completes empty. If it emits a value, the
`switchIfEmpty` branch is never evaluated.

### 18.4 Context Logging

Every reactive chain ends with a `contextWrite` for structured logging:

```java
.contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createRequest"));
```

This enables tracing the execution path through nested method calls in log output.

---

## 19. Caching Strategy

### 19.1 Cache Architecture

Each service defines its own cache namespace:

| Service | Cache Name |
|---------|-----------|
| TicketService | `ticket` |
| OwnerService | `owner` |
| StageService | `stage` |
| ProductCommService | `productComm` |
| CampaignService | `campaign` |
| PartnerService | `Partner` |
| ProductTicketCRuleService | `productTicketCRule` |
| TaskService | `task` |
| NoteService | `note` |
| TicketDuplicationRuleService | `ticketDuplicationRule` |

### 19.2 Cache Key Pattern

Cache keys are constructed using `getCacheKey()` with variable arguments:

```java
super.getCacheKey(access.getAppCode(), access.getClientCode(), productId, source, subSource)
```

Keys are always scoped by `appCode` + `clientCode` to maintain tenant isolation.

### 19.3 Cache Invalidation

Each service overrides `evictCache()` to handle its specific invalidation needs:

```java
// StageService - also invalidates rule caches
@Override
protected Mono<Boolean> evictCache(Stage entity) {
    return Mono.zip(
        super.evictCache(entity),
        super.cacheService.evictAll("productTicketCRule"),
        (stageEvicted, productTicketCRuleEvicted) -> stageEvicted && productTicketCRuleEvicted);
}
```

```java
// CampaignService - evicts by campaignId as well
@Override
protected Mono<Boolean> evictCache(Campaign entity) {
    return Mono.zip(
        super.evictCache(entity),
        super.cacheService.evict(
            this.getCacheName(),
            super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getCampaignId())),
        (baseEvicted, campaignEvicted) -> baseEvicted && campaignEvicted);
}
```

### 19.4 Duplication Rule Caches

The duplication rule service maintains separate caches for product-level and
template-level rule conditions:

- `ticketDuplicationProductRuleCondition` -- Product-specific rule conditions
- `ticketDuplicationProductTemplateRuleCondition` -- Template-level rule conditions

Both are invalidated when duplication rules change.

---

## 20. Analytics and Reporting

### 20.1 Ticket Bucket Service

The `TicketBucketService` provides aggregated ticket counts for dashboards:

- **Per client per stage**: How many tickets each client has in each stage
- **Per user per stage**: How many tickets each user has created in each stage

### 20.2 Analytics Models

| Model | Description |
|-------|-------------|
| `EntityCount` | Count by entity ID |
| `DateCount` | Count by date |
| `StatusEntityCount` | Count per entity per status |
| `StatusNameCount` | Count per status name |
| `EntityDateCount` | Count per entity per date |
| `EntityEntityCount` | Count per entity pair |
| `DateStatusCount` | Count per date per status |

### 20.3 Filtering

The `TicketBucketFilter` extends `BaseFilter` with:

| Filter | Type | Description |
|--------|------|-------------|
| `stageIds` | List<ULong> | Filter by specific stages |
| `clientIds` | List<ULong> | Filter by specific clients |
| `createdByIds` | List<ULong> | Filter by creator users |
| `includeAll` | Boolean | Include all stages |
| `includeNone` | Boolean | Include unassigned |
| `includeZero` | Boolean | Include zero-count entries |
| `includePercentage` | Boolean | Include percentage calculations |
| `includeTotal` | Boolean | Include total count |

### 20.4 Time Periods

The `TimePeriod` enum defines grouping intervals for time-series analytics (daily, weekly,
monthly, etc.).

---

## Appendix A: FlatMapUtil Chaining Patterns

The codebase extensively uses `FlatMapUtil` from the `reactor-flatmap-util` library.
This utility provides type-safe chaining of dependent reactive operations with
accumulating tuple access.

### Pattern 1: Simple Sequential Chain

```java
return FlatMapUtil.flatMapMono(
    () -> stepOne(),                           // Supplier<Mono<A>>
    a -> stepTwo(a),                           // Function<A, Mono<B>>
    (a, b) -> stepThree(a, b));                // BiFunction<A, B, Mono<C>>
```

### Pattern 2: Chain with Nullable Intermediates

```java
return FlatMapUtil.flatMapMonoWithNull(
    () -> stepOne(),                           // May return empty
    a -> stepTwo(a),                           // a could be null
    (a, b) -> stepThree(a, b));                // Both could be null
```

### Pattern 3: Long Chain (up to 8 steps)

```java
return FlatMapUtil.flatMapMono(
    super::hasAccess,                                              // Step 1: ProcessorAccess
    access -> this.productService.readByIdentity(access, id),      // Step 2: Product
    (access, product) -> this.stageService.getParentChild(...),    // Step 3: Stage+Status
    (access, product, stageStatus) -> this.validate(...),          // Step 4: Validation
    (access, product, stageStatus, validated) -> this.create(...), // Step 5: Creation
    (access, ..., created) -> this.createNote(...),                // Step 6: Note
    (access, ..., note) -> this.activityService.acCreate(...),     // Step 7: Activity
    (access, ..., activity) -> Mono.just(result));                 // Step 8: Return
```

Each step has access to all previous results through the accumulated tuple. This avoids
deeply nested callback chains while maintaining type safety.

---

## Appendix B: KIRun Function Registration

Each service registers its methods as `ReactiveFunction` instances in the KIRun runtime
system. This enables the no-code platform to invoke service methods through the KIRun
execution engine.

### Registration Pattern

```java
@PostConstruct
private void init() {
    this.functions.addAll(super.getCommonFunctions(NAMESPACE, Ticket.class, classSchema, gson));

    this.functions.add(AbstractServiceFunction.createServiceFunction(
        NAMESPACE,                                           // Function namespace
        "CreateRequest",                                     // Function name
        ClassSchema.ArgSpec.ofRef("ticketRequest", ...),     // Input parameter
        "created",                                           // Output name
        Schema.ofRef("EntityProcessor.DTO.Ticket"),          // Output schema
        gson,                                                // Serializer
        self::createRequest));                               // Method reference
}
```

### Available Functions Per Service

| Service | Functions |
|---------|----------|
| TicketService | CreateRequest, CreateForCampaign, CreateForWebsite, UpdateStageStatus, ReassignTicket, GetTicketProductComm, UpdateTag |
| OwnerService | CreateRequest |
| StageService | CreateRequest, GetAllValues, GetAllValuesInOrder, ReorderStages |
| TaskService | CreateRequest, SetReminder, SetTaskCompleted, SetTaskCancelled |
| NoteService | CreateRequest |
| CampaignService | CreateRequest |
| PartnerService | CreateRequest, GetLoggedInPartner, UpdateLoggedInPartnerVerificationStatus, ToggleLoggedInPartnerDnc, UpdatePartnerVerificationStatus, TogglePartnerDnc |
| ProductCommService | CreateRequest, GetDefault |
| ProductTicketCRuleService | CreateMultiple |
| ProductWalkInFormService | GetWalkInFromUsers, GetWalkInTickets, GetWalkInProduct, CreateWalkInTicket, GetWalkInFormResponse |

---

## Appendix C: Inter-Service Communication

### Feign Clients

| Client | Target Service | Purpose |
|--------|---------------|---------|
| `IFeignCoreService` | Core | Connection details, documents |
| `IFeignMessageService` | Message | Exotel call integration |
| Security Service | Security | User, client, and hierarchy resolution |

### Security Service Calls

The entity-processor makes frequent calls to the security service for:

- `getClientById()` -- Validate client existence
- `getUserInternal()` -- Resolve user details for activity logging
- `getClientByCode()` -- Resolve client from code
- `getManagingClientIds()` -- Partner client hierarchy
- `hasReadAccess()` -- App/client access validation
- `getClientUserInternal()` -- List users for walk-in forms
- `readClientPageFilterInternal()` -- Paginated client listing
- `readUserPageFilterInternal()` -- Paginated user listing

---

## Appendix D: Database Schema Overview

The entity-processor uses the `entity_processor` MySQL schema with the following primary
tables (managed by JOOQ):

| Table | Primary Entity |
|-------|---------------|
| `entity_processor_tickets` | Ticket |
| `entity_processor_owners` | Owner |
| `entity_processor_products` | Product |
| `entity_processor_product_templates` | ProductTemplate |
| `entity_processor_product_comms` | ProductComm |
| `entity_processor_stages` | Stage (and Status as children) |
| `entity_processor_activities` | Activity |
| `entity_processor_tasks` | Task |
| `entity_processor_task_types` | TaskType |
| `entity_processor_notes` | Note |
| `entity_processor_campaigns` | Campaign |
| `entity_processor_partners` | Partner |
| `entity_processor_product_ticket_c_rules` | ProductTicketCRule |
| `entity_processor_product_ticket_ru_rules` | ProductTicketRuRule |
| `entity_processor_ticket_c_user_distributions` | TicketCUserDistribution |
| `entity_processor_ticket_ru_user_distributions` | TicketRuUserDistribution |
| `entity_processor_ticket_duplication_rules` | TicketDuplicationRule |
| `entity_processor_ticket_pe_duplication_rules` | TicketPeDuplicationRule |
| `entity_processor_product_walk_in_forms` | ProductWalkInForm |
| `entity_processor_product_template_walk_in_forms` | ProductTemplateWalkInForm |

All tables follow the JOOQ code-generation pattern with `R2DBC` for reactive database
access.

---

*This document was generated from the entity-processor source code in the nocode-saas
repository. For the most current behavior, refer to the Java source files directly.*
