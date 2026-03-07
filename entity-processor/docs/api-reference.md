# Entity Processor - API Reference

## Table of Contents

- [Overview](#overview)
- [Base URL and Common Headers](#base-url-and-common-headers)
- [Authentication](#authentication)
- [Common Models](#common-models)
- [Ticket API](#ticket-api)
- [Activity API](#activity-api)
- [Owner API](#owner-api)
- [Product API](#product-api)
- [Product Template API](#product-template-api)
- [Stage API](#stage-api)
- [Product Communications API](#product-communications-api)
- [Campaign API](#campaign-api)
- [Partner API](#partner-api)
- [Task API](#task-api)
- [Task Type API](#task-type-api)
- [Note API](#note-api)
- [Rule APIs](#rule-apis)
- [Walk-In Form API](#walk-in-form-api)
- [Function Execution API](#function-execution-api)
- [Schema API](#schema-api)
- [Analytics API](#analytics-api)
- [Open (Public) APIs](#open-public-apis)
- [Error Responses](#error-responses)
- [Pagination and Filtering](#pagination-and-filtering)

---

## Overview

The Entity Processor API provides RESTful endpoints for managing CRM entities in the Modlix platform. All endpoints are reactive and return JSON responses. The API is accessible through the Gateway at port 8080, which routes to the Entity Processor running on port 8009.

### API Version

The API does not use versioning in the URL path. All endpoints are prefixed with `/api/entity/processor/`.

### Content Type

All request and response bodies use `application/json`.

### Response Format

Successful responses wrap the entity or page object directly in the response body. Error responses use the `GenericException` format with an HTTP status code and error message.

---

## Base URL and Common Headers

### Base URL

```
http://{gateway-host}:8080/api/entity/processor
```

### Required Headers

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `Authorization` | String | Yes* | Bearer JWT token for authenticated endpoints |
| `appCode` | String | Yes | Application code identifying the application context |
| `clientCode` | String | Yes | Client code identifying the tenant organization |
| `Content-Type` | String | Yes (for POST/PATCH/PUT) | Must be `application/json` |

*Not required for `/open/**` endpoints.

---

## Authentication

The Entity Processor relies on the Security microservice for authentication. Requests must include a valid JWT token in the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

The Gateway validates the token and injects the `ContextAuthentication` into the reactive security context, which the Entity Processor uses to construct `ProcessorAccess`.

### Authority Requirements

Each endpoint requires specific authorities. Authority names follow the pattern:
```
Authorities.{EntityName}_{ACTION}
```

Common authorities:
| Authority | Description |
|-----------|-------------|
| `Authorities.Ticket_CREATE` | Create tickets |
| `Authorities.Ticket_READ` | Read tickets |
| `Authorities.Ticket_UPDATE` | Update tickets |
| `Authorities.Ticket_DELETE` | Delete tickets |
| `Authorities.Owner_CREATE` | Create owners |
| `Authorities.Owner_READ` | Read owners |
| `Authorities.Product_CREATE` | Create products |
| `Authorities.Stage_CREATE` | Create stages |
| `Authorities.Task_CREATE` | Create tasks |
| `Authorities.Note_CREATE` | Create notes |
| `Authorities.Campaign_CREATE` | Create campaigns |
| `Authorities.Partner_CREATE` | Create partners |

---

## Common Models

### Identity

The `Identity` type is used throughout the API to reference entities. It can be either a numeric ID or a string CODE:

```json
// Numeric ID form
12345

// String CODE form
"TKT00001"

// Object form (used in request bodies)
{
    "id": 12345
}
// or
{
    "code": "TKT00001"
}
```

### PhoneNumber

```json
{
    "countryCode": 91,
    "number": "9876543210"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `countryCode` | Integer | Yes | International dialing code (e.g., 91 for India, 1 for US) |
| `number` | String | Yes | Phone number without country code |

### Email

```json
{
    "address": "user@example.com"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `address` | String | Yes | Email address |

### ProcessorStatus

Enum values representing entity status.

### Tag

Enum values for ticket tagging. Tags are used to categorize tickets beyond the pipeline stage system.

### Pageable

Standard Spring Data pagination parameters:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | 0 | Page number (0-indexed) |
| `size` | Integer | 20 | Page size |
| `sort` | String | - | Sort field and direction (e.g., `createdAt,desc`) |

### AbstractCondition

Complex filter conditions for querying entities. Supports nested AND/OR conditions:

```json
{
    "conditions": [
        {
            "field": "source",
            "operator": "EQUALS",
            "value": "Website"
        },
        {
            "field": "createdAt",
            "operator": "BETWEEN",
            "value": "2024-01-01T00:00:00",
            "toValue": "2024-12-31T23:59:59"
        }
    ],
    "operator": "AND"
}
```

Supported operators:
| Operator | Description | Fields |
|----------|-------------|--------|
| `EQUALS` | Exact match | `value` |
| `NOT_EQUALS` | Not equal | `value` |
| `LESS_THAN` | Less than | `value` |
| `LESS_THAN_EQUAL` | Less than or equal | `value` |
| `GREATER_THAN` | Greater than | `value` |
| `GREATER_THAN_EQUAL` | Greater than or equal | `value` |
| `BETWEEN` | Range | `value`, `toValue` |
| `IN` | In list | `multiValue` |
| `NOT_IN` | Not in list | `multiValue` |
| `LIKE` | Pattern match | `value` |
| `IS_NULL` | Null check | - |
| `IS_NOT_NULL` | Not null check | - |
| `STRING_LOOSE` | Case-insensitive LIKE | `value` |

---

## Ticket API

Tickets (also known as Deals in the LeadZump application) are the primary entity in the Entity Processor, representing individual opportunities or leads in the CRM pipeline.

### Base Path: `/api/entity/processor/tickets`

### Create Ticket

Creates a new ticket from a structured request. Handles duplicate detection, owner creation, stage assignment, and user assignment automatically.

```
POST /api/entity/processor/tickets/req
```

**Authority Required**: `Authorities.Ticket_CREATE`

**Request Body** (`TicketRequest`):
```json
{
    "productId": {
        "id": 100
    },
    "phoneNumber": {
        "countryCode": 91,
        "number": "9876543210"
    },
    "email": {
        "address": "john@example.com"
    },
    "name": "John Doe",
    "description": "Interested in premium plan",
    "source": "Website",
    "subSource": "Landing Page v2",
    "comment": "Initial inquiry from website form",
    "noteRequest": {
        "content": "Customer showed interest in premium features"
    }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | Identity | Yes | Product to associate the ticket with |
| `phoneNumber` | PhoneNumber | Conditional | Phone number (required if no email) |
| `email` | Email | Conditional | Email address (required if no phone) |
| `name` | String | No | Contact name |
| `description` | String | No | Ticket description |
| `source` | String | Yes | Lead source (e.g., "Website", "Campaign") |
| `subSource` | String | No | Sub-source for granularity |
| `comment` | String | No | Initial comment |
| `noteRequest` | NoteRequest | No | Optional initial note |
| `dnc` | Boolean | No | Do-Not-Call flag (for Business Partner tickets) |

**Response** (200 OK):
```json
{
    "id": 12345,
    "code": "TKT00001",
    "name": "John Doe",
    "description": "Interested in premium plan",
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
    "tag": null,
    "metaData": null,
    "version": 0,
    "appCode": "myapp",
    "clientCode": "MYCLIENT",
    "createdBy": 2001,
    "createdAt": "2024-03-04T10:30:00",
    "updatedBy": 2001,
    "updatedAt": "2024-03-04T10:30:00"
}
```

**Processing Flow**:
1. Validate that source information is provided
2. Resolve product from Identity (ID or CODE)
3. Check DNC status for Business Partner users
4. Run duplicate detection rules
5. If duplicate found: create RE_INQUIRY activity, throw duplicate error (or silently for outside users)
6. Set default stage from product template if not provided
7. Apply user assignment rules (round-robin, random, or condition-based)
8. Create or link owner based on phone/email
9. Create the ticket
10. Create initial note (if provided)
11. Log CREATE activity

### Update Ticket Stage/Status

Updates the pipeline stage and/or status of a ticket. May trigger automatic reassignment.

```
PATCH /api/entity/processor/tickets/req/{id}/stage
```

**Authority Required**: `Authorities.Ticket_UPDATE`

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | Identity | Ticket ID or CODE |

**Request Body** (`TicketStatusRequest`):
```json
{
    "stageId": {
        "id": 302
    },
    "statusId": {
        "id": 402
    },
    "taskRequest": {
        "title": "Follow up with customer",
        "dueDate": "2024-03-10T10:00:00",
        "taskTypeId": {
            "id": 1
        }
    },
    "comment": "Moving to negotiation phase"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stageId` | Identity | Yes | Target stage ID |
| `statusId` | Identity | No | Target status ID within the stage |
| `taskRequest` | TaskRequest | No | Optional task to create with stage change |
| `comment` | String | No | Comment for the activity log |

**Response** (200 OK): Updated Ticket object

**Processing Flow**:
1. Validate that stageId is provided
2. Fetch the ticket by identity
3. Resolve stage/status from the product template
4. If stage unchanged and no status specified, return unchanged ticket
5. Update ticket stage and status
6. Create optional task
7. Log STAGE_UPDATE and STATUS_CREATE activities
8. If stage changed, trigger automatic reassignment based on rules

### Update Ticket Tag

Updates the tag on a ticket.

```
PATCH /api/entity/processor/tickets/req/{id}/tag
```

**Authority Required**: `Authorities.Ticket_UPDATE`

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | Identity | Ticket ID or CODE |

**Request Body** (`TicketTagRequest`):
```json
{
    "tag": "HOT",
    "taskRequest": {
        "title": "Priority follow-up for hot lead",
        "dueDate": "2024-03-05T10:00:00"
    },
    "comment": "Marked as hot lead after demo"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tag` | Tag | Yes | Tag enum value |
| `taskRequest` | TaskRequest | No | Optional task to create |
| `comment` | String | No | Comment for activity log |

**Response** (200 OK): Updated Ticket object

### Reassign Ticket

Reassigns a ticket to a different user.

```
PATCH /api/entity/processor/tickets/req/{id}/reassign
```

**Authority Required**: `Authorities.Ticket_UPDATE`

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | Identity | Ticket ID or CODE |

**Request Body** (`TicketReassignRequest`):
```json
{
    "userId": 2002,
    "comment": "Reassigning to sales specialist"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | ULong | Yes | Target user ID (must be in sub-organization) |
| `comment` | String | No | Reason for reassignment |

**Response** (200 OK): Updated Ticket object

**Validation**: The target user must exist in the current user's sub-organization hierarchy.

### Get Ticket Product Communications

Retrieves the communication configuration for a ticket's product.

```
GET /api/entity/processor/tickets/req/{id}/product-comms
    ?connectionType=CALL
    &connectionSubType=EXOTEL
```

**Authority Required**: `Authorities.Ticket_READ`

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | Identity | Ticket ID or CODE |

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `connectionType` | ConnectionType | Yes | Communication type (CALL, EMAIL, SMS, WHATSAPP) |
| `connectionSubType` | ConnectionSubType | Yes | Service provider (EXOTEL, TWILIO, etc.) |

**Response** (200 OK): `ProductComm` object with connection details

### Create Ticket from DCRM Import

Creates a ticket imported through the DCRM (Distributed CRM) system, typically from a Business Partner.

```
POST /api/entity/processor/tickets/req/DCRM
```

**Required Headers**:
| Header | Type | Description |
|--------|------|-------------|
| `appCode` | String | Application code |
| `clientCode` | String | Client code |

**Request Body** (`TicketPartnerRequest`):
```json
{
    "productId": {
        "id": 100
    },
    "stageId": {
        "id": 301
    },
    "statusId": {
        "id": 401
    },
    "assignedUserId": 2001,
    "clientId": 5001,
    "phoneNumber": {
        "countryCode": 91,
        "number": "9876543210"
    },
    "email": {
        "address": "john@example.com"
    },
    "name": "John Doe",
    "description": "DCRM imported lead",
    "source": "Partner Portal",
    "subSource": "Referral",
    "createdDate": "2024-02-28T15:00:00",
    "activityJson": {
        "notes": "Imported from partner system"
    }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | Identity | Yes | Product to associate with |
| `stageId` | Identity | Yes | Initial stage |
| `statusId` | Identity | Yes | Initial status within the stage |
| `assignedUserId` | ULong | Yes | User to assign the ticket to |
| `clientId` | ULong | No | Business partner client ID |
| `phoneNumber` | PhoneNumber | Yes | Contact phone number |
| `email` | Email | No | Contact email |
| `name` | String | No | Contact name |
| `description` | String | No | Description |
| `source` | String | No | Lead source |
| `subSource` | String | No | Lead sub-source |
| `createdDate` | LocalDateTime | No | Original creation date |
| `activityJson` | Map | No | Additional activity metadata |

**Response** (200 OK): Created Ticket object

### Get Ticket (Internal)

Internal endpoint for fetching a ticket by other microservices.

```
GET /api/entity/processor/tickets/internal/{id}
    ?appCode=myapp
    &clientCode=MYCLIENT
```

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appCode` | String | Yes | Application code |
| `clientCode` | String | Yes | Client code |

**Response** (200 OK): Ticket object

### Read Ticket by Identity

Standard read endpoint inherited from `BaseProcessorController`.

```
GET /api/entity/processor/tickets/req/{id}
```

**Authority Required**: `Authorities.Ticket_READ`

**Response** (200 OK): Ticket object

### Read Ticket (Eager)

Reads a ticket with related entities loaded inline.

```
GET /api/entity/processor/tickets/req/{id}/eager
    ?fields=ownerId,productId,stage,status,assignedUserId
```

**Authority Required**: `Authorities.Ticket_READ`

**Query Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `fields` | String | Comma-separated list of relationship fields to eagerly load |

**Response** (200 OK): Map with ticket data and nested related entities

### Read Tickets Page

Paginated list of tickets with optional filtering and eager loading.

```
POST /api/entity/processor/tickets/page
    ?page=0&size=20&sort=createdAt,desc
    &fields=ownerId,productId,stage
    &timezone=Asia/Kolkata
```

**Authority Required**: `Authorities.Ticket_READ`

**Request Body**: Optional `AbstractCondition` for filtering

**Response** (200 OK): Spring Data `Page<Map<String, Object>>` (when eager) or `Page<Ticket>` (when not eager)

### Update Ticket

Standard update endpoint.

```
PATCH /api/entity/processor/tickets/req/{id}
```

**Authority Required**: `Authorities.Ticket_UPDATE`

**Request Body**: Ticket object with `version` field matching current version

**Response** (200 OK): Updated Ticket object

### Update Ticket (Map-based)

Partial update using a map of field-value pairs.

```
PATCH /api/entity/processor/tickets/{id}
```

**Authority Required**: `Authorities.Ticket_UPDATE`

**Request Body**: `Map<String, Object>` with fields to update

**Response** (200 OK): Updated Ticket object

---

## Activity API

Activities are immutable audit trail records that track all significant actions on entities.

### Base Path: `/api/entity/processor/activities`

### Read Activities Page

Paginated list of activities for a ticket.

```
POST /api/entity/processor/activities/page
    ?page=0&size=20&sort=activityDate,desc
    &ticketId=12345
```

**Authority Required**: `Authorities.Activity_READ`

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `ticketId` | Identity | Yes | Ticket to fetch activities for |

**Request Body**: Optional `AbstractCondition` for additional filtering

**Response** (200 OK): `Page<Activity>` with activity records

### Read Activities (Eager)

Paginated activities with eager-loaded relationships.

```
POST /api/entity/processor/activities/page/eager
    ?page=0&size=20&ticketId=12345
    &fields=ticketId,taskId,noteId,stageId,statusId
```

**Response** (200 OK): `Page<Map<String, Object>>` with nested related data

### Create Call Log

Creates a call log activity entry.

```
POST /api/entity/processor/activities/call-log
```

**Request Body** (`CallLogRequest`):
```json
{
    "ticketId": {
        "id": 12345
    },
    "comment": "Discussed pricing options",
    "isOutbound": true,
    "callStatus": "COMPLETED",
    "callDate": "2024-03-04T14:30:00",
    "callDuration": 300
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ticketId` | Identity | Yes | Associated ticket |
| `comment` | String | No | Call notes |
| `isOutbound` | Boolean | No | Whether the call was outbound |
| `callStatus` | CallStatus | No | Call outcome status |
| `callDate` | LocalDateTime | No | When the call occurred |
| `callDuration` | Integer | No | Duration in seconds |

---

## Owner API

Owners (also known as Leads in LeadZump) represent contacts associated with tickets.

### Base Path: `/api/entity/processor/owners`

### Create Owner

```
POST /api/entity/processor/owners/req
```

**Authority Required**: `Authorities.Owner_CREATE`

**Request Body** (`OwnerRequest`):
```json
{
    "phoneNumber": {
        "countryCode": 91,
        "number": "9876543210"
    },
    "email": {
        "address": "john@example.com"
    },
    "name": "John Doe",
    "description": "Potential enterprise customer",
    "source": "Referral",
    "subSource": "Partner A"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumber` | PhoneNumber | No | Contact phone |
| `email` | Email | No | Contact email |
| `name` | String | No | Full name |
| `description` | String | No | Description |
| `source` | String | No | Lead source |
| `subSource` | String | No | Sub-source |

**Response** (200 OK): Created Owner object

### Read Owner

```
GET /api/entity/processor/owners/req/{id}
```

**Response** (200 OK): Owner object

### Update Owner

```
PATCH /api/entity/processor/owners/req/{id}
```

**Response** (200 OK): Updated Owner object (also updates all linked tickets)

### Read Owners Page

```
POST /api/entity/processor/owners/page
    ?page=0&size=20&sort=name,asc
```

**Response** (200 OK): `Page<Owner>`

---

## Product API

Products (also known as Projects in LeadZump) are containers that define the context for tickets including template, stages, and rules.

### Base Path: `/api/entity/processor/products`

### Create Product

```
POST /api/entity/processor/products/req
```

**Authority Required**: `Authorities.Product_CREATE`

**Request Body** (`ProductRequest`):
```json
{
    "name": "Premium Plan Sales",
    "description": "Sales pipeline for premium plans",
    "productTemplateId": {
        "id": 50
    }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Product name |
| `description` | String | No | Description |
| `productTemplateId` | Identity | No | Template to associate |

**Response** (200 OK): Created Product object

### Read Product

```
GET /api/entity/processor/products/req/{id}
```

**Response** (200 OK): Product object

### Read Products Page

```
POST /api/entity/processor/products/page
    ?page=0&size=20
```

**Response** (200 OK): `Page<Product>`

### Get Products (Internal)

```
GET /api/entity/processor/products/internal
    ?appCode=myapp&clientCode=MYCLIENT
```

For internal microservice-to-microservice calls.

### Update Product from Partner

```
POST /api/entity/processor/products/for-partner
```

Updates product data received from a business partner's system.

---

## Product Template API

Product Templates define the structure for products, including what type of pipeline (FORM, PIPELINE, WIZARD) and associated stages.

### Base Path: `/api/entity/processor/product-templates`

### Create Product Template

```
POST /api/entity/processor/product-templates/req
```

**Request Body**:
```json
{
    "name": "Standard Sales Pipeline",
    "description": "4-stage sales pipeline template",
    "productTemplateType": "PIPELINE"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Template name |
| `description` | String | No | Description |
| `productTemplateType` | ProductTemplateType | Yes | FORM, PIPELINE, or WIZARD |

**Response** (200 OK): Created ProductTemplate object

### Read Product Template

```
GET /api/entity/processor/product-templates/req/{id}
```

### Update Product Template

```
PATCH /api/entity/processor/product-templates/req/{id}
```

### Read Product Templates Page

```
POST /api/entity/processor/product-templates/page
    ?page=0&size=20
```

---

## Stage API

Stages define the steps in a pipeline. Stages are hierarchical - a stage can have child statuses.

### Base Path: `/api/entity/processor/stages`

### Create Stage

```
POST /api/entity/processor/stages/req
```

**Request Body** (`StageRequest`):
```json
{
    "name": "Negotiation",
    "description": "Active negotiation with customer",
    "productTemplateId": {
        "id": 50
    },
    "parentId": null,
    "stageType": "PIPELINE",
    "order": 3
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Stage name |
| `description` | String | No | Description |
| `productTemplateId` | Identity | Yes | Associated product template |
| `parentId` | ULong | No | Parent stage ID (for creating statuses) |
| `stageType` | StageType | Yes | Type of stage |
| `order` | Integer | No | Display order |

**Response** (200 OK): Created Stage object

### Read Stages for Product

```
GET /api/entity/processor/stages/product/{productId}
```

**Response** (200 OK): List of stages for the specified product's template

### Read Stages Page

```
POST /api/entity/processor/stages/page
    ?page=0&size=100
```

### Reorder Stages

```
PATCH /api/entity/processor/stages/reorder
```

**Request Body**: Map of stage ID to new order position

---

## Product Communications API

Product Communications define which communication channels are configured for a product.

### Base Path: `/api/entity/processor/product-comms`

### Create Product Communication

```
POST /api/entity/processor/product-comms/req
```

**Request Body**:
```json
{
    "productId": 100,
    "connectionType": "CALL",
    "connectionSubType": "EXOTEL",
    "connectionId": 500,
    "source": "Website",
    "subSource": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | ULong | Yes | Associated product |
| `connectionType` | ConnectionType | Yes | CALL, EMAIL, SMS, WHATSAPP |
| `connectionSubType` | ConnectionSubType | Yes | Provider (EXOTEL, TWILIO, etc.) |
| `connectionId` | ULong | No | Connection ID from Core service |
| `source` | String | No | Source-specific communication |
| `subSource` | String | No | Sub-source-specific communication |

### Read Product Communication

```
GET /api/entity/processor/product-comms/req/{id}
```

### Read Product Communications Page

```
POST /api/entity/processor/product-comms/page
    ?page=0&size=20
```

---

## Campaign API

Campaigns represent marketing campaigns that generate tickets.

### Base Path: `/api/entity/processor/campaigns`

### Create Campaign

```
POST /api/entity/processor/campaigns/req
```

**Request Body** (`CampaignRequest`):
```json
{
    "name": "Q1 Email Campaign",
    "description": "Email campaign for Q1 2024",
    "productId": 100,
    "campaignId": "fb_camp_12345",
    "platform": "FACEBOOK"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Campaign name |
| `description` | String | No | Description |
| `productId` | ULong | Yes | Associated product |
| `campaignId` | String | Yes | External campaign ID |
| `platform` | CampaignPlatform | No | Platform (FACEBOOK, GOOGLE, etc.) |

### Read Campaign

```
GET /api/entity/processor/campaigns/req/{id}
```

### Update Campaign

```
PATCH /api/entity/processor/campaigns/req/{id}
```

### Read Campaigns Page

```
POST /api/entity/processor/campaigns/page
    ?page=0&size=20
```

---

## Partner API

Partners represent business partner organizations.

### Base Path: `/api/entity/processor/partners`

### Create Partner

```
POST /api/entity/processor/partners/req
```

**Request Body** (`PartnerRequest`):
```json
{
    "name": "Partner Organization",
    "description": "Real estate partner",
    "clientId": 5001,
    "verificationStatus": "VERIFIED",
    "dnc": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Partner name |
| `description` | String | No | Description |
| `clientId` | ULong | No | Associated client ID |
| `verificationStatus` | PartnerVerificationStatus | No | Verification status |
| `dnc` | Boolean | No | Do-Not-Call flag |

### Read Partner

```
GET /api/entity/processor/partners/req/{id}
```

### Update Partner

```
PATCH /api/entity/processor/partners/req/{id}
```

### Read Partners Page

```
POST /api/entity/processor/partners/page
    ?page=0&size=20
```

---

## Task API

Tasks are to-do items associated with tickets or owners.

### Base Path: `/api/entity/processor/tasks`

### Create Task

```
POST /api/entity/processor/tasks/req
```

**Request Body** (`TaskRequest`):
```json
{
    "title": "Follow up call",
    "description": "Call customer about proposal",
    "ticketId": {
        "id": 12345
    },
    "ownerId": null,
    "taskTypeId": {
        "id": 1
    },
    "dueDate": "2024-03-10T10:00:00",
    "priority": "HIGH",
    "nextReminder": "2024-03-09T09:00:00"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | String | Yes | Task title |
| `description` | String | No | Task description |
| `ticketId` | Identity | Conditional | Associated ticket (required if no ownerId) |
| `ownerId` | Identity | Conditional | Associated owner (required if no ticketId) |
| `taskTypeId` | Identity | Yes | Task type |
| `dueDate` | LocalDateTime | No | Due date |
| `priority` | TaskPriority | No | HIGH, MEDIUM, LOW |
| `nextReminder` | LocalDateTime | No | Next reminder date |

**Response** (200 OK): Created Task object

### Mark Task Complete

```
PATCH /api/entity/processor/tasks/req/{id}/complete
```

**Response** (200 OK): Updated Task with completion date set

### Cancel Task

```
PATCH /api/entity/processor/tasks/req/{id}/cancel
```

**Response** (200 OK): Updated Task with cancellation date set

### Read Tasks for Ticket

```
GET /api/entity/processor/tasks/ticket/{ticketId}
    ?page=0&size=20
```

### Read Tasks Page

```
POST /api/entity/processor/tasks/page
    ?page=0&size=20
```

---

## Task Type API

Task Types define categories for tasks.

### Base Path: `/api/entity/processor/task-types`

### Create Task Type

```
POST /api/entity/processor/task-types/req
```

**Request Body**:
```json
{
    "name": "Follow Up",
    "description": "Follow-up tasks for leads"
}
```

### Read Task Types Page

```
POST /api/entity/processor/task-types/page
    ?page=0&size=50
```

---

## Note API

Notes are text entries associated with tickets or owners.

### Base Path: `/api/entity/processor/notes`

### Create Note

```
POST /api/entity/processor/notes/req
```

**Request Body** (`NoteRequest`):
```json
{
    "ticketId": {
        "id": 12345
    },
    "ownerId": null,
    "content": "Customer expressed interest in the enterprise plan. Follow up scheduled for next week."
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ticketId` | Identity | Conditional | Associated ticket |
| `ownerId` | Identity | Conditional | Associated owner |
| `content` | String | Yes | Note content |

**Response** (200 OK): Created Note object

### Update Note

```
PATCH /api/entity/processor/notes/req/{id}
```

### Delete Note

```
DELETE /api/entity/processor/notes/req/{id}
```

### Read Notes for Ticket

```
GET /api/entity/processor/notes/ticket/{ticketId}
    ?page=0&size=20
```

### Read Notes Page

```
POST /api/entity/processor/notes/page
    ?page=0&size=20
```

---

## Rule APIs

### Ticket Creation User Distribution

Defines how new tickets are distributed among users.

**Base Path**: `/api/entity/processor/ticket-c-distributions`

```
POST /api/entity/processor/ticket-c-distributions/req
```

**Request Body**:
```json
{
    "name": "Round Robin Sales Team",
    "ruleId": 200,
    "userIds": [2001, 2002, 2003]
}
```

### Ticket Read/Update User Distribution

Defines which users can read/update tickets.

**Base Path**: `/api/entity/processor/ticket-ru-distributions`

### Ticket Duplication Rules

Configures rules for detecting duplicate tickets.

**Base Path**: `/api/entity/processor/duplication-rules`

```
POST /api/entity/processor/duplication-rules/req
```

**Request Body**:
```json
{
    "productId": 100,
    "productTemplateId": 50,
    "source": "Website",
    "subSource": null,
    "maxStageId": 303,
    "condition": {
        "conditions": [
            {
                "field": "phoneNumber",
                "operator": "EQUALS"
            }
        ],
        "operator": "OR"
    }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | ULong | Conditional | Product scope (or productTemplateId) |
| `productTemplateId` | ULong | Conditional | Template scope |
| `source` | String | Yes | Source to match |
| `subSource` | String | No | Sub-source to match (null = wildcard) |
| `maxStageId` | ULong | Yes | Duplicate check only applies up to this stage |
| `condition` | AbstractCondition | No | Additional condition criteria |

### Product Ticket Creation Rules

**Base Path**: `/api/entity/processor/products/{id}/ticket-c-rules`

### Product Ticket Read/Update Rules

**Base Path**: `/api/entity/processor/products/{id}/ticket-ru-rules`

---

## Walk-In Form API

Walk-in forms allow creating tickets through public-facing forms.

### Base Path: `/api/entity/processor/product-walk-in-forms`

### Create Walk-In Form

```
POST /api/entity/processor/product-walk-in-forms/req
```

### Read Walk-In Form

```
GET /api/entity/processor/product-walk-in-forms/req/{id}
```

### Product Template Walk-In Forms

**Base Path**: `/api/entity/processor/product-template-walk-in-forms`

---

## Function Execution API

The Entity Processor exposes entity operations as callable KIRun functions.

### Base Path: `/api/entity/processor/functions`

### List Functions (Repository Filter)

```
GET /api/entity/processor/functions/repositoryFilter
    ?namespace=EntityProcessor.Ticket
```

**Response**: List of available functions in the namespace

### Find Function

```
GET /api/entity/processor/functions/repositoryFind
    ?namespace=EntityProcessor.Ticket
    &name=CreateRequest
```

### Execute Function

```
POST /api/entity/processor/functions/{namespace}/{name}/execute
```

**Request Body**: Function arguments as JSON

**Available Functions** per namespace:

| Namespace | Function | Description |
|-----------|----------|-------------|
| `EntityProcessor.Ticket` | `CreateRequest` | Create ticket from request |
| `EntityProcessor.Ticket` | `CreateForCampaign` | Create campaign ticket |
| `EntityProcessor.Ticket` | `CreateForWebsite` | Create website ticket |
| `EntityProcessor.Ticket` | `UpdateStageStatus` | Update stage/status |
| `EntityProcessor.Ticket` | `ReassignTicket` | Reassign ticket |
| `EntityProcessor.Ticket` | `UpdateTag` | Update tag |
| `EntityProcessor.Ticket` | `GetTicketProductComm` | Get communication config |

---

## Schema API

### Base Path: `/api/entity/processor/schemas`

### Find Schema

```
GET /api/entity/processor/schemas/repositoryFind
    ?namespace=EntityProcessor.DTO
    &name=Ticket
```

Returns the JSON schema definition for the specified entity type.

---

## Analytics API

### Base Path: `/api/entity/processor/analytics`

### Ticket Buckets

```
GET /api/entity/processor/analytics/tickets/buckets
    ?timePeriod=WEEKLY
    &startDate=2024-01-01
    &endDate=2024-03-31
```

### Ticket Date Count

```
GET /api/entity/processor/analytics/tickets/date-count
    ?startDate=2024-01-01
    &endDate=2024-03-31
```

### Ticket Status Count

```
GET /api/entity/processor/analytics/tickets/status-count
```

---

## Open (Public) APIs

These endpoints do not require authentication and are accessible publicly.

### Base Path: `/api/entity/processor/open`

### Create Ticket from Campaign

```
POST /api/entity/processor/open/tickets/req/campaigns
```

**Request Body** (`CampaignTicketRequest`):
```json
{
    "appCode": "myapp",
    "clientCode": "MYCLIENT",
    "leadDetails": {
        "firstName": "John",
        "lastName": "Doe",
        "fullName": null,
        "phone": {
            "countryCode": 91,
            "number": "9876543210"
        },
        "email": {
            "address": "john@example.com"
        },
        "source": "Facebook Ads",
        "subSource": "Q1 Campaign"
    },
    "campaignDetails": {
        "campaignId": "fb_camp_12345",
        "keyword": "premium plan"
    },
    "comment": "Lead from Facebook campaign",
    "noteRequest": {
        "content": "Customer clicked on premium plan ad"
    }
}
```

### Campaign Subscription Verification

```
GET /api/entity/processor/open/tickets/req/campaigns
    ?appCode=myapp&clientCode=MYCLIENT
```

Returns subscription status for the campaign module.

### Create Ticket from Website

```
POST /api/entity/processor/open/tickets/req/website/{productCode}
```

Creates a ticket from a website form submission. The `productCode` identifies which product the ticket belongs to.

**Request Body**: Same as `CampaignTicketRequest` but without `campaignDetails`.

### Website Subscription Verification

```
GET /api/entity/processor/open/tickets/req/website/{productCode}
    ?appCode=myapp&clientCode=MYCLIENT
```

### Exotel Incoming Call Handler

```
GET /api/entity/processor/open/call
    ?CallSid=abc123&From=919876543210&To=918001234567
```

Handles incoming call webhooks from Exotel. Returns call routing instructions.

### Walk-In Form (Public)

```
GET /api/entity/processor/open/forms/{id}
```

Returns the public form definition including fields, validation rules, and styling.

### Get Walk-In Product

```
GET /api/entity/processor/open/forms/{id}/product
```

Returns product information for the walk-in form.

### Get Walk-In Ticket by Phone

```
GET /api/entity/processor/open/forms/{id}/ticket
    ?phoneNumber=9876543210&dialCode=91
```

Looks up an existing ticket by phone number for a walk-in form.

### List Walk-In Tickets by Phone

```
GET /api/entity/processor/open/forms/{id}/tickets
    ?phoneNumber=9876543210&dialCode=91
```

### Get Walk-In Form Users

```
GET /api/entity/processor/open/forms/users
    ?appCode=myapp&clientCode=MYCLIENT
```

### Create Walk-In Ticket

```
POST /api/entity/processor/open/forms/{id}
```

**Request Body** (`WalkInFormTicketRequest`):
```json
{
    "phoneNumber": {
        "countryCode": 91,
        "number": "9876543210"
    },
    "email": {
        "address": "visitor@example.com"
    },
    "name": "Walk-in Visitor",
    "description": "Walk-in inquiry about premium plan",
    "subSource": "Office Visit",
    "assignedUserId": 2001,
    "stageId": {
        "id": 301
    },
    "statusId": {
        "id": 401
    }
}
```

---

## Error Responses

### Error Format

```json
{
    "status": 400,
    "message": "A Ticket already exists with ID: '12345' for the provided information. Please review that Ticket before proceeding."
}
```

### Common Error Codes

| HTTP Status | Message Key | Description |
|---|---|---|
| 400 | `identity_missing` | Required identifier not provided |
| 400 | `identity_info_missing` | Neither phone nor email provided |
| 400 | `duplicate_entity` | Duplicate ticket detected |
| 400 | `duplicate_entity_outside_user` | Duplicate for outside user (gentler message) |
| 400 | `ticket_stage_missing` | No stage configured for the product |
| 400 | `ticket_assignment_missing` | No user assignment possible |
| 400 | `stage_missing` | Stage not provided or not found |
| 400 | `template_stage_invalid` | Stage doesn't belong to the template |
| 400 | `invalid_user_access` | User not in sub-organization |
| 400 | `content_missing` | Required content not provided |
| 400 | `date_in_past` | Due date/reminder in the past |
| 400 | `version_mismatch` | Optimistic lock conflict |
| 400 | `website_entity_data_invalid` | Invalid website request data |
| 403 | `forbidden_create` | No permission to create |
| 403 | `forbidden_update` | No permission to update |
| 403 | `forbidden_permission` | Missing required authority |
| 403 | `product_forbidden_access` | No access to the product |
| 404 | `object_not_found` | Entity not found |
| 404 | `identity_wrong` | Entity with given identity not found |
| 412 | `version_mismatch` | Entity version conflict |
| 500 | `unknown_error` | Internal server error |

---

## Pagination and Filtering

### Pagination Parameters

All `page` endpoints accept standard Spring Data pagination:

```
?page=0          # Page number (0-indexed)
&size=20         # Items per page
&sort=name,asc   # Sort field and direction
```

### Filter Conditions

For `POST` page endpoints, pass an `AbstractCondition` in the request body:

```json
{
    "conditions": [
        {
            "field": "source",
            "operator": "EQUALS",
            "value": "Website"
        },
        {
            "field": "tag",
            "operator": "IN",
            "multiValue": ["HOT", "WARM"]
        },
        {
            "field": "createdAt",
            "operator": "GREATER_THAN_EQUAL",
            "value": "2024-01-01T00:00:00"
        }
    ],
    "operator": "AND"
}
```

### Eager Loading

Add the `fields` query parameter to load related entities:

```
?fields=ownerId,productId,stage,status,assignedUserId,campaignId
```

Each field name corresponds to a foreign key field on the entity. The response includes the full related entity data nested under the field name instead of just the ID.

### Timezone Support

Date-based queries support timezone conversion:

```
?timezone=Asia/Kolkata
?timezone=America/New_York
```

This ensures date-based filtering and display works correctly across time zones.
