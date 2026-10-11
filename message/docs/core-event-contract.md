# Message service to core: the event contract

Any Modlix app can own a WhatsApp number or an Exotel call account. To do that it uses an EventAction and a server
function. Nothing in the message service or core changes per app. This page describes everything an app relies on:
- who owns a number or a call,
- the core events and their payloads,
- the live-update stream,
- the `CoreServices.Message.*` functions.

The step-by-step recipe for an app is the AppBuilder pattern `kirun-whatsapp-inbox`
(`nocode-ai/app/agents/appbuilder/aicontext/patterns/kirun-whatsapp-inbox/README.md`).

## 1. Who owns what

| Thing | Where the owner is read from | Values |
|---|---|---|
| WhatsApp number (bridge session) | `message_whatsapp_phone_numbers.OWNER_SERVICE`, set when the number is linked | `core`, `entity-processor` |
| Outbound call | `message_exotel_calls.OWNER_SERVICE`, set when the call is placed | `core`, `entity-processor` |
| Inbound call | Set when the connect applet creates the call. See "Inbound calls" below | `core`, `entity-processor`, or none |

**Linking a WhatsApp number.**
- `CoreServices.Message.LinkWhatsappNumber` always links with owner `core`.
- entity-processor names itself (`entity-processor`) when it links a number, and a page cannot override that.
- If the request names no owner, `message.bridge.default-owner-service` applies. Its default is `core`.
- Existing rows keep their owner; the default is read only when a row is created.
- On local, 2026-10-09: 68 rows are `entity-processor` and 2 are NULL (ids 16 and 17, FIN, inactive, Meta Cloud numbers). A NULL owner parks every event of that number.

**Inbound calls.** The owner is decided once, when the connect applet creates the call:
1. The call account first: the Exotel connection the call arrived on, for the app and client in the request. Its `connectionDetails.ownerService` names the owner, for example `{"accountSid": "...", "callerId": "080...", "ownerService": "core"}`.
2. If that is empty, the service that answered the applet. That service sends `ownerService` in the connect request; entity-processor sends `entity-processor`.
3. If neither says, there is no owner. The service logs an ERROR naming the call, app, client and connection, and every status event of that call is parked in the dispatch outbox.

If both are set and they disagree, the account wins and a WARN is logged.

**Outbound calls.**
- `CoreServices.Message.MakeCall` stamps `core`.
- entity-processor's internal make stamps `entity-processor`.
- The public `/api/message/call/exotel/make` takes the account's `connectionDetails.ownerService`.
- An outbound call with no owner at all goes to `message.call.default-owner-service` (default `entity-processor`). This is only for rows from before owners were recorded. Inbound calls never use this setting.

**Delivery.**
- An event for a number or call owned by `core` goes through the dispatch outbox. The message service's `CoreEventHandler` then publishes it as a core event on the RabbitMQ `events` exchange, for the number's or call's own `appCode` and `clientCode`, and core's `EventsQueListener` runs the app's EventAction of that name.
- "Delivered" means the broker took the event. If the app has no EventAction of that name, core drops the event. Core now logs a WARN for every such hand-off event:

  ```
  No EventAction 'WHATSAPP_INBOUND' for app 'X' client 'Y': the event is dropped. ... define an EventAction named WHATSAPP_INBOUND in app 'X' ...
  ```

  Any other event with no EventAction is logged once an hour for each event, app and client.

## 2. Core events

Both events run under an anonymous context of the event's client, so a `CoreServices.Storage.*` step in the function
writes to that client's database (`<CLIENT>_<app>`). The EventAction is looked up as any core object is: the client's own
first, then its base (usually SYSTEM's).

### `WHATSAPP_INBOUND`

One event name covers everything that happens on a number; `eventType` says which kind it is.

| `eventType` | Meaning |
|---|---|
| `INBOUND_MESSAGE` | A customer wrote, or (with `outbound: true`) the tenant wrote from the handset or WhatsApp Web |
| `MESSAGE_STATUS` | A message was sent, delivered, read or failed (`messageStatus`, `failureReason`) |
| `MEDIA_READY` | An attachment finished downloading. It carries the same `metaMessageId` as the message it completes |
| `PROFILE_PICTURE` | The customer's profile picture changed (`profilePictureFileDetail`, `profilePictureId`) |
| `MESSAGE_EDIT` | A message was edited. It carries the new `bodyText` |

Payload. Every event carries these fields; the message fields are null when they do not apply:

| Field | Type | Notes |
|---|---|---|
| `appCode`, `clientCode` | string | the number's tenant |
| `eventType` | string | see the table above |
| `numberId` | number | the number's row id (`message_whatsapp_phone_numbers.ID`); the same as `whatsappPhoneNumberId` |
| `bridgeSessionId` | string | the number's session code: the `sessionCode` that SendWhatsapp and the number functions take |
| `whatsappPhoneNumber` | string | the tenant's number |
| `metaMessageId` | string | the message id. Use it as the idempotency key: delivery is at least once |
| `customerWaId`, `customerDialCode`, `customerPhoneNumber`, `pushName` | | the customer |
| `from`, `to`, `outbound` | | the direction |
| `messageType`, `bodyText`, `occurredAt` | | the message; `occurredAt` is WhatsApp's own timestamp |
| `messageStatus`, `failureReason` | | for `MESSAGE_STATUS` |
| `mediaFileDetail`, `mediaMimeType`, `mediaFileName`, `mediaSize`, `mediaDurationSeconds`, `mediaIsVoiceNote`, `mediaThumbnailFileDetail`, `mediaPageCount`, `mediaError` | | for media |
| `reactionToMessageId`, `buttons`, `backfilled`, `sessionIsDefault`, `productId` | | as named |

Example (INBOUND_MESSAGE):

```json
{
  "appCode": "leadzumptwo", "clientCode": "FIN", "eventType": "INBOUND_MESSAGE",
  "numberId": 81, "bridgeSessionId": "WS7K2M", "whatsappPhoneNumber": "+919880262266",
  "metaMessageId": "3EB0C4A1F2", "customerWaId": "919876543210", "customerDialCode": 91,
  "customerPhoneNumber": "9876543210", "pushName": "Ravi", "from": "919876543210", "to": "919880262266",
  "outbound": false, "messageType": "text", "bodyText": "Is the 2BHK still available?",
  "occurredAt": "2026-10-09T10:15:02", "backfilled": false
}
```

### `CALL_EVENT`

Fired for every status change of a call owned by `core`. `eventType` is `CALL_STATUS`.

| Field | Notes |
|---|---|
| `appCode`, `clientCode`, `eventType` | as above. `numberId` is always null: Exotel numbers live in the tenant's connection |
| `providerCallId` | Exotel's CallSid. Key the call row on it; `MakeCall` returns it as `call.sid` |
| `parentCallSid`, `accountSid`, `callProvider`, `connectionName`, `productId` | |
| `outbound`, `direction`, `from`, `to`, `fromDialCode`, `toDialCode`, `callerId`, `customerDialCode`, `customerPhoneNumber` | |
| `callStatus`, `leg1Status`, `leg2Status`, `answeredBy` | the status text as Exotel names it |
| `startTime`, `endTime`, `duration`, `conversationDuration`, `price`, `recordingUrl` | |

Example:

```json
{
  "appCode": "leadzumptwo", "clientCode": "FIN", "eventType": "CALL_STATUS", "numberId": null,
  "providerCallId": "a1b2c3d4e5", "accountSid": "fincity1", "callProvider": "exotel", "outbound": true,
  "direction": "outbound-api", "from": "09880262266", "to": "09876543210", "callerId": "08047000000",
  "callStatus": "completed", "startTime": "2026-10-09T10:20:00", "endTime": "2026-10-09T10:23:41",
  "duration": 221, "conversationDuration": 205, "recordingUrl": "https://..."
}
```

### Wiring the EventAction

Create the EventAction in the app, named after the event, with a `CALL_CORE_FUNCTION` task:

```json
{ "name": "WHATSAPP_INBOUND", "appCode": "myapp", "clientCode": "SYSTEM",
  "tasks": { "one": { "key": "one", "order": 0, "type": "CALL_CORE_FUNCTION",
    "parameters": { "namespace": "MyApp", "name": "onWhatsappEvent", "functionParameterName": "event" } } } }
```

The function receives the whole payload as its OBJECT parameter `event`.

## 3. Live updates: the WhatsApp stream

```
GET /api/message/events/whatsapp/stream?appCode=<app>&clientCode=<tenant>     (text/event-stream)
```

- It is per tenant, not per user. The caller's client must have access to the app (or be SYSTEM). `clientCode` must be the caller's own client, or the caller's DIRECT managing client when that client is not SYSTEM and has write access to the app (the partner case). Anything else is a 403.
- Each event is sent with no SSE `event:` name, so listen for `message`. Its `data` is JSON:

  ```json
  { "id": "uuid", "eventType": "WHATSAPP_CONVERSATION_CHANGED", "timestamp": "...", "appCode": "leadzumptwo",
    "clientCode": "FIN", "data": { "eventType": "INBOUND_MESSAGE", "numberId": 81, "sessionCode": "WS7K2M",
    "messageId": "3EB0C4A1F2", "customerWaId": "919876543210", "customerPhoneNumber": "9876543210", "outbound": false } }
  ```

- A partner (a CUSTOMER-level client, or a caller watching another client) gets events without `customerWaId` and `customerPhoneNumber`.
- The stream is a nudge, not the data. It fires when core RECEIVES the event, before the app's function has written its row. The page re-reads through its own server function a moment later (about 1.5s).
- The stream is held in memory on one message instance. A page connected to another instance does not hear the event.

## 4. `CoreServices.Message.*` functions

They work only inside a server function definition. A direct call is a 403. The resolved `appCode` must be one the caller's client can read.

| Function | Parameters | Output (`output` event) | Tenant rule |
|---|---|---|---|
| `SendWhatsapp` | `to`, `text` or `filePath` (+ `kind`, `mimeType`, `fileName`, `voiceNote`, `resourceType`), `sessionCode` (blank means the tenant's default number), `appCode`, `clientCode` | `result` (the message service's reply), `sessionCode` | own client, or the direct managing client (partner) |
| `MakeCall` | `toNumber`, `toCountryCode`, `callerId`, `callerIdCountryCode`, `connectionName`, `appCode`, `clientCode` | `call` (`call.sid` = `providerCallId` of later CALL_EVENTs); owner `core` | own client, or the direct managing client |
| `LinkWhatsappNumber` | `phoneNumber`, `productId`, `appCode`, `clientCode` | `session` (`code` = sessionCode, state); owner `core` | own client only |
| `ListWhatsappNumbers` | `appCode`, `clientCode` | `numbers` (rows with `code`, `displayPhoneNumber`, `sessionState`, `isDefault`, ...) | own client, or the direct managing client |
| `GetWhatsappPairingCode` | `sessionCode`, `appCode`, `clientCode` | `pairing` (QR payload while the number is pairing) | own client only |
| `RemoveWhatsappNumber` | `sessionCode`, `appCode`, `clientCode` | `removed` (true) | own client only |

- Blank `appCode` means the app that owns the running function, then the request's app.
- Blank `clientCode` means the caller's own client.
- Inside an event, the caller is the event's client.

The app's server function decides who may link or send. Gate it with the function's permission and with
`kirun-gate-by-authority`. The message service checks nothing beyond the tenant.

## 5. Not covered yet

- There is no generic inbound-call applet for core apps. Exotel's connect applet must reach a service that picks the agent to ring; today that is entity-processor. The owner of the call can already be `core` through the account's `ownerService`.
- No API moves an existing number to another owner. Unlink it and link it again with the new owner.
